package me.santiagoreina.parcheu.screenshots

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import me.santiagoreina.parcheu.ParcheUApp
import me.santiagoreina.parcheu.navigation.Onboarding
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captura de ejemplo de Roborazzi (ADR-0022): stub de A4 con los enlaces legales. Es una prueba de
 * regresión contra la captura versionada en app/src/test/screenshots, con `changeThreshold` 0,01 (1 %).
 * Grabar: `./gradlew :app:recordRoborazziDevDebug`; verificar: `./gradlew :app:verifyRoborazziDevDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-night-xhdpi")
class OnboardingStubScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun onboardingStubWithLegalLinks() {
        composeRule.setContent { ParcheUApp(startDestination = Onboarding(page = 2)) }

        composeRule.onNodeWithTag("legal:terms").assertIsDisplayed()
        composeRule.onNodeWithTag("legal:privacy").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/onboarding_stub_a4.png",
            roborazziOptions = ROBORAZZI_OPTIONS
        )
    }

    companion object {
        /** Umbral documentado en arquitectura.md §2.7 y ADR-0022. */
        const val CHANGE_THRESHOLD = 0.01f

        val ROBORAZZI_OPTIONS =
            RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(
                    changeThreshold = CHANGE_THRESHOLD
                )
            )
    }
}
