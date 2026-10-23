package me.santiagoreina.parcheu.api.billing

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.HttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.androidpublisher.AndroidPublisher
import com.google.api.services.androidpublisher.model.SubscriptionPurchaseV2
import com.google.api.services.androidpublisher.model.SubscriptionPurchasesAcknowledgeRequest
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import me.santiagoreina.parcheu.contract.EntitlementState
import java.time.Instant

/** Estado de `subscriptionsv2.subscriptionState` (API de Google Play Developer, v3). */
enum class PlaySubscriptionState {
    PENDING,
    ACTIVE,
    PAUSED,
    IN_GRACE_PERIOD,
    ON_HOLD,
    CANCELED,
    EXPIRED,
    PENDING_PURCHASE_CANCELED,
    UNSPECIFIED,
    ;

    /**
     * Estado que se guarda en `entitlements` (arquitectura.md §9). Nulo si la compra aún no da derecho
     * a nada (pago pendiente, compra pendiente cancelada o estado desconocido): no se crea ni se toca la fila.
     */
    fun toEntitlementState(): EntitlementState? = when (this) {
        ACTIVE -> EntitlementState.ACTIVE
        IN_GRACE_PERIOD -> EntitlementState.IN_GRACE_PERIOD
        ON_HOLD -> EntitlementState.ON_HOLD
        PAUSED -> EntitlementState.PAUSED
        CANCELED -> EntitlementState.CANCELED
        EXPIRED -> EntitlementState.EXPIRED
        PENDING, PENDING_PURCHASE_CANCELED, UNSPECIFIED -> null
    }

    companion object {
        private const val PREFIX = "SUBSCRIPTION_STATE_"

        fun parse(raw: String?): PlaySubscriptionState =
            entries.firstOrNull { raw == PREFIX + it.name } ?: UNSPECIFIED
    }
}

/** Lo que el servidor necesita de una compra de suscripción, leído de `purchases.subscriptionsv2.get`. */
data class PlaySubscription(
    val state: PlaySubscriptionState,
    /** `acknowledgementState == ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED`. */
    val acknowledged: Boolean,
    /** `externalAccountIdentifiers.obfuscatedExternalAccountId`: el cliente manda `sha256(userId)`. */
    val obfuscatedAccountId: String?,
    val productId: String?,
    /** `lineItems[].offerDetails.basePlanId`: `mensual` o `anual`. */
    val basePlan: String?,
    val expiresAt: Instant?,
    val autoRenewing: Boolean,
    /** Token anterior al cambiar de plan o volver a suscribirse (Play emite un token nuevo). */
    val linkedPurchaseToken: String?,
    val testPurchase: Boolean,
)

/**
 * Puerto hacia la API de Google Play Developer. La implementación real es [GooglePlaySubscriptions];
 * los tests usan la misma clase sobre un transporte HTTP falso que sirve respuestas grabadas.
 */
interface PlaySubscriptions {
    /** `purchases.subscriptionsv2.get`. Nulo si Play no reconoce el token (400, 404 o 410). */
    fun get(purchaseToken: String): PlaySubscription?

    /** `purchases.subscriptions.acknowledge` (reconocer en el servidor, ADR-0013). */
    fun acknowledge(productId: String, purchaseToken: String)
}

/** Sin credenciales de Play (solo `ENV=local` sin `PLAY_SA_JSON`): cualquier uso falla con un mensaje claro. */
object UnconfiguredPlaySubscriptions : PlaySubscriptions {
    private const val MESSAGE =
        "La API de Google Play no está configurada: define PLAY_SA_JSON (local), PLAY_SERVICE_ACCOUNT o SECRET_PLAY_SA. " +
            "Para probar Plus sin Play usa POST /internal/dev/grant-plus."

    override fun get(purchaseToken: String): PlaySubscription? = error(MESSAGE)

    override fun acknowledge(productId: String, purchaseToken: String) = error(MESSAGE)
}

/** Cliente real sobre `google-api-services-androidpublisher`. */
class GooglePlaySubscriptions(
    private val publisher: AndroidPublisher,
    private val packageName: String,
) : PlaySubscriptions {
    override fun get(purchaseToken: String): PlaySubscription? = try {
        publisher.purchases().subscriptionsv2().get(packageName, purchaseToken).execute().toPlaySubscription()
    } catch (e: GoogleJsonResponseException) {
        // 400 (token mal formado), 404 (no existe) y 410 (vencida hace más de 60 días): no es una compra válida.
        if (e.statusCode in TOKEN_NOT_FOUND) null else throw e
    }

    override fun acknowledge(productId: String, purchaseToken: String) {
        publisher.purchases().subscriptions()
            .acknowledge(packageName, productId, purchaseToken, SubscriptionPurchasesAcknowledgeRequest())
            .execute()
    }

    companion object {
        private val TOKEN_NOT_FOUND = setOf(400, 404, 410)
        /** Tiempos máximos por llamada a Play: sin ellos una Play colgada deja sin hilo al servidor (por defecto, 20 s de conexión y lectura). */
        internal const val CONNECT_TIMEOUT_MS = 5_000
        internal const val READ_TIMEOUT_MS = 10_000

        /** Envuelve [auth] (puede ser nulo) fijando [CONNECT_TIMEOUT_MS] y [READ_TIMEOUT_MS] en cada petición. */
        internal fun withTimeouts(auth: HttpRequestInitializer?): HttpRequestInitializer = HttpRequestInitializer { request ->
            auth?.initialize(request)
            request.connectTimeout = CONNECT_TIMEOUT_MS
            request.readTimeout = READ_TIMEOUT_MS
        }

        const val SCOPE = "https://www.googleapis.com/auth/androidpublisher"
        private const val APPLICATION_NAME = "parcheu-api"

        /** Cliente con [credentials] (cuenta de servicio con acceso a la app en Play Console). */
        fun create(
            credentials: GoogleCredentials?,
            packageName: String,
            transport: HttpTransport = NetHttpTransport(),
            jsonFactory: JsonFactory = GsonFactory.getDefaultInstance(),
        ): GooglePlaySubscriptions {
            val auth = credentials?.let { HttpCredentialsAdapter(it.createScoped(listOf(SCOPE))) }
            val publisher = AndroidPublisher.Builder(transport, jsonFactory, withTimeouts(auth))
                .setApplicationName(APPLICATION_NAME)
                .build()
            return GooglePlaySubscriptions(publisher, packageName)
        }
    }
}

/** Traduce la respuesta de `subscriptionsv2.get`. Hay un solo producto (`parcheu_plus`), así que una sola línea. */
internal fun SubscriptionPurchaseV2.toPlaySubscription(): PlaySubscription {
    val item = lineItems.orEmpty().firstOrNull()
    return PlaySubscription(
        state = PlaySubscriptionState.parse(subscriptionState),
        acknowledged = acknowledgementState == "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
        obfuscatedAccountId = externalAccountIdentifiers?.obfuscatedExternalAccountId,
        productId = item?.productId,
        basePlan = item?.offerDetails?.basePlanId,
        expiresAt = item?.expiryTime?.let(Instant::parse),
        autoRenewing = item?.autoRenewingPlan?.autoRenewEnabled ?: false,
        linkedPurchaseToken = linkedPurchaseToken,
        testPurchase = testPurchase != null,
    )
}
