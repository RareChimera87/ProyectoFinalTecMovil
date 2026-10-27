package me.santiagoreina.parcheu.feature.parche.communities

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** D8 (E6.4): con comunidades, bloqueado (gratis) y vacío. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class CommunitiesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(
        state: CommunitiesUiState,
        callbacks: CommunitiesCallbacks = CommunitiesCallbacks()
    ) = composeRule.setContent {
        ParcheUTheme(darkTheme = true) { CommunitiesScreen(state, callbacks, animate = false) }
    }

    @Test
    fun plusSeesCommunitiesWithSizeAndTraits() {
        show(CommunitiesUiState.Loaded(CommunitiesSamples.plus))
        composeRule.onNodeWithText("Los del tinto de las 7").assertIsDisplayed()
        composeRule.onNodeWithText("5 personas").assertIsDisplayed()
        composeRule.onNodeWithText("muy sociables").assertIsDisplayed()
        composeRule.onNodeWithTag(CommunitiesTags.card("c2")).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun viewInGraphSendsTheCommunityId() {
        val opened = mutableListOf<String>()
        show(
            CommunitiesUiState.Loaded(CommunitiesSamples.plus),
            CommunitiesCallbacks(onViewInGraph = { opened += it })
        )
        composeRule.onAllNodesWithText("Ver en el grafo")[1].performScrollTo().performClick()
        assertEquals(listOf("c2"), opened)
    }

    @Test
    fun freeSeesLockWithCountAndNoCommunities() {
        var unlocked = 0
        show(CommunitiesUiState.Locked(2), CommunitiesCallbacks(onUnlock = { unlocked++ }))
        composeRule.onNodeWithTag(CommunitiesTags.LOCKED).assertIsDisplayed()
        composeRule.onNodeWithText("Estás en 2 comunidades").assertIsDisplayed()
        composeRule.onAllNodes(hasTestTag(CommunitiesTags.LIST)).assertCountEquals(0)
        composeRule.onAllNodesWithText("Ver en el grafo").assertCountEquals(0)
        composeRule.onNodeWithText("Ver ParcheU+").performClick()
        assertEquals(1, unlocked)
    }

    @Test
    fun emptyExplainsTheRules() {
        show(CommunitiesUiState.Empty)
        composeRule.onNodeWithTag(CommunitiesTags.EMPTY).assertIsDisplayed()
        composeRule.onNodeWithText("Aún no tienes comunidades").assertIsDisplayed()
    }

    @Test
    fun errorOffersRetry() {
        var retried = 0
        show(
            CommunitiesUiState.Error(AppError.Network),
            CommunitiesCallbacks(onRetry = {
                retried++
            })
        )
        composeRule.onNodeWithText("Reintentar").performClick()
        assertEquals(1, retried)
    }

    @Test
    fun helpersFormatText() {
        assertEquals("1 persona", peopleText(1))
        assertEquals("4 personas", peopleText(4))
        assertEquals("Estás en 1 comunidad", communitiesLockedTitle(1))
        assertEquals("Descubre los grupos que se forman en tu red", communitiesLockedTitle(0))
        val avatars = CommunitiesSamples.items[0].members.map { it.toAvatar() }
        assertEquals(listOf(false, false, true, false), avatars.map { it.anonymous })
        assertEquals("Alguien", avatars[2].name)
    }
}
