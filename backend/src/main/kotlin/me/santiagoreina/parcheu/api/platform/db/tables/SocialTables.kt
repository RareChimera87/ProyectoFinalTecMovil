package me.santiagoreina.parcheu.api.platform.db.tables

import me.santiagoreina.parcheu.contract.IntroductionStatus
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.RedeemStatus
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

/* Social: invitaciones por QR/código, presentaciones y solicitudes de presentación (§4, §6). */

object Invites : Table("invites") {
    /** Formato `XXXX-0000`. */
    val code = text("code")
    val userId = userRef("user_id", ReferenceOption.CASCADE)
    override val primaryKey = PrimaryKey(code)
}

/**
 * Canjes de códigos (E6.1, `V20261023_1200`): [redeemerId] pidió conectar con [ownerId] en [mode]. La
 * arista es simétrica, así que esta fila es la que dice quién debe aceptar.
 */
object InviteRedemptions : Table("invite_redemptions") {
    val ownerId = userRef("owner_id", ReferenceOption.CASCADE)
    val redeemerId = userRef("redeemer_id", ReferenceOption.CASCADE)
    val mode = enumText<Mode>("mode")
    val status = enumText<RedeemStatus>("status").default(RedeemStatus.PENDING)
    val createdAt = timestampWithTimeZone("created_at")
    val acceptedAt = timestampWithTimeZone("accepted_at").nullable()
    override val primaryKey = PrimaryKey(ownerId, redeemerId, mode)
}

/** Solo Amistad o Estudio: Pareja nunca se presenta a terceros (ADR-0012). */
object Introductions : Table("introductions") {
    val id = uuidPk()
    val introducerId = userRef("introducer_id", ReferenceOption.CASCADE)
    val mode = enumText<Mode>("mode")
    val userA = userRef("user_a", ReferenceOption.CASCADE)
    val userB = userRef("user_b", ReferenceOption.CASCADE)
    val simulationId = javaUUID("simulation_id").references(Simulations.id).nullable()
    val aResponse = enumText<IntroResponse>("a_response").nullable()
    val bResponse = enumText<IntroResponse>("b_response").nullable()
    val status = enumText<IntroductionStatus>("status").default(IntroductionStatus.SIMULATING)
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object IntroRequests : Table("intro_requests") {
    val id = uuidPk()
    val requesterId = userRef("requester_id", ReferenceOption.CASCADE)
    val viaUserId = userRef("via_user_id", ReferenceOption.CASCADE)
    val targetUserId = userRef("target_user_id", ReferenceOption.CASCADE)
    val status = enumText<IntroRequestStatus>("status").default(IntroRequestStatus.PENDING)
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}
