package me.santiagoreina.parcheu.feature.basicprofile

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.contract.IsoDate
import me.santiagoreina.parcheu.contract.OnboardingStep
import me.santiagoreina.parcheu.contract.UpdateMe
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.data.auth.FakeAuthRepository
import me.santiagoreina.parcheu.data.auth.sessionDto
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import me.santiagoreina.parcheu.navigation.Home
import me.santiagoreina.parcheu.navigation.TwinQuestions
import me.santiagoreina.parcheu.navigation.Underage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** B0 'Tu perfil' (E2.6): validación del formulario, orden de llamadas y bloqueo por edad. */
class BasicProfileViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val auth = FakeAuthRepository(signedIn = true).apply {
        nextSessions(ApiResult.Success(sessionDto(OnboardingStep.NEEDS_TWIN)))
    }

    private fun BasicProfileViewModel.form() = state.value as BasicProfileUiState.Form

    private fun BasicProfileViewModel.fill(
        date: String? = "2004-05-17",
        university: Int? = 1,
        interests: List<String> = listOf("musica"),
    ) {
        date?.let { onBirthDateSelected(IsoDate(it)) }
        university?.let { onUniversitySelected(it) }
        interests.forEach { onInterestToggle(it) }
    }

    @Test
    fun continueIsDisabledWithoutBirthDateOrUniversity() = runTest {
        val noDate = BasicProfileViewModel(false, FakeBasicProfileRepository(), auth).apply { fill(date = null) }
        assertFalse(noDate.form().canSubmit)
        val noUniversity = BasicProfileViewModel(false, FakeBasicProfileRepository(), auth).apply {
            fill(university = null)
        }
        assertFalse(noUniversity.form().canSubmit)
        val noInterests = BasicProfileViewModel(false, FakeBasicProfileRepository(), auth).apply {
            fill(interests = emptyList())
        }
        assertFalse(noInterests.form().canSubmit)
        val complete = BasicProfileViewModel(false, FakeBasicProfileRepository(), auth).apply { fill() }
        assertTrue(complete.form().canSubmit)

        // 'Continuar' deshabilitado no hace nada.
        val repository = FakeBasicProfileRepository()
        BasicProfileViewModel(false, repository, auth).apply { fill(date = null) }.onSubmit()
        assertEquals(listOf("catalog", "me"), repository.calls)
    }

    @Test
    fun interestsStopAtFiveAndCareerOverSixtyBlocksContinue() = runTest {
        val viewModel = BasicProfileViewModel(false, FakeBasicProfileRepository(), auth)
        viewModel.fill(interests = listOf("musica", "futbol", "cine", "lectura", "videojuegos", "cafe"))
        assertEquals(listOf("musica", "futbol", "cine", "lectura", "videojuegos"), viewModel.form().form.interests)
        viewModel.onInterestToggle("musica")
        assertEquals(4, viewModel.form().form.interests.size)

        viewModel.onCareerChange("x".repeat(61))
        assertTrue(viewModel.form().form.careerTooLong)
        assertFalse(viewModel.form().canSubmit)
        viewModel.onCareerChange("x".repeat(60))
        assertTrue(viewModel.form().canSubmit)
    }

    @Test
    fun submitVerifiesAgeThenSavesProfileThenFollowsSession() = runTest {
        val repository = FakeBasicProfileRepository()
        val viewModel = BasicProfileViewModel(false, repository, auth)
        viewModel.fill(interests = listOf("musica", "cine"))
        viewModel.onCareerChange("  Ing. Industrial ")
        viewModel.effects.test {
            viewModel.onSubmit()
            assertEquals(BasicProfileEffect.ReplaceAll(TwinQuestions), awaitItem())
        }
        assertEquals(listOf("catalog", "me", "age:2004-05-17", "update"), repository.calls)
        assertEquals(
            UpdateMe(
                displayName = null,
                career = "Ing. Industrial",
                universityId = 1,
                interests = listOf("musica", "cine"),
            ),
            repository.updates.single(),
        )
        assertEquals(listOf("session"), auth.calls)
    }

    @Test
    fun underageGoesToAgeLockAndNeverSavesTheProfile() = runTest {
        val repository = FakeBasicProfileRepository().apply { verifyResult = ApiResult.Failure(AppError.Underage) }
        val viewModel = BasicProfileViewModel(false, repository, auth)
        viewModel.fill(date = "2010-01-01")
        viewModel.effects.test {
            viewModel.onSubmit()
            assertEquals(BasicProfileEffect.ReplaceAll(Underage), awaitItem())
        }
        assertEquals(listOf("catalog", "me", "age:2010-01-01"), repository.calls)
    }

    @Test
    fun underageAccountOpeningB0GoesStraightToAgeLock() = runTest {
        val repository = FakeBasicProfileRepository(meResult = ApiResult.Failure(AppError.Underage))
        val viewModel = BasicProfileViewModel(false, repository, auth)
        viewModel.effects.test { assertEquals(BasicProfileEffect.ReplaceAll(Underage), awaitItem()) }
    }

    @Test
    fun failedPatchKeepsDateLockedSoRetryDoesNotVerifyAgain() = runTest {
        val repository = FakeBasicProfileRepository().apply { updateResult = ApiResult.Failure(AppError.Network) }
        val viewModel = BasicProfileViewModel(false, repository, auth)
        viewModel.fill()
        viewModel.onSubmit()
        assertEquals(AppError.Network, viewModel.form().error)
        assertTrue(viewModel.form().form.birthDateLocked)
        assertFalse(viewModel.form().saving)

        repository.updateResult = ApiResult.Success(BasicProfileSamples.savedUser)
        viewModel.effects.test {
            viewModel.onSubmit()
            assertEquals(BasicProfileEffect.ReplaceAll(TwinQuestions), awaitItem())
        }
        assertEquals(1, repository.calls.count { it.startsWith("age:") })
    }

    @Test
    fun conflictReloadsTheSavedDateAndLocksIt() = runTest {
        val repository = FakeBasicProfileRepository().apply { verifyResult = ApiResult.Failure(AppError.Conflict) }
        val viewModel = BasicProfileViewModel(false, repository, auth)
        viewModel.fill(date = "2001-01-01")
        repository.meResult = ApiResult.Success(BasicProfileSamples.savedUser)
        viewModel.onSubmit()
        val form = viewModel.form()
        assertEquals(AppError.Conflict, form.error)
        assertEquals(IsoDate("2004-05-17"), form.form.birthDate)
        assertTrue(form.form.birthDateLocked)
        // La fecha bloqueada ya no cambia.
        viewModel.onBirthDateSelected(IsoDate("2000-02-02"))
        assertEquals(IsoDate("2004-05-17"), viewModel.form().form.birthDate)
    }

    @Test
    fun editModePrefillsLocksDateAndOnlyPatches() = runTest {
        val repository = FakeBasicProfileRepository(meResult = ApiResult.Success(BasicProfileSamples.savedUser))
        val viewModel = BasicProfileViewModel(true, repository, auth)
        val form = viewModel.form()
        assertTrue(form.edit)
        assertEquals(BasicProfileSamples.complete.copy(birthDateLocked = true, displayName = "Ana"), form.form)
        viewModel.onCareerChange("")
        viewModel.effects.test {
            viewModel.onSubmit()
            assertEquals(BasicProfileEffect.Back, awaitItem())
        }
        assertEquals(listOf("catalog", "me", "update"), repository.calls)
        assertEquals("", repository.updates.single().career)
        assertTrue(auth.calls.isEmpty())
    }

    @Test
    fun missingGoogleNameAsksForIt() = runTest {
        val repository =
            FakeBasicProfileRepository(
                meResult = ApiResult.Success(BasicProfileSamples.newUser.copy(displayName = null)),
            )
        val viewModel = BasicProfileViewModel(false, repository, auth)
        viewModel.fill()
        assertTrue(viewModel.form().form.askName)
        assertFalse(viewModel.form().canSubmit)
        viewModel.onDisplayNameChange(" Ana ")
        viewModel.effects.test {
            viewModel.onSubmit()
            awaitItem()
        }
        assertEquals("Ana", repository.updates.single().displayName)
    }

    @Test
    fun loadErrorOffersRetry() = runTest {
        val repository = FakeBasicProfileRepository(catalogResult = ApiResult.Failure(AppError.Network))
        val viewModel = BasicProfileViewModel(false, repository, auth)
        assertEquals(BasicProfileUiState.LoadError(AppError.Network), viewModel.state.value)
        repository.catalogResult = ApiResult.Success(BasicProfileSamples.catalog)
        viewModel.onRetryLoad()
        assertNull(viewModel.form().error)
    }

    @Test
    fun finishedOnboardingFollowsSessionDestination() = runTest {
        val done = FakeAuthRepository(signedIn = true).apply {
            nextSessions(ApiResult.Success(sessionDto(OnboardingStep.DONE)))
        }
        val viewModel = BasicProfileViewModel(false, FakeBasicProfileRepository(), done)
        viewModel.fill()
        viewModel.effects.test {
            viewModel.onSubmit()
            assertEquals(BasicProfileEffect.ReplaceAll(Home), awaitItem())
        }
    }
}
