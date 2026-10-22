package me.santiagoreina.parcheu.api.matching

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.Timestamp
import com.google.cloud.firestore.Firestore
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.cloud.FirestoreClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Espejo de solo lectura de los tickets en Firestore (`matchTickets/{firebaseUid}`, ADR-0004).
 * Postgres es la fuente de verdad: si publicar falla, el servicio lo registra y sigue; la app tiene
 * `GET /v1/match/queue` de respaldo.
 */
interface MatchTicketMirror {
    suspend fun publish(doc: TicketMirrorDoc)

    /** Borra el documento de quien canceló: el ticket ya no existe en la fila. */
    suspend fun remove(firebaseUid: String)
}

/** Sin Firestore (`ENV=local` y pruebas de otros dominios). */
object NoopMatchTicketMirror : MatchTicketMirror {
    override suspend fun publish(doc: TicketMirrorDoc) = Unit

    override suspend fun remove(firebaseUid: String) = Unit
}

/** Guarda cada publicación para que las pruebas las verifiquen. */
class RecordingMatchTicketMirror : MatchTicketMirror {
    private val published = CopyOnWriteArrayList<TicketMirrorDoc>()
    val docs: List<TicketMirrorDoc> get() = published.toList()

    fun last(firebaseUid: String): TicketMirrorDoc? = published.lastOrNull { it.firebaseUid == firebaseUid }

    private val removedUids = CopyOnWriteArrayList<String>()
    val removed: List<String> get() = removedUids.toList()

    override suspend fun publish(doc: TicketMirrorDoc) {
        published += doc
    }

    override suspend fun remove(firebaseUid: String) {
        removedUids += firebaseUid
    }
}

/**
 * Escribe `matchTickets/{uid}` con el Admin SDK (cuenta `sa-api`, ADC), que ignora las reglas. El
 * cliente de Firestore se crea en la primera publicación para que el arranque y las pruebas no
 * dependan de credenciales; con `FIRESTORE_EMULATOR_HOST` apunta al emulador.
 */
class FirestoreMatchTicketMirror(firestore: () -> Firestore) : MatchTicketMirror {
    private val firestore: Firestore by lazy(firestore)

    override suspend fun publish(doc: TicketMirrorDoc) {
        withContext(Dispatchers.IO) { firestore.collection(COLLECTION).document(doc.firebaseUid).set(fields(doc)).get() }
    }

    override suspend fun remove(firebaseUid: String) {
        withContext(Dispatchers.IO) { firestore.collection(COLLECTION).document(firebaseUid).delete().get() }
    }

    companion object {
        const val COLLECTION = "matchTickets"

        /** Campos de `matchTickets/{uid}` (arquitectura.md §8.2): los que leen las reglas y la UI de F1. */
        fun fields(doc: TicketMirrorDoc): Map<String, Any?> = mapOf(
            "status" to doc.status.name,
            "mode" to doc.mode.name,
            "matchId" to doc.matchId?.toString(),
            "expiresAt" to doc.expiresAt.timestamp(),
        )

        /** Espejo del proyecto [projectId] con la app por defecto de Firebase Admin. */
        fun forProject(
            projectId: String,
            credentials: () -> GoogleCredentials = { GoogleCredentials.getApplicationDefault() },
        ) = FirestoreMatchTicketMirror {
            val app = FirebaseApp.getApps().firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
                ?: FirebaseApp.initializeApp(FirebaseOptions.builder().setCredentials(credentials()).setProjectId(projectId).build())
            FirestoreClient.getFirestore(app)
        }

        private fun Instant.timestamp(): Timestamp = Timestamp.ofTimeSecondsAndNanos(epochSecond, nano)
    }
}
