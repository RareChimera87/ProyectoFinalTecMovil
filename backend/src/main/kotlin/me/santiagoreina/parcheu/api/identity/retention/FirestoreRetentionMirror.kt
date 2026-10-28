package me.santiagoreina.parcheu.api.identity.retention

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.santiagoreina.parcheu.api.identity.deletion.FirestoreAccountMirrorCleaner
import me.santiagoreina.parcheu.api.identity.deletion.defaultFirebaseApp
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.cloud.FirestoreClient
import java.time.Instant
import java.util.Date
import java.util.UUID

/**
 * Retención del espejo de Firestore (E8.8) con el Admin SDK (`sa-api`, ADC). Postgres es la verdad, pero el
 * espejo se limpia por sus propias fechas: `simulations/{id}.updatedAt` (la última publicación de una
 * simulación terminada es su cierre) y `matchTickets/{uid}.expiresAt`. Son consultas de un solo campo, así
 * que no piden índices compuestos. Borrar lo que ya no existe no falla: una segunda pasada devuelve 0.
 * Con `FIRESTORE_EMULATOR_HOST` apunta al emulador.
 */
class FirestoreRetentionMirror(firestore: () -> Firestore) : RetentionMirror {
    private val firestore: Firestore by lazy(firestore)

    override suspend fun deleteSimulationsBefore(cutoff: Instant): Int = withContext(Dispatchers.IO) {
        deleteWhereBefore(FirestoreAccountMirrorCleaner.SIMULATIONS, "updatedAt", cutoff) { doc ->
            // Una simulación sin terminar (QUEUED/RUNNING) no es 'terminada hace más de 30 días'.
            doc.getString("status") !in IN_PROGRESS
        }
    }

    override suspend fun deleteMatchTicketsExpiredBefore(cutoff: Instant): Int = withContext(Dispatchers.IO) {
        deleteWhereBefore(FirestoreAccountMirrorCleaner.MATCH_TICKETS, "expiresAt", cutoff) { true }
    }

    override suspend fun deleteChats(ids: List<UUID>): Int = withContext(Dispatchers.IO) {
        var deleted = 0
        for (id in ids) {
            val chat = firestore.collection(FirestoreAccountMirrorCleaner.CHATS).document(id.toString())
            val existed = chat.get().get().exists()
            val messages = chat.collection(FirestoreAccountMirrorCleaner.MESSAGES).get().get().documents
            messages.chunked(BATCH_SIZE).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().get()
            }
            if (existed) {
                chat.delete().get()
                deleted++
            } else if (messages.isNotEmpty()) {
                deleted++
            }
        }
        deleted
    }

    /** Pagina por [field] (sin repetir documentos que [accept] rechaza) y borra por lotes. */
    private fun deleteWhereBefore(collection: String, field: String, cutoff: Instant, accept: (DocumentSnapshot) -> Boolean): Int {
        var deleted = 0
        var last: DocumentSnapshot? = null
        while (true) {
            var query: Query = firestore.collection(collection)
                .whereLessThan(field, Date.from(cutoff)).orderBy(field).limit(BATCH_SIZE)
            last?.let { query = query.startAfter(it) }
            val page = query.get().get().documents
            if (page.isEmpty()) return deleted
            val doomed = page.filter(accept)
            if (doomed.isNotEmpty()) {
                val batch = firestore.batch()
                doomed.forEach { batch.delete(it.reference) }
                batch.commit().get()
                deleted += doomed.size
            }
            if (page.size < BATCH_SIZE) return deleted
            last = page.last()
        }
    }

    companion object {
        private val IN_PROGRESS = setOf("QUEUED", "RUNNING")
        private const val BATCH_SIZE = 400

        /** Retención del proyecto [projectId] con la app por defecto de Firebase Admin. */
        fun forProject(
            projectId: String,
            credentials: () -> GoogleCredentials = { GoogleCredentials.getApplicationDefault() },
        ) = FirestoreRetentionMirror { FirestoreClient.getFirestore(defaultFirebaseApp(projectId, credentials)) }
    }
}
