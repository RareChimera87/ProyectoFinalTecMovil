package me.santiagoreina.parcheu.api.social.introductions

import me.santiagoreina.parcheu.api.platform.notifier.Notification
import me.santiagoreina.parcheu.api.platform.notifier.Notifier
import me.santiagoreina.parcheu.api.simulation.SimulationNotifier
import me.santiagoreina.parcheu.contract.NotificationKinds
import me.santiagoreina.parcheu.contract.SimulationStatus
import java.util.UUID

/**
 * Aviso de fin de simulación consciente de las presentaciones (E6.2). Si la simulación es de una
 * presentación (mismo id), las personas presentadas **no** reciben 'Tu simulación está lista': la
 * propuesta solo les llega cuando quien presenta la envía. Solo se avisa a quien presenta (una vez,
 * aunque el motor llame por cada participante). El resto de simulaciones sigue en [delegate].
 */
class IntroductionSimulationNotifier(
    private val repo: IntroductionRepository,
    private val delegate: SimulationNotifier,
    private val push: Notifier,
) : SimulationNotifier {
    override suspend fun finished(userId: UUID, simulationId: UUID, status: SimulationStatus, score: Int?) {
        val intro = repo.find(simulationId) ?: return delegate.finished(userId, simulationId, status, score)
        if (userId != intro.userA) return
        val notification = if (status == SimulationStatus.DONE) {
            Notification(
                kind = NotificationKinds.INTRODUCTION_READY,
                title = "Sus gemelos ya simularon",
                body = score?.let { "Afinidad: $it/100. Decide si les envías la propuesta." } ?: "Decide si les envías la propuesta.",
                route = "parcheu://intro-result/${intro.id}",
            )
        } else {
            Notification(
                kind = NotificationKinds.INTRODUCTION_READY,
                title = "La presentación no se pudo simular",
                body = "No se gastó tu cupo. Puedes intentarlo de nuevo.",
                route = "parcheu://intro-result/${intro.id}",
            )
        }
        push.notify(intro.introducerId, notification)
    }
}
