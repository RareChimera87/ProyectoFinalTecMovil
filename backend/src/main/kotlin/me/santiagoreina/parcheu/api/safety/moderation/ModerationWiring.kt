package me.santiagoreina.parcheu.api.safety.moderation

import me.santiagoreina.parcheu.api.ai.AiGateway
import me.santiagoreina.parcheu.api.chat.ChatModule
import me.santiagoreina.parcheu.api.platform.notifier.NoopNotifier
import me.santiagoreina.parcheu.api.platform.notifier.Notifier
import me.santiagoreina.parcheu.api.platform.tasks.TaskQueue
import me.santiagoreina.parcheu.api.quota.DailyKind
import me.santiagoreina.parcheu.api.quota.DailyLimiter
import me.santiagoreina.parcheu.api.safety.SafetyModule
import java.time.Clock
import java.util.UUID

/**
 * Servicio de moderación sobre los repositorios de [chat] y [safety]; [tasks] es la cola `ai-light` y [notifier] entrega
 * la ayuda a quien escribió. Con [daily] aplica el tope diario por persona (`MODERATION_DAILY_PER_USER`) y, pasado el
 * tope, modera 1 de cada [sampleEvery] mensajes (B-M1, ADR-0026); sin él modera todo.
 */
fun moderationService(
    ai: AiGateway,
    chat: ChatModule,
    safety: SafetyModule,
    tasks: TaskQueue,
    clock: Clock,
    notifier: Notifier = NoopNotifier,
    daily: DailyLimiter? = null,
    sampleEvery: Int = ModerationService.DEFAULT_SAMPLE_EVERY,
) = ModerationService(
    MessageModerator(ai), chat.repository, chat.service, safety.repository, tasks, clock, notifier,
    withinDailyCap = daily?.let { limiter -> { author: UUID -> limiter.tryConsume(author, DailyKind.MODERATION) } } ?: { true },
    sampleEvery = sampleEvery,
)

/**
 * Activa la moderación para cada mensaje nuevo del [chat] (E8.6 es recortable: `MODERATION_ENABLED=false`
 * la apaga sin tocar código, ADR-0026).
 */
fun ModerationService.attachTo(chat: ChatModule) {
    chat.service.onMessageSaved = { enqueue(it) }
}

/** `MODERATION_ENABLED` (por defecto true). */
fun moderationEnabled(vars: Map<String, String>): Boolean = vars["MODERATION_ENABLED"]?.trim()?.lowercase() != "false"

/** `MODERATION_SAMPLE_EVERY` (por defecto 10): pasado el tope diario se modera 1 de cada N mensajes del autor. */
fun moderationSampleEvery(vars: Map<String, String>): Int {
    val raw = vars["MODERATION_SAMPLE_EVERY"]?.takeIf { it.isNotBlank() } ?: return ModerationService.DEFAULT_SAMPLE_EVERY
    return raw.trim().toIntOrNull()?.takeIf { it >= 1 } ?: error("MODERATION_SAMPLE_EVERY inválido: '$raw' (se espera un entero >= 1)")
}
