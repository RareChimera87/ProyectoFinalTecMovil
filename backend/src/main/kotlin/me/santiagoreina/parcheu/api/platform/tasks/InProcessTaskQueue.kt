package me.santiagoreina.parcheu.api.platform.tasks

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

/**
 * Cola en proceso para el homeserver (`TASKS_MODE=inline`, ADR-0033). Ejecuta el handler registrado
 * para la ruta de cada tarea en una corrutina, sin bloquear la petición que encola.
 *
 * - Reintenta hasta [maxAttempts] veces con backoff 10 s, 30 s, 90 s, 270 s (±20 % de jitter).
 * - Limita la concurrencia por cola ([concurrency]).
 * - Deduplica por `cola/nombre` durante [dedupTtl], como la deduplicación de Cloud Tasks.
 * - No sobrevive a un reinicio: la recuperación la hacen los barridos de [me.santiagoreina.parcheu.api.platform.jobs.InProcessScheduler].
 */
class InProcessTaskQueue(
    private val clock: Clock,
    context: CoroutineContext = Dispatchers.IO,
    private val maxAttempts: Int = MAX_ATTEMPTS,
    private val backoff: List<Duration> = BACKOFF,
    private val dedupTtl: Duration = DEDUP_TTL,
    concurrency: Map<QueueKind, Int> = CONCURRENCY,
    /** Devuelve un factor en [-1, 1]; inyectable para tests deterministas. */
    private val jitter: () -> Double = { Math.random() * 2 - 1 },
) : TaskQueue, AutoCloseable {
    private val log = LoggerFactory.getLogger(InProcessTaskQueue::class.java)
    private val scope = CoroutineScope(SupervisorJob() + context)
    private val handlers = ConcurrentHashMap<String, suspend (String) -> Unit>()
    private val permits = QueueKind.entries.associateWith { Semaphore(concurrency[it] ?: 1) }
    private val seen = ConcurrentHashMap<String, Instant>()

    /** Rutas con handler (para el test de cobertura frente a las rutas `/internal/tasks`). */
    val registeredPaths: Set<String> get() = handlers.keys.toSet()

    fun register(path: String, handler: suspend (body: String) -> Unit) {
        handlers[path] = handler
    }

    override suspend fun enqueue(task: TaskRequest) {
        val handler = handlers[task.path] ?: error("Sin handler registrado para la tarea ${task.path}")
        val now = clock.instant()
        val key = "${task.queue}/${task.name}"
        // Limpia vencidas y comprueba/registra de forma atómica.
        seen.entries.removeIf { Duration.between(it.value, now) >= dedupTtl }
        if (seen.putIfAbsent(key, now) != null) {
            log.info("Tarea {} ya encolada en {}: no se duplica", task.name, task.queue)
            return
        }
        scope.launch {
            permits.getValue(task.queue).withPermit { run(task, handler) }
        }
    }

    private suspend fun run(task: TaskRequest, handler: suspend (String) -> Unit) {
        for (attempt in 1..maxAttempts) {
            try {
                handler(task.body)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attempt == maxAttempts) {
                    log.error("Tarea {} ({}) agotó {} intentos: se abandona", task.name, task.path, maxAttempts, e)
                    return
                }
                val wait = delayFor(attempt)
                log.warn("Tarea {} ({}) falló en el intento {}: reintento en {} s", task.name, task.path, attempt, wait.seconds, e)
                delay(wait.toMillis())
            }
        }
    }

    internal fun delayFor(attempt: Int): Duration {
        val base = backoff[minOf(attempt - 1, backoff.lastIndex)]
        val factor = 1.0 + JITTER * jitter().coerceIn(-1.0, 1.0)
        return Duration.ofMillis((base.toMillis() * factor).toLong())
    }

    override fun close() {
        scope.cancel()
    }

    companion object {
        const val MAX_ATTEMPTS = 5
        const val JITTER = 0.2
        val BACKOFF: List<Duration> = listOf(10L, 30L, 90L, 270L).map(Duration::ofSeconds)
        val DEDUP_TTL: Duration = Duration.ofHours(1)
        val CONCURRENCY: Map<QueueKind, Int> =
            mapOf(QueueKind.SIMULATIONS to 2, QueueKind.AI_LIGHT to 4, QueueKind.MAINTENANCE to 1)
    }
}
