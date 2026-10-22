package me.santiagoreina.parcheu.feature.modes

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** B2 y B3 (criterios de UI de E2.5) con [FakeModesRepository]. */
class ModesViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private fun viewModel(repo: FakeModesRepository = FakeModesRepository()) = ModesViewModel(repo) { TODAY }

    @Test
    fun startsWithEveryModeOffAndContinueDisabled() {
        val vm = viewModel()
        val state = vm.state.value
        assertFalse(state.loading)
        assertEquals(Mode.entries.associateWith { false }, state.modes)
        assertFalse(state.canContinue)
    }

    @Test
    fun turningOnParejaOpensB3WithTheVerifiedDateInsteadOfTurningItOn() {
        val vm = viewModel()
        vm.onToggle(Mode.PAREJA, true)
        val dialog = vm.state.value.dialog!!
        assertEquals("14 / 03 / 2005", dialog.birthDate)
        assertEquals(21, dialog.age)
        assertFalse(dialog.canConfirm)
        assertEquals(false, vm.state.value.modes[Mode.PAREJA])
    }

    @Test
    fun cancellingB3LeavesParejaOff() {
        val vm = viewModel()
        vm.onToggle(Mode.PAREJA, true)
        vm.onDialogConfirmedChange(true)
        vm.onDialogCancel()
        assertNull(vm.state.value.dialog)
        assertEquals(false, vm.state.value.modes[Mode.PAREJA])
        assertFalse(vm.state.value.canContinue)
    }

    @Test
    fun confirmingB3TurnsParejaOnAndSendsTheExplicitConfirmation() = runTest {
        val repo = FakeModesRepository()
        val vm = viewModel(repo)
        vm.onToggle(Mode.PAREJA, true)
        vm.onDialogConfirm() // sin la casilla no hace nada
        assertNotNull(vm.state.value.dialog)
        vm.onDialogConfirmedChange(true)
        vm.onDialogConfirm()
        assertNull(vm.state.value.dialog)
        assertEquals(true, vm.state.value.modes[Mode.PAREJA])
        assertTrue(vm.state.value.canContinue)

        vm.effects.test {
            vm.onContinue()
            assertTrue(awaitItem() is ModesEffect.Saved)
        }
        assertEquals(
            listOf(mapOf(Mode.AMISTAD to false, Mode.PAREJA to true, Mode.ESTUDIO to false) to true),
            repo.updates,
        )
    }

    @Test
    fun amistadAndEstudioDoNotSendParejaConfirmation() {
        val repo = FakeModesRepository()
        val vm = viewModel(repo)
        vm.onToggle(Mode.AMISTAD, true)
        vm.onToggle(Mode.ESTUDIO, true)
        vm.onContinue()
        assertEquals(false, repo.updates.single().second)
    }

    @Test
    fun withoutVerifiedAgeB3CannotBeConfirmed() {
        val vm = viewModel(FakeModesRepository(ApiResult.Success(meDto(ageVerified = false))))
        vm.onToggle(Mode.PAREJA, true)
        vm.onDialogConfirmedChange(true)
        vm.onDialogConfirm()
        assertNull(vm.state.value.dialog!!.birthDate)
        assertEquals(false, vm.state.value.modes[Mode.PAREJA])
    }

    @Test
    fun savedModesAreLoadedAndServerErrorsAreShown() {
        val repo = FakeModesRepository(ApiResult.Success(meDto(mapOf(Mode.ESTUDIO to true))))
        repo.updateResult = ApiResult.Failure(AppError.AgeRequired)
        val vm = viewModel(repo)
        assertEquals(true, vm.state.value.modes[Mode.ESTUDIO])
        vm.onContinue()
        assertEquals(AppError.AgeRequired, vm.state.value.saveError)
        assertFalse(vm.state.value.saving)
    }

    @Test
    fun loadFailureCanBeRetried() {
        val repo = FakeModesRepository(ApiResult.Failure(AppError.Network))
        val vm = viewModel(repo)
        assertEquals(AppError.Network, vm.state.value.loadError)
        assertFalse(vm.state.value.canContinue)
        repo.me = ApiResult.Success(meDto())
        vm.load()
        assertNull(vm.state.value.loadError)
    }

    @Test
    fun ageIsComputedWithTheBirthdayOfThisYear() {
        val birth = CivilDate(2008, 10, 23)
        assertEquals(17, birth.ageOn(CivilDate(2026, 10, 22)))
        assertEquals(18, birth.ageOn(CivilDate(2026, 10, 23)))
        assertEquals("23 / 10 / 2008", birth.display())
    }
}
