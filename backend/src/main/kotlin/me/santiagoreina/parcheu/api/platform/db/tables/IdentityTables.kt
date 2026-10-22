package me.santiagoreina.parcheu.api.platform.db.tables

import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Visibility
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

/* Identidad: usuarios, catálogos, modos, consentimientos y tokens FCM (arquitectura.md §4). */

object Universities : Table("universities") {
    val id = short("id")
    val shortName = text("short_name")
    val name = text("name")
    val active = bool("active").default(true)
    override val primaryKey = PrimaryKey(id)
}

object Interests : Table("interests") {
    val slug = text("slug")
    val label = text("label")
    val icon = text("icon")
    override val primaryKey = PrimaryKey(slug)
}

object Users : Table("users") {
    val id = uuidPk()
    val firebaseUid = text("firebase_uid")
    val email = text("email").nullable()
    val displayName = text("display_name").nullable()
    val photoUrl = text("photo_url").nullable()
    val career = text("career").nullable()
    val universityId = short("university_id").references(Universities.id).nullable()
    val interests = array("interests", TextColumnType()).default(emptyList())
    val birthDate = date("birth_date").nullable()
    val ageVerifiedAt = timestampWithTimeZone("age_verified_at").nullable()
    val underageAt = timestampWithTimeZone("underage_at").nullable()
    val parejaConfirmedAt = timestampWithTimeZone("pareja_confirmed_at").nullable()
    val visibility = enumText<Visibility>("visibility").default(Visibility.ANONYMOUS)
    val isDemo = bool("is_demo").default(false)
    val isTest = bool("is_test").default(false)
    val suspendedUntil = timestampWithTimeZone("suspended_until").nullable()
    val deletedAt = timestampWithTimeZone("deleted_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object UserModes : Table("user_modes") {
    val userId = userRef("user_id", ReferenceOption.CASCADE)
    val mode = enumText<Mode>("mode")
    val enabled = bool("enabled").default(false)
    val enabledAt = timestampWithTimeZone("enabled_at").nullable()
    override val primaryKey = PrimaryKey(userId, mode)
}

/** Solo agrega filas: la base rechaza UPDATE y DELETE con un trigger (ADR-0017). */
object Consents : Table("consents") {
    val id = long("id").autoIncrement()
    val userId = userRef("user_id")
    val kind = enumText<ConsentKind>("kind")
    val granted = bool("granted")
    val textVersion = text("text_version")
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object FcmTokens : Table("fcm_tokens") {
    val token = text("token")
    val userId = userRef("user_id", ReferenceOption.CASCADE)
    val updatedAt = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(token)
}
