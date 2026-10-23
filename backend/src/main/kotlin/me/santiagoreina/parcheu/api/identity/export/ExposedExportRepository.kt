package me.santiagoreina.parcheu.api.identity.export

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.santiagoreina.parcheu.api.identity.ConsentRecord
import me.santiagoreina.parcheu.api.platform.db.tables.Blocks
import me.santiagoreina.parcheu.api.platform.db.tables.ChatMembers
import me.santiagoreina.parcheu.api.platform.db.tables.ChatMessages
import me.santiagoreina.parcheu.api.platform.db.tables.Chats
import me.santiagoreina.parcheu.api.platform.db.tables.Consents
import me.santiagoreina.parcheu.api.platform.db.tables.EdgesView
import me.santiagoreina.parcheu.api.platform.db.tables.Entitlements
import me.santiagoreina.parcheu.api.platform.db.tables.Simulations
import me.santiagoreina.parcheu.api.platform.db.tables.TwinAnswers
import me.santiagoreina.parcheu.api.platform.db.tables.TwinMemories
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.contract.Answer
import me.santiagoreina.parcheu.contract.MemoryDto
import me.santiagoreina.parcheu.contract.SimulationOrigin
import me.santiagoreina.parcheu.api.identity.toKotlin
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/**
 * Lecturas de la exportación sobre Postgres, en una transacción de solo lectura. De otras personas
 * solo se leen el uuid (para su `anonId`) y `is_demo`.
 */
class ExposedExportRepository(private val db: Database) : ExportRepository {
    override suspend fun rows(userId: UUID): ExportRows = withContext(Dispatchers.IO) {
        transaction(db) {
            val consents = Consents.selectAll().where { Consents.userId eq userId }.orderBy(Consents.id to SortOrder.ASC).map {
                ConsentRecord(it[Consents.id], it[Consents.kind], it[Consents.granted], it[Consents.textVersion], it[Consents.createdAt].toInstant())
            }
            val answers = TwinAnswers.selectAll().where { TwinAnswers.userId eq userId }
                .orderBy(TwinAnswers.questionId to SortOrder.ASC)
                .map { Answer(it[TwinAnswers.questionId].toInt(), it[TwinAnswers.answer]) }
            val memories = TwinMemories.selectAll().where { TwinMemories.userId eq userId }
                .orderBy(TwinMemories.createdAt to SortOrder.ASC)
                .map {
                    MemoryDto(
                        id = it[TwinMemories.id].toString(),
                        text = it[TwinMemories.text],
                        mode = it[TwinMemories.mode],
                        status = it[TwinMemories.status],
                        createdAt = it[TwinMemories.createdAt].toInstant().toKotlin(),
                    )
                }

            val simRows = Simulations.selectAll()
                // La simulación de una presentación (E6.2) es de quien presenta: las personas presentadas no la
                // ven en ningún endpoint, así que tampoco en su exportación (revelaría el 'No por ahora').
                .where {
                    ((Simulations.userA eq userId) or (Simulations.userB eq userId)) and
                        (Simulations.origin neq SimulationOrigin.INTRODUCTION)
                }
                .orderBy(Simulations.createdAt to SortOrder.ASC)
                .toList()
            val others = simRows.map { if (it[Simulations.userA] == userId) it[Simulations.userB] else it[Simulations.userA] }.toSet()
            val demo = if (others.isEmpty()) {
                emptyMap()
            } else {
                Users.select(Users.id, Users.isDemo).where { Users.id inList others }.associate { it[Users.id] to it[Users.isDemo] }
            }
            val simulations = simRows.map {
                val other = if (it[Simulations.userA] == userId) it[Simulations.userB] else it[Simulations.userA]
                ExportSimulationRow(
                    id = it[Simulations.id],
                    status = it[Simulations.status],
                    mode = it[Simulations.mode],
                    origin = it[Simulations.origin],
                    otherUserId = other,
                    otherIsDemo = demo[other] ?: false,
                    score = it[Simulations.score]?.toInt(),
                    createdAt = it[Simulations.createdAt].toInstant(),
                )
            }

            val connections = EdgesView.selectAll().where { EdgesView.me eq userId }
                .orderBy(EdgesView.createdAt to SortOrder.ASC)
                .map {
                    ExportConnectionRow(it[EdgesView.other], it[EdgesView.mode], it[EdgesView.state], it[EdgesView.connectedAt]?.toInstant())
                }

            val own = ChatMessages.selectAll().where { ChatMessages.senderId eq userId }
                .orderBy(ChatMessages.createdAt to SortOrder.ASC)
                .groupBy({ it[ChatMessages.chatId] }, { ExportMessageRow(it[ChatMessages.id], it[ChatMessages.text], it[ChatMessages.createdAt].toInstant()) })
            val memberOf = ChatMembers.select(ChatMembers.chatId).where { ChatMembers.userId eq userId }.map { it[ChatMembers.chatId] }
            val chatIds = (memberOf + own.keys).distinct()
            val chats = if (chatIds.isEmpty()) {
                emptyList()
            } else {
                Chats.selectAll().where { Chats.id inList chatIds }.orderBy(Chats.createdAt to SortOrder.ASC).map {
                    ExportChatRow(it[Chats.id], it[Chats.type], it[Chats.mode], own[it[Chats.id]].orEmpty())
                }
            }

            val blocks = Blocks.selectAll().where { Blocks.blockerId eq userId }
                .orderBy(Blocks.createdAt to SortOrder.ASC)
                .map { ExportBlockRow(it[Blocks.blockedId], it[Blocks.createdAt].toInstant()) }

            val entitlement = Entitlements.selectAll().where { Entitlements.userId eq userId }.singleOrNull()?.let {
                ExportEntitlementRow(it[Entitlements.state], it[Entitlements.basePlan], it[Entitlements.expiresAt]?.toInstant(), it[Entitlements.autoRenewing])
            }

            ExportRows(consents, answers, memories, simulations, connections, chats, blocks, entitlement)
        }
    }
}
