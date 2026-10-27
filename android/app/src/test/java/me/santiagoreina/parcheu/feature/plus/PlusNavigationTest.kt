package me.santiagoreina.parcheu.feature.plus

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.reflect.KClass
import me.santiagoreina.parcheu.contract.QuotaKind
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.AppRoute
import me.santiagoreina.parcheu.navigation.Paywall
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Criterio de E7.3: cada `AppError` premium abre la pantalla correcta. El error se traduce a una ruta
 * (`PlusErrorRouter`) y la ruta [Paywall] se resuelve con `PaywallEntry` a G1 o a la hoja G2 del cupo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class PlusNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private class RecordingNavigator : AppNavigator {
        val replaced = mutableListOf<Pair<AppRoute, KClass<out AppRoute>>>()
        var backs = 0
        override fun navigate(route: AppRoute) = Unit
        override fun navigateReplacing(route: AppRoute, replacing: KClass<out AppRoute>) {
            replaced += route to replacing
        }
        override fun navigateToTopLevel(route: AppRoute) = Unit
        override fun replaceAll(route: AppRoute) = Unit
        override fun back(): Boolean {
            backs++
            return true
        }
        override fun canGoBack(): Boolean = true
    }

    private fun open(error: AppError, navigator: AppNavigator = RecordingNavigator()) {
        val route = PlusErrorRouter.route(error, "test") as Paywall
        composeRule.setContent {
            ParcheUTheme(darkTheme = true) {
                // G1 se sustituye por una marca: su ViewModel necesita Koin y Play, y aquí solo importa cuál se abre.
                PaywallEntry(route = route, navigator = navigator, paywall = { _, _ -> Text("G1") })
            }
        }
    }

    @Test
    fun plusRequiredOpensG1() {
        open(AppError.PlusRequired)
        composeRule.onNodeWithText("G1").assertIsDisplayed()
    }

    @Test
    fun quotaExceededSimulationOpensTheSimulationSheet() {
        open(AppError.QuotaExceeded(QuotaKind.SIMULATION, null))
        composeRule.onNodeWithTag(PlusTags.LIMIT).assertIsDisplayed()
        composeRule.onNodeWithText("Ya usaste tus simulaciones de esta semana").assertIsDisplayed()
    }

    @Test
    fun quotaExceededRandomMatchOpensTheMatchSheet() {
        open(AppError.QuotaExceeded(QuotaKind.RANDOM_MATCH, null))
        composeRule.onNodeWithTag(PlusTags.LIMIT).assertIsDisplayed()
        composeRule.onNodeWithText("Ya usaste tu match al azar de hoy").assertIsDisplayed()
    }

    @Test
    fun quotaExceededFullReportOpensTheReportSheet() {
        open(AppError.QuotaExceeded(QuotaKind.FULL_REPORT, null))
        composeRule.onNodeWithTag(PlusTags.LIMIT).assertIsDisplayed()
        composeRule.onNodeWithText("Ya usaste tu informe completo de este mes").assertIsDisplayed()
    }

    @Test
    fun seePlusInTheSheetReplacesItWithG1AndWaitGoesBack() {
        val navigator = RecordingNavigator()
        open(AppError.QuotaExceeded(QuotaKind.SIMULATION, null), navigator)
        composeRule.onNodeWithTag(PlusTags.LIMIT_SEE_PLUS).performClick()
        assertEquals(
            listOf<Pair<AppRoute, KClass<out AppRoute>>>(
                Paywall(PAYWALL_SOURCE_LIMIT) to Paywall::class
            ),
            navigator.replaced
        )
        // El destino de "Ver ParcheU+" abre G1, no otra hoja.
        assertEquals(
            PlusEntry.Paywall(PAYWALL_SOURCE_LIMIT),
            PlusSources.parse(PAYWALL_SOURCE_LIMIT)
        )
        composeRule.onNodeWithTag(PlusTags.LIMIT_WAIT).performClick()
        assertEquals(1, navigator.backs)
    }
}
