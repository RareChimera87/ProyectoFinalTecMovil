package me.santiagoreina.parcheu.api.platform.db.tables

import me.santiagoreina.parcheu.contract.FeedbackKind
import me.santiagoreina.parcheu.contract.MemoryStatus
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.TwinAnswer
import org.jetbrains.exposed.v1.core.FloatColumnType
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

/* Gemelo: preguntas, respuestas, rasgos, versiones, precisión, memorias y feedback (§4, §7.1). */

object Questions : Table("questions") {
    val id = short("id")
    val ord = short("ord")
    val text = text("text")
    val optionA = text("option_a")
    val optionB = text("option_b")
    /** `{"SOCIAL_ENERGY": 1.0, ...}` con las claves de `TraitKey`. */
    val weights = jsonbColumn("weights")
    override val primaryKey = PrimaryKey(id)
}

object TwinAnswers : Table("twin_answers") {
    val userId = userRef("user_id", ReferenceOption.CASCADE)
    val questionId = short("question_id").references(Questions.id)
    val answer = enumText<TwinAnswer>("answer")
    val answeredAt = timestampWithTimeZone("answered_at")
    override val primaryKey = PrimaryKey(userId, questionId)
}

object Twins : Table("twins") {
    val userId = userRef("user_id", ReferenceOption.CASCADE)
    /** 6 valores en [0, 1] en el orden de `TraitKey`. */
    val traits = array("traits", FloatColumnType()).nullable()
    val personaSummary = text("persona_summary").nullable()
    val personaReady = bool("persona_ready").default(false)
    val version = integer("version").default(1)
    val level = integer("level").default(1)
    val xp = integer("xp").default(0)
    val updatedAt = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(userId)
}

object TwinVersions : Table("twin_versions") {
    val userId = userRef("user_id", ReferenceOption.CASCADE)
    val version = integer("version")
    val traits = array("traits", FloatColumnType()).nullable()
    val personaSummary = text("persona_summary").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(userId, version)
}

object TwinAccuracy : Table("twin_accuracy") {
    val userId = userRef("user_id", ReferenceOption.CASCADE)
    /** Lunes de la semana en America/Bogota. */
    val week = date("week")
    val accuracy = short("accuracy")
    val ratings = integer("ratings").default(0)
    val predictions = integer("predictions").default(0)
    override val primaryKey = PrimaryKey(userId, week)
}

object TwinMemories : Table("twin_memories") {
    val id = uuidPk()
    val userId = userRef("user_id", ReferenceOption.CASCADE)
    val mode = enumText<Mode>("mode")
    val text = text("text")
    val sourceChatId = javaUUID("source_chat_id").nullable()
    val status = enumText<MemoryStatus>("status").default(MemoryStatus.PENDING)
    val createdAt = timestampWithTimeZone("created_at")
    val decidedAt = timestampWithTimeZone("decided_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object TwinFeedback : Table("twin_feedback") {
    val id = uuidPk()
    val userId = userRef("user_id", ReferenceOption.CASCADE)
    val kind = enumText<FeedbackKind>("kind")
    val simulationId = javaUUID("simulation_id").nullable()
    val turnIndex = short("turn_index").nullable()
    val questionId = short("question_id").references(Questions.id).nullable()
    val predicted = text("predicted").nullable()
    val actual = text("actual").nullable()
    val rating = short("rating").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object TwinLearningCursor : Table("twin_learning_cursor") {
    val userId = userRef("user_id", ReferenceOption.CASCADE)
    val lastMessageAt = timestampWithTimeZone("last_message_at").nullable()
    override val primaryKey = PrimaryKey(userId)
}
