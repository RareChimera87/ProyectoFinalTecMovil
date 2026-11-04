package me.santiagoreina.parcheu.api.chat

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.Timestamp
import com.google.cloud.firestore.Firestore
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.cloud.FirestoreClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID

/**
 * Escribe `chats/{id}` y `chats/{id}/messages/{id}` con el Admin SDK (cuenta `sa-api`, ADC), que
 * ignora las reglas. El cliente de Firestore se crea en la primera escritura para que el arranque y
 * las pruebas no dependan de credenciales. Con `FIRESTORE_EMULATOR_HOST` apunta al emulador.
 */
class FirestoreChatMirror(firestore: () -> Firestore) : ChatMirror {
    private val firestore: Firestore by lazy(firestore)

    override suspend fun upsertChat(doc: ChatDoc) {
        withContext(Dispatchers.IO) { firestore.collection(CHATS).document(doc.id.toString()).set(chatFields(doc)).get() }
    }

    override suspend fun upsertMessage(chatId: UUID, doc: MessageDoc) {
        withContext(Dispatchers.IO) {
            firestore.collection(CHATS).document(chatId.toString())
                .collection(MESSAGES).document(doc.id.toString())
                .set(messageFields(doc))
                .get()
        }
    }

    companion object {
        const val CHATS = "chats"
        const val MESSAGES = "messages"

        /** Espejo del proyecto [projectId] con la app por defecto de Firebase Admin. */
        fun forProject(
            projectId: String,
            credentials: () -> GoogleCredentials = { GoogleCredentials.getApplicationDefault() },
        ) = FirestoreChatMirror {
            val app = FirebaseApp.getApps().firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
                ?: FirebaseApp.initializeApp(FirebaseOptions.builder().setCredentials(credentials()).setProjectId(projectId).build())
            FirestoreClient.getFirestore(app)
        }

        /** Campos de `chats/{id}` (arquitectura.md §8.2). */
        fun chatFields(doc: ChatDoc): Map<String, Any?> = mapOf(
            "members" to doc.members,
            "type" to doc.type.name,
            "mode" to doc.mode.name,
            "status" to doc.status.name,
            "endsAt" to doc.endsAt?.timestamp(),
        )

        /** Campos de `chats/{id}/messages/{id}`: los nombres son los de E0.8 y E5.2. */
        fun messageFields(doc: MessageDoc): Map<String, Any?> = mapOf(
            "senderUid" to doc.senderUid,
            "system" to doc.system,
            "text" to doc.text,
            "createdAt" to doc.createdAt.timestamp(),
            "hidden" to doc.hidden,
        )

        private fun Instant.timestamp(): Timestamp = Timestamp.ofTimeSecondsAndNanos(epochSecond, nano)
    }
}
