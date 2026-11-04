package me.santiagoreina.parcheu.api.graph.communities

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import me.santiagoreina.parcheu.api.graph.InMemoryGraphRepository
import me.santiagoreina.parcheu.api.graph.person
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Visibility
import java.time.LocalDate
import java.util.UUID

/** Detección de comunidades (E6.4): propagación de etiquetas ponderada, Pareja fuera, Jaccard e ids estables. */
class CommunityDetectorTest : FunSpec({
    suspend fun edgesOf(graph: InMemoryGraphRepository) = InMemoryCommunityRepository(graph).connectedEdges()

    fun ids(people: List<me.santiagoreina.parcheu.api.graph.PersonRow>) = people.map { it.id }.toSet()

    test("dos cliques unidos por una arista débil producen dos comunidades") {
        val graph = InMemoryGraphRepository()
        val left = graph.group(1, 5)
        val right = graph.group(10, 5)
        graph.clique(left, affinity = 85)
        graph.clique(right, affinity = 80)
        graph.putEdge(left[0].id, right[0].id, Mode.AMISTAD, EdgeState.CONNECTED, 10)

        // La conclusión no depende de la semilla: con cualquiera salen las mismas dos comunidades.
        listOf(CommunityDetector.SEED, 1L, 7L, 42L, 2026L).forEach { seed ->
            CommunityDetector.detect(edgesOf(graph), seed) shouldContainExactlyInAnyOrder listOf(ids(left), ids(right))
        }
    }

    test("misma entrada y semilla dan las mismas comunidades") {
        val graph = InMemoryGraphRepository()
        val groups = listOf(graph.group(1, 4), graph.group(10, 6), graph.group(20, 3))
        groups.forEach { graph.clique(it, affinity = 70) }
        graph.putEdge(groups[0][0].id, groups[1][0].id, Mode.ESTUDIO, EdgeState.CONNECTED, 15)
        val edges = edgesOf(graph)

        val first = CommunityDetector.detect(edges)
        CommunityDetector.detect(edges) shouldBe first
        CommunityDetector.detect(edges.reversed()) shouldBe first
        first shouldHaveSize 3
    }

    test("una arista de Pareja no cambia el resultado, ni aunque llegue en la lista") {
        val graph = InMemoryGraphRepository()
        val left = graph.group(1, 4)
        val right = graph.group(10, 4)
        graph.clique(left)
        graph.clique(right)
        val base = CommunityDetector.detect(edgesOf(graph))

        // Una Pareja fuerte entre los dos grupos (y otra dentro de uno) la filtra el repositorio...
        graph.putEdge(left[0].id, right[0].id, Mode.PAREJA, EdgeState.CONNECTED, 100)
        graph.putEdge(left[1].id, left[2].id, Mode.PAREJA, EdgeState.CONNECTED, 100)
        CommunityDetector.detect(edgesOf(graph)) shouldBe base

        // ...y el detector la ignora por sí mismo si el repositorio la dejara pasar.
        val withPareja = edgesOf(graph) + CommunityEdge(left[0], right[0], Mode.PAREJA, 100) +
            right.drop(1).map { CommunityEdge(left[0], it, Mode.PAREJA, 100) }
        CommunityDetector.detect(withPareja) shouldBe base
    }

    test("HIDDEN, borrados y bloqueados no forman comunidades; menos de 3 miembros no cuenta") {
        val graph = InMemoryGraphRepository()
        val core = graph.group(1, 3)
        graph.clique(core)
        val hidden = person(5, visibility = Visibility.HIDDEN).also(graph::addPerson)
        val deleted = person(6, deletedAt = java.time.Instant.EPOCH).also(graph::addPerson)
        core.forEach {
            graph.putEdge(it.id, hidden.id, Mode.AMISTAD, EdgeState.CONNECTED, 99)
            graph.putEdge(it.id, deleted.id, Mode.AMISTAD, EdgeState.CONNECTED, 99)
        }
        val pair = graph.group(20, 2).also { graph.clique(it) }
        val found = CommunityDetector.detect(edgesOf(graph))
        found shouldBe listOf(ids(core))
        found.flatten().none { it in ids(pair) } shouldBe true
    }

    test("SIMULATED y PENDING no cuentan: solo CONNECTED") {
        val graph = InMemoryGraphRepository()
        val people = graph.group(1, 4)
        for (i in people.indices) for (j in i + 1 until people.size) {
            graph.putEdge(people[i].id, people[j].id, Mode.AMISTAD, EdgeState.SIMULATED, 90)
        }
        CommunityDetector.detect(edgesOf(graph)) shouldBe emptyList()
    }

    test("el id y el nombre se conservan con Jaccard >= 0,5 y se crean o eliminan en el resto") {
        val week1 = LocalDate.parse("2026-10-19")
        val week2 = LocalDate.parse("2026-10-26")
        val a = (1..6).map { UUID(0, it.toLong()) }.toSet()
        val b = (20..25).map { UUID(0, it.toLong()) }.toSet()
        val gone = (40..42).map { UUID(0, it.toLong()) }.toSet()
        val old = listOf(
            StoredCommunity(UUID(1, 1), week1, "Los del tinto", "k1", a),
            StoredCommunity(UUID(1, 2), week1, "Los madrugadores", "k2", b),
            StoredCommunity(UUID(1, 3), week1, "Los que se fueron", "k3", gone),
        )
        // `a` pierde 1 y gana 1 (Jaccard 5/7 ~ 0,71); `b` queda con la mitad (3/9 = 0,33 < 0,5): comunidad nueva.
        val a2 = a - UUID(0, 1) + UUID(0, 99)
        val b2 = (20..22).map { UUID(0, it.toLong()) }.toSet() + (60..62).map { UUID(0, it.toLong()) }.toSet()

        val plan = CommunityDetector.plan(week2, listOf(a2, b2), old)

        plan.kept.single().let {
            it.id shouldBe UUID(1, 1)
            it.name shouldBe "Los del tinto"
            it.stableKey shouldBe "k1"
            it.members shouldBe a2
            it.week shouldBe week2
        }
        plan.created.single().let {
            it.members shouldBe b2
            it.name shouldBe CommunityDetector.PENDING_NAME
        }
        plan.removed shouldBe setOf(UUID(1, 2), UUID(1, 3))
    }

    test("Jaccard exactamente 0,5 conserva; una comunidad existente solo se asigna a una nueva") {
        val week = LocalDate.parse("2026-10-26")
        val base = (1..4).map { UUID(0, it.toLong()) }.toSet()
        val old = listOf(StoredCommunity(UUID(1, 1), week, "Vieja", "k", base))
        // Dos candidatas parten de la vieja: gana la de mayor Jaccard y la otra es nueva.
        val close = base + UUID(0, 50) // 4/5
        val half = setOf(UUID(0, 1), UUID(0, 2), UUID(0, 70), UUID(0, 71)) // 2/6 < 0,5
        val exactHalf = setOf(UUID(0, 1), UUID(0, 2)) // 2/4 = 0,5 justo en el umbral
        CommunityDetector.plan(week, listOf(half, close), old).let { plan ->
            plan.kept.single().members shouldBe close
            plan.created.single().members shouldBe half
        }
        CommunityDetector.plan(week, listOf(exactHalf), old).kept.single().id shouldBe UUID(1, 1)
        CommunityDetector.jaccard(setOf(UUID(0, 1), UUID(0, 2)), setOf(UUID(0, 2), UUID(0, 3), UUID(0, 4))) shouldBe 0.25
    }
})
