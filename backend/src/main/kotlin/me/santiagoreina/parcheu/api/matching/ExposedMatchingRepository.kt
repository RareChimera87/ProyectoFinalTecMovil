package me.santiagoreina.parcheu.api.matching

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.santiagoreina.parcheu.api.platform.db.tables.Blocks
import me.santiagoreina.parcheu.api.platform.db.tables.ChatMembers
import me.santiagoreina.parcheu.api.platform.db.tables.Chats
import me.santiagoreina.parcheu.api.platform.db.tables.EdgesView
import me.santiagoreina.parcheu.api.platform.db.tables.MatchQueue
import me.santiagoreina.parcheu.api.platform.db.tables.Matches
import me.santiagoreina.parcheu.api.platform.db.tables.Scenarios
import me.santiagoreina.parcheu.api.platform.db.tables.Universities
import me.santiagoreina.parcheu.api.platform.db.tables.UserModes
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.contract.ChatType
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.MatchStatus
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.TicketStatus
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.notExists
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Fila y matches sobre Postgres (arquitectura.md §7.4).
 *
 * Emparejar y encolar es **una** transacción:
 * 1. `pg_advisory_xact_lock` por persona y luego por modo. Sin él, dos personas que entran a la vez
 *    no se ven (READ COMMITTED no muestra filas sin confirmar) y quedan esperando para siempre.
 * 2. Se guarda el ticket propio.
 * 3. `SELECT ... FROM match_queue JOIN users ... FOR UPDATE OF match_queue SKIP LOCKED LIMIT 1` (sin borrados ni suspendidos)
 *    elige a la pareja; SKIP LOCKED impide que otra transacción (cancelar, vencer, otro emparejamiento)
 *    la tome a la vez.
 * 4. Se crea el match y los dos tickets pasan a MATCHED.
 */
class ExposedMatchingRepository(private val db: Database) : MatchingRepository {

    private suspend fun <T> tx(block: () -> T): T = withContext(Dispatchers.IO) { transaction(db) { block() } }

    override suspend fun person(userId: UUID): MatchPerson? = tx {
        val row = Users.join(Universities, JoinType.LEFT, Users.universityId, Universities.id)
            .selectAll().where { Users.id eq userId }.singleOrNull() ?: return@tx null
        val modes = UserModes.select(UserModes.mode)
            .where { (UserModes.userId eq userId) and (UserModes.enabled eq true) }
            .map { it[UserModes.mode] }
            .toSet()
        MatchPerson(
            id = userId,
            firebaseUid = row[Users.firebaseUid],
            activeModes = modes,
            ageVerified = row[Users.ageVerifiedAt] != null,
            underage = row[Users.underageAt] != null,
            parejaConfirmed = row[Users.parejaConfirmedAt] != null,
            isDemo = row[Users.isDemo],
            deleted = row[Users.deletedAt] != null,
            birthDate = row[Users.birthDate],
            universityShort = row.getOrNull(Universities.shortName),
            interests = row[Users.interests],
            suspendedUntil = row[Users.suspendedUntil]?.toInstant(),
        )
    }

    override suspend fun ticket(userId: UUID): QueueEntry? = tx {
        MatchQueue.selectAll().where { MatchQueue.userId eq userId }.singleOrNull()?.toEntry()
    }

    override suspend fun pendingMatchOf(userId: UUID): MatchRecord? = tx {
        Matches.selectAll()
            .where { ((Matches.userA eq userId) or (Matches.userB eq userId)) and (Matches.status eq MatchStatus.PENDING_CHOICE) }
            .orderBy(Matches.createdAt, SortOrder.DESC)
            .limit(1)
            .singleOrNull()?.toMatch()
    }

