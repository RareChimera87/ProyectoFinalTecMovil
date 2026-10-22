package me.santiagoreina.parcheu.api.simulation

import me.santiagoreina.parcheu.contract.PersonRef
import me.santiagoreina.parcheu.contract.Progress
import me.santiagoreina.parcheu.contract.SimulationStatus
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/** Documento `simulations/{id}` de Firestore (arquitectura.md §8.2): solo lectura para los participantes. */
data class SimulationMirrorDoc(
    val id: UUID,
    /** UID de Firebase de los dos participantes (las reglas leen `participants`). */
    val participants: List<String>,
    val status: SimulationStatus,
    val progress: Progress,
    val score: Int?,
    val updatedAt: Instant,
)

/**
 * Espejo del progreso en Firestore (ADR-0004). Postgres es la fuente de verdad: si el espejo falla,
 * el motor registra el error y sigue; la app tiene `GET /v1/simulations/{id}` de respaldo.
 */
fun interface SimulationMirror {
    suspend fun publish(doc: SimulationMirrorDoc)
}

/** Sin Firestore (`ENV=local` y pruebas de otros dominios). */
object NoopSimulationMirror : SimulationMirror {
    override suspend fun publish(doc: SimulationMirrorDoc) = Unit
}

/** Guarda cada publicación para que las pruebas la verifiquen. */
class RecordingSimulationMirror : SimulationMirror {
    private val published = CopyOnWriteArrayList<SimulationMirrorDoc>()
    val docs: List<SimulationMirrorDoc> get() = published.toList()

    fun last(id: UUID): SimulationMirrorDoc? = published.lastOrNull { it.id == id }

    override suspend fun publish(doc: SimulationMirrorDoc) {
        published += doc
    }
}

/**
 * Aviso al terminar (DONE o FAILED) a cada participante. El puerto general `Notifier` llega con
 * E5.3; mientras tanto el contenedor cablea [NoopSimulationNotifier] y, al mezclar E5.3, un
 * adaptador que llama a `Notifier.notify` con `kind = simulation_done | simulation_failed`.
 */
fun interface SimulationNotifier {
    suspend fun finished(userId: UUID, simulationId: UUID, status: SimulationStatus, score: Int?)
}

object NoopSimulationNotifier : SimulationNotifier {
    override suspend fun finished(userId: UUID, simulationId: UUID, status: SimulationStatus, score: Int?) = Unit
}

class RecordingSimulationNotifier : SimulationNotifier {
    data class Sent(val userId: UUID, val simulationId: UUID, val status: SimulationStatus, val score: Int?)

    private val sentList = CopyOnWriteArrayList<Sent>()
    val sent: List<Sent> get() = sentList.toList()

    override suspend fun finished(userId: UUID, simulationId: UUID, status: SimulationStatus, score: Int?) {
        sentList += Sent(userId, simulationId, status, score)
    }
}

/**
 * Cómo ve [viewer] a la otra persona de una simulación (`SimulationSummary.other`). La decide
 * `GraphVisibilityPolicy` (E3.1): con conexión mutua el nombre; si no, "Alguien de la UIS" y `anonId`.
 */
fun interface PersonPresenter {
    suspend fun present(viewer: UUID, subject: UUID): PersonRef
}
