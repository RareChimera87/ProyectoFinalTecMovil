package me.santiagoreina.parcheu.api.social.introductions

import me.santiagoreina.parcheu.api.platform.db.tables.IntroRequestStatus
import me.santiagoreina.parcheu.api.platform.db.tables.IntroResponse
import me.santiagoreina.parcheu.contract.IntroductionStatus
import me.santiagoreina.parcheu.contract.Mode
import java.time.Instant
import java.util.UUID

/**
 * Una fila de `introductions` (arquitectura.md §4). El `id` coincide con el de su simulación
 * (`simulation_id`): así el cupo que se cobra a quien presenta lleva `ref_id = introductionId` y la
 * devolución automática del motor (que usa el id de la simulación) también lo alcanza.
 *
 * [status] guardado solo toma SIMULATING, PROPOSED, CONNECTED o DECLINED; READY y FAILED se derivan
 * de la simulación al leer (ver [IntroductionService]).
 */
data class IntroductionRecord(
    val id: UUID,
    val introducerId: UUID,
    val mode: Mode,
    val userA: UUID,
    val userB: UUID,
    val simulationId: UUID?,
    val aResponse: IntroResponse?,
    val bResponse: IntroResponse?,
    val status: IntroductionStatus,
    val createdAt: Instant,
) {
    /** Respuesta guardada de [userId] (null si no responde o no es una de las personas presentadas). */
    fun responseOf(userId: UUID): IntroResponse? = when (userId) {
        userA -> aResponse
        userB -> bResponse
        else -> null
    }

    fun isIntroduced(userId: UUID): Boolean = userId == userA || userId == userB

    fun otherThan(userId: UUID): UUID = if (userId == userA) userB else userA

    /** DECLINED por quien presenta ('No por ahora' en E2): nadie respondió todavía. */
    val declinedByIntroducer: Boolean get() = status == IntroductionStatus.DECLINED && aResponse == null && bResponse == null
}

/** Una fila de `intro_requests` ('Pedir presentación' desde D6). */
data class IntroRequestRecord(
    val id: UUID,
    val requesterId: UUID,
    val viaUserId: UUID,
    val targetUserId: UUID,
    val status: IntroRequestStatus,
    val createdAt: Instant,
)

/**
 * Persistencia de presentaciones y solicitudes. [ExposedIntroductionRepository] es la real;
 * [InMemoryIntroductionRepository] replica su semántica (incluida la serialización de [update]).
 */
interface IntroductionRepository {
    suspend fun insert(record: IntroductionRecord)

    /** Deshace un alta cuya simulación no se pudo crear (sin cupo, presupuesto, validación). */
    suspend fun delete(id: UUID)

    suspend fun attachSimulation(id: UUID, simulationId: UUID)

    suspend fun find(id: UUID): IntroductionRecord?

    /** Presentaciones de [introducerId] entre [a] y [b] (en cualquier orden) en [mode], más recientes primero. */
    suspend fun byIntroducer(introducerId: UUID, a: UUID, b: UUID, mode: Mode): List<IntroductionRecord>

    /**
     * Lee la fila bloqueándola (`SELECT ... FOR UPDATE`), aplica [change] y guarda el resultado en una
     * sola transacción: dos respuestas simultáneas se serializan y la segunda ve la primera. Si
     * [change] lanza, no se escribe nada. Devuelve (antes, después) o null si no existe.
     */
    suspend fun update(id: UUID, change: (IntroductionRecord) -> IntroductionRecord): Pair<IntroductionRecord, IntroductionRecord>?

    /** Solicitud PENDING de [requesterId] para conocer a [targetUserId], si existe. */
    suspend fun pendingRequest(requesterId: UUID, targetUserId: UUID): IntroRequestRecord?

    suspend fun insertRequest(record: IntroRequestRecord)
}

/** En memoria (pruebas y `ENV=local` sin base). Un candado global hace de `FOR UPDATE`. */
class InMemoryIntroductionRepository : IntroductionRepository {
    private val lock = Any()
    private val rows = mutableMapOf<UUID, IntroductionRecord>()
    private val requestRows = mutableListOf<IntroRequestRecord>()

    /** Solicitudes guardadas (para las pruebas). */
    val requests: List<IntroRequestRecord> get() = synchronized(lock) { requestRows.toList() }

    fun all(): List<IntroductionRecord> = synchronized(lock) { rows.values.toList() }

    override suspend fun insert(record: IntroductionRecord) = synchronized(lock) {
        check(record.id !in rows) { "La presentación ${record.id} ya existe" }
        rows[record.id] = record
    }

    override suspend fun delete(id: UUID) {
        synchronized(lock) { rows.remove(id) }
    }

    override suspend fun attachSimulation(id: UUID, simulationId: UUID) {
        synchronized(lock) { rows[id]?.let { rows[id] = it.copy(simulationId = simulationId) } }
    }

    override suspend fun find(id: UUID): IntroductionRecord? = synchronized(lock) { rows[id] }

    override suspend fun byIntroducer(introducerId: UUID, a: UUID, b: UUID, mode: Mode): List<IntroductionRecord> =
        synchronized(lock) {
            rows.values
                .filter { it.introducerId == introducerId && it.mode == mode && setOf(it.userA, it.userB) == setOf(a, b) }
                .sortedByDescending { it.createdAt }
        }

    override suspend fun update(id: UUID, change: (IntroductionRecord) -> IntroductionRecord): Pair<IntroductionRecord, IntroductionRecord>? =
        synchronized(lock) {
            val before = rows[id] ?: return null
            val after = change(before)
            rows[id] = after
            before to after
        }

    override suspend fun pendingRequest(requesterId: UUID, targetUserId: UUID): IntroRequestRecord? = synchronized(lock) {
        requestRows.firstOrNull { it.requesterId == requesterId && it.targetUserId == targetUserId && it.status == IntroRequestStatus.PENDING }
    }

    override suspend fun insertRequest(record: IntroRequestRecord) {
        synchronized(lock) { requestRows += record }
    }
}
