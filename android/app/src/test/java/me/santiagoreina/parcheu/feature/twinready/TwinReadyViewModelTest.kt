package me.santiagoreina.parcheu.feature.twinready

import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import me.santiagoreina.parcheu.feature.modes.FakeModesRepository
import me.santiagoreina.parcheu.feature.modes.meDto
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TwinReadyViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    @Test
    fun showsOnlyTheActiveModesInOrder() {
        val repo = FakeModesRepository(
            ApiResult.Success(
                meDto(
                    mapOf(
                        Mode.ESTUDIO to true,
                        Mode.AMISTAD to true
                    )
                )
            )
        )
        assertEquals(listOf(Mode.AMISTAD, Mode.ESTUDIO), TwinReadyViewModel(repo).state.value.modes)
    }

    @Test
    fun withoutConnectionCelebratesWithoutChips() {
        val repo = FakeModesRepository(ApiResult.Failure(AppError.Network))
        assertEquals(emptyList<Mode>(), TwinReadyViewModel(repo).state.value.modes)
    }
}
