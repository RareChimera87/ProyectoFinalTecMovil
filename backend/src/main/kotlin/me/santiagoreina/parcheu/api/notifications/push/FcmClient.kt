package me.santiagoreina.parcheu.api.notifications.push

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/** Resultado de enviar un mensaje de datos a un token. */
sealed interface FcmResult {
    data object Sent : FcmResult

    /** FCM dice que el token ya no existe (app desinstalada o token rotado): hay que borrarlo. */
    data object Unregistered : FcmResult

    /** Fallo transitorio o de configuración: el token se conserva. */
    data class Failed(val reason: String) : FcmResult
}

/** Costura sobre FCM: la real usa Firebase Admin; los tests usan [FakeFcmClient]. */
interface FcmClient {
    suspend fun send(token: String, data: Map<String, String>): FcmResult
}

/** Implementación real con las credenciales por defecto de la cuenta `sa-api` (permiso `firebasecloudmessaging.messages.create`). */
class FirebaseFcmClient(
    private val projectId: String,
    private val credentials: () -> GoogleCredentials = { GoogleCredentials.getApplicationDefault() },
) : FcmClient {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Diferido, igual que `FirebaseTokenVerifier`: el arranque y los tests no dependen de las credenciales. */
    private val messaging: FirebaseMessaging by lazy {
        val app = FirebaseApp.getApps().firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
            ?: FirebaseApp.initializeApp(
                FirebaseOptions.builder().setCredentials(credentials()).setProjectId(projectId).build(),
            )
        FirebaseMessaging.getInstance(app)
    }

    override suspend fun send(token: String, data: Map<String, String>): FcmResult = withContext(Dispatchers.IO) {
        try {
            // Solo mensajes de datos: la app arma la notificación (canal, deep link) aunque esté en segundo plano.
            val message = Message.builder()
                .setToken(token)
                .putAllData(data)
                .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
                .build()
            messaging.send(message)
            FcmResult.Sent
        } catch (e: FirebaseMessagingException) {
            if (e.messagingErrorCode == MessagingErrorCode.UNREGISTERED) {
                FcmResult.Unregistered
            } else {
                log.warn("FCM rechazó el envío: {}", e.messagingErrorCode)
                FcmResult.Failed(e.messagingErrorCode?.name ?: "UNKNOWN")
            }
        } catch (e: RuntimeException) {
            log.warn("No se pudo enviar el push", e)
            FcmResult.Failed(e.javaClass.simpleName)
        }
    }
}

/** Cliente de pruebas: responde según el token y guarda lo enviado. */
class FakeFcmClient(private val results: Map<String, FcmResult> = emptyMap()) : FcmClient {
    data class Call(val token: String, val data: Map<String, String>)

    val calls = java.util.Collections.synchronizedList(mutableListOf<Call>())

    override suspend fun send(token: String, data: Map<String, String>): FcmResult {
        calls += Call(token, data)
        return results[token] ?: FcmResult.Sent
    }
}
