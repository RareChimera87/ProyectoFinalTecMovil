package me.santiagoreina.parcheu.feature.matchqueue

import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.feature.modes.FakeModesRepository
import me.santiagoreina.parcheu.feature.modes.meDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Conversión de `MeDto.modes` en los modos activos que F1 ofrece (lambda `loadActiveModes` de Koin). */
class MatchQueueModuleTest {
    @Test
    fun onlyTheModesTurnedOnAreActive() = runTest {
        val repo =
            FakeModesRepository(
                ApiResult.Success(meDto(modes = mapOf(Mode.AMISTAD to true, Mode.ESTUDIO to true)))
            )

        assertEquals(listOf(Mode.AMISTAD, Mode.ESTUDIO), repo.activeModes())
    }

    @Test
    fun noModeOnGivesAnEmptyList() = runTest {
        assertEquals(emptyList<Mode>(), FakeModesRepository().activeModes())
    }

    @Test
    fun aFailedReadGivesNull() = runTest {
        val repo = FakeModesRepository(ApiResult.Failure(AppError.Network))

        assertNull(repo.activeModes())
    }
}
