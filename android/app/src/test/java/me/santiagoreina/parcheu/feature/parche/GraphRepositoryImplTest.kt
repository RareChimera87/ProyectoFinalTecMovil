package me.santiagoreina.parcheu.feature.parche

import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.contract.EntitlementDto
import me.santiagoreina.parcheu.contract.GraphDto
import me.santiagoreina.parcheu.contract.NodeDetail
import me.santiagoreina.parcheu.contract.SimulationSummary
import me.santiagoreina.parcheu.contract.StartSimulation
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.core.network.ApiResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** La caché del grafo pertenece a la sesión vigente (hallazgo de revisión de E3.4). */
class GraphRepositoryImplTest {
    private class MemoryCache : GraphCache {
        var content: ParcheContent? = null
        var owner: String? = null

        override suspend fun load(ownerUid: String) = content.takeIf { owner == ownerUid }

        override suspend fun save(content: ParcheContent, ownerUid: String) {
            this.content = content
            owner = ownerUid
        }

        override suspend fun clear() {
            content = null
            owner = null
        }
    }

    private class FakeApi(val graph: GraphDto) : GraphApi {
        override suspend fun graph(community: String?) = graph

        override suspend fun node(id: String): NodeDetail = error("no se usa")

        override suspend fun entitlement(): EntitlementDto = error("sin plan")

        override suspend fun startSimulation(body: StartSimulation): SimulationSummary =
            error("no se usa")
    }

    private val graph = ParcheSamples.content.graph
    private val cache = MemoryCache()
    private var uid: String? = "a"
    private val repo = GraphRepositoryImpl(FakeApi(graph), cache, ApiErrorMapper()) { uid }

    @Test
    fun savedGraphIsOnlyShownToItsOwner() = runTest {
        repo.graph()
        assertEquals(graph, repo.cached()?.graph)

        uid = "b"
        assertNull(repo.cached())
    }

    @Test
    fun withoutSessionNothingIsCachedOrShown() = runTest {
        uid = null
        val result = repo.graph()
        assertEquals(graph, (result as ApiResult.Success).value.graph)
        assertNull(cache.content)
        assertNull(repo.cached())
    }

    @Test
    fun communityGraphIsNotCached() = runTest {
        repo.graph(community = "c1")
        assertNull(cache.content)
    }
}
