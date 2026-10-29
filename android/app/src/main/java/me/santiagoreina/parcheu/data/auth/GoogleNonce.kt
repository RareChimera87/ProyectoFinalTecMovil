package me.santiagoreina.parcheu.data.auth

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * `nonce` del login con Google (A-B2): 32 bytes aleatorios por intento, de los que se entrega a Credential
 * Manager (`GetGoogleIdOption.setNonce`) el SHA-256 en hexadecimal. Google lo copia en la claim `nonce` del
 * ID token, así que el token queda ligado a *esta* petición.
 *
 * **Limitación:** `GoogleAuthProvider.getCredential(idToken, accessToken)` no recibe el nonce y Firebase Auth
 * no lo verifica para el proveedor `google.com` (solo lo hace con `OAuthProvider` y `rawNonce`, que es otro
 * flujo). Por eso la comprobación la hace la app con [matches] antes de llamar a `signInWithCredential`;
 * [raw] se conserva por si Firebase llega a exigirlo.
 */
class GoogleNonce private constructor(val raw: String, val hashed: String) {
    /**
     * `false` solo si el ID token trae una claim `nonce` distinta de [hashed] (token de otra petición). Un
     * token sin la claim (Play Services antiguos que ignoran `setNonce`) se acepta para no romper el login.
     */
    fun matches(idToken: String): Boolean {
        val claim = nonceClaimOf(idToken) ?: return true
        return claim == hashed
    }

    companion object {
        fun generate(random: SecureRandom = SecureRandom()): GoogleNonce {
            val bytes = ByteArray(NONCE_BYTES).also(random::nextBytes)
            val raw = bytes.toHex()
            return GoogleNonce(raw, MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).toHex())
        }

        private const val NONCE_BYTES = 32

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

        /** Claim `nonce` del payload del JWT, o null si el token no es un JWT legible o no la trae. */
        internal fun nonceClaimOf(idToken: String): String? = try {
            val payload = idToken.split('.').getOrNull(1) ?: return null
            val json = String(
                Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP),
                Charsets.UTF_8
            )
            (Json.parseToJsonElement(json) as? JsonObject)?.get("nonce")?.jsonPrimitive?.content
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: kotlinx.serialization.SerializationException) {
            null
        }
    }
}
