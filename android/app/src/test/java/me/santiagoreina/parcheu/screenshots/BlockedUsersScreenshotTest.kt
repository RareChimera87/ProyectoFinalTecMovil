package me.santiagoreina.parcheu.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.BlockedDto
import me.santiagoreina.parcheu.contract.ReportReason
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.ui.UiState
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.blockedusers.BlockedUsersActions
import me.santiagoreina.parcheu.feature.blockedusers.BlockedUsersScreen
import me.santiagoreina.parcheu.feature.blockedusers.BlockedUsersState
import me.santiagoreina.parcheu.feature.safety.ReportActions
import me.santiagoreina.parcheu.feature.safety.ReportScreen
import me.santiagoreina.parcheu.feature.safety.ReportUiState
import me.santiagoreina.parcheu.feature.safety.aiContentTarget
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capturas de 'Personas bloqueadas' (con ítems, vacía, con error) y de H2 para contenido de la IA (E8.7), en
 * ambos temas, contra app/src/test/screenshots/blocked. Grabar con
 * `./gradlew :app:testDevDebugUnitTest -Proborazzi.test.record=true`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class BlockedUsersScreenshotTest(private val dark: Boolean) {
    @get:Rule
    val composeRule = createComposeRule()

    private val theme = if (dark) "dark" else "light"
    private val now = Instant.parse("2026-11-14T15:00:00Z")

    private val items = listOf(
        BlockedDto("u-laura", "Laura Pérez", "LP", Instant.parse("2026-11-10T15:00:00Z")),
        BlockedDto("a_x", "Alguien de la UIS", "A", Instant.parse("2026-11-13T15:00:00Z")),
        BlockedDto("a_y", "Alguien", "A", Instant.parse("2026-11-14T09:00:00Z"))
    )

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { ParcheUTheme(darkTheme = dark) { content() } }
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/blocked/${name}_$theme.png",
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f)
            )
        )
    }

    @Test
    fun withItems() = capture("bloqueados_con_items") {
        BlockedUsersScreen(
            BlockedUsersState(
                UiState.Loaded(
                    items.sortedByDescending {
                        it.blockedAt
                    }
                )
            ),
            BlockedUsersActions(),
            now = now
        )
    }

    @Test
    fun empty() = capture("bloqueados_vacia") {
        BlockedUsersScreen(BlockedUsersState(UiState.Empty), BlockedUsersActions(), now = now)
    }

    @Test
    fun failed() = capture("bloqueados_error") {
        BlockedUsersScreen(
            BlockedUsersState(UiState.Error(AppError.Network)),
            BlockedUsersActions(),
            now = now
        )
    }

    @Test
    fun reportAiContent() = capture("h2_contenido_ia") {
        ReportScreen(
            ReportUiState(aiContentTarget("sim-1", 2), reason = ReportReason.AI_HARMFUL),
            ReportActions()
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }
}
