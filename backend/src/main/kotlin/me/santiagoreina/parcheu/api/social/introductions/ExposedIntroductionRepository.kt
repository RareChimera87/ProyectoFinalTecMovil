package me.santiagoreina.parcheu.api.social.introductions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.santiagoreina.parcheu.api.platform.db.tables.IntroRequestStatus
import me.santiagoreina.parcheu.api.platform.db.tables.IntroRequests
import me.santiagoreina.parcheu.api.platform.db.tables.Introductions
import me.santiagoreina.parcheu.contract.Mode
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/** `introductions` e `intro_requests` sobre Postgres. */
class ExposedIntroductionRepository(private val db: Database) : IntroductionRepository {

    private suspend fun <T> tx(block: () -> T): T = withContext(Dispatchers.IO) { transaction(db) { block() } }

    override suspend fun insert(record: IntroductionRecord) {
        tx {
            Introductions.insert {
                it[id] = record.id
                it[introducerId] = record.introducerId
                it[mode] = record.mode
                it[userA] = record.userA
                it[userB] = record.userB
                it[simulationId] = record.simulationId
                it[aResponse] = record.aResponse
                it[bResponse] = record.bResponse
                it[status] = record.status
                it[createdAt] = record.createdAt.toOffset()
            }
        }
    }

    override suspend fun delete(id: UUID) {
        tx { Introductions.deleteWhere { Introductions.id eq id } }
    }

    override suspend fun attachSimulation(id: UUID, simulationId: UUID) {
        tx { Introductions.update({ Introductions.id eq id }) { it[Introductions.simulationId] = simulationId } }
    }

    override suspend fun find(id: UUID): IntroductionRecord? = tx {
        Introductions.selectAll().where { Introductions.id eq id }.singleOrNull()?.toRecord()
    }

    override suspend fun byIntroducer(introducerId: UUID, a: UUID, b: UUID, mode: Mode): List<IntroductionRecord> = tx {
        Introductions.selectAll()
            .where {
                (Introductions.introducerId eq introducerId) and (Introductions.mode eq mode) and (
                    ((Introductions.userA eq a) and (Introductions.userB eq b)) or ((Introductions.userA eq b) and (Introductions.userB eq a))
                    )
            }
            .orderBy(Introductions.createdAt, SortOrder.DESC)
            .map { it.toRecord() }
    }

    override suspend fun involving(userId: UUID, limit: Int): List<IntroductionRecord> = tx {
        Introductions.selectAll()
            .where { (Introductions.userA eq userId) or (Introductions.userB eq userId) }
            .orderBy(Introductions.createdAt, SortOrder.DESC)
            .limit(limit)
            .map { it.toRecord() }
    }

    override suspend fun update(id: UUID, change: (IntroductionRecord) -> IntroductionRecord): Pair<IntroductionRecord, IntroductionRecord>? = tx {
        // FOR UPDATE: las respuestas de las dos personas presentadas se serializan (E6.2).
        val before = Introductions.selectAll().where { Introductions.id eq id }.forUpdate().singleOrNull()?.toRecord()
            ?: return@tx null
        val after = change(before)
        if (after != before) {
            Introductions.update({ Introductions.id eq id }) {
                it[aResponse] = after.aResponse
                it[bResponse] = after.bResponse
                it[status] = after.status
            }
        }
        before to after
    }

    override suspend fun pendingRequest(requesterId: UUID, targetUserId: UUID, since: Instant): IntroRequestRecord? = tx {
        IntroRequests.selectAll()
            .where {
                (IntroRequests.requesterId eq requesterId) and (IntroRequests.targetUserId eq targetUserId) and
                    (IntroRequests.status eq IntroRequestStatus.PENDING) and
                    (IntroRequests.createdAt greaterEq since.atOffset(ZoneOffset.UTC))
            }
            .limit(1)
            .singleOrNull()
            ?.let {
                IntroRequestRecord(
                    id = it[IntroRequests.id],
                    requesterId = it[IntroRequests.requesterId],
                    viaUserId = it[IntroRequests.viaUserId],
                    targetUserId = it[IntroRequests.targetUserId],
                    status = it[IntroRequests.status],
                    createdAt = it[IntroRequests.createdAt].toInstant(),
                )
            }
    }

    override suspend fun insertRequest(record: IntroRequestRecord) {
        tx {
            IntroRequests.insert {
                it[id] = record.id
                it[requesterId] = record.requesterId
                it[viaUserId] = record.viaUserId
                it[targetUserId] = record.targetUserId
                it[status] = record.status
                it[createdAt] = record.createdAt.toOffset()
            }
        }
    }

    private fun ResultRow.toRecord() = IntroductionRecord(
        id = this[Introductions.id],
        introducerId = this[Introductions.introducerId],
        mode = this[Introductions.mode],
        userA = this[Introductions.userA],
        userB = this[Introductions.userB],
        simulationId = this[Introductions.simulationId],
        aResponse = this[Introductions.aResponse],
        bResponse = this[Introductions.bResponse],
        status = this[Introductions.status],
        createdAt = this[Introductions.createdAt].toInstant(),
    )
}

private fun Instant.toOffset(): OffsetDateTime = OffsetDateTime.ofInstant(this, ZoneOffset.UTC)
