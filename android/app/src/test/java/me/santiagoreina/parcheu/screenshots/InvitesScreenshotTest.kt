package me.santiagoreina.parcheu.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.invites.FakeInvitesRepository
import me.santiagoreina.parcheu.feature.invites.InviteMessage
import me.santiagoreina.parcheu.feature.invites.InvitesActions
import me.santiagoreina.parcheu.feature.invites.InvitesScreen
import me.santiagoreina.parcheu.feature.invites.InvitesTab
import me.santiagoreina.parcheu.feature.invites.InvitesUiState
import me.santiagoreina.parcheu.feature.invites.QrEncoder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capturas de C5 (E6.1) en ambos temas: 'Mi código' (mockup 07), con un canje por aceptar, y 'Escanear'
 * (sin mockup) con el aviso de un QR ajeno. Grabar con
 * `./gradlew :app:testDevDebugUnitTest -Proborazzi.test.record=true`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class InvitesScreenshotTest(private val dark: Boolean) {
    @get:Rule
    val composeRule = createComposeRule()

    private val theme = if (dark) "dark" else "light"

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { ParcheUTheme(darkTheme = dark) { content() } }
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/invites/${name}_$theme.png",
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f)
            )
        )
    }

    private fun loaded(pending: Boolean = false) = InvitesUiState(
        loading = false,
        invite = FakeInvitesRepository.sampleInvite(
            if (pending) listOf(FakeInvitesRepository.pendingLaura) else emptyList()
        ),
        qr = QrEncoder.encode("https://parcheu-dev.web.app/i/MARI-4827"),
        name = "Mariana Gómez"
    )

    @Test
    fun myCode() = capture("c5_mi_codigo") {
        InvitesScreen(loaded(), InvitesActions())
    }

    @Test
    fun myCodeWithPendingInvite() = capture("c5_mi_codigo_pendiente") {
        InvitesScreen(loaded(pending = true).copy(codeInput = "SEBA-1290"), InvitesActions())
    }

    @Test
    fun scan() = capture("c5_escanear") {
        InvitesScreen(loaded().copy(tab = InvitesTab.SCAN), InvitesActions())
    }

    @Test
    fun scanForeignQr() = capture("c5_escanear_qr_ajeno") {
        InvitesScreen(
            loaded().copy(
                tab = InvitesTab.SCAN,
                message = InviteMessage("Este código no es de ParcheU", isError = true)
            ),
            InvitesActions()
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }
}
