package me.santiagoreina.parcheu.api.notifications.push

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.santiagoreina.parcheu.api.platform.notifier.Notification
import me.santiagoreina.parcheu.api.platform.notifier.Notifier
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

/**
 * `Notifier` real (E5.3): guarda el aviso en `notifications` (la bandeja C2 lo lee de ahí aunque
 * el push falle) y manda un mensaje de datos a cada token de la persona. Los tokens que FCM
 * responde `UNREGISTERED` se borran. Un fallo de FCM nunca rompe la operación que originó el aviso.
 */
class FcmNotifier(
    private val store: PushStore,
    private val fcm: FcmClient,
    private val clock: Clock,
) : Notifier {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun notify(userId: UUID, notification: Notification) {
        val notificationId = store.saveNotification(userId, notification.kind, payloadOf(notification), clock.instant())
        val tokens = store.tokensOf(userId)
        if (tokens.isEmpty()) return
        val data = dataOf(notificationId, notification)
        val results = coroutineScope { tokens.map { token -> async { token to fcm.send(token, data) } }.map { it.await() } }
        val dead = results.filter { it.second is FcmResult.Unregistered }.map { it.first }
        if (dead.isNotEmpty()) {
            store.deleteTokens(dead)
            log.info("Tokens FCM borrados por UNREGISTERED: {}", dead.size)
        }
    }

    /** `payload` de la fila: lo que C2 necesita para armar el `NotificationDto`. */
    private fun payloadOf(n: Notification) = JsonObject(
        mapOf(
            "title" to JsonPrimitive(n.title),
            "body" to JsonPrimitive(n.body),
            "route" to (n.route?.let(::JsonPrimitive) ?: JsonNull),
            "actorId" to (n.actorId?.let { JsonPrimitive(it.toString()) } ?: JsonNull),
            "locked" to JsonPrimitive(n.locked),
        ),
    )

    /** Mensaje de datos (arquitectura.md §8.4): `type`, `route` y `channel` más el texto que la app muestra. */
    private fun dataOf(id: UUID, n: Notification): Map<String, String> = buildMap {
        put("id", id.toString())
        put("type", n.kind)
        put("channel", PushChannel.forKind(n.kind).id)
        put("title", n.title)
        put("body", n.body)
        n.route?.let { put("route", it) }
    }
}
