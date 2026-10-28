package me.santiagoreina.parcheu.api.safety.moderation

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import me.santiagoreina.parcheu.api.chat.ChatRepository
import me.santiagoreina.parcheu.api.chat.ChatService
import me.santiagoreina.parcheu.api.chat.MessageRecord
import me.santiagoreina.parcheu.api.platform.notifier.Notification
import me.santiagoreina.parcheu.api.platform.notifier.Notifier
import me.santiagoreina.parcheu.api.platform.tasks.QueueKind
import me.santiagoreina.parcheu.api.platform.tasks.TaskQueue
import me.santiagoreina.parcheu.api.platform.tasks.TaskRequest
import me.santiagoreina.parcheu.api.safety.HelpResources
import me.santiagoreina.parcheu.api.safety.NewReport
import me.santiagoreina.parcheu.api.safety.SafetyRepository
import me.santiagoreina.parcheu.api.safety.SafetyService
import me.santiagoreina.parcheu.contract.NotificationKinds
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
 * - SELF_HARM: las líneas de ayuda llegan solo al autor, como aviso del centro de notificaciones y push
 *   (no entran al chat compartido: revelarían a la otra persona una inferencia de salud). No se oculta ni se reporta.
 * - POSSIBLE_MINOR: reporte AUTO de prioridad alta; el mensaje sigue visible y un admin decide.
 * - el resto (acoso, sexual, odio, amenaza, spam, datos personales): el mensaje se oculta en Postgres y
 *   Firestore y se crea un reporte AUTO.
 * La severidad LOW no hace nada. Si el LLM falla (error, JSON inválido, presupuesto agotado) el mensaje
 * queda visible y se registra `moderation_failed` (fail-open); la tarea termina sin error para no reintentar.
 *
 * Reintentable tras un fallo parcial: el reporte AUTO se inserta antes de ocultar (nunca queda un mensaje
 * oculto sin reporte), se crea una sola vez por mensaje y ocultar es idempotente y repara el espejo de
 * Firestore, así que cada reintento repite el ocultamiento. El aviso de ayuda va primero: un reintento puede duplicarlo,
 * pero nunca se pierde (tras ocultar y reportar, el reintento sale temprano y ya no lo enviaría).
 */
class ModerationService(
    private val moderator: MessageModerator,
    private val chats: ChatRepository,
    private val chat: ChatService,
    private val safety: SafetyRepository,
    private val tasks: TaskQueue,
    private val clock: Clock,
    private val notifier: Notifier,
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
        // Salir por `hidden` a secas dejaría un mensaje oculto sin reporte, o con Firestore aún visible, si un intento
        // anterior falló a mitad. Solo se da por terminado con reporte AUTO y mensaje oculto, y aun así se repite
        // ocultar (idempotente) para reparar el espejo.
        if (message.hidden && safety.hasAutoReport(messageId)) {
            chat.hideMessage(messageId)
            return ModerationOutcome.SKIPPED
        }

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

        val hide = high.any { it != ModerationCategory.SELF_HARM && it != ModerationCategory.POSSIBLE_MINOR }
        val reportable = high.filter { it != ModerationCategory.SELF_HARM }
        // La ayuda va primero: si algo falla después, el reintento la repite (un aviso duplicado es mejor que perderlo),
        // porque la salida temprana de arriba ya no vuelve a pasar por aquí con el mensaje oculto y reportado.
        if (ModerationCategory.SELF_HARM in high) sendHelp(author)
        if (reportable.isNotEmpty()) {
            // Primero el reporte: si ocultar falla, el reintento lo repite sin duplicar y un admin ya puede revisarlo.
            if (!safety.hasAutoReport(messageId)) safety.insertReport(autoReport(message, author, reportable))
            if (hide) chat.hideMessage(messageId)
        }
        log.atInfo().setMessage(MODERATION_ACTED).addKeyValue("event", MODERATION_ACTED)
            .addKeyValue("messageId", messageId.toString()).addKeyValue("categories", high.joinToString(",") { it.name })
            .addKeyValue("hidden", hide).log()
        return ModerationOutcome.ACTED
    }

    /** Las líneas de ayuda van solo a [author] (aviso del centro + push), nunca al chat que ve la otra persona. */
    private suspend fun sendHelp(author: UUID) {
        notifier.notify(
            author,
            Notification(kind = NotificationKinds.ACCOUNT, title = HELP_TITLE, body = helpText()),
        )
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

        const val HELP_TITLE = "No estás solo"

        /** Texto para quien escribió el mensaje; solo lo recibe él. */
        fun helpText(): String = buildString {
            append("Si te sientes mal o piensas en hacerte daño, no estás solo y hay ayuda. ")
            append(HelpResources.colombia.joinToString(" ") { "${it.name}: ${it.description}" })
        }
    }
}
