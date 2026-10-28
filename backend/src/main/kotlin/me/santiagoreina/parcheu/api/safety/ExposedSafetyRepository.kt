package me.santiagoreina.parcheu.api.safety

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.santiagoreina.parcheu.api.graph.PersonRow
import me.santiagoreina.parcheu.api.platform.db.tables.Blocks
import me.santiagoreina.parcheu.api.platform.db.tables.ChatMessages
import me.santiagoreina.parcheu.api.platform.db.tables.Reports
import me.santiagoreina.parcheu.api.platform.db.tables.Simulations
import me.santiagoreina.parcheu.api.platform.db.tables.Universities
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.contract.ReportSource
import me.santiagoreina.parcheu.contract.ReportStatus
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/** Bloqueos y reportes sobre Postgres (arquitectura.md §4, tablas `blocks` y `reports`). */
class ExposedSafetyRepository(private val db: Database) : SafetyRepository {

    private suspend fun <T> tx(block: JdbcTransaction.() -> T): T = withContext(Dispatchers.IO) { transaction(db) { block() } }

    override suspend fun person(userId: UUID): PersonRow? = tx {
        Users.join(Universities, JoinType.LEFT, Users.universityId, Universities.id)
            .selectAll()
            .where { Users.id eq userId }
            .singleOrNull()
            ?.toPerson()
    }

    override suspend fun block(blocker: UUID, blocked: UUID, now: Instant): Boolean = tx {
        Blocks.insertIgnore {
            it[blockerId] = blocker
            it[blockedId] = blocked
            it[createdAt] = now.odt()
        }.insertedCount == 1
    }

    override suspend fun unblock(blocker: UUID, blocked: UUID): Boolean = tx {
        Blocks.deleteWhere { (Blocks.blockerId eq blocker) and (Blocks.blockedId eq blocked) } > 0
    }

    override suspend fun blocksOf(blocker: UUID): List<BlockRow> = tx {
        Blocks.join(Users, JoinType.INNER, Blocks.blockedId, Users.id)
            .join(Universities, JoinType.LEFT, Users.universityId, Universities.id)
            .selectAll()
            .where { (Blocks.blockerId eq blocker) and Users.deletedAt.isNull() }
            .orderBy(Blocks.createdAt to SortOrder.DESC)
            .map { BlockRow(it.toPerson(), it[Blocks.createdAt].toInstant()) }
    }

    override suspend fun recentMessages(chatId: UUID, limit: Int): List<SnapshotMessage> = tx {
        ChatMessages.selectAll()
            .where { ChatMessages.chatId eq chatId }
            .orderBy(ChatMessages.createdAt to SortOrder.DESC, ChatMessages.id to SortOrder.DESC)
            .limit(limit)
            .map {
                SnapshotMessage(
                    id = it[ChatMessages.id],
                    senderId = it[ChatMessages.senderId],
                    text = it[ChatMessages.text],
                    createdAt = it[ChatMessages.createdAt].toInstant(),
                    hidden = it[ChatMessages.hidden],
                )
            }
            .reversed()
    }

    override suspend fun simulationParticipants(simulationId: UUID): Pair<UUID, UUID>? = tx {
        Simulations.select(Simulations.userA, Simulations.userB)
            .where { Simulations.id eq simulationId }
            .singleOrNull()
            ?.let { it[Simulations.userA] to it[Simulations.userB] }
    }

    override suspend fun hasAutoReport(messageId: UUID): Boolean = tx {
        !Reports.select(Reports.id)
            .where { (Reports.messageId eq messageId) and (Reports.reportSource eq ReportSource.AUTO) }
            .limit(1)
            .empty()
    }

    override suspend fun insertReport(report: NewReport) {
        tx {
            Reports.insert {
                it[id] = report.id
                it[reporterId] = report.reporterId
                it[targetId] = report.targetId
                it[chatId] = report.chatId
                it[messageId] = report.messageId
                it[kind] = report.kind
                it[simulationId] = report.simulationId
                it[turnIndex] = report.turnIndex?.toShort()
                it[reason] = report.reason
                it[details] = report.details
                it[snapshot] = report.snapshot?.let(::snapshotJson)
                it[reportSource] = report.source
                it[priority] = report.priority.toShort()
                it[status] = ReportStatus.OPEN
                it[createdAt] = report.createdAt.odt()
            }
        }
    }
}

private fun ResultRow.toPerson() = PersonRow(
    id = this[Users.id],
    displayName = this[Users.displayName],
    photoUrl = this[Users.photoUrl],
    universityShort = this.getOrNull(Universities.shortName),
    visibility = this[Users.visibility],
    isDemo = this[Users.isDemo],
    deletedAt = this[Users.deletedAt]?.toInstant(),
)

private fun Instant.odt(): OffsetDateTime = OffsetDateTime.ofInstant(this, ZoneOffset.UTC)
