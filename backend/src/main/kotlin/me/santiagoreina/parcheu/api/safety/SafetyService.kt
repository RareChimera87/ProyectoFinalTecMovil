package me.santiagoreina.parcheu.api.safety

import me.santiagoreina.parcheu.api.chat.ChatRepository
import me.santiagoreina.parcheu.api.chat.ChatService
import me.santiagoreina.parcheu.api.graph.AnonIdSigner
import me.santiagoreina.parcheu.api.graph.GraphFilter
import me.santiagoreina.parcheu.api.graph.GraphRepository
import me.santiagoreina.parcheu.api.graph.GraphVisibilityPolicy
import me.santiagoreina.parcheu.api.graph.ViewContext
import me.santiagoreina.parcheu.api.identity.toKotlin
import me.santiagoreina.parcheu.api.matching.MatchingRepository
import me.santiagoreina.parcheu.api.matching.MatchingService
import me.santiagoreina.parcheu.api.platform.errors.NotFound
import me.santiagoreina.parcheu.api.platform.errors.ValidationFailed
import me.santiagoreina.parcheu.contract.BlockedDto
import me.santiagoreina.parcheu.contract.ChatStatus
import me.santiagoreina.parcheu.contract.ReportCreated
import me.santiagoreina.parcheu.contract.ReportKind
import me.santiagoreina.parcheu.contract.ReportReason
import me.santiagoreina.parcheu.contract.ReportRequest
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

/**
 * Reportar y bloquear (E8.1, arquitectura.md §10 y ADR-0026). Siempre gratis: nada aquí consulta el
 * cupo ni el plan, así que nunca responde 402.
 *
 * El bloqueo es inmediato y transversal: la fila de `blocks` la leen el grafo, el chat (envío → 403
 * BLOCKED y lista), la fila de match y la simulación; además se cierran los chats abiertos entre los
 * dos y se cancela el match pendiente que tengan. Las vistas de amigos de amigos y presentaciones
 * lo aplican en E6.3 y E6.2.
 */
