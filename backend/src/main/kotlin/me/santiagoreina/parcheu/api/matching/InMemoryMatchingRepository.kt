package me.santiagoreina.parcheu.api.matching

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.santiagoreina.parcheu.contract.MatchStatus
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.TicketStatus
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** Chat SCENARIO que crea el repositorio en memoria (las pruebas lo inspeccionan). */
data class InMemoryScenarioChat(val id: UUID, val matchId: UUID, val mode: Mode, val scenarioId: Int, val endsAt: Instant, val members: List<UUID>)

/** Escenario disponible para el chat del match. */
data class InMemoryScenario(val id: Int, val mode: Mode, val isBasic: Boolean, val timerSec: Int)

/**
 * Misma semántica que [ExposedMatchingRepository] sin base de datos (`ENV=local` y pruebas de
 * rutas). Un [Mutex] reemplaza a los candados de Postgres: emparejar es atómico.
 *
 * Las personas, bloqueos y conexiones se cargan con [addPerson], [block] y [connect].
 */
class InMemoryMatchingRepository(
    private val scenarios: List<InMemoryScenario> = listOf(
        InMemoryScenario(1, Mode.AMISTAD, true, 600), InMemoryScenario(2, Mode.PAREJA, true, 600), InMemoryScenario(3, Mode.ESTUDIO, true, 600),
    ),
) : MatchingRepository {
    private val lock = Mutex()
    private val people = HashMap<UUID, MatchPerson>()
    private val blocks = HashSet<Pair<UUID, UUID>>()
    private val connections = HashSet<Set<UUID>>()
    private val tickets = LinkedHashMap<UUID, QueueEntry>()
    private val matches = LinkedHashMap<UUID, MatchRecord>()
    private val chats = ArrayList<InMemoryScenarioChat>()
    private val versions = AtomicLong()

    /** Guarda el ticket con una versión nueva, como el trigger de Postgres. */
    private fun put(entry: QueueEntry): QueueEntry = entry.copy(version = versions.incrementAndGet()).also { tickets[it.userId] = it }

    fun addPerson(person: MatchPerson) = synchronized(people) { people[person.id] = person }

    fun block(blocker: UUID, blocked: UUID) = synchronized(blocks) { blocks += blocker to blocked }

    fun connect(a: UUID, b: UUID) = synchronized(connections) { connections += setOf(a, b) }

    /** Chats SCENARIO creados hasta ahora. */
    fun scenarioChats(): List<InMemoryScenarioChat> = synchronized(chats) { chats.toList() }

    /** Todos los matches, para verificaciones de pruebas. */
    fun allMatches(): List<MatchRecord> = synchronized(matches) { matches.values.toList() }

    private fun isBlocked(a: UUID, b: UUID) = synchronized(blocks) { (a to b) in blocks || (b to a) in blocks }

    private fun isConnected(a: UUID, b: UUID) = synchronized(connections) { setOf(a, b) in connections }

    override suspend fun person(userId: UUID): MatchPerson? = synchronized(people) { people[userId] }

    override suspend fun ticket(userId: UUID): QueueEntry? = lock.withLock { tickets[userId] }

    override suspend fun pendingMatchOf(userId: UUID): MatchRecord? = lock.withLock {
        matches.values.filter { it.involves(userId) && it.status == MatchStatus.PENDING_CHOICE }.maxByOrNull { it.createdAt }
    }

    override suspend fun enqueueAndPair(entry: QueueEntry, demoUsersEnabled: Boolean, now: Instant, allowExisting: Boolean): PairOutcome = lock.withLock {
        val existing = tickets[entry.userId]
        val waiting = existing != null && existing.status == TicketStatus.WAITING && existing.expiresAt.isAfter(now)
        if (waiting && !allowExisting) return@withLock PairOutcome.AlreadyQueued
        val own = if (waiting) existing!! else put(entry)

        val partner = tickets.values
            .filter { it.status == TicketStatus.WAITING && it.mode == own.mode && it.userId != own.userId && it.expiresAt.isAfter(now) }
            .filter { eligible(own, it, demoUsersEnabled) }
            .sortedWith(compareByDescending<QueueEntry> { it.priority }.thenBy { it.enqueuedAt })
            .firstOrNull() ?: return@withLock PairOutcome.Waiting(own)
        val match = MatchRecord(
            id = UUID.randomUUID(), mode = own.mode, userA = partner.userId, userB = own.userId, choiceA = null, choiceB = null,
            status = MatchStatus.PENDING_CHOICE, simulationId = null, chatId = null,
            createdAt = now, decideBy = now.plus(MatchingRules.DECIDE_WINDOW),
        )
        synchronized(matches) { matches[match.id] = match }
        val ownMatched = put(own.copy(status = TicketStatus.MATCHED, matchId = match.id))
        val partnerMatched = put(partner.copy(status = TicketStatus.MATCHED, matchId = match.id))
        PairOutcome.Paired(match, ownMatched, partnerMatched)
    }

    private fun eligible(own: QueueEntry, candidate: QueueEntry, demoUsersEnabled: Boolean): Boolean {
        val person = synchronized(people) { people[candidate.userId] } ?: return false
        if (person.deleted || own.mode !in person.activeModes) return false
        if (isBlocked(own.userId, candidate.userId) || isConnected(own.userId, candidate.userId)) return false
        if ((!demoUsersEnabled || own.mode == Mode.PAREJA) && person.isDemo) return false
        if (own.mode == Mode.PAREJA && (!person.ageVerified || person.underage || !person.parejaConfirmed)) return false
        return true
    }

    override suspend fun undoPairing(matchId: UUID, requeue: QueueEntry?, dropped: UUID?) {
        lock.withLock {
            synchronized(matches) {
                matches[matchId]?.takeIf { it.status == MatchStatus.PENDING_CHOICE }?.let { matches[matchId] = it.copy(status = MatchStatus.EXPIRED) }
            }
            if (requeue != null) put(requeue.copy(status = TicketStatus.WAITING, matchId = null))
            if (dropped != null) tickets[dropped]?.let { put(it.copy(status = TicketStatus.EXPIRED, matchId = null)) }
        }
    }

    override suspend fun cancel(userId: UUID): Boolean = lock.withLock {
        val current = tickets[userId]
        if (current != null && current.status == TicketStatus.WAITING) {
            tickets.remove(userId)
            true
        } else {
            false
        }
    }

    override suspend fun expireTickets(now: Instant, limit: Int): List<QueueEntry> = lock.withLock {
        val due = tickets.values
            .filter { it.status == TicketStatus.WAITING && !it.expiresAt.isAfter(now) }
            .sortedBy { it.expiresAt }
            .take(limit)
        due.map { entry -> put(entry.copy(status = TicketStatus.EXPIRED)) }
    }

    override suspend fun match(id: UUID): MatchRecord? = synchronized(matches) { matches[id] }

    override suspend fun updateMatch(id: UUID, change: (MatchRecord) -> MatchRecord?): MatchUpdate? = lock.withLock {
        val current = synchronized(matches) { matches[id] } ?: return@withLock null
        val next = change(current) ?: return@withLock MatchUpdate(current, current)
        synchronized(matches) { matches[id] = next }
        MatchUpdate(current, next)
    }

    override suspend fun dueMatches(now: Instant, limit: Int): List<UUID> = synchronized(matches) {
        matches.values.filter { it.status == MatchStatus.PENDING_CHOICE && !it.decideBy.isAfter(now) }.sortedBy { it.decideBy }.take(limit).map { it.id }
    }

    override suspend fun matchesMissingSimulation(limit: Int): List<MatchRecord> = synchronized(matches) {
        matches.values.filter { it.status == MatchStatus.SIMULATION && it.simulationId == null }.sortedBy { it.createdAt }.take(limit)
    }

    override suspend fun attachSimulation(matchId: UUID, simulationId: UUID) {
        lock.withLock {
            synchronized(matches) { matches[matchId]?.takeIf { it.simulationId == null }?.let { matches[matchId] = it.copy(simulationId = simulationId) } }
        }
    }

    override suspend fun closedBy(a: UUID, b: UUID, now: Instant): Set<UUID>? = lock.withLock { culprits(a, b, now) }

    /** Null si pueden interactuar; si no, quienes provocaron el cierre (bloqueó, borrada o suspendida). */
    private fun culprits(a: UUID, b: UUID, now: Instant): Set<UUID>? {
        val result = mutableSetOf<UUID>()
        synchronized(blocks) {
            if ((a to b) in blocks) result += a
            if ((b to a) in blocks) result += b
        }
        listOf(a, b).forEach { id ->
            val person = synchronized(people) { people[id] }
            if (person == null || person.deleted || person.suspendedUntil.let { it != null && it.isAfter(now) }) result += id
        }
        return result.takeIf { it.isNotEmpty() }
    }

    override suspend fun createScenarioChat(matchId: UUID, now: Instant, allowPremium: Boolean): ScenarioChatOutcome = lock.withLock {
        val match = synchronized(matches) { matches[matchId] } ?: return@withLock ScenarioChatOutcome.NoScenarios
        match.chatId?.let { return@withLock ScenarioChatOutcome.Created(it) }
        culprits(match.userA, match.userB, now)?.let { causedBy ->
            synchronized(matches) { matches[matchId] = match.copy(status = MatchStatus.EXPIRED) }
            return@withLock ScenarioChatOutcome.Unavailable(causedBy)
        }
        val options = scenarios.filter { it.mode == match.mode && (allowPremium || it.isBasic) }.sortedBy { it.id }
        if (options.isEmpty()) return@withLock ScenarioChatOutcome.NoScenarios
        val scenario = options[Math.floorMod(match.id.hashCode(), options.size)]
        val chat = InMemoryScenarioChat(UUID.randomUUID(), matchId, match.mode, scenario.id, now.plusSeconds(scenario.timerSec.toLong()), listOf(match.userA, match.userB))
        synchronized(chats) { chats += chat }
        synchronized(matches) { matches[matchId] = match.copy(chatId = chat.id) }
        ScenarioChatOutcome.Created(chat.id)
    }
}
