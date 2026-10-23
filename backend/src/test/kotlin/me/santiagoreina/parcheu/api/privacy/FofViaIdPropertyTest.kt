package me.santiagoreina.parcheu.api.privacy

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import me.santiagoreina.parcheu.api.graph.AnonIdSigner
import me.santiagoreina.parcheu.api.graph.GraphVisibilityPolicy
import me.santiagoreina.parcheu.api.graph.fof.FofService
import me.santiagoreina.parcheu.api.graph.fof.InMemoryFofRepository
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
})