    override suspend fun enqueueAndPair(entry: QueueEntry, demoUsersEnabled: Boolean, now: Instant, allowExisting: Boolean): PairOutcome = tx {
        advisoryLock("match_user:${entry.userId}")
        advisoryLock("match_mode:${entry.mode.name}")
        val existing = MatchQueue.selectAll().where { MatchQueue.userId eq entry.userId }.forUpdate().singleOrNull()?.toEntry()
        val waiting = existing != null && existing.status == TicketStatus.WAITING && existing.expiresAt.isAfter(now)
        if (waiting && !allowExisting) return@tx PairOutcome.AlreadyQueued
        val own = if (waiting) existing!! else entry
        if (!waiting) {
            if (existing == null) {
                MatchQueue.insert { it.fill(entry) }
            } else {
                MatchQueue.update({ MatchQueue.userId eq entry.userId }) { it.fill(entry) }
            }
        }

        val partnerRow = candidateQuery(own, demoUsersEnabled, now).singleOrNull() ?: return@tx PairOutcome.Waiting(own)
        val partner = partnerRow.toEntry()
        val matchId = UUID.randomUUID()
        val match = MatchRecord(
            id = matchId, mode = own.mode, userA = partner.userId, userB = own.userId, choiceA = null, choiceB = null,
            status = MatchStatus.PENDING_CHOICE, simulationId = null, chatId = null,
            createdAt = now, decideBy = now.plus(MatchingRules.DECIDE_WINDOW),
        )
        Matches.insert {
            it[id] = match.id
            it[mode] = match.mode
            it[userA] = match.userA
            it[userB] = match.userB
            it[status] = match.status
            it[createdAt] = match.createdAt.odt()
            it[decideBy] = match.decideBy.odt()
        }
        MatchQueue.update({ MatchQueue.userId inList listOf(partner.userId, own.userId) }) {
            it[status] = TicketStatus.MATCHED
            it[MatchQueue.matchId] = matchId
        }
        PairOutcome.Paired(
            match,
            own = own.copy(status = TicketStatus.MATCHED, matchId = matchId),
            partner = partner.copy(status = TicketStatus.MATCHED, matchId = matchId),
        )
    }

    /** Candidatos válidos para [own], con el candado `FOR UPDATE OF match_queue SKIP LOCKED`. */
    private fun candidateQuery(own: QueueEntry, demoUsersEnabled: Boolean, now: Instant) = run {
        var condition: Op<Boolean> = (MatchQueue.status eq TicketStatus.WAITING) and
            (MatchQueue.mode eq own.mode) and
            (MatchQueue.userId neq own.userId) and
            (MatchQueue.expiresAt greater now.odt()) and
            Users.deletedAt.isNull() and
            (Users.suspendedUntil.isNull() or (Users.suspendedUntil lessEq now.odt())) and
            exists(
                UserModes.select(UserModes.userId)
                    .where { (UserModes.userId eq MatchQueue.userId) and (UserModes.mode eq own.mode) and (UserModes.enabled eq true) },
            ) and
            notExists(
                Blocks.select(Blocks.blockerId).where {
                    ((Blocks.blockerId eq own.userId) and (Blocks.blockedId eq MatchQueue.userId)) or
                        ((Blocks.blockerId eq MatchQueue.userId) and (Blocks.blockedId eq own.userId))
                },
            ) and
            notExists(
                EdgesView.select(EdgesView.me).where {
                    (EdgesView.me eq own.userId) and (EdgesView.other eq MatchQueue.userId) and (EdgesView.state eq EdgeState.CONNECTED)
                },
            )
        // ADR-0027: sin la bandera nadie se empareja con una cuenta de demostración; en Pareja nunca.
        if (!demoUsersEnabled || own.mode == Mode.PAREJA) condition = condition and (Users.isDemo eq false)
        if (own.mode == Mode.PAREJA) {
            condition = condition and Users.ageVerifiedAt.isNotNull() and Users.underageAt.isNull() and Users.parejaConfirmedAt.isNotNull()
        }
        (MatchQueue innerJoin Users)
            .select(MatchQueue.columns)
            .where { condition }
            .orderBy(MatchQueue.priority to SortOrder.DESC, MatchQueue.enqueuedAt to SortOrder.ASC)
            .limit(1)
            .forUpdate(ForUpdateOption.PostgreSQL.ForUpdate(ForUpdateOption.PostgreSQL.MODE.SKIP_LOCKED, MatchQueue))
    }

