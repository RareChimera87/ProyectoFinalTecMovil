package me.santiagoreina.parcheu.api.simulation

import me.santiagoreina.parcheu.api.platform.notifier.Notification
import me.santiagoreina.parcheu.api.platform.notifier.Notifier
import me.santiagoreina.parcheu.contract.NotificationKinds
import me.santiagoreina.parcheu.contract.PersonRef
import me.santiagoreina.parcheu.contract.Progress
import me.santiagoreina.parcheu.contract.SimulationOrigin
import me.santiagoreina.parcheu.contract.SimulationStatus
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/** Documento `simulations/{id}` de Firestore (arquitectura.md §8.2): solo lectura para los participantes. */
data class SimulationMirrorDoc(
    val id: UUID,
    /** UID de Firebase de quienes leen el documento (las reglas leen `participants`; ver [mirrorReaders]). */
    val participants: List<String>,
    val status: SimulationStatus,
    val progress: Progress,
    val score: Int?,
    val updatedAt: Instant,
)

/**
 * UID de Firebase que pueden leer `simulations/{id}`: los dos participantes. En una presentación
 * (`INTRODUCTION`, E6.2) solo quien presenta: las personas presentadas se enteran por la propuesta
 * (`GET /v1/introductions/{id}`) y nunca si quien presenta eligió 'No por ahora'.
 */
suspend fun SimulationRepository.mirrorReaders(record: SimulationRecord): List<String> =
    if (record.origin == SimulationOrigin.INTRODUCTION) {
        listOfNotNull(record.requestedBy?.let { participant(it) }?.takeIf { !it.deleted }?.firebaseUid)
    } else {
        listOf(record.userA, record.userB).mapNotNull { participant(it)?.firebaseUid }
    }

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
 * Aviso al terminar (DONE o FAILED) a cada participante. El contenedor cablea
 * [NotifierSimulationNotifier], que lo entrega al puerto general `Notifier` (E5.3).
 */
fun interface SimulationNotifier {
    suspend fun finished(userId: UUID, simulationId: UUID, status: SimulationStatus, score: Int?)
}

/**
 * Adaptador al puerto general de avisos (E5.3, arquitectura.md §8.4): `SIMULATION_READY` lleva a
 * `parcheu://results/{id}` y `SIMULATION_FAILED` a `parcheu://simulation/{id}`.
 */
class NotifierSimulationNotifier(private val notifier: Notifier) : SimulationNotifier {
    override suspend fun finished(userId: UUID, simulationId: UUID, status: SimulationStatus, score: Int?) {
        val notification = if (status == SimulationStatus.FAILED) {
            Notification(
                kind = NotificationKinds.SIMULATION_FAILED,
                title = "La simulación no se pudo completar",
                body = "No se gastó tu cupo. Puedes intentarlo de nuevo.",
                route = "parcheu://simulation/$simulationId",
            )
        } else {
            Notification(
                kind = NotificationKinds.SIMULATION_READY,
                title = "Tu simulación está lista",
                body = score?.let { "Afinidad: $it/100. Mira cómo les fue." } ?: "Mira cómo les fue.",
                route = "parcheu://results/$simulationId",
            )
        }
        notifier.notify(userId, notification)
    }
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