class SafetyService(
    private val repo: SafetyRepository,
    private val chat: ChatService,
    private val chats: ChatRepository,
    private val graph: GraphRepository,
    private val policy: GraphVisibilityPolicy,
    private val anonIds: AnonIdSigner,
    private val matches: MatchingRepository?,
    private val clock: Clock,
    private val matching: MatchingService? = null,
    private val contacts: List<AnonymousContacts> = emptyList(),
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // --- reportes ------------------------------------------------------------------------------

    /**
     * `POST /v1/reports`. Exactamente un objetivo: `targetUserId` o `anonId` (USER), o
     * `simulationId` + `turnIndex` (AI_CONTENT: `turnIndex` cuenta los turnos de todos los escenarios en orden, desde 0, y `snapshot` guarda el texto de ese turno). Con `chatId` adjunta en `snapshot` los últimos
     * [SNAPSHOT_SIZE] mensajes de ese chat (quien reporta debe ser miembro). Motivo SELF_HARM →
     * `helpResources` con las líneas de ayuda; los demás → lista vacía.
     */
    suspend fun report(reporter: UUID, request: ReportRequest): ReportCreated {
        val details = request.details?.trim()?.takeIf { it.isNotEmpty() }
        if (details != null && details.codePointCount(0, details.length) > MAX_DETAILS) {
            throw ValidationFailed("El detalle admite máximo $MAX_DETAILS caracteres")
        }
        val chatId = request.chatId?.let { parseUuid(it) ?: throw NotFound("El chat no existe") }
        val chatRecord = chatId?.let { id ->
            chats.chat(id)?.takeIf { it.member(reporter) != null } ?: throw NotFound("El chat no existe")
        }
        val messageId = request.messageId?.let { parseUuid(it) ?: throw ValidationFailed("messageId no es válido") }
        if (messageId != null && chatId == null) throw ValidationFailed("messageId necesita chatId")

        var target: UUID? = null
        var simulationId: UUID? = null
        var turnIndex: Int? = null
        var turnSnapshot: List<SnapshotMessage>? = null
        when (request.kind) {
            ReportKind.USER -> {
                if (request.simulationId != null || request.turnIndex != null) {
                    throw ValidationFailed("Un reporte USER no lleva simulationId ni turnIndex")
                }
                val raw = listOfNotNull(request.targetUserId, request.anonId)
                if (raw.size != 1) throw ValidationFailed("Indica exactamente un objetivo: targetUserId o anonId")
                val resolved = resolveTarget(reporter, raw.single(), chatId)
                if (resolved == reporter) throw ValidationFailed("No puedes reportarte a ti mismo")
                if (chatRecord != null && chatRecord.member(resolved) == null) {
                    throw ValidationFailed("La persona reportada no está en ese chat")
                }
                target = resolved
            }
            ReportKind.AI_CONTENT -> {
                if (request.targetUserId != null || request.anonId != null) {
                    throw ValidationFailed("Un reporte AI_CONTENT no lleva targetUserId ni anonId")
                }
                val sim = request.simulationId?.let { parseUuid(it) } ?: throw ValidationFailed("AI_CONTENT necesita simulationId")
                val turn = request.turnIndex ?: throw ValidationFailed("AI_CONTENT necesita turnIndex")
                if (turn < 0 || turn > Short.MAX_VALUE) throw ValidationFailed("turnIndex fuera de rango")
                val participants = repo.simulationParticipants(sim)
                if (participants == null || reporter !in participants.toList()) throw NotFound("La simulación no existe")
                // El texto del turno se copia al reporte: la transcripción se purga y la administración
                // (E8.5) debe poder revisar lo que se reportó sin depender de ella.
                val text = repo.simulationTurnText(sim, turn) ?: throw NotFound("El turno no existe")
                turnSnapshot = listOf(SnapshotMessage(UUID.randomUUID(), null, text, clock.instant(), hidden = false))
                simulationId = sim
                turnIndex = turn
            }
        }

        val snapshot = turnSnapshot ?: chatId?.let { repo.recentMessages(it, SNAPSHOT_SIZE) }
        val report = NewReport(
            id = UUID.randomUUID(),
            reporterId = reporter,
            targetId = target,
            kind = request.kind,
            chatId = chatId,
            messageId = messageId,
            simulationId = simulationId,
            turnIndex = turnIndex,
            reason = request.reason,
            details = details,
            snapshot = snapshot,
            priority = priorityOf(request.reason),
            createdAt = clock.instant(),
        )
        repo.insertReport(report)
        // Evento para la métrica basada en logs y la alerta por correo (E10.5). Nunca lleva textos
        // de mensajes ni el detalle: solo ids internos, motivo y tamaños.
        log.atInfo()
            .setMessage(REPORT_CREATED)
            .addKeyValue("event", REPORT_CREATED)
            .addKeyValue("reportId", report.id.toString())
            .addKeyValue("kind", report.kind.name)
            .addKeyValue("reason", report.reason.name)
            .addKeyValue("priority", report.priority)
            .addKeyValue("hasChat", chatId != null)
            .addKeyValue("snapshotMessages", snapshot?.size ?: 0)
            .log()
        val help = if (request.reason == ReportReason.SELF_HARM) HelpResources.colombia else emptyList()
        return ReportCreated(id = report.id.toString(), helpResources = help)
    }

    // --- bloqueos ------------------------------------------------------------------------------

    /**
     * `POST /v1/blocks/{userId}`: [rawTarget] es el uuid (persona conectada) o el `anonId` con el que
     * [blocker] la ve. Idempotente. Cierra los chats abiertos entre los dos y cancela el match
     * pendiente; el resto de vistas lo filtran por la tabla `blocks`.
     */
    suspend fun block(blocker: UUID, rawTarget: String) {
        val target = resolveTarget(blocker, rawTarget, chatId = null)
        if (target == blocker) throw ValidationFailed("No puedes bloquearte a ti mismo")
        val inserted = repo.block(blocker, target, clock.instant())

        // Bloquear termina la conexión: desbloquear después no la restaura (E8.7).
        graph.removeConnections(blocker, target)

        // Se repite aunque el bloqueo ya existiera: repara un efecto que haya fallado a medias.
        chats.chatsOf(blocker)
            .filter { row -> row.chat.status == ChatStatus.OPEN && row.chat.member(target) != null }
            .forEach { row -> chat.close(row.chat.id) }
        matching?.cancelPendingMatchBetween(blocker, target)

        log.atInfo()
            .setMessage(BLOCK_CREATED)
            .addKeyValue("event", BLOCK_CREATED)
            .addKeyValue("new", inserted)
            .log()
    }

    /**
     * `DELETE /v1/blocks/{userId}`: solo acepta el id con el que `GET /v1/blocks` presenta a la persona
     * (uuid si están conectados, `anonId` si no), resuelto entre los bloqueados de [blocker]. El uuid
     * interno de una persona anónima no sirve: si sirviera, la respuesta confirmaría a quién pertenece.
     * Idempotente: un uuid que no está entre los bloqueados responde igual (204) sin hacer nada.
     */
    suspend fun unblock(blocker: UUID, rawTarget: String) {
        val target = presentedBlocks(blocker).firstOrNull { (_, dto) -> dto.userId == rawTarget }?.first
        if (target == null) {
            // Un uuid desconocido o de alguien no conectado es un no-op indistinguible; un anonId
            // que no está entre los bloqueados es 404, como en POST.
            if (parseUuid(rawTarget) != null) return
            throw NotFound("La persona no existe")
        }
        // Los chats cerrados siguen cerrados: un DIRECT se reabre solo con una nueva conexión mutua.
        repo.unblock(blocker, target)
    }

    /**
     * `GET /v1/blocks`: las personas que bloqueó [viewer], como las presenta [GraphVisibilityPolicy]:
     * nombre y uuid solo con conexión mutua; si no, `anonId` y etiqueta anónima (nunca el uuid interno).
     */
    suspend fun blocks(viewer: UUID): List<BlockedDto> = presentedBlocks(viewer).map { it.second }

    private suspend fun presentedBlocks(viewer: UUID): List<Pair<UUID, BlockedDto>> {
        val connected = graph.connectedWith(viewer)
        return repo.blocksOf(viewer).map { row ->
            val view = policy.present(viewer, row.blocked, connected = row.blocked.id in connected, context = ViewContext.EGO)
            row.blocked.id to BlockedDto(
                userId = view.id,
                label = view.label,
                initials = view.initials,
                blockedAt = row.createdAt.toKotlin(),
            )
        }
    }

    // --- internos ------------------------------------------------------------------------------

    /**
     * Traduce el id que ve el cliente a un `users.id`. Un uuid debe existir (y no estar borrado). Un
     * `anonId` solo se puede resolver entre las personas con las que [viewer] tiene contexto: los
     * miembros de [chatId] (o de sus chats), su ego-grafo, su match pendiente y los [contacts] (C5, D6, E3).
     */
    private suspend fun resolveTarget(viewer: UUID, raw: String, chatId: UUID?): UUID {
        parseUuid(raw)?.let { id ->
            val person = repo.person(id)
            if (person == null || person.deletedAt != null) throw NotFound("La persona no existe")
            return id
        }
        if (!raw.startsWith(AnonIdSigner.PREFIX)) throw NotFound("La persona no existe")
        val candidates = linkedSetOf<UUID>()
        if (chatId != null) {
            chats.chat(chatId)?.members?.forEach { candidates += it.userId }
        } else {
            chats.chatsOf(viewer).forEach { row -> row.chat.members.forEach { candidates += it.userId } }
            graph.egoEdges(viewer, GraphFilter()).forEach { candidates += it.other.id }
            matches?.pendingMatchOf(viewer)?.let { candidates += it.otherThan(viewer) }
            // C5 (canjes pendientes), D6 (amigos de amigos) y E3 (presentaciones): E8.7.
            contacts.forEach { candidates += it.of(viewer) }
        }
        candidates -= viewer
        return candidates.firstOrNull { anonIds.anonId(viewer, it) == raw } ?: throw NotFound("La persona no existe")
    }

    private fun priorityOf(reason: ReportReason): Int = when (reason) {
        ReportReason.SELF_HARM, ReportReason.UNDERAGE -> HIGH_PRIORITY
        else -> 0
    }

    private fun parseUuid(raw: String): UUID? = runCatching { UUID.fromString(raw) }.getOrNull()

    companion object {
        const val SNAPSHOT_SIZE = 20
        const val MAX_DETAILS = 1000
        const val HIGH_PRIORITY = 2
        const val REPORT_CREATED = "report_created"
        const val BLOCK_CREATED = "block_created"
    }
}
