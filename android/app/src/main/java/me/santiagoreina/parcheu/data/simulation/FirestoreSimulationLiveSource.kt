package me.santiagoreina.parcheu.data.simulation

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import me.santiagoreina.parcheu.contract.Progress
import me.santiagoreina.parcheu.contract.SimulationStatus

/** Escucha `simulations/{id}` con el SDK de Firestore (solo lectura, reglas de participantes). */
class FirestoreSimulationLiveSource(
    private val firestore: () -> FirebaseFirestore = {
        FirebaseFirestore.getInstance()
    }
) : SimulationLiveSource {
    override fun observe(id: String): Flow<SimulationSnapshot> = callbackFlow {
        val registration =
            firestore().collection("simulations").document(id).addSnapshotListener {
                    snapshot,
                    error
                ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                snapshot?.toSimulationSnapshot()?.let { trySend(it) }
            }
        awaitClose { registration.remove() }
    }
}

/** `null` si el documento no existe todavía o no trae un estado reconocible. */
internal fun DocumentSnapshot.toSimulationSnapshot(): SimulationSnapshot? {
    if (!exists()) return null
    val status =
        getString("status")?.let { runCatching { SimulationStatus.valueOf(it) }.getOrNull() }
            ?: return null
    val progress = get("progress") as? Map<*, *>
    return SimulationSnapshot(
        status = status,
        progress =
        Progress(
            scenarioIndex = (progress?.get("scenarioIndex") as? Number)?.toInt() ?: 0,
            turn = (progress?.get("turn") as? Number)?.toInt() ?: 0,
            of = (progress?.get("of") as? Number)?.toInt() ?: 0
        ),
        score = getLong("score")?.toInt()
    )
}
