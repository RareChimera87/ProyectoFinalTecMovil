package me.santiagoreina.parcheu.api.platform.tasks

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger

/** Reloj atado al tiempo virtual del [TestScope]. */
private class VirtualClock(private val scope: TestScope) : Clock() {
    override fun getZone() = ZoneOffset.UTC
    override fun withZone(zone: java.time.ZoneId?) = this
    override fun instant(): Instant = Instant.EPOCH.plusMillis(scope.currentTime)
}

@OptIn(ExperimentalCoroutinesApi::class)
class InProcessTaskQueueTest : FunSpec({
    fun task(name: String = "t1", queue: QueueKind = QueueKind.AI_LIGHT, path: String = "/tasks/x") =
        TaskRequest(queue, path, name, """{"id":"1"}""")

    fun TestScope.queue(concurrency: Map<QueueKind, Int> = InProcessTaskQueue.CONCURRENCY) =
        InProcessTaskQueue(VirtualClock(this), StandardTestDispatcher(testScheduler), jitter = { 0.0 }, concurrency = concurrency)

    test("ejecuta el handler con el cuerpo, sin bloquear al que encola") {
        kotlinx.coroutines.test.runTest {
            val q = queue()
            val bodies = mutableListOf<String>()
            q.register("/tasks/x") { bodies += it }
            q.enqueue(task())
            bodies.size shouldBe 0
            runCurrent()
            bodies shouldBe listOf("""{"id":"1"}""")
            q.close()
        }
    }

    test("deduplica por cola y nombre, y vuelve a aceptar tras el TTL de 1 h") {
        kotlinx.coroutines.test.runTest {
            val q = queue()
            val runs = AtomicInteger()
            q.register("/tasks/x") { runs.incrementAndGet() }
            q.enqueue(task())
            q.enqueue(task())
            runCurrent()
            runs.get() shouldBe 1
            q.enqueue(task(queue = QueueKind.MAINTENANCE)) // otra cola: no choca
            runCurrent()
            runs.get() shouldBe 2
            advanceTimeBy(Duration.ofMinutes(59).toMillis())
            q.enqueue(task())
            runCurrent()
            runs.get() shouldBe 2
            advanceTimeBy(Duration.ofMinutes(2).toMillis())
            q.enqueue(task())
            runCurrent()
            runs.get() shouldBe 3
            q.close()
        }
    }

    test("reintenta con backoff 10/30/90/270 s y se detiene a los 5 intentos") {
        kotlinx.coroutines.test.runTest {
            val q = queue()
            val times = mutableListOf<Long>()
            q.register("/tasks/x") {
                times += currentTime
                error("falla")
            }
            q.enqueue(task())
            advanceUntilIdle()
            times shouldBe listOf(0L, 10_000L, 40_000L, 130_000L, 400_000L)
            q.close()
        }
    }

    test("un reintento exitoso detiene los siguientes") {
        kotlinx.coroutines.test.runTest {
            val q = queue()
            val runs = AtomicInteger()
            q.register("/tasks/x") { if (runs.incrementAndGet() < 3) error("falla") }
            q.enqueue(task())
            advanceUntilIdle()
            runs.get() shouldBe 3
            q.close()
        }
    }

    test("el jitter se aplica dentro de ±20 %") {
        kotlinx.coroutines.test.runTest {
            val q = InProcessTaskQueue(VirtualClock(this), StandardTestDispatcher(testScheduler), jitter = { 1.0 })
            q.delayFor(1) shouldBe Duration.ofSeconds(12)
            val low = InProcessTaskQueue(VirtualClock(this), StandardTestDispatcher(testScheduler), jitter = { -1.0 })
            low.delayFor(2) shouldBe Duration.ofSeconds(24)
            q.close()
            low.close()
        }
    }

    test("ruta sin handler lanza al encolar") {
        kotlinx.coroutines.test.runTest {
            val q = queue()
            shouldThrow<IllegalStateException> { q.enqueue(task(path = "/tasks/nada")) }
            q.close()
        }
    }

    test("la concurrencia por cola se respeta") {
        kotlinx.coroutines.test.runTest {
            val q = queue(mapOf(QueueKind.SIMULATIONS to 2))
            val active = AtomicInteger()
            var peak = 0
            val done = AtomicInteger()
            q.register("/tasks/x") {
                peak = maxOf(peak, active.incrementAndGet())
                delay(1_000)
                active.decrementAndGet()
                done.incrementAndGet()
            }
            repeat(5) { q.enqueue(task("s$it", QueueKind.SIMULATIONS)) }
            advanceUntilIdle()
            done.get() shouldBe 5
            peak shouldBe 2
            q.close()
        }
    }

    test("close cancela el trabajo pendiente") {
        kotlinx.coroutines.test.runTest {
            val q = queue()
            val runs = AtomicInteger()
            q.register("/tasks/x") { runs.incrementAndGet(); error("falla") }
            q.enqueue(task())
            runCurrent()
            q.close()
            advanceUntilIdle()
            runs.get() shouldBe 1
        }
    }
})
