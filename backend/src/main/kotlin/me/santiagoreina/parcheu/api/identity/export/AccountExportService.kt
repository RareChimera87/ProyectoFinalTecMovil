package me.santiagoreina.parcheu.api.identity.export

import me.santiagoreina.parcheu.api.graph.AnonIdSigner
import me.santiagoreina.parcheu.api.graph.GraphVisibilityPolicy
import me.santiagoreina.parcheu.api.identity.IdentityService
import me.santiagoreina.parcheu.api.identity.toKotlin
import me.santiagoreina.parcheu.api.platform.auth.FirebaseUser
import me.santiagoreina.parcheu.api.quota.grantsPlus
import me.santiagoreina.parcheu.api.twin.TwinState
import me.santiagoreina.parcheu.api.twin.xpNextFor
import me.santiagoreina.parcheu.contract.BlockedDto
import me.santiagoreina.parcheu.contract.ConsentState
import me.santiagoreina.parcheu.contract.EntitlementDto
import me.santiagoreina.parcheu.contract.ExportChat
import me.santiagoreina.parcheu.contract.ExportConnection
import me.santiagoreina.parcheu.contract.ExportDto
import me.santiagoreina.parcheu.contract.ExportIntroRole
import me.santiagoreina.parcheu.contract.ExportIntroduction
import me.santiagoreina.parcheu.contract.ExportInviteRedemption
import me.santiagoreina.parcheu.contract.ExportInviteRole
import me.santiagoreina.parcheu.contract.ExportInvites
import me.santiagoreina.parcheu.contract.ExportMatch
import me.santiagoreina.parcheu.contract.ExportPurchase
import me.santiagoreina.parcheu.contract.ExportQueueTicket
import me.santiagoreina.parcheu.contract.ExportReport
import me.santiagoreina.parcheu.contract.ExportTwinFeedback
import me.santiagoreina.parcheu.contract.NotificationDto
import me.santiagoreina.parcheu.contract.NotificationKinds
import me.santiagoreina.parcheu.contract.ExportMessage
import me.santiagoreina.parcheu.contract.MeDto
import me.santiagoreina.parcheu.contract.PersonRef
import me.santiagoreina.parcheu.contract.SimulationSummary
import me.santiagoreina.parcheu.contract.Trait
import me.santiagoreina.parcheu.contract.TraitKey
import me.santiagoreina.parcheu.contract.TwinDto
import java.time.Clock
import java.util.UUID

/**
 * `GET /v1/me/export` (E8.3, Ley 1581 de 2012): copia en JSON de los datos de la persona: perfil,
 * consentimientos (historial completo), gemelo y respuestas, memorias, simulaciones, conexiones, chats
 * con **solo sus mensajes**, bloqueos y suscripción.
 *
 * También: avisos de la bandeja, resumen de su persona, reportes que hizo, presentaciones (hechas y
 * recibidas), matches y su turno en la fila, calificaciones del gemelo, invitaciones y eventos de compra.
 *
 * De otras personas solo sale su `anonId` (HMAC del observador, arquitectura.md §6.1): nunca su uuid,
 * nombre, correo, foto ni mensajes. Los avisos que pueden citar a terceros salen con el texto
 * reemplazado ([NEUTRAL_TITLE]); de las compras nunca el token. La consulta no escribe nada.
 */
