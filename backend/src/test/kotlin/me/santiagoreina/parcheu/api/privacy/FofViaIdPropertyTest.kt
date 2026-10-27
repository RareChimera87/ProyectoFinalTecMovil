package me.santiagoreina.parcheu.api.privacy

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import me.santiagoreina.parcheu.api.graph.AnonIdSigner
import me.santiagoreina.parcheu.api.graph.GraphVisibilityPolicy
import me.santiagoreina.parcheu.api.graph.fof.FofService
import me.santiagoreina.parcheu.api.graph.fof.InMemoryFofRepository
import me.santiagoreina.parcheu.api.social.introductions.IntroductionPeople
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.Mode
import java.util.UUID

/**
 * Propiedad `viaId` de E3.5 (backlog, "Hueco de contrato" de E3.4): el amigo en común que
 * `GET /v1/graph/fof` entrega como `viaId` es siempre una conexión directa del observador (CONNECTED,
 * sin Pareja) y tiene con el candidato una arista CONNECTED que **no** es de Pareja. Si `viaId`
 * saliera de cualquier arista con el candidato, revelaría una Pareja ajena (ADR-0012, regla 1).
 */
class FofViaIdPropertyTest : FunSpec({
    val signer = AnonIdSigner.forTests()

    /** Problemas del `viaId` de cada candidato de [viewer] en [world] (vacío si todo está bien). */
    suspend fun viaProblems(world: PrivacyWorld, viewer: UUID): Pair<Int, List<String>> {
        val graph = world.toGraphRepository()
        val service = FofService(InMemoryFofRepository(graph), graph, GraphVisibilityPolicy(signer), isPlus = { true })
        val items = service.fof(viewer).items.orEmpty()
        val byAnon = world.people.associateBy { signer.anonId(viewer, it.id) }
        fun connectedNonPareja(a: UUID, b: UUID) = world.edges.any {
            it.involves(a) && it.involves(b) && it.state == EdgeState.CONNECTED && it.mode != Mode.PAREJA
        }
        val problems = items.mapNotNull { item ->
            val via = item.viaId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@mapNotNull "candidato ${item.anonId} sin viaId válido (${item.viaId})"
            val candidate = byAnon[item.anonId]?.id
                ?: return@mapNotNull "candidato ${item.anonId} no corresponde a nadie"
            when {
                !connectedNonPareja(viewer, via) -> "viaId $via no es conexión directa sin Pareja del observador"
                !connectedNonPareja(via, candidate) -> "viaId $via no tiene arista CONNECTED sin Pareja con ${item.anonId}"
                else -> null
            }
        }
        return items.size to problems
    }

    test("viaId nunca es alguien cuya arista con el candidato sea de Pareja ni no CONNECTED") {
        var withCandidates = 0
        val cases = PrivacyProperties.check("fof viaId", PrivacyWorld.arb()) { world ->
            world.observers().forEach { viewer ->
                val (count, problems) = viaProblems(world, viewer)
                withCandidates += count
                withClue("observador ${world.people.indexOfFirst { it.id == viewer }} en $world") {
                    problems.shouldBeEmpty()
                }
            }
        }
        cases shouldBeGreaterThanOrEqual PrivacyProperties.ITERATIONS
        withCandidates shouldBeGreaterThan 0
    }

    /**
     * 'Pedir presentación' (E6.2) y D6 (E6.3) comparten la definición de amigos de amigos: `resolveFof`
     * da un resultado solo para los `anonId` que fof lista, y con el mismo `viaId`. Si no, el 202 frente
     * al 404 sería un oráculo sobre las aristas de un usuario HIDDEN (ADR-0012, regla 2).
     */
    test("resolveFof(anonId) es no nulo solo si fof lo lista y con el mismo viaId") {
        var resolved = 0
        PrivacyProperties.check("resolveFof coherente con fof", PrivacyWorld.arb()) { world ->
            val graph = world.toGraphRepository()
            val policy = GraphVisibilityPolicy(signer)
            val fof = FofService(InMemoryFofRepository(graph), graph, policy, isPlus = { true })
            val people = IntroductionPeople(graph, policy, fof)
            world.observers().forEach { viewer ->
                val listed = fof.fof(viewer).items.orEmpty().associateBy { it.anonId }
                world.people.forEach { person ->
                    val anon = signer.anonId(viewer, person.id)
                    val result = people.resolveFof(viewer, anon)
                    withClue("observador ${world.people.indexOfFirst { it.id == viewer }}, candidato $anon en $world") {
                        if (anon in listed) {
                            result?.second?.toString() shouldBe listed.getValue(anon).viaId
                            result?.first shouldBe person.id
                            resolved++
                        } else {
                            result shouldBe null
                        }
                    }
                }
            }
        }
        resolved shouldBeGreaterThan 0
    }
})
