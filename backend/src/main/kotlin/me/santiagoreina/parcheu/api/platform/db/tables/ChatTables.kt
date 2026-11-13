package me.santiagoreina.parcheu.api.platform.db.tables

import me.santiagoreina.parcheu.contract.ChatStatus
import me.santiagoreina.parcheu.contract.ChatType
import me.santiagoreina.parcheu.contract.Mode
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

/* Chat directo y de escenario (§4, §8). Postgres es la fuente de verdad; Firestore solo replica. */

object Chats : Table("chats") {
    val id = uuidPk()
    val type = enumText<ChatType>("type")
    val mode = enumText<Mode>("mode")
    val scenarioId = short("scenario_id").references(Scenarios.id).nullable()
    val endsAt = timestampWithTimeZone("ends_at").nullable()
    val status = enumText<ChatStatus>("status").default(ChatStatus.OPEN)
    val createdAt = timestampWithTimeZone("created_at")
    val lastMessageAt = timestampWithTimeZone("last_message_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object ChatMembers : Table("chat_members") {
    val chatId = javaUUID("chat_id").references(Chats.id, onDelete = ReferenceOption.CASCADE)
    val userId = userRef("user_id", ReferenceOption.CASCADE)
    val muted = bool("muted").default(false)
    val lastReadAt = timestampWithTimeZone("last_read_at").nullable()
    val agreedAt = timestampWithTimeZone("agreed_at").nullable()
    override val primaryKey = PrimaryKey(chatId, userId)
}

object ChatMessages : Table("chat_messages") {
    val id = uuidPk()
    val chatId = javaUUID("chat_id").references(Chats.id, onDelete = ReferenceOption.CASCADE)
    /** Nulo = mensaje del sistema o autor anonimizado tras borrar su cuenta. */
    val senderId = userRef("sender_id").nullable()
    val text = text("text")
    val learnable = bool("learnable")
    val hidden = bool("hidden").default(false)
    val clientId = text("client_id").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    /** Nulo = la moderación aún no terminó con este mensaje (lo recupera el barrido `moderation-sweep`). */
    val moderatedAt = timestampWithTimeZone("moderated_at").nullable()
    /** El aviso de ayuda ante autolesión falló y sigue pendiente (`moderate-help`). */
    val moderationHelpPending = bool("moderation_help_pending").default(false)
    /** Cuántas veces el barrido reencoló este mensaje sin que terminara; al llegar al tope se da por moderado. */
    val moderationSweeps = integer("moderation_sweeps").default(0)
    override val primaryKey = PrimaryKey(id)
}
