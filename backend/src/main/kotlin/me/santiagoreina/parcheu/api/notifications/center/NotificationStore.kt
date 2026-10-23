package me.santiagoreina.parcheu.api.notifications.center

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import me.santiagoreina.parcheu.api.platform.db.tables.Notifications
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/** Fila de `notifications` tal como la escribe `FcmNotifier` (E5.3): `payload = {title, body, route, actorId, locked}`. */
data class NotificationRecord(
    val id: UUID,
    val userId: UUID,
    val kind: String,
    val payload: JsonObject?,
    val createdAt: Instant,
    val readAt: Instant?,
)

/** Lectura de la bandeja C2 (E5.4) sobre `notifications`. Solo marca lecturas: las filas las crea el push. */
interface NotificationStore {
    /** Los [limit] avisos más recientes de [userId], del más nuevo al más viejo. */
    suspend fun recent(userId: UUID, limit: Int): List<NotificationRecord>

    /** Marca como leídos los avisos de [userId] sin leer; con [ids] nulo, todos. Ids ajenos se ignoran. */
    suspend fun markRead(userId: UUID, ids: Collection<UUID>?, now: Instant)

    /** Avisos sin leer de [userId] (la campana de C1), sin contar los que tienen `actorId` en [excludingActors]. */
    suspend fun unread(userId: UUID, excludingActors: Set<UUID> = emptySet()): Int
}

class ExposedNotificationStore(private val db: Database) : NotificationStore {
    private suspend fun <T> tx(block: () -> T): T = withContext(Dispatchers.IO) { transaction(db) { block() } }

    override suspend fun recent(userId: UUID, limit: Int): List<NotificationRecord> = tx {
        Notifications.selectAll()
            .where { Notifications.userId eq userId }
            .orderBy(Notifications.createdAt to SortOrder.DESC, Notifications.id to SortOrder.DESC)
            .limit(limit)
            .map {
                NotificationRecord(
                    id = it[Notifications.id],
                    userId = it[Notifications.userId],
                    kind = it[Notifications.kind],
                    payload = it[Notifications.payload] as? JsonObject,
                    createdAt = it[Notifications.createdAt].toInstant(),
                    readAt = it[Notifications.readAt]?.toInstant(),
                )
            }
    }

    override suspend fun markRead(userId: UUID, ids: Collection<UUID>?, now: Instant) {
        if (ids != null && ids.isEmpty()) return
        tx {
            val readAt = OffsetDateTime.ofInstant(now, ZoneOffset.UTC)
            Notifications.update({
                val mine = (Notifications.userId eq userId) and Notifications.readAt.isNull()
                if (ids == null) mine else mine and (Notifications.id inList ids.toList())
            }) { it[Notifications.readAt] = readAt }
        }
    }

    override suspend fun unread(userId: UUID, excludingActors: Set<UUID>): Int = tx {
        val query = Notifications.selectAll().where { (Notifications.userId eq userId) and Notifications.readAt.isNull() }
        if (excludingActors.isEmpty()) {
            query.count().toInt()
        } else {
            // Pocas filas por usuario (retención de 90 días, E8.5): se filtra el actor del payload aquí.
            query.count { row -> actorIdOf(row[Notifications.payload] as? JsonObject) !in excludingActors }
        }
    }
}

/** Bandeja en memoria (pruebas y contenedor por defecto). [add] simula lo que guarda `FcmNotifier`. */
class InMemoryNotificationStore : NotificationStore {
    private val rows = CopyOnWriteArrayList<NotificationRecord>()

    fun add(userId: UUID, kind: String, payload: Map<String, JsonElement>, createdAt: Instant, id: UUID = UUID.randomUUID()): UUID {
        rows += NotificationRecord(id, userId, kind, JsonObject(payload), createdAt, readAt = null)
        return id
    }

    fun all(): List<NotificationRecord> = rows.toList()

    override suspend fun recent(userId: UUID, limit: Int): List<NotificationRecord> =
        rows.filter { it.userId == userId }
            .sortedWith(compareByDescending<NotificationRecord> { it.createdAt }.thenByDescending { it.id })
            .take(limit)

    override suspend fun markRead(userId: UUID, ids: Collection<UUID>?, now: Instant) {
        rows.replaceAll { row ->
            val target = row.userId == userId && row.readAt == null && (ids == null || row.id in ids)
            if (target) row.copy(readAt = now) else row
        }
    }

    override suspend fun unread(userId: UUID, excludingActors: Set<UUID>): Int =
        rows.count { it.userId == userId && it.readAt == null && it.actorId() !in excludingActors }
}
