package me.santiagoreina.parcheu.api.platform.auth

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * Verifica tokens OIDC emitidos por Google: firma, expiración, audiencia (= URL del servicio)
 * y que el correo verificado sea el de la cuenta de servicio de tareas (`sa-tasks`).
 */
class GoogleOidcVerifier(
    private val audience: String,
    private val allowedEmail: String,
) : OidcVerifier {
    private val log = LoggerFactory.getLogger(javaClass)
    private val verifier = GoogleIdTokenVerifier.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance())
        .setAudience(listOf(audience))
        .build()

    override suspend fun verify(token: String): ServiceCaller? = withContext(Dispatchers.IO) {
        try {
            val payload = verifier.verify(token)?.payload ?: return@withContext null
            val email = payload.email
            if (payload.emailVerified != true || !email.equals(allowedEmail, ignoreCase = true)) {
                log.warn("Token OIDC de una cuenta no autorizada: {}", email)
                return@withContext null
            }
            ServiceCaller(email)
        } catch (e: Exception) {
            log.debug("Token OIDC rechazado: {}", e.message)
            null
        }
    }
}
