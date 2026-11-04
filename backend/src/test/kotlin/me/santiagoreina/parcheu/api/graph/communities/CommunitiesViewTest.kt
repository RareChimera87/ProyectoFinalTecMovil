package me.santiagoreina.parcheu.api.graph.communities

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.ai.testGateway
import me.santiagoreina.parcheu.api.graph.AnonIdSigner
import me.santiagoreina.parcheu.api.graph.GraphVisibilityPolicy
import me.santiagoreina.parcheu.api.graph.InMemoryGraphRepository
import me.santiagoreina.parcheu.api.graph.PersonRow
import me.santiagoreina.parcheu.api.graph.person
import me.santiagoreina.parcheu.api.platform.tasks.FakeTaskQueue
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Visibility

/** `GET /v1/communities` (E6.4): solo mis comunidades, miembros por la policy, gratis solo el conteo. */
class CommunitiesViewTest : FunSpec({
    val signer = AnonIdSigner.forTests()

    class Fixture(val plus: Boolean = true, demos: Boolean = true) {
        val graph = InMemoryGraphRepository()
        val repo = InMemoryCommunityRepository(graph)
        val view = CommunitiesView(repo, graph, GraphVisibilityPolicy(signer), { plus }, demos)
        suspend fun runJob() = CommunityService(repo, FakeTaskQueue(), CommunityNamer(testGateway()), FIXED_CLOCK).runWeekly()
    }

    fun Fixture.team(): List<PersonRow> = graph.group(1, 5).also { graph.clique(it) }

    test("cada quien ve solo las comunidades en las que está") {
        val f = Fixture()
        val mine = f.team()
        val other = f.graph.group(20, 3).also { f.graph.clique(it) }
        f.runJob()

        f.view.communities(mine[0].id).single().size shouldBe 5
        f.view.communities(other[0].id).single().size shouldBe 3
        f.view.communities(java.util.UUID.randomUUID()).shouldBeEmpty()
    }

    test("sin conexión mutua los miembros salen con anonId; con conexión, con su id") {
        val f = Fixture()
        val team = f.team()
        f.runJob()
        val dto = f.view.communitiesDto(team[0].id)
        val members = dto.items!!.single().members
        // En esta comunidad todos están conectados con Ana (clique CONNECTED): ven nombre e id reales.
        members.map { it.id } shouldContainExactlyInAnyOrder team.drop(1).map { it.id.toString() }
        members.all { it.anonId == null } shouldBe true
    }

    test("un miembro con el que no hay arista directa se enmascara con su anonId") {
        val f = Fixture()
        val people = f.graph.group(1, 4)
        // Cadena 0-1-2-3 con afinidad alta y el cierre 0-2, 1-3 para que formen una sola comunidad; 0 y 3 sin arista directa.
        listOf(0 to 1, 1 to 2, 2 to 3, 0 to 2, 1 to 3).forEach { (a, b) ->
            f.graph.putEdge(people[a].id, people[b].id, Mode.AMISTAD, EdgeState.CONNECTED, 90)
        }
        f.runJob()

        val members = f.view.communitiesDto(people[0].id).items!!.single().members
        val far = members.single { it.anonId != null }
        far.id.shouldBeNull()
        far.anonId shouldBe signer.anonId(people[0].id, people[3].id)
        members.filter { it.anonId == null }.map { it.id } shouldContainExactlyInAnyOrder listOf(people[1].id.toString(), people[2].id.toString())
    }

    test("HIDDEN, bloqueados, borrados y demos apagados no se listan ni cuentan en size") {
        val f = Fixture(demos = false)
        val team = f.graph.group(1, 8).also { f.graph.clique(it) }
        f.runJob()
        // Después de la corrida cambian: uno se oculta, uno se borra, uno se bloquea y uno es demo.
        f.graph.addPerson(team[1].copy(visibility = Visibility.HIDDEN))
        f.graph.addPerson(team[2].copy(deletedAt = java.time.Instant.EPOCH))
        f.graph.block(team[0].id, team[3].id)
        f.graph.addPerson(team[4].copy(isDemo = true))

        val community = f.view.communities(team[0].id).single()
        community.size shouldBe 4 // observador + team[5], team[6] y team[7]
        community.members.size shouldBe 3
        community.members.map { it.id } shouldContainExactlyInAnyOrder team.drop(5).map { it.id.toString() }
    }

    test("una comunidad que queda con menos de 3 visibles no se muestra") {
        val f = Fixture()
        val team = f.graph.group(1, 3).also { f.graph.clique(it) }
        f.runJob()
        f.graph.addPerson(team[1].copy(visibility = Visibility.HIDDEN))
        f.view.communities(team[0].id).shouldBeEmpty()
        f.view.communitiesDto(team[0].id).count shouldBe 0
    }

    test("gratis recibe solo el conteo y ParcheU+ la lista") {
        val free = Fixture(plus = false)
        val team = free.team()
        free.runJob()
        free.view.communitiesDto(team[0].id).let {
            it.count shouldBe 1
            it.items.shouldBeNull()
        }
        val plus = Fixture(plus = true)
        val team2 = plus.team()
        plus.runJob()
        plus.view.communitiesDto(team2[0].id).items!!.single().let {
            it.size shouldBe 5
            it.members.size shouldBe 4
            it.name shouldBe CommunityDetector.PENDING_NAME
        }
    }

    test("las aristas de Pareja no hacen a nadie miembro de una comunidad") {
        val f = Fixture()
        val people = f.graph.group(1, 4)
        f.graph.clique(people, mode = Mode.PAREJA)
        f.runJob()
        f.repo.all().shouldBeEmpty()
        f.view.communities(people[0].id).shouldBeEmpty()
    }
})
