package me.santiagoreina.parcheu.feature.basicprofile

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.underage.UnderageScreen
import me.santiagoreina.parcheu.feature.underage.UnderageTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Criterios de UI de E2.6: 'Continuar' deshabilitado sin fecha o universidad y bloqueo con 'Borrar mi cuenta'. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class BasicProfileScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(form: BasicProfileForm, onSubmit: () -> Unit = {}) {
        composeRule.setContent {
            ParcheUTheme {
                BasicProfileScreen(
                    state = BasicProfileUiState.Form(
                        BasicProfileSamples.catalog,
                        form,
                        edit = false
                    ),
                    onPickBirthDate = {},
                    onPickUniversity = {},
                    onCareerChange = {},
                    onDisplayNameChange = {},
                    onInterestToggle = {},
                    onSubmit = onSubmit,
                    onRetryLoad = {},
                    onBack = {},
                    animateLoading = false
                )
            }
        }
    }

    @Test
    fun continueDisabledWithoutBirthDate() {
        show(BasicProfileSamples.complete.copy(birthDate = null))
        composeRule.onNodeWithTag(BasicProfileTags.SUBMIT).assertIsNotEnabled()
    }

    @Test
    fun continueDisabledWithoutUniversity() {
        show(BasicProfileSamples.complete.copy(universityId = null))
        composeRule.onNodeWithTag(BasicProfileTags.SUBMIT).assertIsNotEnabled()
    }

    @Test
    fun continueEnabledWhenComplete() {
        var submitted = 0
        show(BasicProfileSamples.complete) { submitted++ }
        composeRule.onNodeWithTag(BasicProfileTags.SUBMIT).assertIsEnabled().performClick()
        assertEquals(1, submitted)
        composeRule.onNodeWithText("Universidad Industrial de Santander").assertExists()
        composeRule.onNodeWithText("17 de mayo de 2004").assertExists()
    }

    @Test
    fun underageLockOffersDeleteAccount() {
        var deleted = 0
        composeRule.setContent {
            ParcheUTheme { UnderageScreen(onDeleteAccount = { deleted++ }, onSignOut = {}) }
        }
        composeRule.onNodeWithText("ParcheU es solo para mayores de 18").assertExists()
        composeRule.onNodeWithTag(UnderageTags.DELETE).assertIsEnabled().performClick()
        composeRule.onNodeWithText("Borrar mi cuenta").assertExists()
        assertEquals(1, deleted)
    }
}
