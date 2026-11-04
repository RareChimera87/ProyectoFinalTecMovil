package me.santiagoreina.parcheu.feature.parche.fof

import java.io.IOException
import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.contract.FofDto
import me.santiagoreina.parcheu.contract.IntroRequest
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.feature.parche.FakeGraphRepository
import me.santiagoreina.parcheu.feature.parche.ParcheSamples
import me.santiagoreina.parcheu.feature.parche.failure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [FofRepositoryImpl]: el plan sale de la respuesta y el grafo de fondo tolera fallos. */
class FofRepositoryImplTest {
    private class FakeApi(var dto: FofDto) : FofApi {
        val requests = mutableListOf<IntroRequest>()
        var fail = false

        override suspend fun fof(): FofDto = if (fail) throw IOException("sin red") else dto

        override suspend fun requestIntroduction(body: IntroRequest) {
            requests += body
        }
    }

    private val graph = FakeGraphRepository().apply {
        graphReturns(ApiResult.Success(ParcheSamples.content))
    }

    @Test
    fun withItemsIsPlusAndCarriesTheGraph() = runTest {
        val repo =
            FofRepositoryImpl(FakeApi(FofDto(3, FofSamples.candidates)), graph, ApiErrorMapper())
        val content = (repo.load() as ApiResult.Success).value
        assertTrue(content.plus)
        assertEquals(FofSamples.candidates, content.candidates)
        assertEquals(ParcheSamples.nodes, content.nodes)
    }

    @Test
    fun withoutItemsIsFreeWithOnlyTheCount() = runTest {
        val repo = FofRepositoryImpl(FakeApi(FofDto(5)), graph, ApiErrorMapper())
        val content = (repo.load() as ApiResult.Success).value
        assertEquals(false, content.plus)
        assertEquals(5, content.count)
        assertEquals(emptyList<Any>(), content.candidates)
    }

    @Test
    fun plusWithNoCandidatesIsStillPlus() = runTest {
        val repo = FofRepositoryImpl(FakeApi(FofDto(0, emptyList())), graph, ApiErrorMapper())
        assertTrue((repo.load() as ApiResult.Success).value.plus)
    }

    @Test
    fun graphFailureFallsBackToTheCachedGraph() = runTest {
        val cachedGraph = FakeGraphRepository(ParcheSamples.content).apply {
            graphReturns(failure())
        }
        val repo =
            FofRepositoryImpl(
                FakeApi(FofDto(1, FofSamples.candidates.take(1))),
                cachedGraph,
                ApiErrorMapper()
            )
        assertEquals(ParcheSamples.nodes, (repo.load() as ApiResult.Success).value.nodes)
    }

    @Test
    fun fofFailureIsAnError() = runTest {
        val api = FakeApi(FofDto(1)).apply { fail = true }
        val repo = FofRepositoryImpl(api, graph, ApiErrorMapper())
        assertEquals(ApiResult.Failure(AppError.Network), repo.load())
    }

    @Test
    fun requestSendsTheAnonId() = runTest {
        val api = FakeApi(FofDto(1))
        val repo = FofRepositoryImpl(api, graph, ApiErrorMapper())
        assertEquals(ApiResult.Success(Unit), repo.requestIntroduction("a_x"))
        assertEquals(listOf(IntroRequest("a_x")), api.requests)
    }
}
