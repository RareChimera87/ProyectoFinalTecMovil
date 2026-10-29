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
import me.santiagoreina.parcheu.api.platform.db.tables.InviteRedeemAttempts
import me.santiagoreina.parcheu.api.platform.db.tables.InviteRedemptions
import me.santiagoreina.parcheu.api.platform.db.tables.Introductions
import me.santiagoreina.parcheu.api.platform.db.tables.Invites
import me.santiagoreina.parcheu.api.platform.db.tables.MatchQueue
import me.santiagoreina.parcheu.api.platform.db.tables.Matches
import me.santiagoreina.parcheu.api.platform.db.tables.Notifications
import me.santiagoreina.parcheu.api.platform.db.tables.PurchaseEvents
import me.santiagoreina.parcheu.api.platform.db.tables.Reports
import me.santiagoreina.parcheu.api.platform.db.tables.TwinFeedback
import me.santiagoreina.parcheu.api.platform.db.tables.Twins
import me.santiagoreina.parcheu.api.billing.notificationType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
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

            val notifications = Notifications.selectAll().where { Notifications.userId eq userId }
                .orderBy(Notifications.createdAt to SortOrder.ASC, Notifications.id to SortOrder.ASC)
                .map {
                    val payload = it[Notifications.payload] as? JsonObject
                    fun text(key: String) = (payload?.get(key) as? JsonPrimitive)?.contentOrNull
                    ExportNotificationRow(
                        id = it[Notifications.id],
                        kind = it[Notifications.kind],
                        title = text("title").orEmpty(),
                        body = text("body").orEmpty(),
                        route = text("route"),
                        actorId = text("actorId")?.let { id -> runCatching { UUID.fromString(id) }.getOrNull() },
                        locked = (payload?.get("locked") as? JsonPrimitive)?.booleanOrNull ?: false,
                        createdAt = it[Notifications.createdAt].toInstant(),
                        readAt = it[Notifications.readAt]?.toInstant(),
                    )
                }

            val personaSummary = Twins.select(Twins.personaSummary).where { Twins.userId eq userId }.singleOrNull()?.get(Twins.personaSummary)

            // Solo los reportes que hizo (reporter_id); en los que es la parte reportada no se exporta nada.
            val reportsMade = Reports.selectAll().where { Reports.reporterId eq userId }
                .orderBy(Reports.createdAt to SortOrder.ASC, Reports.id to SortOrder.ASC)
                .map {
                    ExportReportRow(
                        it[Reports.id], it[Reports.kind], it[Reports.reason], it[Reports.details], it[Reports.targetId],
                        it[Reports.status], it[Reports.createdAt].toInstant(), it[Reports.resolvedAt]?.toInstant(),
                    )
                }

            val introductions = Introductions.selectAll().where {
                (Introductions.introducerId eq userId) or (Introductions.userA eq userId) or (Introductions.userB eq userId)
            }.orderBy(Introductions.createdAt to SortOrder.ASC, Introductions.id to SortOrder.ASC).map {
                // Si además es la persona que presentó, no es "presentada": la respuesta propia solo aplica a las presentadas.
                val myResponse = when {
                    it[Introductions.introducerId] == userId -> null
                    it[Introductions.userA] == userId -> it[Introductions.aResponse]
                    else -> it[Introductions.bResponse]
                }
                ExportIntroductionRow(
                    it[Introductions.id], it[Introductions.introducerId], it[Introductions.userA], it[Introductions.userB],
                    it[Introductions.mode], it[Introductions.status], myResponse?.name, it[Introductions.createdAt].toInstant(),
                )
            }

            val matches = Matches.selectAll().where { (Matches.userA eq userId) or (Matches.userB eq userId) }
                .orderBy(Matches.createdAt to SortOrder.ASC, Matches.id to SortOrder.ASC)
                .map {
                    val isA = it[Matches.userA] == userId
                    ExportMatchRow(
                        it[Matches.id], if (isA) it[Matches.userB] else it[Matches.userA], it[Matches.mode], it[Matches.status],
                        if (isA) it[Matches.choiceA] else it[Matches.choiceB], it[Matches.createdAt].toInstant(), it[Matches.decideBy].toInstant(),
                    )
                }
            val queueTicket = MatchQueue.selectAll().where { MatchQueue.userId eq userId }.singleOrNull()?.let {
                ExportQueueTicketRow(it[MatchQueue.mode], it[MatchQueue.status], it[MatchQueue.enqueuedAt].toInstant(), it[MatchQueue.expiresAt].toInstant())
            }

            val twinFeedback = TwinFeedback.selectAll().where { TwinFeedback.userId eq userId }
                .orderBy(TwinFeedback.createdAt to SortOrder.ASC, TwinFeedback.id to SortOrder.ASC)
                .map {
                    ExportTwinFeedbackRow(
                        it[TwinFeedback.id], it[TwinFeedback.kind], it[TwinFeedback.simulationId], it[TwinFeedback.turnIndex]?.toInt(),
                        it[TwinFeedback.questionId]?.toInt(), it[TwinFeedback.predicted], it[TwinFeedback.actual],
                        it[TwinFeedback.rating]?.toInt(), it[TwinFeedback.createdAt].toInstant(),
                    )
                }

            val inviteCode = Invites.select(Invites.code).where { Invites.userId eq userId }.singleOrNull()?.get(Invites.code)
            val redemptions = InviteRedemptions.selectAll()
                .where { (InviteRedemptions.ownerId eq userId) or (InviteRedemptions.redeemerId eq userId) }
                .orderBy(InviteRedemptions.createdAt to SortOrder.ASC)
                .map {
                    ExportRedemptionRow(
                        it[InviteRedemptions.ownerId], it[InviteRedemptions.redeemerId], it[InviteRedemptions.mode],
                        it[InviteRedemptions.status], it[InviteRedemptions.createdAt].toInstant(), it[InviteRedemptions.acceptedAt]?.toInstant(),
                    )
                }
            val redeemAttempts = InviteRedeemAttempts.select(InviteRedeemAttempts.createdAt).where { InviteRedeemAttempts.userId eq userId }
                .orderBy(InviteRedeemAttempts.createdAt to SortOrder.ASC)
                .map { it[InviteRedeemAttempts.createdAt].toInstant() }

            // Nunca se lee `purchase_token`; del payload solo el tipo de notificación (completo o ya redactado).
            val purchases = PurchaseEvents.select(PurchaseEvents.receivedAt, PurchaseEvents.payload)
                .where { PurchaseEvents.userId eq userId }
                .orderBy(PurchaseEvents.receivedAt to SortOrder.ASC, PurchaseEvents.messageId to SortOrder.ASC)
                .map { ExportPurchaseRow(it[PurchaseEvents.receivedAt].toInstant(), notificationType(it[PurchaseEvents.payload])) }

            ExportRows(
                consents, answers, memories, simulations, connections, chats, blocks, entitlement,
                notifications, personaSummary, reportsMade, introductions, matches, queueTicket, twinFeedback,
                inviteCode, redemptions, redeemAttempts, purchases,
            )
        }
    }
}
