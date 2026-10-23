package me.santiagoreina.parcheu.api.platform.notifier

import java.util.UUID

/**
 * Aviso a una persona. `kind` es uno de `NotificationKinds` del contrato (texto abierto) y `route`
 * el deep link `parcheu://...` al que lleva al tocarlo (ver docs/arquitectura.md §8.4).
 */
data class Notification(
    val kind: String,
    val title: String,
    val body: String,
    val route: String? = null,
    /** Persona que origina el aviso (quien escribe, quien quiere conectar); la usa C2. */
    val actorId: UUID? = null,
    /** Contenido de ParcheU+ que el plan gratis ve bloqueado en C2. */
    val locked: Boolean = false,
)

/**
 * Puerto de salida de los avisos (arquitectura.md §3.1, `platform/notifier`). Los demás dominios
 * dependen de esta interfaz; `FcmNotifier` es la implementación real y [FakeNotifier] la de pruebas.
 */
interface Notifier {
    /** Guarda el aviso para C2 y lo envía como push a los dispositivos de [userId]. */
    suspend fun notify(userId: UUID, notification: Notification)
}

/** No hace nada: valor por defecto del contenedor cuando no hay push (tests de otros dominios). */
object NoopNotifier : Notifier {
    override suspend fun notify(userId: UUID, notification: Notification) = Unit
}

/** Registra los avisos en memoria para que las pruebas de otros dominios los verifiquen. */
class FakeNotifier : Notifier {
    data class Sent(val userId: UUID, val notification: Notification)

    private val _sent = java.util.Collections.synchronizedList(mutableListOf<Sent>())
    val sent: List<Sent> get() = synchronized(_sent) { _sent.toList() }

    override suspend fun notify(userId: UUID, notification: Notification) {
        _sent += Sent(userId, notification)
    }
}
