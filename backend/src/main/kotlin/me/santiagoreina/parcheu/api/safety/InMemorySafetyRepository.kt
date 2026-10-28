package me.santiagoreina.parcheu.api.safety

import me.santiagoreina.parcheu.api.graph.PersonRow
import java.time.Instant
import java.util.UUID

/**
 * [SafetyRepository] en memoria (pruebas de rutas y `ENV=local`). En Postgres todos los dominios leen
 * la misma tabla `blocks`; aquí cada dominio en memoria guarda su propia copia, así que los
 * bloqueos se propagan con [onBlock] y [onUnblock] (los registra [inMemorySafetyModule]).
 *
 * Las personas y los mensajes se leen con [people] y [messages] para no duplicar los datos que ya
 * siembran las pruebas en los repositorios del chat y del grafo.
 */
class InMemorySafetyRepository(
    private val people: suspend (UUID) -> PersonRow? = { null },
    private val messages: suspend (UUID) -> List<SnapshotMessage> = { emptyList() },
    private val simulations: suspend (UUID) -> Pair<UUID, UUID>? = { null },
    private val turns: suspend (UUID) -> List<String> = { emptyList() },
) : SafetyRepository {
    private val lock = Any()
    private val blocks = LinkedHashMap<Pair<UUID, UUID>, Instant>()
    private val reports = mutableListOf<NewReport>()
    private val blockListeners = mutableListOf<(UUID, UUID) -> Unit>()
    private val unblockListeners = mutableListOf<(UUID, UUID) -> Unit>()

    /** Reportes guardados (las pruebas los inspeccionan). */
    val allReports: List<NewReport> get() = synchronized(lock) { reports.toList() }

    /** Bloqueos vigentes `(blocker, blocked)`. */
    val allBlocks: Set<Pair<UUID, UUID>> get() = synchronized(lock) { blocks.keys.toSet() }

    fun onBlock(listener: (blocker: UUID, blocked: UUID) -> Unit) = synchronized(lock) { blockListeners += listener }

    fun onUnblock(listener: (blocker: UUID, blocked: UUID) -> Unit) = synchronized(lock) { unblockListeners += listener }

    override suspend fun person(userId: UUID): PersonRow? = people(userId)

    override suspend fun block(blocker: UUID, blocked: UUID, now: Instant): Boolean {
        val listeners = synchronized(lock) {
            if (blocks.containsKey(blocker to blocked)) return false
            blocks[blocker to blocked] = now
            blockListeners.toList()
        }
        listeners.forEach { it(blocker, blocked) }
        return true
    }

    override suspend fun unblock(blocker: UUID, blocked: UUID): Boolean {
        val listeners = synchronized(lock) {
            if (blocks.remove(blocker to blocked) == null) return false
            unblockListeners.toList()
        }
        listeners.forEach { it(blocker, blocked) }
        return true
    }

    override suspend fun blocksOf(blocker: UUID): List<BlockRow> {
        val own = synchronized(lock) { blocks.filterKeys { it.first == blocker }.toList() }
        return own.mapNotNull { (pair, at) -> people(pair.second)?.takeIf { it.deletedAt == null }?.let { BlockRow(it, at) } }
            .sortedByDescending { it.createdAt }
    }

    override suspend fun recentMessages(chatId: UUID, limit: Int): List<SnapshotMessage> =
        messages(chatId).sortedWith(compareBy<SnapshotMessage> { it.createdAt }.thenBy { it.id }).takeLast(limit)

    override suspend fun simulationParticipants(simulationId: UUID): Pair<UUID, UUID>? = simulations(simulationId)

    override suspend fun simulationTurnText(simulationId: UUID, turnIndex: Int): String? = turns(simulationId).getOrNull(turnIndex)

    override suspend fun insertReport(report: NewReport) {
        synchronized(lock) { reports += report }
    }
}
