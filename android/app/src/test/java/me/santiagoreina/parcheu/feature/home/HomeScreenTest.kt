package me.santiagoreina.parcheu.feature.home

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.contract.Suggestion
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Estados de C1 y sus acciones (E4.7). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(state: HomeUiState, callbacks: HomeCallbacks = HomeCallbacks()) =
        composeRule.setContent {
            ParcheUTheme(darkTheme = true) {
                HomeScreen(state, callbacks, now = HomeSamples.now, animate = false)
            }
        }

    @Test
    fun freeQuotaShowsThreeOfFiveAndBellBadge() {
        show(HomeSamples.freeThreeOfFive)
        composeRule.onNodeWithText("3 de 5").assertIsDisplayed()
        composeRule.onNodeWithText("Te quedan 2 · se renuevan el lunes").assertIsDisplayed()
        composeRule.onNodeWithTag(HomeTags.BELL_BADGE).assertIsDisplayed()
        composeRule.onNodeWithText("¿Qué más, Mari?").assertIsDisplayed()
    }

    @Test
    fun freeSuggestionsDoNotShowMutualFriends() {
        show(HomeSamples.freeThreeOfFive)
        composeRule.onAllNodesWithText("amigos en común", substring = true).assertCountEquals(0)
        composeRule.onNodeWithText("La última vez salieron 72 % afines").assertIsDisplayed()
    }

    @Test
    fun plusShowsMutualFriendsAndNoQuotaBar() {
        show(HomeSamples.plus)
        composeRule.onNodeWithText("2 amigos en común").assertIsDisplayed()
        composeRule.onNodeWithText("Sin límite semanal · llevas 4 hoy").assertIsDisplayed()
        composeRule.onAllNodesWithTag(HomeTags.PLUS_CHIP).assertCountEquals(0)
    }

    @Test
    fun noneLeftOffersParcheUPlus() {
        var plus = 0
        show(HomeSamples.noneLeft, HomeCallbacks(onPlus = { plus++ }))
        composeRule.onNodeWithText("Ya usaste las 5 · se renuevan el lunes").assertIsDisplayed()
        composeRule.onNodeWithText("Simula sin límite con ParcheU+").performClick()
        composeRule.onNodeWithTag(HomeTags.PLUS_CHIP).performClick()
        assertEquals(2, plus)
    }

    @Test
    fun runningWithoutProgressHidesTheBar() {
        show(HomeSamples.inProgress.copy(progress = null))
        composeRule.onNodeWithText("En curso · Estudio").assertIsDisplayed()
        composeRule
            .onAllNodesWithContentDescription("Avance de la simulación", substring = true)
            .assertCountEquals(0)
    }

    @Test
    fun runningWithProgressShowsTheBar() {
        show(HomeSamples.inProgress)
        composeRule
            .onNodeWithContentDescription("Avance de la simulación: 60 %")
            .assertIsDisplayed()
    }

    @Test
    fun runningAndReadyOpenTheirSimulations() {
        val opened = mutableListOf<String>()
        show(HomeSamples.resultReady, HomeCallbacks(onOpenSimulation = { opened += it.id }))
        composeRule.onNodeWithText("Tú y Laura están conversando").assertIsDisplayed()
        composeRule.onNodeWithText("Escenario 2 de 3 · Estudio").assertIsDisplayed()
        composeRule.onNodeWithText("78 % afines · hace 1 h").assertIsDisplayed()
        composeRule.onNodeWithTag(HomeTags.RUNNING).performClick()
        composeRule.onNodeWithTag(HomeTags.ready("sim-daniela")).performClick()
        assertEquals(listOf("sim-laura", "sim-daniela"), opened)
    }

    @Test
    fun simulateAndSearchCallbacks() {
        val simulated = mutableListOf<Suggestion>()
        var random = 0
        var bell = 0
        show(
            HomeSamples.freeThreeOfFive,
            HomeCallbacks(onSimulate = {
                simulated += it
            }, onRandomMatch = { random++ }, onBell = { bell++ })
        )
        composeRule.onNodeWithText("Simular").performClick()
        composeRule.onNodeWithText("Buscar").performClick()
        composeRule.onNodeWithTag(HomeTags.BELL).onChildren().onFirst().performClick()
        assertEquals("u-sebas", simulated.single().other.id)
        assertEquals(1, random)
        assertEquals(1, bell)
    }

    @Test
    fun avatarOpensProfileWithA48dpTargetAndDescription() {
        var profile = 0
        show(HomeSamples.freeThreeOfFive, HomeCallbacks(onAvatar = { profile++ }))
        val avatar = composeRule.onNodeWithTag(HomeTags.AVATAR)
        avatar.assertContentDescriptionEquals("Tu perfil")
        avatar.assertHasClickAction()
        avatar.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        composeRule.onAllNodesWithContentDescription("Mari").assertCountEquals(0)
        avatar.performClick()
        assertEquals(1, profile)
    }

    @Test
    fun bellBadgeIsNotReadTwice() {
        show(HomeSamples.freeThreeOfFive)
        composeRule.onNodeWithContentDescription("Notificaciones, 2 sin leer").assertIsDisplayed()
        composeRule.onAllNodesWithText("2").assertCountEquals(0)
    }

    @Test
    fun actionErrorIsADismissButton() {
        var dismissed = 0
        show(
            HomeSamples.freeThreeOfFive.copy(actionError = AppError.Network),
            HomeCallbacks(onDismissError = { dismissed++ })
        )
        val error = composeRule.onNodeWithTag(HomeTags.ACTION_ERROR)
        error.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        error.assert(
            SemanticsMatcher("acción 'Descartar'") {
                it.config.getOrNull(SemanticsActions.OnClick)?.label == "Descartar"
            }
        )
        error.performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun demoProfilesShowTheBadgeInRunningReadyAndSuggestions() {
        show(HomeSamples.demo)
        composeRule.onAllNodesWithTag(
            HomeTags.DEMO_BADGE,
            useUnmergedTree = true
        ).assertCountEquals(3)
        composeRule.onNodeWithText("Resultado listo con Alguien de la UIS", substring = true)
            .assertIsDisplayed()
        // Filas en curso y resultado listo: la insignia completa; la sugerencia, compacta con la misma lectura.
        composeRule.onAllNodesWithText(
            DEMO_PROFILE_BADGE,
            useUnmergedTree = true
        ).assertCountEquals(2)
        composeRule.onAllNodesWithContentDescription(DEMO_PROFILE_BADGE, useUnmergedTree = true)
            .assertCountEquals(1)
    }

    @Test
    fun nonDemoProfilesHaveNoBadge() {
        show(HomeSamples.resultReady)
        composeRule.onAllNodesWithTag(
            HomeTags.DEMO_BADGE,
            useUnmergedTree = true
        ).assertCountEquals(0)
    }

    @Test
    fun emptyStateAndError() {
        var parche = 0
        show(HomeSamples.empty, HomeCallbacks(onPickFromParche = { parche++ }))
        composeRule.onNodeWithTag(HomeTags.EMPTY).assertIsDisplayed()
        composeRule.onAllNodesWithTag(HomeTags.BELL_BADGE).assertCountEquals(0)
        composeRule.onNodeWithText("Elegir de mi parche").performClick()
        assertEquals(1, parche)
    }

    @Test
    fun errorRetries() {
        var retried = false
        show(HomeUiState.Error(AppError.Network), HomeCallbacks(onRetry = { retried = true }))
        composeRule.onNodeWithTag(HomeTags.ERROR).assertIsDisplayed()
        composeRule.onNodeWithText("Reintentar").performClick()
        assertTrue(retried)
    }
}
