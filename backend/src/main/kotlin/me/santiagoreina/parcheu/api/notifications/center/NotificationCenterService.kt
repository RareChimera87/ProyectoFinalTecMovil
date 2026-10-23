package me.santiagoreina.parcheu.api.notifications.center

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import me.santiagoreina.parcheu.api.platform.BogotaZone
import me.santiagoreina.parcheu.contract.MarkRead
import me.santiagoreina.parcheu.contract.NotificationDto
import me.santiagoreina.parcheu.contract.NotificationsDto
import me.santiagoreina.parcheu.contract.PersonRef
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlin.time.Instant

/** Cómo ve el usuario al actor de un aviso (la policy del grafo, ADR-0012); `null` si no se puede mostrar. */
fun interface ActorPresenter {
    suspend fun present(viewer: UUID, actor: UUID): PersonRef?
}

/**
 * Centro de notificaciones C2 (E5.4). `GET /v1/notifications` agrupa por fecha de Bogotá: 'Hoy' es el día
 * calendario de hoy en America/Bogota, 'Esta semana' los seis días anteriores y el resto va a `older`.
 * Un aviso `locked` (p. ej. 'quieren conectar contigo') solo se muestra bloqueado al plan gratis, y sin actor.
 */
class NotificationCenterService(
    private val store: NotificationStore,
    private val actors: ActorPresenter,
    private val isPlus: suspend (UUID) -> Boolean,
    private val clock: Clock,
) {
    suspend fun list(viewer: UUID): NotificationsDto {
        val rows = store.recent(viewer, MAX_ITEMS)
        if (rows.isEmpty()) return NotificationsDto()
        val plus = isPlus(viewer)
        val today = LocalDate.now(clock.withZone(BogotaZone))
        val weekStart = today.minusDays(WEEK_DAYS - 1)
        val presented = mutableMapOf<UUID, PersonRef?>()
        val dtos = rows.map { row ->
            val day = row.createdAt.atZone(BogotaZone).toLocalDate()
            val group = when {
                !day.isBefore(today) -> Group.TODAY
                !day.isBefore(weekStart) -> Group.WEEK
                else -> Group.OLDER
            }
            group to toDto(viewer, row, plus, presented)
        }
        return NotificationsDto(
            today = dtos.filter { it.first == Group.TODAY }.map { it.second },
            week = dtos.filter { it.first == Group.WEEK }.map { it.second },
            older = dtos.filter { it.first == Group.OLDER }.map { it.second },
        )
    }

    /** `POST /v1/notifications/read`: `ids` nulo o vacío marca todas; ids que no son uuid se ignoran. */
    suspend fun markRead(viewer: UUID, body: MarkRead) {
        val ids = body.ids?.takeIf { it.isNotEmpty() }?.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
        if (ids != null && ids.isEmpty()) return
        store.markRead(viewer, ids, clock.instant())
    }

    private suspend fun toDto(
        viewer: UUID,
        row: NotificationRecord,
        plus: Boolean,
        presented: MutableMap<UUID, PersonRef?>,
    ): NotificationDto {
        val payload = row.payload ?: JsonObject(emptyMap())
        val locked = payload.bool("locked") && !plus
        val actorId = payload.text("actorId")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val actor = if (locked || actorId == null) {
            null
        } else {
            presented.getOrPut(actorId) { actors.present(viewer, actorId) }
        }
        return NotificationDto(
            id = row.id.toString(),
            kind = row.kind,
            title = payload.text("title").orEmpty(),
            body = payload.text("body").orEmpty(),
            route = payload.text("route"),
            actor = actor,
            locked = locked,
            read = row.readAt != null,
            createdAt = Instant.fromEpochMilliseconds(row.createdAt.toEpochMilli()),
        )
    }

    private enum class Group { TODAY, WEEK, OLDER }

    companion object {
        /** Avisos que devuelve la bandeja; la retención (90 días, E8.5) borra los viejos. */
        const val MAX_ITEMS = 100

        /** 'Hoy' más los seis días anteriores. */
        private const val WEEK_DAYS = 7L
    }
}

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.bool(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull ?: false
