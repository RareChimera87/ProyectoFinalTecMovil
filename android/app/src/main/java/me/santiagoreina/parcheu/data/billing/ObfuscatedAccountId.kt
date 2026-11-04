package me.santiagoreina.parcheu.data.billing

import java.security.MessageDigest

/**
 * `obfuscatedAccountId` que se manda a Play en `launchBillingFlow` (arquitectura §9, ADR-0013):
 * `sha256(userId)` en hex minúscula, donde `userId` es `MeDto.id` (id del usuario en la API). El servidor
 * (E7.1) recalcula el mismo valor y rechaza con 403 una compra hecha desde otra cuenta. Nunca se manda el
 * userId en claro: Play prohíbe datos personales en este campo.
 */
fun obfuscatedAccountId(userId: String): String {
    require(userId.isNotBlank()) { "userId vacío" }
    val digest = MessageDigest.getInstance("SHA-256").digest(userId.toByteArray(Charsets.UTF_8))
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
