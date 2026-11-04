package me.santiagoreina.parcheu.feature.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import me.santiagoreina.parcheu.contract.TwinPoint
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class ProfileScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun loaded(points: List<TwinPoint>) = ProfileUiState.Loaded(
        ProfileSamples.me,
        ProfileSamples.twin,
        chartOf(points),
        "Universidad Industrial de Santander",
        listOf("Música", "Cine", "Café")
    )

    private fun show(state: ProfileUiState, callbacks: ProfileCallbacks = ProfileCallbacks()) =
        composeRule.setContent {
            ParcheUTheme(darkTheme = true) { ProfileScreen(state, callbacks) }
        }

    @Test
    fun emptyChartExplainsHowToTeachTheTwin() {
        show(loaded(ProfileSamples.zero))
        composeRule.onNodeWithTag(ProfileTags.CHART_EMPTY).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(ProfileTags.CHART_EMPTY).assertTextEquals(CHART_EMPTY_TEXT)
    }

    @Test
    fun singlePointShowsValue() {
        show(loaded(ProfileSamples.one))
        composeRule.onNodeWithTag(
            ProfileTags.CHART_VALUE
        ).performScrollTo().assertTextEquals("42 %")
    }

    @Test
    fun interestsShowCatalogLabelsNotSlugs() {
        show(loaded(ProfileSamples.zero))
        composeRule.onNodeWithText("Música").assertIsDisplayed()
    }

    @Test
    fun lineAxisUsesMonthsOfFirstAndLastPoint() {
        val points =
            listOf(TwinPoint("2026-08", 40, 2), TwinPoint("2026-11", 61, 3))
        show(loaded(points))
        composeRule.onNodeWithText("ago 2026").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("nov 2026").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun linePointsShowLatestValue() {
        show(loaded(ProfileSamples.sixteen))
        composeRule.onNodeWithTag(
            ProfileTags.CHART_VALUE
        ).performScrollTo().assertTextEquals("78 %")
    }

    @Test
    fun entriesInvokeCallbacks() {
        val clicks = mutableListOf<String>()
        show(
            loaded(ProfileSamples.zero),
            ProfileCallbacks(
                onSettings = { clicks += "settings" },
                onEditProfile = { clicks += "edit" },
                onTrainTwin = { clicks += "train" },
                onSubscription = { clicks += "sub" },
                onAddFriends = { clicks += "friends" }
            )
        )
        composeRule.onNodeWithTag(ProfileTags.SETTINGS).performClick()
        listOf(
            ProfileTags.EDIT,
            ProfileTags.TRAIN,
            ProfileTags.SUBSCRIPTION,
            ProfileTags.ADD_FRIENDS
        ).forEach { composeRule.onNodeWithTag(it).performScrollTo().performClick() }
        assertEquals(listOf("settings", "edit", "train", "sub", "friends"), clicks)
    }

    @Test
    fun errorOffersRetry() {
        var retried = false
        show(ProfileUiState.Error(AppError.Network), ProfileCallbacks(onRetry = { retried = true }))
        composeRule.onNodeWithTag(ProfileTags.ERROR).assertIsDisplayed()
        composeRule.onNodeWithTag("profile:root").assertIsDisplayed()
        composeRule.onNodeWithText("Reintentar").performClick()
        assertEquals(true, retried)
    }
}
