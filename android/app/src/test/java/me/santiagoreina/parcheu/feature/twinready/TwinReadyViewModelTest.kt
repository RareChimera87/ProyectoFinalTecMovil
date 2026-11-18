package me.santiagoreina.parcheu.feature.twinready

import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import me.santiagoreina.parcheu.feature.modes.FakeModesRepository
import me.santiagoreina.parcheu.feature.modes.meDto
import me.santiagoreina.parcheu.feature.profile.FakeProfileRepository
import me.santiagoreina.parcheu.feature.profile.ProfileSamples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals(
            listOf(Mode.AMISTAD, Mode.ESTUDIO),
            TwinReadyViewModel(repo, FakeProfileRepository()).state.value.modes
        )
    }

    @Test
    fun withoutConnectionCelebratesWithoutChips() {
        val repo = FakeModesRepository(ApiResult.Failure(AppError.Network))
        assertEquals(
            emptyList<Mode>(),
            TwinReadyViewModel(repo, FakeProfileRepository()).state.value.modes
        )
    }

    @Test
    fun showsTheRealTwinLevel() {
        val repo = FakeModesRepository(ApiResult.Failure(AppError.Network))
        val profile = FakeProfileRepository(twinResult = ApiResult.Success(ProfileSamples.twin))
        assertEquals(
            ProfileSamples.twin.level,
            TwinReadyViewModel(repo, profile).state.value.twinLevel
        )
    }

    @Test
    fun hidesTheLevelWhenTheTwinFails() {
        val repo = FakeModesRepository(ApiResult.Failure(AppError.Network))
        val profile = FakeProfileRepository(twinResult = ApiResult.Failure(AppError.Network))
        assertNull(TwinReadyViewModel(repo, profile).state.value.twinLevel)
    }
}
