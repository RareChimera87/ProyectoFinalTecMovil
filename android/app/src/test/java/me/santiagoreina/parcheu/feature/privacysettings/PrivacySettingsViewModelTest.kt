package me.santiagoreina.parcheu.feature.privacysettings

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Visibility
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.data.auth.FakeAuthRepository
import me.santiagoreina.parcheu.data.auth.LOGIN_PAGE
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import me.santiagoreina.parcheu.feature.modes.FakeModesRepository
import me.santiagoreina.parcheu.feature.modes.TODAY
import me.santiagoreina.parcheu.feature.modes.meDto
import me.santiagoreina.parcheu.navigation.Onboarding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** H3 (E8.2) con fakes: modos, Pareja desde B3, visibilidad, aprendizaje y 'Cerrar sesión'. */
class PrivacySettingsViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val activeModes = mapOf(Mode.AMISTAD to true, Mode.ESTUDIO to true)
    private val privacy = FakePrivacySettingsRepository()
    private val auth = FakeAuthRepository(signedIn = true)

    private fun viewModel(modes: FakeModesRepository = FakeModesRepository(meResult(true))) =
        PrivacySettingsViewModel(privacy, modes, auth) { TODAY }

    private fun meResult(ageVerified: Boolean) =
        ApiResult.Success(meDto(modes = activeModes, ageVerified = ageVerified))

    @Test
    fun loadsModesVisibilityAndLearningWithParejaOffByDefault() {
        val state = viewModel().state.value
        assertFalse(state.loading)
        assertEquals(Visibility.ANONYMOUS, state.visibility)
        assertEquals(
            mapOf(Mode.AMISTAD to true, Mode.PAREJA to false, Mode.ESTUDIO to true),
            state.modes
        )
        assertEquals(
            mapOf(Mode.AMISTAD to true, Mode.PAREJA to false, Mode.ESTUDIO to true),
            state.learning
        )
    }

    @Test
    fun aLoadFailureShowsTheErrorAndRetryRecovers() {
        privacy.loadResult = ApiResult.Failure(AppError.Network)
        val vm = viewModel()
        assertEquals(AppError.Network, vm.state.value.loadError)
        privacy.loadResult = null
        vm.load()
        assertNull(vm.state.value.loadError)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun turningOnParejaWithoutVerifiedAgeOpensB3AndCannotBeConfirmed() {
        val modes = FakeModesRepository(meResult(ageVerified = false))
        val vm = viewModel(modes)
        vm.onModeToggle(Mode.PAREJA, true)
        val dialog = vm.state.value.dialog!!
        assertNull(dialog.birthDate)
        assertFalse(dialog.canConfirm)
        vm.onDialogConfirmedChange(true)
        vm.onDialogConfirm()
        // Sin fecha verificada no se escribe nada: Pareja sigue apagado.
        assertTrue(modes.updates.isEmpty())
        assertEquals(false, vm.state.value.modes[Mode.PAREJA])
    }

    @Test
    fun confirmingB3SavesParejaWithTheExplicitConfirmation() {
        val modes = FakeModesRepository(meResult(ageVerified = true))
        val vm = viewModel(modes)
        vm.onModeToggle(Mode.PAREJA, true)
        val dialog = vm.state.value.dialog!!
        assertEquals("14 / 03 / 2005", dialog.birthDate)
        assertEquals(21, dialog.age)
        assertTrue(modes.updates.isEmpty())

        vm.onDialogConfirmedChange(true)
        // El servidor refleja el cambio de modos en GET /v1/me/privacy.
        privacy.current = privacyDto(
            modes = activeModes + (Mode.PAREJA to true),
            learning = activeModes
        )
        vm.onDialogConfirm()

        assertEquals(
            listOf(
                mapOf(Mode.AMISTAD to true, Mode.PAREJA to true, Mode.ESTUDIO to true) to true
            ),
            modes.updates
        )
        assertNull(vm.state.value.dialog)
        assertEquals(true, vm.state.value.modes[Mode.PAREJA])
    }

    @Test
    fun cancellingB3LeavesParejaOffAndWritesNothing() {
        val modes = FakeModesRepository(meResult(true))
        val vm = viewModel(modes)
        vm.onModeToggle(Mode.PAREJA, true)
        vm.onDialogCancel()
        assertNull(vm.state.value.dialog)
        assertTrue(modes.updates.isEmpty())
        assertEquals(false, vm.state.value.modes[Mode.PAREJA])
    }

    @Test
    fun turningOffAModeSavesItWithoutConfirmation() {
        val modes = FakeModesRepository(meResult(true))
        val vm = viewModel(modes)
        vm.onModeToggle(Mode.ESTUDIO, false)
        assertEquals(
            listOf(
                mapOf(Mode.AMISTAD to true, Mode.PAREJA to false, Mode.ESTUDIO to false) to false
            ),
            modes.updates
        )
    }

    @Test
    fun theLastActiveModeCannotBeTurnedOff() {
        val modes = FakeModesRepository(
            ApiResult.Success(meDto(modes = mapOf(Mode.AMISTAD to true)))
        )
        privacy.current = privacyDto(modes = mapOf(Mode.AMISTAD to true))
        val vm = viewModel(modes)
        vm.onModeToggle(Mode.AMISTAD, false)
        assertTrue(modes.updates.isEmpty())
        assertEquals(SettingsError.LastMode, vm.state.value.error)
        assertEquals(true, vm.state.value.modes[Mode.AMISTAD])
    }

    @Test
    fun aFailedModeSaveKeepsTheStoredValueAndShowsTheError() {
        val modes = FakeModesRepository(meResult(true))
        modes.updateResult = ApiResult.Failure(AppError.Network)
        val vm = viewModel(modes)
        vm.onModeToggle(Mode.ESTUDIO, false)
        assertEquals(true, vm.state.value.modes[Mode.ESTUDIO])
        assertEquals(SettingsError.Api(AppError.Network), vm.state.value.error)
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun choosingAVisibilitySendsThePatch() {
        val vm = viewModel()
        vm.onVisibility(Visibility.HIDDEN)
        assertEquals(
            listOf<Pair<Visibility?, Map<Mode, Boolean>?>>(Visibility.HIDDEN to null),
            privacy.updates
        )
        assertEquals(Visibility.HIDDEN, vm.state.value.visibility)
        // Elegir la que ya está no llama al servidor.
        vm.onVisibility(Visibility.HIDDEN)
        assertEquals(1, privacy.updates.size)
    }

    @Test
    fun turningOffLearningSendsTheModeAndKeepsTheOthers() {
        val vm = viewModel()
        vm.onLearningToggle(Mode.AMISTAD, false)
        assertEquals(
            listOf<Pair<Visibility?, Map<Mode, Boolean>?>>(null to mapOf(Mode.AMISTAD to false)),
            privacy.updates
        )
        assertEquals(false, vm.state.value.learning[Mode.AMISTAD])
        assertEquals(true, vm.state.value.learning[Mode.ESTUDIO])
    }

    @Test
    fun learningFromParejaNeedsParejaActive() {
        val vm = viewModel()
        vm.onLearningToggle(Mode.PAREJA, true)
        assertTrue(privacy.updates.isEmpty())
    }

    @Test
    fun signOutDelegatesToAuthAndLeavesA4AsTheOnlyScreen() = runTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onSignOut()
            assertEquals(
                PrivacySettingsEffect.ReplaceAll(Onboarding(page = LOGIN_PAGE)),
                awaitItem()
            )
        }
        assertEquals(listOf("signOut"), auth.calls)
        assertFalse(auth.signedIn)
    }

    @Test
    fun signOutTwiceOnlyRunsOnce() {
        val vm = viewModel()
        vm.onSignOut()
        vm.onSignOut()
        assertEquals(listOf("signOut"), auth.calls)
    }
}