    override suspend fun undoPairing(matchId: UUID, requeue: QueueEntry?, dropped: UUID?) {
        tx {
            Matches.update({ (Matches.id eq matchId) and (Matches.status eq MatchStatus.PENDING_CHOICE) }) {
                it[status] = MatchStatus.EXPIRED
            }
            if (requeue != null) {
                MatchQueue.update({ MatchQueue.userId eq requeue.userId }) {
                    it[status] = TicketStatus.WAITING
                    it[MatchQueue.matchId] = null
                    it[priority] = requeue.priority.toShort()
                    it[enqueuedAt] = requeue.enqueuedAt.odt()
                    it[expiresAt] = requeue.expiresAt.odt()
                }
            }
            if (dropped != null) {
                MatchQueue.update({ MatchQueue.userId eq dropped }) {
                    it[status] = TicketStatus.EXPIRED
                    it[MatchQueue.matchId] = null
                }
            }
        }
    }

    override suspend fun cancel(userId: UUID): Boolean = tx {
        MatchQueue.deleteWhere { (MatchQueue.userId eq userId) and (MatchQueue.status eq TicketStatus.WAITING) } > 0
    }

    override suspend fun expireTickets(now: Instant, limit: Int): List<QueueEntry> = tx {
        val due = MatchQueue.selectAll()
            .where { (MatchQueue.status eq TicketStatus.WAITING) and (MatchQueue.expiresAt lessEq now.odt()) }
            .orderBy(MatchQueue.expiresAt, SortOrder.ASC)
            .limit(limit)
            .forUpdate(ForUpdateOption.PostgreSQL.ForUpdate(ForUpdateOption.PostgreSQL.MODE.SKIP_LOCKED))
            .map { it.toEntry() }
        if (due.isNotEmpty()) {
            MatchQueue.update({ MatchQueue.userId inList due.map { it.userId } }) { it[status] = TicketStatus.EXPIRED }
        }
        due.map { it.copy(status = TicketStatus.EXPIRED) }
    }

    override suspend fun match(id: UUID): MatchRecord? = tx {
        Matches.selectAll().where { Matches.id eq id }.singleOrNull()?.toMatch()
    }

    override suspend fun updateMatch(id: UUID, change: (MatchRecord) -> MatchRecord?): MatchUpdate? = tx {
        val current = Matches.selectAll().where { Matches.id eq id }.forUpdate().singleOrNull()?.toMatch() ?: return@tx null
        val next = change(current) ?: return@tx MatchUpdate(current, current)
        if (next != current) {
            Matches.update({ Matches.id eq id }) {
                it[choiceA] = next.choiceA
                it[choiceB] = next.choiceB
                it[status] = next.status
                it[simulationId] = next.simulationId
                it[chatId] = next.chatId
            }
        }
        MatchUpdate(current, next)
    }

    override suspend fun dueMatches(now: Instant, limit: Int): List<UUID> = tx {
        Matches.select(Matches.id)
            .where { (Matches.status eq MatchStatus.PENDING_CHOICE) and (Matches.decideBy lessEq now.odt()) }
            .orderBy(Matches.decideBy, SortOrder.ASC)
            .limit(limit)
            .map { it[Matches.id] }
    }

    override suspend fun matchesMissingSimulation(limit: Int): List<MatchRecord> = tx {
        Matches.selectAll()
            .where { (Matches.status eq MatchStatus.SIMULATION) and Matches.simulationId.isNull() }
            .orderBy(Matches.createdAt, SortOrder.ASC)
            .limit(limit)
            .map { it.toMatch() }
    }

    override suspend fun attachSimulation(matchId: UUID, simulationId: UUID) {
        tx {
            Matches.update({ (Matches.id eq matchId) and Matches.simulationId.isNull() }) { it[Matches.simulationId] = simulationId }
        }
    }

    override suspend fun canInteract(a: UUID, b: UUID, now: Instant): Boolean = tx { usable(a, b, now) }

