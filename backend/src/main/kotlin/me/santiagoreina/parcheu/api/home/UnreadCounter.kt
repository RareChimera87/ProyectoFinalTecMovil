package me.santiagoreina.parcheu.api.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.santiagoreina.parcheu.api.platform.db.tables.Notifications
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/** Conteo de avisos no leídos para la campana de Inicio (`notifications.read_at` nulo). */
fun interface UnreadCounter {
    suspend fun unread(userId: UUID): Int

    companion object {
        /** Sin base de datos (pruebas y `ENV=local` en memoria): nada pendiente. */
        val NONE = UnreadCounter { 0 }
    }
}

/** Cuenta en Postgres las filas de `notifications` del usuario sin `read_at` (las escribe E5.3). */
class ExposedUnreadCounter(private val db: Database) : UnreadCounter {
    override suspend fun unread(userId: UUID): Int = withContext(Dispatchers.IO) {
        transaction(db) {
            Notifications.selectAll()
                .where { (Notifications.userId eq userId) and Notifications.readAt.isNull() }
                .count()
                .toInt()
        }
    }
}
