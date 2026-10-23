package me.santiagoreina.parcheu.api.demo

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.Firestore
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.cloud.FirestoreClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.santiagoreina.parcheu.api.chat.FirestoreChatMirror
import me.santiagoreina.parcheu.api.matching.FirestoreMatchTicketMirror
import me.santiagoreina.parcheu.api.simulation.FirestoreSimulationMirror

/**
 * Borra los espejos de Firestore de lo que purgó [DemoPurger]: `matchTickets/{uid}` de cada demo,
 * `chats/{id}` (con su subcolección `messages`) y `simulations/{id}`. Usa el Admin SDK (cuenta `sa-api`, ADC);
 * con `FIRESTORE_EMULATOR_HOST` apunta al emulador.
 */
class FirestoreDemoMirrorCleaner(firestore: () -> Firestore) : DemoMirrorCleaner {
    private val firestore: Firestore by lazy(firestore)

    override suspend fun clean(result: PurgeResult) {
        withContext(Dispatchers.IO) {
            result.firebaseUids.forEach { uid ->
                firestore.collection(FirestoreMatchTicketMirror.COLLECTION).document(uid).delete().get()
            }
            result.chatIds.forEach { id ->
                firestore.recursiveDelete(firestore.collection(FirestoreChatMirror.CHATS).document(id.toString())).get()
            }
            result.simulationIds.forEach { id ->
                firestore.collection(FirestoreSimulationMirror.COLLECTION).document(id.toString()).delete().get()
            }
        }
    }

    companion object {
        fun forProject(
            projectId: String,
            credentials: () -> GoogleCredentials = { GoogleCredentials.getApplicationDefault() },
        ) = FirestoreDemoMirrorCleaner {
            val app = FirebaseApp.getApps().firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
                ?: FirebaseApp.initializeApp(FirebaseOptions.builder().setCredentials(credentials()).setProjectId(projectId).build())
            FirestoreClient.getFirestore(app)
        }
    }
}
