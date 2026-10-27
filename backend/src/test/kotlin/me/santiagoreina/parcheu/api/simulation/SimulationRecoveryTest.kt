package me.santiagoreina.parcheu.api.simulation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.SimulationOrigin
import me.santiagoreina.parcheu.contract.SimulationStatus
import java.time.Duration
import java.time.Instant
import java.util.UUID

class SimulationRecoveryTest : FunSpec({
    val now: Instant = FIXED_CLOCK.instant()

    fun record(id: UUID, status: SimulationStatus, age: Duration) = SimulationRecord(
        id = id, mode = Mode.AMISTAD, userA = UUID.randomUUID(), userB = UUID.randomUUID(), requestedBy = null,
        origin = SimulationOrigin.PARCHE, status = status, progress = initialProgress(), score = null, report = null,
        transcript = StoredTranscript(emptyList()), twinVersionA = 1, twinVersionB = 1, cachedFrom = null, provider = null,
        usage = UsageTotals(), error = null, createdAt = now.minus(age), finishedAt = null,
    )

    val queued = UUID.fromString("00000000-0000-0000-0000-000000000001")
    val running = UUID.fromString("00000000-0000-0000-0000-000000000002")
    val fresh = UUID.fromString("00000000-0000-0000-0000-000000000003")
    val old = UUID.fromString("00000000-0000-0000-0000-000000000004")
    val done = UUID.fromString("00000000-0000-0000-0000-000000000005")

    suspend fun seed(repo: SimulationRepository) {
        repo.create(record(queued, SimulationStatus.QUEUED, Duration.ofMinutes(30)))
        repo.create(record(running, SimulationStatus.RUNNING, Duration.ofHours(2)))
        repo.create(record(fresh, SimulationStatus.QUEUED, Duration.ofMinutes(2)))
        repo.create(record(old, SimulationStatus.QUEUED, Duration.ofHours(30)))
        repo.create(record(done, SimulationStatus.DONE, Duration.ofHours(1)))
    }

    test("unfinished: solo QUEUED/RUNNING dentro de la ventana, las más antiguas primero y con límite") {
        runBlocking {
            val repo = InMemorySimulationRepository()
            seed(repo)
            val window = repo.unfinished(now.minus(Duration.ofMinutes(10)), now.minus(Duration.ofHours(24)), 100)
            window.map { it.id } shouldBe listOf(running, queued)
            repo.unfinished(now.minus(Duration.ofMinutes(10)), now.minus(Duration.ofHours(24)), 1).map { it.id } shouldBe listOf(running)
        }
    }

    test("requeueUnfinished reencola simulate-<id> y es idempotente en la cola") {
        runBlocking {
            val fixture = SimFixture(inline = false)
            seed(fixture.repo)
            fixture.module.service.requeueUnfinished() shouldBe 2
            fixture.module.service.requeueUnfinished() shouldBe 2
            fixture.queue.tasks.map { it.name } shouldBe listOf("simulate-$running", "simulate-$queued")
            fixture.queue.tasks.all { it.path == SimulationService.SIMULATE_PATH } shouldBe true
        }
    }
})
