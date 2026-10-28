package me.santiagoreina.parcheu.api.safety.moderation

import me.santiagoreina.parcheu.api.ai.AiGateway
import me.santiagoreina.parcheu.api.chat.ChatModule
import me.santiagoreina.parcheu.api.platform.notifier.NoopNotifier
import me.santiagoreina.parcheu.api.platform.notifier.Notifier
import me.santiagoreina.parcheu.api.platform.tasks.TaskQueue
import me.santiagoreina.parcheu.api.safety.SafetyModule
import java.time.Clock

/** Servicio de moderación sobre los repositorios de [chat] y [safety]; [tasks] es la cola `ai-light` y [notifier] entrega la ayuda a quien escribió. */
fun moderationService(
    ai: AiGateway,
    chat: ChatModule,
    safety: SafetyModule,
    tasks: TaskQueue,
    clock: Clock,
    notifier: Notifier = NoopNotifier,
) = ModerationService(MessageModerator(ai), chat.repository, chat.service, safety.repository, tasks, clock, notifier)

/**
 * Activa la moderación para cada mensaje nuevo del [chat] (E8.6 es recortable: `MODERATION_ENABLED=false`
 * la apaga sin tocar código, ADR-0026).
 */
fun ModerationService.attachTo(chat: ChatModule) {
    chat.service.onMessageSaved = { enqueue(it) }
}

/** `MODERATION_ENABLED` (por defecto true). */
fun moderationEnabled(vars: Map<String, String>): Boolean = vars["MODERATION_ENABLED"]?.trim()?.lowercase() != "false"
