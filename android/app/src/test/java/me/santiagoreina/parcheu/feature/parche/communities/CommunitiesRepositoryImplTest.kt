package me.santiagoreina.parcheu.feature.parche.communities

import java.io.IOException
import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.contract.CommunitiesDto
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [CommunitiesRepositoryImpl]: el plan sale de la respuesta (solo ParcheU+ recibe `items`). */
class CommunitiesRepositoryImplTest {
    private class FakeApi(var dto: CommunitiesDto, var fail: Boolean = false) : CommunitiesApi {
        override suspend fun communities(): CommunitiesDto =
            if (fail) throw IOException("sin red") else dto
    }

    @Test
    fun withItemsIsPlus() = runTest {
        val repo =
            CommunitiesRepositoryImpl(
                FakeApi(CommunitiesDto(2, CommunitiesSamples.items)),
                ApiErrorMapper()
            )
        val content = (repo.load() as ApiResult.Success).value
        assertTrue(content.plus)
        assertEquals(CommunitiesSamples.items, content.items)
    }

    @Test
    fun withoutItemsIsFreeWithOnlyTheCount() = runTest {
        val content = (
            CommunitiesRepositoryImpl(
                FakeApi(CommunitiesDto(4)),
                ApiErrorMapper()
            ).load() as ApiResult.Success
            ).value
        assertFalse(content.plus)
        assertEquals(4, content.count)
        assertEquals(emptyList<Any>(), content.items)
    }

    @Test
    fun plusWithEmptyListIsStillPlus() = runTest {
        val content = (
            CommunitiesRepositoryImpl(
                FakeApi(CommunitiesDto(0, emptyList())),
                ApiErrorMapper()
            ).load() as ApiResult.Success
            ).value
        assertTrue(content.plus)
    }

    @Test
    fun networkFailureIsMapped() = runTest {
        val result = CommunitiesRepositoryImpl(
            FakeApi(CommunitiesDto(0), fail = true),
            ApiErrorMapper()
        ).load()
        assertEquals(ApiResult.Failure(AppError.Network), result)
    }
}
