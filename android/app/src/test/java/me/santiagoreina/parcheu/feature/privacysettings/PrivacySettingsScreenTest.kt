package me.santiagoreina.parcheu.feature.privacysettings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Visibility
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.modes.ModesTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrivacySettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val loaded = PrivacySettingsUiState(
        loading = false,
        modes = mapOf(Mode.AMISTAD to true, Mode.PAREJA to false, Mode.ESTUDIO to true),
        learning = mapOf(Mode.AMISTAD to true, Mode.PAREJA to false, Mode.ESTUDIO to true)
    )

    private fun show(
        state: PrivacySettingsUiState = loaded,
        actions: PrivacySettingsActions = PrivacySettingsActions()
    ) {
        composeRule.setContent {
            ParcheUTheme(darkTheme = true) {
                PrivacySettingsScreen(state, actions, animateLoading = false)
            }
        }
    }

    @Test
    fun showsModesVisibilityLearningAndEveryLink() {
        show()
        composeRule.onNodeWithText("Privacidad").assertIsDisplayed()
        composeRule.onNodeWithTag(PrivacySettingsTags.mode(Mode.AMISTAD)).assertIsOn()
        composeRule.onNodeWithTag(PrivacySettingsTags.mode(Mode.PAREJA)).assertIsOff()
        composeRule.onNodeWithTag(
            PrivacySettingsTags.visibility(Visibility.ANONYMOUS)
        ).assertIsSelected()
        composeRule.onNodeWithTag(PrivacySettingsTags.learning(Mode.ESTUDIO)).assertIsOn()
        composeRule.onNodeWithTag(PrivacySettingsTags.learning(Mode.PAREJA)).assertIsOff()
        listOf(
            PrivacySettingsTags.BLOCKED,
            PrivacySettingsTags.EXPORT,
            PrivacySettingsTags.DELETE,
            PrivacySettingsTags.TERMS,
            PrivacySettingsTags.PRIVACY_POLICY,
            PrivacySettingsTags.SIGN_OUT
        ).forEach { composeRule.onNodeWithTag(it).performScrollTo().assertIsDisplayed() }
        composeRule.onNodeWithText("Cerrar sesión").assertExists()
    }

    @Test
    fun learningFromParejaStaysOffUntilParejaIsActive() {
        show()
        composeRule.onNodeWithTag(PrivacySettingsTags.learning(Mode.PAREJA)).assertIsNotEnabled()
    }

    @Test
    fun learningFromParejaIsEnabledOnceParejaIsActive() {
        show(loaded.copy(modes = loaded.modes + (Mode.PAREJA to true)))
        composeRule.onNodeWithTag(PrivacySettingsTags.learning(Mode.PAREJA)).assertIsEnabled()
    }

    @Test
    fun linksAndSignOutInvokeTheirActions() {
        val calls = mutableListOf<String>()
        show(
            actions = PrivacySettingsActions(
                onBlockedUsers = { calls += "blocked" },
                onExport = { calls += "export" },
                onDelete = { calls += "delete" },
                onTerms = { calls += "terms" },
                onPrivacyPolicy = { calls += "privacy" },
                onSignOut = { calls += "signOut" }
            )
        )
        listOf(
            PrivacySettingsTags.BLOCKED,
            PrivacySettingsTags.EXPORT,
            PrivacySettingsTags.DELETE,
            PrivacySettingsTags.TERMS,
            PrivacySettingsTags.PRIVACY_POLICY,
            PrivacySettingsTags.SIGN_OUT
        ).forEach { composeRule.onNodeWithTag(it).performScrollTo().performClick() }
        assertEquals(listOf("blocked", "export", "delete", "terms", "privacy", "signOut"), calls)
    }

    @Test
    fun togglesAndVisibilityReportTheChange() {
        val calls = mutableListOf<String>()
        show(
            actions = PrivacySettingsActions(
                onModeToggle = { m, on -> calls += "mode:$m:$on" },
                onVisibility = { calls += "vis:$it" },
                onLearningToggle = { m, on -> calls += "learn:$m:$on" }
            )
        )
        composeRule.onNodeWithTag(PrivacySettingsTags.mode(Mode.PAREJA)).performClick()
        composeRule.onNodeWithTag(
            PrivacySettingsTags.visibility(Visibility.HIDDEN)
        ).performScrollTo().performClick()
        composeRule.onNodeWithTag(
            PrivacySettingsTags.learning(Mode.AMISTAD)
        ).performScrollTo().performClick()
        assertEquals(listOf("mode:PAREJA:true", "vis:HIDDEN", "learn:AMISTAD:false"), calls)
    }

    @Test
    fun controlsAreOffWhileSaving() {
        show(loaded.copy(busy = true))
        composeRule.onNodeWithTag(PrivacySettingsTags.mode(Mode.AMISTAD)).assertIsNotEnabled()
        composeRule.onNodeWithTag(
            PrivacySettingsTags.visibility(Visibility.NAME)
        ).assertIsNotEnabled()
    }

    @Test
    fun signOutRowShowsProgress() {
        show(loaded.copy(signingOut = true))
        composeRule.onNodeWithText("Cerrando sesión…").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(PrivacySettingsTags.SIGN_OUT).assertIsNotEnabled()
    }

    @Test
    fun theParejaDialogOpensOverTheScreen() {
        show(
            loaded.copy(
                dialog = me.santiagoreina.parcheu.feature.modes.ParejaDialogState(null, null)
            )
        )
        composeRule.onNodeWithTag(ModesTags.DIALOG, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(
            ModesTags.DIALOG_CONFIRM,
            useUnmergedTree = true
        ).assertIsNotEnabled()
    }

    @Test
    fun aFailedActionShowsItsMessage() {
        show(loaded.copy(error = SettingsError.Api(AppError.Network)))
        composeRule.onNodeWithTag(PrivacySettingsTags.STATUS).assertIsDisplayed()
        composeRule.onNodeWithText(
            "No hay conexión. Revisa tu internet e inténtalo de nuevo."
        ).assertIsDisplayed()
    }

    @Test
    fun theLoadErrorOffersRetry() {
        var retried = false
        show(
            PrivacySettingsUiState(loading = false, loadError = AppError.Network),
            PrivacySettingsActions(onRetry = { retried = true })
        )
        composeRule.onNodeWithTag(PrivacySettingsTags.ERROR).assertIsDisplayed()
        composeRule.onNodeWithText("Reintentar").performClick()
        assertEquals(true, retried)
    }
}