class AccountExportService(
    private val identity: IdentityService,
    private val repository: ExportRepository,
    private val anonIds: AnonIdSigner,
    private val twinState: (UUID) -> TwinState?,
    private val clock: Clock,
) {
    suspend fun export(firebase: FirebaseUser): ExportDto {
        val user = identity.requireUser(firebase)
        val me = identity.me(firebase)
        return build(user.id, me, repository.rows(user.id), twinState(user.id))
    }

    internal fun build(userId: UUID, me: MeDto, rows: ExportRows, twin: TwinState?): ExportDto {
        val now = clock.instant()
        fun anon(other: UUID) = anonIds.anonId(userId, other)
        return ExportDto(
            generatedAt = now.toKotlin(),
            me = me,
            consents = rows.consents.map { ConsentState(it.kind, it.granted, it.textVersion, it.createdAt.toKotlin()) },
            twin = twin?.toDto(),
            twinAnswers = rows.answers,
            memories = rows.memories,
            simulations = rows.simulations.map { sim ->
                SimulationSummary(
                    id = sim.id.toString(),
                    status = sim.status,
                    mode = sim.mode,
                    other = anonymousRef(anon(sim.otherUserId), sim.otherIsDemo),
                    createdAt = sim.createdAt.toKotlin(),
                    origin = sim.origin,
                    score = sim.score,
                )
            },
            connections = rows.connections.map {
                ExportConnection(label = anonymousLabel(anon(it.otherUserId)), mode = it.mode, state = it.state, connectedAt = it.connectedAt?.toKotlin())
            },
            chats = rows.chats.map { chat ->
                ExportChat(
                    id = chat.id.toString(),
                    type = chat.type,
                    mode = chat.mode,
                    messages = chat.ownMessages.map { ExportMessage(it.id.toString(), it.text, it.createdAt.toKotlin()) },
                )
            },
            blocks = rows.blocks.map {
                BlockedDto(
                    userId = anon(it.blockedUserId),
                    label = GraphVisibilityPolicy.ANON_LABEL,
                    initials = GraphVisibilityPolicy.ANON_INITIALS,
                    blockedAt = it.createdAt.toKotlin(),
                )
            },
            notifications = rows.notifications.map { notification(it, ::anon) },
            twinPersonaSummary = rows.personaSummary,
            reportsMade = rows.reportsMade.map {
                ExportReport(
                    id = it.id.toString(), kind = it.kind, reason = it.reason, details = it.details,
                    reportedAnonId = it.targetId?.let(::anon), status = it.status,
                    createdAt = it.createdAt.toKotlin(), resolvedAt = it.resolvedAt?.toKotlin(),
                )
            },
            introductions = rows.introductions.map { intro ->
                if (intro.introducerId == userId) {
                    ExportIntroduction(
                        id = intro.id.toString(), role = ExportIntroRole.MADE, mode = intro.mode,
                        people = listOf(anon(intro.userA), anon(intro.userB)), status = intro.status,
                        createdAt = intro.createdAt.toKotlin(),
                    )
                } else {
                    ExportIntroduction(
                        id = intro.id.toString(), role = ExportIntroRole.RECEIVED, mode = intro.mode,
                        people = listOf(anon(if (intro.userA == userId) intro.userB else intro.userA)),
                        introducerAnonId = anon(intro.introducerId), myResponse = intro.myResponse,
                        createdAt = intro.createdAt.toKotlin(),
                    )
                }
            },
            matches = rows.matches.map {
                ExportMatch(
                    id = it.id.toString(), mode = it.mode, status = it.status, otherAnonId = anon(it.otherUserId),
                    myChoice = it.myChoice, createdAt = it.createdAt.toKotlin(), decideBy = it.decideBy.toKotlin(),
                )
            },
            queueTicket = rows.queueTicket?.let { ExportQueueTicket(it.mode, it.status, it.enqueuedAt.toKotlin(), it.expiresAt.toKotlin()) },
            twinFeedback = rows.twinFeedback.map {
                ExportTwinFeedback(
                    id = it.id.toString(), kind = it.kind, simulationId = it.simulationId?.toString(), turnIndex = it.turnIndex,
                    questionId = it.questionId, predicted = it.predicted, actual = it.actual, rating = it.rating,
                    createdAt = it.createdAt.toKotlin(),
                )
            },
            invites = if (rows.inviteCode == null && rows.inviteRedemptions.isEmpty() && rows.redeemAttempts.isEmpty()) {
                null
            } else {
                ExportInvites(
                    code = rows.inviteCode,
                    redemptions = rows.inviteRedemptions.map {
                        val owner = it.ownerId == userId
                        ExportInviteRedemption(
                            role = if (owner) ExportInviteRole.OWNER else ExportInviteRole.REDEEMER,
                            otherAnonId = anon(if (owner) it.redeemerId else it.ownerId),
                            mode = it.mode, status = it.status, createdAt = it.createdAt.toKotlin(), acceptedAt = it.acceptedAt?.toKotlin(),
                        )
                    },
                    redeemAttempts = rows.redeemAttempts.map { it.toKotlin() },
                )
            },
            purchases = rows.purchases.map { ExportPurchase(it.receivedAt.toKotlin(), it.notificationType) },
            entitlement = rows.entitlement?.let {
                EntitlementDto(
                    plus = grantsPlus(it.state, it.expiresAt, now, it.autoRenewing),
                    state = it.state,
                    basePlan = it.basePlan,
                    expiresAt = it.expiresAt?.toKotlin(),
                    autoRenewing = it.autoRenewing,
                )
            },
        )
    }

    /**
     * Los avisos generados para la propia persona (su simulación falló, nadie en la fila, memorias, moderación)
     * conservan su texto; el resto cita nombres o mensajes de terceros y sale con texto neutro y sin ruta.
     */
    private fun notification(row: ExportNotificationRow, anon: (UUID) -> String): NotificationDto {
        val own = row.kind in OWN_TEXT_KINDS
        return NotificationDto(
            id = row.id.toString(),
            kind = row.kind,
            title = if (own) row.title else NEUTRAL_TITLE,
            body = if (own) row.body else NEUTRAL_BODY,
            route = if (own) row.route else null,
            actor = row.actorId?.let { anonymousRef(anon(it), isDemo = false) },
            locked = row.locked,
            read = row.readAt != null,
            createdAt = row.createdAt.toKotlin(),
        )
    }

    private fun anonymousRef(anonId: String, isDemo: Boolean) = PersonRef(
        anonId = anonId,
        label = GraphVisibilityPolicy.ANON_LABEL,
        initials = GraphVisibilityPolicy.ANON_INITIALS,
        isDemo = isDemo,
    )

    /** Etiqueta de una conexión: el `anonId`, que distingue a cada persona sin decir quién es. */
    private fun anonymousLabel(anonId: String) = "${GraphVisibilityPolicy.ANON_LABEL} · $anonId"

    private fun TwinState.toDto() = TwinDto(
        traits = traits?.let { values -> TraitKey.entries.mapIndexed { i, key -> Trait(key, values[i]) } }.orEmpty(),
        level = level,
        xp = xp,
        xpNext = xpNextFor(xp),
        version = version,
        personaReady = personaReady,
        accuracy = accuracy,
    )

    private companion object {
        const val NEUTRAL_TITLE = "Aviso de ParcheU"
        const val NEUTRAL_BODY = "El texto se omite porque menciona a otra persona."
        val OWN_TEXT_KINDS = setOf(
            NotificationKinds.SIMULATION_FAILED,
            NotificationKinds.MATCH_EXPIRED,
            NotificationKinds.TWIN_MEMORIES,
            NotificationKinds.TWIN_FEEDBACK,
            NotificationKinds.ACCOUNT,
        )
    }
}
