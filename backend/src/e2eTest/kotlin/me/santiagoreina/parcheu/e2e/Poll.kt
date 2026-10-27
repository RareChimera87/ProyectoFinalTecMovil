package me.santiagoreina.parcheu.e2e

import java.time.Duration

/** Falló la espera de una condición dentro de su plazo. */
class PollTimeout(description: String, timeout: Duration, last: String?) :
    AssertionError("Se agotó la espera (${timeout.seconds}s) de: $description" + (last?.let { ". Último estado: $it" } ?: ""))

/**
 * Repite [probe] hasta que devuelva no nulo o venza [timeout]. [probe] puede lanzar [PollFatal] para cortar de
 * inmediato (p. ej. una simulación FAILED no se arregla esperando).
 */
fun <T : Any> pollUntil(
    description: String,
    timeout: Duration,
    interval: Duration = Duration.ofSeconds(2),
    sleep: (Duration) -> Unit = { Thread.sleep(it.toMillis()) },
    now: () -> Long = System::nanoTime,
    probe: () -> T?,
): T {
    val deadline = now() + timeout.toNanos()
    var last: String? = null
    while (true) {
        try {
            probe()?.let { return it }
        } catch (e: PollFatal) {
            throw AssertionError("$description: ${e.message}", e)
        } catch (e: ApiException) {
            // Un 5xx o 404 transitorio (réplica fría, espejo que aún no llega) se reintenta hasta el plazo.
            last = e.message
        }
        if (now() >= deadline) throw PollTimeout(description, timeout, last)
        sleep(interval)
    }
}

class PollFatal(message: String) : RuntimeException(message)
