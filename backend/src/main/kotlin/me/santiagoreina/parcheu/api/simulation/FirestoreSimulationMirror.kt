package me.santiagoreina.parcheu.api.simulation

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.Firestore
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.cloud.FirestoreClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * Escribe `simulations/{id}` con Firebase Admin (cuenta `sa-api`, ADC). La conexión se crea en la
 * primera publicación, no al arrancar, para que el servicio y las pruebas no dependan de credenciales.
 */
class FirestoreSimulationMirror(
    private val projectId: String,
    private val credentials: () -> GoogleCredentials = { GoogleCredentials.getApplicationDefault() },
) : SimulationMirror {
    private val firestore: Firestore by lazy {
        val app = FirebaseApp.getApps().firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
            ?: FirebaseApp.initializeApp(FirebaseOptions.builder().setCredentials(credentials()).setProjectId(projectId).build())
        FirestoreClient.getFirestore(app)
    }

    override suspend fun publish(doc: SimulationMirrorDoc) {
        val data = mapOf(
            "participants" to doc.participants,
            "status" to doc.status.name,
            "progress" to mapOf("scenarioIndex" to doc.progress.scenarioIndex, "turn" to doc.progress.turn, "of" to doc.progress.of),
            "score" to doc.score,
            "updatedAt" to Date.from(doc.updatedAt),
        )
        withContext(Dispatchers.IO) { firestore.collection(COLLECTION).document(doc.id.toString()).set(data).get() }
    }

    companion object {
        const val COLLECTION = "simulations"
    }
}
