package me.santiagoreina.parcheu.api.graph

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.Mode
import java.util.UUID

/** Alta y actualización de aristas desde simulaciones, decisiones, invitaciones y presentaciones. */
class EdgeServiceTest : FunSpec({
    val a = person(1)
    val b = person(2)
    val now = FIXED_CLOCK.instant()

    fun setup(): Pair<InMemoryGraphRepository, EdgeService> {
        val repo = memoryRepo(a, b)
        return repo to EdgeService(repo, FIXED_CLOCK)
    }

    test("una simulación crea la arista SIMULATED con su afinidad y la última simulación") {
        val (repo, edges) = setup()
        val sim = UUID.randomUUID()

        edges.apply(EdgeEvent.Simulated(a.id, b.id, Mode.AMISTAD, sim, 81))

        repo.edge(b.id, a.id, Mode.AMISTAD) shouldBe EdgeSnapshot(EdgeState.SIMULATED, 81, sim, now, null)
    }

    test("decisión unilateral -> PENDING, mutua -> CONNECTED con connected_at; una nueva simulación no la baja") {
        val (repo, edges) = setup()
        val sim1 = UUID.randomUUID()
        edges.apply(EdgeEvent.Simulated(a.id, b.id, Mode.ESTUDIO, sim1, 70))

        edges.apply(EdgeEvent.InterestShown(a.id, b.id, Mode.ESTUDIO, EdgeSource.DECISION))?.state shouldBe EdgeState.PENDING
        edges.apply(EdgeEvent.Connected(b.id, a.id, Mode.ESTUDIO, EdgeSource.DECISION))?.connectedAt shouldBe now

        val sim2 = UUID.randomUUID()
        val after = edges.apply(EdgeEvent.Simulated(a.id, b.id, Mode.ESTUDIO, sim2, 90))
        after shouldBe EdgeSnapshot(EdgeState.CONNECTED, 90, sim2, now, now)
        edges.apply(EdgeEvent.Declined(a.id, b.id, Mode.ESTUDIO, EdgeSource.DECISION))?.state shouldBe EdgeState.CONNECTED
        repo.edge(a.id, b.id, Mode.ESTUDIO)?.state shouldBe EdgeState.CONNECTED
    }

    test("invitación: pendiente sin simulación y rechazada desaparece; aceptada queda CONNECTED") {
        val (repo, edges) = setup()
        edges.apply(EdgeEvent.InterestShown(a.id, b.id, Mode.AMISTAD, EdgeSource.INVITE))?.state shouldBe EdgeState.PENDING
        edges.apply(EdgeEvent.Declined(b.id, a.id, Mode.AMISTAD, EdgeSource.INVITE)).shouldBeNull()
        repo.edge(a.id, b.id, Mode.AMISTAD).shouldBeNull()

        edges.apply(EdgeEvent.Connected(a.id, b.id, Mode.AMISTAD, EdgeSource.INVITE)) shouldBe
            EdgeSnapshot(EdgeState.CONNECTED, null, null, now, now)
    }

    test("presentación rechazada con simulación vuelve a SIMULATED") {
        val (_, edges) = setup()
        val sim = UUID.randomUUID()
        edges.apply(EdgeEvent.Simulated(a.id, b.id, Mode.AMISTAD, sim, 77, EdgeSource.INTRODUCTION))
        edges.apply(EdgeEvent.InterestShown(a.id, b.id, Mode.AMISTAD, EdgeSource.INTRODUCTION))
        edges.apply(EdgeEvent.Declined(b.id, a.id, Mode.AMISTAD, EdgeSource.INTRODUCTION)) shouldBe
            EdgeSnapshot(EdgeState.SIMULATED, 77, sim, now, null)
    }

    test("los modos son aristas independientes") {
        val (repo, edges) = setup()
        edges.apply(EdgeEvent.Connected(a.id, b.id, Mode.PAREJA, EdgeSource.DECISION))
        repo.edge(a.id, b.id, Mode.AMISTAD).shouldBeNull()
        repo.edge(a.id, b.id, Mode.PAREJA)?.state shouldBe EdgeState.CONNECTED
    }

    test("con un bloqueo en cualquier sentido no se crea ni cambia la arista") {
        val (repo, edges) = setup()
        repo.block(b.id, a.id)
        edges.apply(EdgeEvent.Connected(a.id, b.id, Mode.AMISTAD, EdgeSource.INVITE)).shouldBeNull()
        repo.edge(a.id, b.id, Mode.AMISTAD).shouldBeNull()
    }

    test("afinidad fuera de rango o la misma persona en los dos extremos fallan") {
        val (_, edges) = setup()
        shouldThrow<IllegalArgumentException> { edges.apply(EdgeEvent.Simulated(a.id, b.id, Mode.AMISTAD, UUID.randomUUID(), 101)) }
        shouldThrow<IllegalArgumentException> { edges.apply(EdgeEvent.Connected(a.id, a.id, Mode.AMISTAD, EdgeSource.INVITE)) }
    }
})
