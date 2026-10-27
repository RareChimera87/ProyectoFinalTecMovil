package me.santiagoreina.parcheu.api.platform.auth

import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.auth.oauth2.GoogleCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/** Implementación real: Firebase Admin con las credenciales por defecto de la cuenta `sa-api`. */
class FirebaseTokenVerifier(
    private val projectId: String,
    private val credentials: () -> GoogleCredentials = { GoogleCredentials.getApplicationDefault() },
) : TokenVerifier {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Diferido: las credenciales (ADC) se resuelven en la primera verificación, no al construir,
     * para que el arranque y los tests no dependan de ellas.
     */
    private val auth: FirebaseAuth by lazy {
        val app = FirebaseApp.getApps().firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
            ?: FirebaseApp.initializeApp(
                FirebaseOptions.builder()
                    .setCredentials(credentials())
                    .setProjectId(projectId)
                    .build(),
            )
        FirebaseAuth.getInstance(app)
    }

    override suspend fun verify(token: String): FirebaseUser? = withContext(Dispatchers.IO) {
        try {
            val decoded = auth.verifyIdToken(token)
            FirebaseUser(
                decoded.uid,
                decoded.email,
                decoded.name,
                admin = decoded.claims["admin"] == true,
                isTest = decoded.claims["is_test"] == true,
            )
        } catch (e: FirebaseAuthException) {
            log.debug("ID token rechazado: {}", e.authErrorCode)
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
