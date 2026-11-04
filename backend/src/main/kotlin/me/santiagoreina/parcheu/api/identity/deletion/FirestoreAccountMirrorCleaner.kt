package me.santiagoreina.parcheu.api.identity.deletion

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.DocumentReference
import com.google.cloud.firestore.FieldValue
import com.google.cloud.firestore.Firestore
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.cloud.FirestoreClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Limpia el espejo de Firestore de una cuenta borrada (arquitectura.md §8.2 y §10.2) con el Admin SDK
 * (`sa-api`, ADC): borra `simulations/{id}` y `matchTickets/{uid}`, saca su uid de `members` de sus chats
 * y deja sus mensajes sin autor (`senderUid` = null, 'Usuario eliminado'); los mensajes del otro no se
 * tocan. Es idempotente: borrar lo que no existe no falla y la consulta de mensajes propios ya no
 * encuentra nada en el reintento. En una cuenta de prueba ([PurgePlan.isTest]) borra los chats completos con sus
 * mensajes. Con `FIRESTORE_EMULATOR_HOST` apunta al emulador.
 */
class FirestoreAccountMirrorCleaner(firestore: () -> Firestore) : AccountMirrorCleaner {
    private val firestore: Firestore by lazy(firestore)

    override suspend fun clean(plan: PurgePlan) {
        withContext(Dispatchers.IO) {
            val fs = firestore
            val uid = plan.firebaseUid
            commitInChunks(plan.simulationIds.map { fs.collection(SIMULATIONS).document(it.toString()) }) { batch, ref -> batch.delete(ref) }
            fs.collection(MATCH_TICKETS).document(uid).delete().get()

            // Chats de Postgres más los que el espejo aún liste con su uid (por si Postgres ya no los tiene).
            val mirrored = fs.collection(CHATS).whereArrayContains("members", uid).get().get().documents.map { it.id }
            val chatIds = (plan.chats.map { it.chatId.toString() } + mirrored).distinct()
            for (chatId in chatIds) {
                val chat = fs.collection(CHATS).document(chatId)
                if (plan.isTest) {
                    // Cuenta de prueba: no se conserva nada, ni el chat ni sus mensajes (los del otro tampoco).
                    commitInChunks(chat.collection(MESSAGES).get().get().documents.map { it.reference }) { batch, ref -> batch.delete(ref) }
                    chat.delete().get()
                    continue
                }
                val own = chat.collection(MESSAGES).whereEqualTo("senderUid", uid).get().get().documents.map { it.reference }
                commitInChunks(own) { batch, ref -> batch.update(ref, "senderUid", null) }
                if (chat.get().get().exists()) chat.update("members", FieldValue.arrayRemove(uid)).get()
            }
        }
    }

    override suspend fun removeSimulations(ids: List<UUID>) {
        withContext(Dispatchers.IO) {
            commitInChunks(ids.map { firestore.collection(SIMULATIONS).document(it.toString()) }) { batch, ref -> batch.delete(ref) }
        }
    }

    private fun commitInChunks(
        refs: List<DocumentReference>,
        op: (com.google.cloud.firestore.WriteBatch, DocumentReference) -> Unit,
    ) {
        // Un lote de Firestore admite 500 escrituras.
        refs.chunked(BATCH_SIZE).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { op(batch, it) }
            batch.commit().get()
        }
    }

    companion object {
        const val SIMULATIONS = "simulations"
        const val MATCH_TICKETS = "matchTickets"
        const val CHATS = "chats"
        const val MESSAGES = "messages"
        private const val BATCH_SIZE = 400

        /** Limpieza del proyecto [projectId] con la app por defecto de Firebase Admin. */
        fun forProject(
            projectId: String,
            credentials: () -> GoogleCredentials = { GoogleCredentials.getApplicationDefault() },
        ) = FirestoreAccountMirrorCleaner { FirestoreClient.getFirestore(defaultFirebaseApp(projectId, credentials)) }
    }
}

/** La app por defecto de Firebase Admin (la comparten verificador de tokens, espejos y FCM). */
internal fun defaultFirebaseApp(projectId: String, credentials: () -> GoogleCredentials): FirebaseApp =
    FirebaseApp.getApps().firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
        ?: FirebaseApp.initializeApp(FirebaseOptions.builder().setCredentials(credentials()).setProjectId(projectId).build())
