package me.santiagoreina.parcheu.api.safety.moderation

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import me.santiagoreina.parcheu.api.chat.ChatRepository
import me.santiagoreina.parcheu.api.chat.ChatService
import me.santiagoreina.parcheu.api.chat.MessageRecord
import me.santiagoreina.parcheu.api.platform.tasks.QueueKind
import me.santiagoreina.parcheu.api.platform.tasks.TaskQueue
import me.santiagoreina.parcheu.api.platform.tasks.TaskRequest
import me.santiagoreina.parcheu.api.safety.HelpResources
import me.santiagoreina.parcheu.api.safety.NewReport
import me.santiagoreina.parcheu.api.safety.SafetyRepository
import me.santiagoreina.parcheu.api.safety.SafetyService
import me.santiagoreina.parcheu.contract.ReportKind
import me.santiagoreina.parcheu.contract.ReportReason
import me.santiagoreina.parcheu.contract.ReportSource
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

/**
 * Moderación asíncrona con IA (E8.6, ADR-0026). El mensaje ya se entregó: [enqueue] solo agrega una
 * tarea en `ai-light` y [moderate] (el handler de `POST /internal/tasks/moderate`, o `InProcessTaskQueue`
 * con `TASKS_MODE=inline`) lo revisa con el modelo lite.
 *
 * Con severidad HIGH:
 * - SELF_HARM: mensaje del sistema en el chat con las líneas de ayuda (no se oculta ni se reporta).
 * - POSSIBLE_MINOR: reporte AUTO de prioridad alta; el mensaje sigue visible y un admin decide.
 * - el resto (acoso, sexual, odio, amenaza, spam, datos personales): el mensaje se oculta en Postgres y
 *   Firestore y se crea un reporte AUTO.
 * La severidad LOW no hace nada. Si el LLM falla (error, JSON inválido, presupuesto agotado) el mensaje
 * queda visible y se registra `moderation_failed` (fail-open); la tarea termina sin error para no reintentar.
 *
 * Idempotente: ocultar es idempotente, el reporte AUTO se crea una sola vez por mensaje y el mensaje de
 * ayuda usa un id determinista.
 */
class ModerationService(
    private val moderator: MessageModerator,
    private val chats: ChatRepository,
    private val chat: ChatService,
    private val safety: SafetyRepository,
    private val tasks: TaskQueue,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Encola la moderación de [message]; nunca propaga errores (el envío ya ocurrió). */
    suspend fun enqueue(message: MessageRecord) {
        if (message.senderId == null) return
        try {
            tasks.enqueue(
                TaskRequest(
                    queue = QueueKind.AI_LIGHT,
                    path = TASK_PATH,
                    name = "moderate-${message.id}",
                    body = buildJsonObject { put("id", JsonPrimitive(message.id.toString())) }.toString(),
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.atWarn().setMessage(ENQUEUE_FAILED).addKeyValue("event", ENQUEUE_FAILED)
                .addKeyValue("messageId", message.id.toString()).addKeyValue("error", e.javaClass.simpleName).log()
        }
    }

    /** Revisa el mensaje [messageId] y aplica las acciones que correspondan. */
    suspend fun moderate(messageId: UUID): ModerationOutcome {
        val message = chats.message(messageId) ?: return ModerationOutcome.SKIPPED
        val author = message.senderId ?: return ModerationOutcome.SKIPPED
        if (message.hidden) return ModerationOutcome.SKIPPED

        val findings = try {
            moderator.review(author, message.text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Fail-open (ADR-0026): sin textos ni ids de personas, solo el mensaje, la clase del error y su causa corta.
            log.atWarn().setMessage(MODERATION_FAILED).addKeyValue("event", MODERATION_FAILED)
                .addKeyValue("messageId", messageId.toString()).addKeyValue("error", e.javaClass.simpleName).log()
            return ModerationOutcome.FAILED
        }

        val high = findings.filter { it.severity == ModerationSeverity.HIGH }.map { it.category }
        if (high.isEmpty()) return ModerationOutcome.CLEAN

        if (ModerationCategory.SELF_HARM in high) sendHelp(message)

        val hide = high.any { it != ModerationCategory.SELF_HARM && it != ModerationCategory.POSSIBLE_MINOR }
        val reportable = high.filter { it != ModerationCategory.SELF_HARM }
        if (reportable.isNotEmpty() && !safety.hasAutoReport(messageId)) {
            if (hide) chat.hideMessage(messageId)
            safety.insertReport(autoReport(message, author, reportable))
        }
        log.atInfo().setMessage(MODERATION_ACTED).addKeyValue("event", MODERATION_ACTED)
            .addKeyValue("messageId", messageId.toString()).addKeyValue("categories", high.joinToString(",") { it.name })
            .addKeyValue("hidden", hide).log()
        return ModerationOutcome.ACTED
    }

    private suspend fun sendHelp(message: MessageRecord) {
        val id = UUID.nameUUIDFromBytes("moderation-help-${message.id}".toByteArray())
        chat.postSystemMessage(message.chatId, id, helpText())
    }

    private suspend fun autoReport(message: MessageRecord, author: UUID, categories: List<ModerationCategory>): NewReport {
        // La categoría más grave decide el motivo: los menores van primero porque exigen la revisión humana más rápida.
        val main = ORDER.first { it in categories }
        val minor = ModerationCategory.POSSIBLE_MINOR in categories
        return NewReport(
            id = UUID.randomUUID(),
            reporterId = null,
            targetId = author,
            kind = ReportKind.USER,
            chatId = message.chatId,
            messageId = message.id,
            simulationId = null,
            turnIndex = null,
            reason = if (minor) ReportReason.UNDERAGE else main.reason,
            details = "Moderación automática: ${categories.joinToString(", ") { it.name }}",
            snapshot = safety.recentMessages(message.chatId, SafetyService.SNAPSHOT_SIZE),
            priority = if (minor) SafetyService.HIGH_PRIORITY else AUTO_PRIORITY,
            createdAt = clock.instant(),
            source = ReportSource.AUTO,
        )
    }

    companion object {
        const val TASK_PATH = "/tasks/moderate"
        const val MODERATION_FAILED = "moderation_failed"
        const val MODERATION_ACTED = "moderation_acted"
        const val ENQUEUE_FAILED = "moderation_enqueue_failed"

        /** Prioridad de un reporte AUTO que oculta un mensaje: arriba de los de usuario normales (0), debajo de menor o autolesión. */
        const val AUTO_PRIORITY = 1

        private val ORDER = listOf(
            ModerationCategory.POSSIBLE_MINOR, ModerationCategory.THREAT, ModerationCategory.SEXUAL, ModerationCategory.HATE,
            ModerationCategory.HARASSMENT, ModerationCategory.SPAM, ModerationCategory.PERSONAL_DATA,
        )

        /**
         * Texto neutro: el mensaje lo ven los dos miembros del chat, así que no dice quién lo provocó.
         */
        fun helpText(): String = buildString {
            append("Un recordatorio de ParcheU: si alguien en este chat se siente mal o piensa en hacerse daño, no está solo y hay ayuda. ")
            append(HelpResources.colombia.joinToString(" ") { "${it.name}: ${it.description}" })
        }
    }
}