    /** Sin bloqueo en ningún sentido, y ninguna de las dos personas borrada ni suspendida a [now]. */
    private fun usable(a: UUID, b: UUID, now: Instant): Boolean {
        val blocked = Blocks.select(Blocks.blockerId).where {
            ((Blocks.blockerId eq a) and (Blocks.blockedId eq b)) or ((Blocks.blockerId eq b) and (Blocks.blockedId eq a))
        }.limit(1).any()
        if (blocked) return false
        val rows = Users.select(Users.id, Users.deletedAt, Users.suspendedUntil).where { Users.id inList listOf(a, b) }.toList()
        return rows.size == 2 && rows.all { row ->
            row[Users.deletedAt] == null && row[Users.suspendedUntil].let { it == null || !it.toInstant().isAfter(now) }
        }
    }

    override suspend fun createScenarioChat(matchId: UUID, now: Instant, allowPremium: Boolean): ScenarioChatOutcome = tx {
        val match = Matches.selectAll().where { Matches.id eq matchId }.forUpdate().singleOrNull()?.toMatch()
            ?: return@tx ScenarioChatOutcome.NoScenarios
        match.chatId?.let { return@tx ScenarioChatOutcome.Created(it) }
        if (!usable(match.userA, match.userB, now)) {
            Matches.update({ Matches.id eq matchId }) { it[status] = MatchStatus.EXPIRED }
            return@tx ScenarioChatOutcome.Unavailable
        }
        val options = Scenarios.selectAll()
            .where { (Scenarios.mode eq match.mode) and (if (allowPremium) Op.TRUE else (Scenarios.isBasic eq true)) }
            .orderBy(Scenarios.id, SortOrder.ASC)
            .toList()
        if (options.isEmpty()) return@tx ScenarioChatOutcome.NoScenarios
        val scenario = options[Math.floorMod(match.id.hashCode(), options.size)]
        val chatId = UUID.randomUUID()
        Chats.insert {
            it[id] = chatId
            it[type] = ChatType.SCENARIO
            it[mode] = match.mode
            it[scenarioId] = scenario[Scenarios.id]
            it[endsAt] = now.plusSeconds(scenario[Scenarios.timerSec].toLong()).odt()
            it[createdAt] = now.odt()
        }
        listOf(match.userA, match.userB).forEach { member ->
            ChatMembers.insert {
                it[ChatMembers.chatId] = chatId
                it[userId] = member
            }
        }
        Matches.update({ Matches.id eq matchId }) { it[Matches.chatId] = chatId }
        ScenarioChatOutcome.Created(chatId)
    }
}

/** Candado de aviso de Postgres de ámbito transacción, por nombre. */
private fun advisoryLock(name: String) {
    TransactionManager.current().exec("select pg_advisory_xact_lock(hashtext('${name.replace("'", "''")}'))") { }
}

private fun org.jetbrains.exposed.v1.core.statements.UpdateBuilder<*>.fill(entry: QueueEntry) {
    this[MatchQueue.userId] = entry.userId
    this[MatchQueue.mode] = entry.mode
    this[MatchQueue.priority] = entry.priority.toShort()
    this[MatchQueue.enqueuedAt] = entry.enqueuedAt.odt()
    this[MatchQueue.expiresAt] = entry.expiresAt.odt()
    this[MatchQueue.status] = TicketStatus.WAITING
    this[MatchQueue.matchId] = null
}

private fun Instant.odt(): OffsetDateTime = OffsetDateTime.ofInstant(this, ZoneOffset.UTC)

private fun ResultRow.toEntry() = QueueEntry(
    userId = this[MatchQueue.userId],
    mode = this[MatchQueue.mode],
    priority = this[MatchQueue.priority].toInt(),
    enqueuedAt = this[MatchQueue.enqueuedAt].toInstant(),
    expiresAt = this[MatchQueue.expiresAt].toInstant(),
    status = this[MatchQueue.status],
    matchId = this[MatchQueue.matchId],
    version = this[MatchQueue.version],
)

private fun ResultRow.toMatch() = MatchRecord(
    id = this[Matches.id],
    mode = this[Matches.mode],
    userA = this[Matches.userA],
    userB = this[Matches.userB],
    choiceA = this[Matches.choiceA],
    choiceB = this[Matches.choiceB],
    status = this[Matches.status],
    simulationId = this[Matches.simulationId],
    chatId = this[Matches.chatId],
    createdAt = this[Matches.createdAt].toInstant(),
    decideBy = this[Matches.decideBy].toInstant(),
)
