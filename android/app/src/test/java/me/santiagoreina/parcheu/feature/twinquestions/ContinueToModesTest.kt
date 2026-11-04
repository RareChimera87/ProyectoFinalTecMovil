package me.santiagoreina.parcheu.feature.twinquestions

import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.AppRoute
import me.santiagoreina.parcheu.navigation.Modes
import org.junit.Assert.assertEquals
import org.junit.Test

class ContinueToModesTest {
    private class RecordingNavigator : AppNavigator {
        val calls = mutableListOf<String>()
        override fun navigate(route: AppRoute) {
            calls += "navigate:$route"
        }
        override fun navigateToTopLevel(route: AppRoute) {
            calls += "top:$route"
        }
        override fun replaceAll(route: AppRoute) {
            calls += "replaceAll:$route"
        }
        override fun back(): Boolean {
            calls += "back"
            return true
        }
        override fun canGoBack(): Boolean = true
    }

    @Test
    fun finishingPopsB1BeforeOpeningB2() {
        val navigator = RecordingNavigator()
        navigator.continueToModes()
        assertEquals(listOf("back", "navigate:${Modes()}"), navigator.calls)
    }
}
