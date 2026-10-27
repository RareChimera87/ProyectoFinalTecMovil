package me.santiagoreina.parcheu.api.platform.jobs

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.coroutines.CoroutineContext

/**
 * Sustituto de Cloud Scheduler en el homeserver (`TASKS_MODE=inline`, ADR-0033): un bucle por job.
 * Un fallo se registra y el siguiente ciclo reintenta; nunca tumba el proceso.
 */
class InProcessScheduler(context: CoroutineContext = Dispatchers.IO) : AutoCloseable {
    private val log = LoggerFactory.getLogger(InProcessScheduler::class.java)
    private val scope = CoroutineScope(SupervisorJob() + context)

    fun every(name: String, period: Duration, initialDelay: Duration = Duration.ZERO, block: suspend () -> Unit) {
        scope.launch {
            delay(initialDelay.toMillis())
            while (true) {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.error("El job {} falló: se reintenta en el siguiente ciclo", name, e)
                }
                delay(period.toMillis())
            }
        }
    }

    override fun close() {
        scope.cancel()
    }
}
