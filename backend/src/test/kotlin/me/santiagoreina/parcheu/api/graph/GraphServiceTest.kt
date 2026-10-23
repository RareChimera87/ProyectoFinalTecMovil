package me.santiagoreina.parcheu.api.graph

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import me.santiagoreina.parcheu.api.platform.errors.NotFound
import me.santiagoreina.parcheu.api.platform.errors.ValidationFailed
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.Mode
import java.time.Instant
import java.util.UUID

/**
 * Criterios de E3.1 sobre [GraphService] con el repositorio en memoria (misma semántica que el SQL)
 * y con un repositorio "con fugas" que no filtra nada, para probar que la policy es una segunda barrera.
 */
class GraphServiceTest : FunSpec({
    val me = person(0, name = "Santiago Reina")

    test("el dueño ve sus aristas de Pareja junto con las de otros modos") {
        val pareja = person(1)
        val amigo = person(2)
        val repo = memoryRepo(me, pareja, amigo)
        repo.putEdge(me.id, pareja.id, Mode.PAREJA, EdgeState.CONNECTED, 91)
        repo.putEdge(me.id, amigo.id, Mode.AMISTAD, EdgeState.SIMULATED, 70)

        val graph = graphService(repo).graph(me.id, GraphFilter())

        graph.edges.map { it.mode } shouldContainExactlyInAnyOrder listOf(Mode.PAREJA, Mode.AMISTAD)
        graph.nodes.single { it.mode == Mode.PAREJA }.label shouldBe "Persona 1"
    }

    test("un bloqueo en cualquier sentido oculta el nodo") {
        val bloqueado = person(1)
        val bloqueador = person(2)
        val visible = person(3)
        val repo = memoryRepo(me, bloqueado, bloqueador, visible)
        listOf(bloqueado, bloqueador, visible).forEach { repo.putEdge(me.id, it.id, Mode.AMISTAD, EdgeState.CONNECTED, 80) }
        repo.block(me.id, bloqueado.id)
        repo.block(bloqueador.id, me.id)

        val graph = graphService(repo).graph(me.id, GraphFilter())

        graph.nodes.map { it.id } shouldContainExactlyInAnyOrder listOf(visible.id.toString())
        graph.edges.map { it.otherId } shouldContainExactlyInAnyOrder listOf(visible.id.toString())
    }

    test("un usuario borrado no aparece") {
        val borrado = person(1, deletedAt = Instant.parse("2026-10-20T00:00:00Z"))
        val repo = memoryRepo(me, borrado)
        repo.putEdge(me.id, borrado.id, Mode.AMISTAD, EdgeState.CONNECTED, 88)

        val graph = graphService(repo).graph(me.id, GraphFilter())

        graph.nodes.shouldBeEmpty()
        graph.stats.connections shouldBe 0
    }

    test("la policy filtra bloqueados y borrados aunque el repositorio no lo haga") {
        val bloqueado = person(1)
        val borrado = person(2, deletedAt = Instant.parse("2026-10-20T00:00:00Z"))
        val visible = person(3)
        val repo = LeakyRepository(
            me,
            rows = listOf(bloqueado, borrado, visible).map { EgoEdgeRow(it, Mode.AMISTAD, EdgeState.CONNECTED, 80, null, null) },
            blocked = setOf(bloqueado.id),
        )

        val graph = graphService(repo).graph(me.id, GraphFilter())

        graph.nodes.map { it.id } shouldContainExactlyInAnyOrder listOf(visible.id.toString())
    }

    test("stats.connections cuenta solo CONNECTED: 5 CONNECTED, 3 SIMULATED y 1 PENDING -> 5") {
        val others = (1..9).map { person(it) }
        val repo = memoryRepo(me, *others.toTypedArray())
        others.take(5).forEach { repo.putEdge(me.id, it.id, Mode.AMISTAD, EdgeState.CONNECTED, 80) }
        others.drop(5).take(3).forEach { repo.putEdge(me.id, it.id, Mode.ESTUDIO, EdgeState.SIMULATED, 60) }
        repo.putEdge(me.id, others[8].id, Mode.AMISTAD, EdgeState.PENDING, 75)

        val stats = graphService(repo).graph(me.id, GraphFilter()).stats

        stats.connections shouldBe 5
        stats.simulated shouldBe 3
        stats.pending shouldBe 1
        stats.avgAffinity shouldBe 73 // (5*80 + 3*60 + 75) / 9 = 72.8
    }

    test("antes de la conexión mutua el nodo SIMULATED de un match al azar se muestra como 'Alguien'") {
        val match = person(1, name = "Laura Pérez", university = "UIS")
        val sinUniversidad = person(2, name = "Mario Díaz", university = null)
        val repo = memoryRepo(me, match, sinUniversidad)
        repo.putEdge(me.id, match.id, Mode.AMISTAD, EdgeState.SIMULATED, 87)
        repo.putEdge(me.id, sinUniversidad.id, Mode.ESTUDIO, EdgeState.PENDING, 64)

        val graph = graphService(repo).graph(me.id, GraphFilter())

        val node = graph.nodes.single { it.state == EdgeState.SIMULATED }
        node.label shouldBe "Alguien de la UIS"
        node.anonymous shouldBe true
        node.photoUrl shouldBe null
        node.initials shouldBe "A"
        node.id shouldStartWith AnonIdSigner.PREFIX
        (node.id == match.id.toString()) shouldBe false
        graph.edges.single { it.mode == Mode.AMISTAD }.otherId shouldBe node.id
        graph.nodes.single { it.state == EdgeState.PENDING }.label shouldBe "Alguien"
    }

    test("después de conectar se ve el nombre, incluso en las aristas de otros modos") {
        val laura = person(1, name = "Laura Pérez")
        val repo = memoryRepo(me, laura)
        repo.putEdge(me.id, laura.id, Mode.AMISTAD, EdgeState.CONNECTED, 87)
        repo.putEdge(me.id, laura.id, Mode.ESTUDIO, EdgeState.SIMULATED, 72)

        val node = graphService(repo).graph(me.id, GraphFilter(mode = Mode.ESTUDIO)).nodes.single()

        node.label shouldBe "Laura Pérez"
        node.initials shouldBe "LP"
        node.id shouldBe laura.id.toString()
        node.mode shouldBe Mode.ESTUDIO
        node.state shouldBe EdgeState.SIMULATED
    }

    test("los filtros mode y minAffinity devuelven el subconjunto esperado") {
        val a = person(1)
        val b = person(2)
        val c = person(3)
        val d = person(4)
        val repo = memoryRepo(me, a, b, c, d)
        repo.putEdge(me.id, a.id, Mode.AMISTAD, EdgeState.CONNECTED, 90)
        repo.putEdge(me.id, b.id, Mode.AMISTAD, EdgeState.SIMULATED, 65)
        repo.putEdge(me.id, c.id, Mode.ESTUDIO, EdgeState.CONNECTED, 85)
        repo.putEdge(me.id, d.id, Mode.PAREJA, EdgeState.PENDING, null)
        val service = graphService(repo)

        val amistad = service.graph(me.id, GraphFilter(mode = Mode.AMISTAD))
        amistad.edges.map { it.affinity } shouldContainExactlyInAnyOrder listOf(90, 65)

        val altas = service.graph(me.id, GraphFilter(minAffinity = 80))
        altas.edges.map { it.affinity } shouldContainExactlyInAnyOrder listOf(90, 85)
        altas.stats.connections shouldBe 2

        val ambos = service.graph(me.id, GraphFilter(mode = Mode.AMISTAD, minAffinity = 70))
        ambos.nodes.map { it.id } shouldContainExactlyInAnyOrder listOf(a.id.toString())
    }

    test("el filtro community solo deja miembros y solo si el observador es miembro") {
        val a = person(1)
        val b = person(2)
        val repo = memoryRepo(me, a, b)
        repo.putEdge(me.id, a.id, Mode.AMISTAD, EdgeState.CONNECTED, 90)
        repo.putEdge(me.id, b.id, Mode.AMISTAD, EdgeState.CONNECTED, 80)
        val mine = UUID.randomUUID()
        val ajena = UUID.randomUUID()
        repo.communities[mine] = mutableSetOf(me.id, a.id)
        repo.communities[ajena] = mutableSetOf(a.id, b.id)
        val service = graphService(repo)

        service.graph(me.id, GraphFilter(community = mine)).nodes.map { it.id } shouldContainExactlyInAnyOrder listOf(a.id.toString())
        service.graph(me.id, GraphFilter(community = ajena)).nodes.shouldBeEmpty()
    }

    test("el detalle de nodo se pide con el id que dio el grafo y nunca con el uuid de un anónimo") {
        val match = person(1)
        val repo = memoryRepo(me, match)
        repo.putEdge(me.id, match.id, Mode.AMISTAD, EdgeState.SIMULATED, 87)
        val service = graphService(repo)
        val anonId = service.graph(me.id, GraphFilter()).nodes.single().id

        val detail = service.node(me.id, anonId)
        detail.node.label shouldBe "Alguien de la UIS"
        detail.sharedModes shouldBe listOf(Mode.AMISTAD)

        shouldThrow<NotFound> { service.node(me.id, match.id.toString()) }
        shouldThrow<NotFound> { service.node(me.id, "a_inexistente") }
    }

    test("parseFilter valida los parámetros") {
        GraphService.parseFilter("estudio", "70", null) shouldBe GraphFilter(Mode.ESTUDIO, 70, null)
        GraphService.parseFilter(null, "", " ") shouldBe GraphFilter()
        shouldThrow<ValidationFailed> { GraphService.parseFilter("CITA", null, null) }
        shouldThrow<ValidationFailed> { GraphService.parseFilter(null, "101", null) }
        shouldThrow<ValidationFailed> { GraphService.parseFilter(null, "-1", null) }
        shouldThrow<ValidationFailed> { GraphService.parseFilter(null, "alta", null) }
        shouldThrow<ValidationFailed> { GraphService.parseFilter(null, null, "no-es-uuid") }
    }
})

/** Repositorio que devuelve filas sin filtrar (simula un SQL con un filtro roto). */
private class LeakyRepository(
    private val me: PersonRow,
    private val rows: List<EgoEdgeRow>,
    private val blocked: Set<UUID>,
) : GraphRepository {
    override suspend fun egoEdges(viewer: UUID, filter: GraphFilter) = rows
    override suspend fun connectedWith(viewer: UUID) = rows.filter { it.state == EdgeState.CONNECTED }.map { it.other.id }.toSet()
    override suspend fun blockedEitherWay(viewer: UUID) = blocked
    override suspend fun person(userId: UUID) = me.takeIf { it.id == userId }
    override suspend fun lastSimulation(a: UUID, b: UUID): SimulationRow? = null
    override suspend fun updateEdge(a: UUID, b: UUID, mode: Mode, now: Instant, change: (EdgeSnapshot?) -> EdgeChange): EdgeSnapshot? =
        error("solo lectura")
}
