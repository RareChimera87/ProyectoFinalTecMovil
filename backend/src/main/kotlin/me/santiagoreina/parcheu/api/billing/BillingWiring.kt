package me.santiagoreina.parcheu.api.billing

import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ImpersonatedCredentials
import me.santiagoreina.parcheu.api.graph.SecretManagerReader
import me.santiagoreina.parcheu.api.platform.config.Env
import org.jetbrains.exposed.v1.jdbc.Database
import java.time.Clock

/** Lo que el dominio billing expone al contenedor (E7.1). */
class BillingModule(
    val store: BillingStore,
    /** Implementación real de `EntitlementService` sobre [store] (la usa también el cupo). */
    val entitlements: StoredEntitlementService,
    val service: BillingService,
)

/**
 * Cableado con Postgres y la API de Google Play Developer. El cupo (`quotaModule`) lee la misma tabla
 * `entitlements` con su propio [StoredEntitlementService], así que Plus es el mismo en todo el backend.
 */
fun billingModule(env: Env, db: Database, clock: Clock, vars: Map<String, String> = System.getenv()): BillingModule {
    val store = ExposedBillingStore(db)
    val config = PlayConfig.fromEnv(env, vars)
    return BillingModule(store, StoredEntitlementService(store), BillingService(store, config.client, clock, config.packageName))
}

/** Todo en memoria (pruebas y valor por defecto del contenedor). [play] es la API de Play (un Fake en los tests). */
fun inMemoryBillingModule(
    clock: Clock,
    play: PlaySubscriptions = UnconfiguredPlaySubscriptions,
    store: BillingStore = InMemoryBillingStore(),
    packageName: String = BillingService.DEFAULT_PACKAGE_NAME,
): BillingModule = BillingModule(store, StoredEntitlementService(store), BillingService(store, play, clock, packageName))

/**
 * Configuración de la API de Play, solo de variables de entorno (en orden de preferencia):
 * - `PLAY_PACKAGE_NAME`: paquete de la app en Play (por defecto `me.santiagoreina.parcheu`, el flavor prod).
 * - `PLAY_SA_JSON`: la llave JSON de una cuenta de servicio, directa (solo desarrollo local; nunca en el repo).
 * - `PLAY_SERVICE_ACCOUNT`: correo de `sa-play` (lo define el Terraform de prod, E7.1); la API la suplanta con
 *   sus credenciales por defecto (`sa-api` tiene `serviceAccountTokenCreator` sobre ella), sin llaves.
 * - `SECRET_PLAY_SA`: nombre del secreto `play-sa` con una llave JSON (respaldo si no se puede suplantar); se
 *   lee la versión `latest` en el primer uso, para que el servicio arranque aunque el secreto no tenga valor.
 * - nada: en `ENV=local` la API queda sin configurar (usa `grant-plus`); en dev o prod falla al primer uso.
 *
 * Las credenciales se crean en el primer uso: un error de configuración no impide arrancar el servicio.
 */
class PlayConfig(val packageName: String, val client: PlaySubscriptions) {
    companion object {
        fun fromEnv(env: Env, vars: Map<String, String>): PlayConfig {
            val packageName = vars["PLAY_PACKAGE_NAME"].orNull() ?: BillingService.DEFAULT_PACKAGE_NAME
            val direct = vars["PLAY_SA_JSON"].orNull()
            val impersonated = vars["PLAY_SERVICE_ACCOUNT"].orNull()
            val secret = vars["SECRET_PLAY_SA"].orNull()
            val credentials: (() -> GoogleCredentials)? = when {
                direct != null -> { { GoogleCredentials.fromStream(direct.byteInputStream()) } }
                impersonated != null -> { { impersonate(impersonated) } }
                secret != null -> { { GoogleCredentials.fromStream(SecretManagerReader.latest(secret).inputStream()) } }
                else -> null
            }
            val client = when {
                credentials != null -> LazyPlaySubscriptions { GooglePlaySubscriptions.create(credentials(), packageName) }
                env == Env.LOCAL -> UnconfiguredPlaySubscriptions
                else -> LazyPlaySubscriptions {
                    error("Falta la cuenta de Play: define PLAY_SERVICE_ACCOUNT o SECRET_PLAY_SA (o PLAY_SA_JSON)")
                }
            }
            return PlayConfig(packageName, client)
        }

        /** Tokens de corta vida de [target] a partir de las credenciales por defecto (`sa-api` en Cloud Run). */
        private fun impersonate(target: String): GoogleCredentials = ImpersonatedCredentials.create(
            GoogleCredentials.getApplicationDefault(),
            target,
            null,
            listOf(GooglePlaySubscriptions.SCOPE),
            TOKEN_LIFETIME_SECONDS,
        )

        private const val TOKEN_LIFETIME_SECONDS = 3600

        private fun String?.orNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
    }
}

/** Crea el cliente real en el primer uso (y lo reintenta si falla, p. ej. secreto aún sin versión). */
internal class LazyPlaySubscriptions(private val factory: () -> PlaySubscriptions) : PlaySubscriptions {
    @Volatile
    private var delegate: PlaySubscriptions? = null

    private fun client(): PlaySubscriptions = delegate ?: synchronized(this) {
        delegate ?: factory().also { delegate = it }
    }

    override fun get(purchaseToken: String): PlaySubscription? = client().get(purchaseToken)

    override fun acknowledge(productId: String, purchaseToken: String) = client().acknowledge(productId, purchaseToken)
}
