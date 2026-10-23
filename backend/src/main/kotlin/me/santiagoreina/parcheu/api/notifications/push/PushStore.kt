package me.santiagoreina.parcheu.api.notifications.push

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import me.santiagoreina.parcheu.api.platform.db.tables.FcmTokens
import me.santiagoreina.parcheu.api.platform.db.tables.Notifications
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/** Persistencia que necesita el push: la fila de `notifications` y los tokens de `fcm_tokens`. */
interface PushStore {
    /** Inserta el aviso (para C2) y devuelve su id. */
    suspend fun saveNotification(userId: UUID, kind: String, payload: JsonObject, now: Instant): UUID

    suspend fun tokensOf(userId: UUID): List<String>

    suspend fun deleteTokens(tokens: Collection<String>)
}

class ExposedPushStore(private val db: Database) : PushStore {
    private suspend fun <T> tx(block: () -> T): T = withContext(Dispatchers.IO) { transaction(db) { block() } }

    override suspend fun saveNotification(userId: UUID, kind: String, payload: JsonObject, now: Instant): UUID = tx {
        val id = UUID.randomUUID()
        Notifications.insert {
            it[Notifications.id] = id
            it[Notifications.userId] = userId
            it[Notifications.kind] = kind
            it[Notifications.payload] = payload
            it[createdAt] = OffsetDateTime.ofInstant(now, ZoneOffset.UTC)
        }
        id
    }

    override suspend fun tokensOf(userId: UUID): List<String> = tx {
        FcmTokens.selectAll().where { FcmTokens.userId eq userId }.map { it[FcmTokens.token] }
    }

    override suspend fun deleteTokens(tokens: Collection<String>) {
        if (tokens.isEmpty()) return
        tx { FcmTokens.deleteWhere { FcmTokens.token inList tokens.toList() } }
    }
}

/** Almacén en memoria para las pruebas sin base de datos. */
class InMemoryPushStore : PushStore {
    data class Row(val id: UUID, val userId: UUID, val kind: String, val payload: JsonObject, val createdAt: Instant)

    val notifications = java.util.Collections.synchronizedList(mutableListOf<Row>())
    val tokens = java.util.concurrent.ConcurrentHashMap<String, UUID>()

    override suspend fun saveNotification(userId: UUID, kind: String, payload: JsonObject, now: Instant): UUID {
        val id = UUID.randomUUID()
        notifications += Row(id, userId, kind, payload, now)
        return id
    }

    override suspend fun tokensOf(userId: UUID): List<String> = tokens.filterValues { it == userId }.keys.sorted()

    override suspend fun deleteTokens(tokens: Collection<String>) {
        tokens.forEach { this.tokens.remove(it) }
    }
}
