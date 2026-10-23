package me.santiagoreina.parcheu.api.identity.deletion

import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Lo que la purga necesita saber de una cuenta antes de tocar sistemas externos (Firestore y Firebase
 * Auth). Se lee de Postgres mientras la fila de `users` aún existe: si la purga se corta a mitad, el
 * reintento vuelve a armar el mismo plan.
 */
data class PurgePlan(
    val userId: UUID,
    val firebaseUid: String,
    /** Nombre visible al borrar (se busca también en los avisos de terceros). */
    val displayName: String?,
    /** Simulaciones donde participa: se borra su documento `simulations/{id}`. */
    val simulationIds: List<UUID>,
    /** Chats donde es miembro o escribió: sus mensajes se anonimizan en el espejo. */
    val chats: List<ChatPurge>,
)

data class ChatPurge(val chatId: UUID, val ownMessageIds: List<UUID>)

/**
 * Match de la cuenta borrada que la purga cerró en Postgres (E4.2): [counterpart] es la otra persona,
 * cuyo ticket quedó EXPIRED. [notify] es true si el match seguía esperando la elección: entonces se le
 * devolvió el cupo `RANDOM_MATCH` y hay que avisarle que el parche ya no está disponible.
 */
data class ClosedMatch(val matchId: UUID, val counterpart: UUID, val notify: Boolean)

/**
 * Efectos fuera de Postgres de los matches cerrados por una purga: republicar `matchTickets/{uid}` de la
 * contraparte y avisarle (lo implementa `MatchingService`). Si falla solo se registra: la purga ya terminó.
 */
fun interface ClosedMatchesListener {
    suspend fun onClosed(matches: List<ClosedMatch>)
}

/** Postgres: plan y purga (implementación real en [ExposedAccountPurgeRepository]). */
interface AccountPurgeRepository {
    /** Plan de purga, o null si la cuenta ya no existe (purga terminada) o no está marcada como borrada. */
    suspend fun plan(userId: UUID): PurgePlan?

    /**
     * Aplica la tabla de retención de arquitectura.md §10.2 en una sola transacción. Idempotente.
     * Devuelve los matches que cerró (vacío si no había nada que purgar).
     */
    suspend fun purge(plan: PurgePlan, now: Instant): List<ClosedMatch>

    /** Cuentas marcadas como borradas antes de [deletedBefore] que siguen sin purgar (no son lápidas). */
    suspend fun stalledDeletions(deletedBefore: Instant, limit: Int): List<UUID>
}

/** Espejo de Firestore: borra `simulations/{id}` y `matchTickets/{uid}` y anonimiza los chats. Idempotente. */
interface AccountMirrorCleaner {
    suspend fun clean(plan: PurgePlan)

    /** Solo borra `simulations/{id}` (segunda pasada tras la purga de Postgres). Idempotente. */
    suspend fun removeSimulations(ids: List<UUID>)
}

/** Borra el usuario de Firebase Auth. Si ya no existe no hace nada (idempotente). */
fun interface AuthAccountRemover {
    suspend fun remove(firebaseUid: String)
}

/** Sin Firestore (`ENV=local` y pruebas que no lo miran). */
object NoopAccountMirrorCleaner : AccountMirrorCleaner {
    override suspend fun clean(plan: PurgePlan) = Unit

    override suspend fun removeSimulations(ids: List<UUID>) = Unit
}

/** Registra las limpiezas pedidas para que las pruebas las verifiquen. */
class RecordingAccountMirrorCleaner : AccountMirrorCleaner {
    private val recorded = CopyOnWriteArrayList<PurgePlan>()
    val plans: List<PurgePlan> get() = recorded.toList()

    private val removedIds = CopyOnWriteArrayList<List<UUID>>()

    /** Cada llamada a [removeSimulations], en orden. */
    val removedSimulations: List<List<UUID>> get() = removedIds.toList()

    override suspend fun clean(plan: PurgePlan) {
        recorded += plan
    }

    override suspend fun removeSimulations(ids: List<UUID>) {
        removedIds += ids
    }
}

/** Firebase Auth Fake (`ENV=local` y pruebas): recuerda los uid borrados. */
class FakeAuthAccountRemover : AuthAccountRemover {
    private val recorded = CopyOnWriteArrayList<String>()
    val removed: List<String> get() = recorded.toList()

    /** Si no es null, [remove] lanza este error (simula una caída de Firebase Auth). */
    @Volatile var failWith: Throwable? = null

    override suspend fun remove(firebaseUid: String) {
        failWith?.let { throw it }
        recorded += firebaseUid
    }
}

/**
 * Plan y purga en memoria para las pruebas de rutas: [plans] lo llena la prueba y [purged] registra
 * las cuentas purgadas (sin plan registrado no hay nada que purgar).
 */
class InMemoryAccountPurgeRepository : AccountPurgeRepository {
    val plans = java.util.concurrent.ConcurrentHashMap<UUID, PurgePlan>()
    private val recorded = CopyOnWriteArrayList<UUID>()
    val purged: List<UUID> get() = recorded.toList()

    override suspend fun plan(userId: UUID): PurgePlan? = plans[userId]

    /** Matches que devuelve la próxima purga (la prueba los fija). */
    val closedMatches = CopyOnWriteArrayList<ClosedMatch>()

    override suspend fun purge(plan: PurgePlan, now: Instant): List<ClosedMatch> {
        recorded += plan.userId
        plans.remove(plan.userId)
        return closedMatches.toList().also { closedMatches.clear() }
    }

    /** Toda cuenta con plan registrado y sin purgar cuenta como atascada. */
    override suspend fun stalledDeletions(deletedBefore: Instant, limit: Int): List<UUID> = plans.keys.take(limit)
}

/**
 * `firebase_uid` de la lápida que hereda lo que la ley obliga a conservar (consentimientos, compras,
 * reportes y simulaciones compartidas): `deleted:` + SHA-256 del uid de Firebase. No es un dato de
 * contacto ni el uuid interno, pero permite demostrar ante un reclamo que esa cuenta sí autorizó.
 */
fun tombstoneUid(firebaseUid: String): String = "deleted:" + sha256Hex(firebaseUid)

internal fun sha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
