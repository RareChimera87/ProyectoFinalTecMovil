package me.santiagoreina.parcheu.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.EntitlementDto
import me.santiagoreina.parcheu.contract.QuotaKind
import me.santiagoreina.parcheu.designsystem.components.compound.ParcheUModalDialogContent
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.plus.CancelDialogBody
import me.santiagoreina.parcheu.feature.plus.LimitSheet
import me.santiagoreina.parcheu.feature.plus.ManageSubscriptionActions
import me.santiagoreina.parcheu.feature.plus.ManageSubscriptionScreen
import me.santiagoreina.parcheu.feature.plus.ManageUiState
import me.santiagoreina.parcheu.feature.plus.PLAN_PENDING
import me.santiagoreina.parcheu.feature.plus.PaywallActions
import me.santiagoreina.parcheu.feature.plus.PaywallScreen
import me.santiagoreina.parcheu.feature.plus.PaywallUiState
import me.santiagoreina.parcheu.feature.plus.PlansUi
import me.santiagoreina.parcheu.feature.plus.PlusMessage
import me.santiagoreina.parcheu.feature.plus.PlusTestData
import me.santiagoreina.parcheu.feature.plus.PurchaseSuccessActions
import me.santiagoreina.parcheu.feature.plus.PurchaseSuccessScreen
import me.santiagoreina.parcheu.feature.plus.PurchaseSuccessUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capturas de todas las variantes de G1-G4 en ambos temas (la clara de G1 es I4): paywall con anual por
 * defecto, mensual, cargando precios y error; hoja de límite de simulaciones, match diario e informe
 * mensual; compra anual, mensual y verificación pendiente; suscripción activa anual y mensual, cancelada,
 * en gracia y sin suscripción, con el diálogo de cancelar. Grabar con
 * `./gradlew :app:testDevDebugUnitTest -Proborazzi.test.record=true`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class PlusScreenshotTest(private val dark: Boolean) {
    @get:Rule
    val composeRule = createComposeRule()

    private val theme = if (dark) "dark" else "light"

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { ParcheUTheme(darkTheme = dark) { content() } }
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/plus/${name}_$theme.png",
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f)
            )
        )
    }

    private val loaded = PaywallUiState(plans = PlansUi.Loaded(PlusTestData.plans))

    // G1 / I4

    @Test
    fun paywallAnnual() = capture("g1_paywall_anual") { PaywallScreen(loaded, PaywallActions()) }

    @Test
    fun paywallMonthly() = capture("g1_paywall_mensual") {
        PaywallScreen(loaded.copy(selected = "mensual"), PaywallActions())
    }

    @Test
    fun paywallLoading() = capture("g1_paywall_cargando") {
        PaywallScreen(PaywallUiState(), PaywallActions())
    }

    @Test
    fun paywallError() = capture("g1_paywall_error") {
        PaywallScreen(
            PaywallUiState(plans = PlansUi.Error(billingUnavailable = true)),
            PaywallActions()
        )
    }

    @Test
    fun paywallNothingToRestore() = capture("g1_paywall_sin_compras") {
        PaywallScreen(loaded.copy(message = PlusMessage.NothingToRestore), PaywallActions())
    }

    // G2

    private val weekReset = Instant.parse("2026-11-09T05:00:00Z")

    @Test
    fun limitSimulations() = capture("g2_limite_simulaciones") {
        LimitSheet(QuotaKind.SIMULATION, weekReset, {}, {})
    }

    @Test
    fun limitRandomMatch() = capture("g2_limite_match") {
        LimitSheet(QuotaKind.RANDOM_MATCH, Instant.parse("2026-11-04T05:00:00Z"), {}, {})
    }

    @Test
    fun limitFullReport() = capture("g2_limite_informe") {
        LimitSheet(QuotaKind.FULL_REPORT, Instant.parse("2026-12-01T05:00:00Z"), {}, {})
    }

    // G3

    @Test
    fun successAnnual() = capture("g3_compra_anual") {
        PurchaseSuccessScreen(
            PurchaseSuccessUiState("anual", "$45.000", "21 de octubre de 2027"),
            PurchaseSuccessActions()
        )
    }

    @Test
    fun successMonthly() = capture("g3_compra_mensual") {
        PurchaseSuccessScreen(
            PurchaseSuccessUiState("mensual", "$5.000", "21 de noviembre de 2026"),
            PurchaseSuccessActions()
        )
    }

    @Test
    fun successPending() = capture("g3_verificacion_pendiente") {
        PurchaseSuccessScreen(PurchaseSuccessUiState(PLAN_PENDING), PurchaseSuccessActions())
    }

    // G4

    private fun manage(e: EntitlementDto, dialog: Boolean = false) = @Composable {
        ManageSubscriptionScreen(
            ManageUiState.Loaded(e, PlusTestData.plans),
            ManageSubscriptionActions()
        )
        if (dialog) {
            // El diálogo real vive en su propia ventana; aquí se compone su contenido sobre el scrim.
            Box(Modifier.fillMaxSize().background(ParcheUTheme.colors.scrim), Alignment.Center) {
                ParcheUModalDialogContent { CancelDialogBody({}, {}) }
            }
        }
    }

    @Test
    fun manageActiveAnnual() = capture("g4_activa_anual", manage(PlusTestData.activeAnnual))

    @Test
    fun manageActiveMonthly() = capture("g4_activa_mensual", manage(PlusTestData.activeMonthly))

    @Test
    fun manageCanceled() = capture("g4_cancelada", manage(PlusTestData.canceled))

    @Test
    fun manageGrace() = capture("g4_gracia", manage(PlusTestData.grace))

    @Test
    fun manageInactive() = capture("g4_sin_suscripcion", manage(PlusTestData.free))

    @Test
    fun manageCancelDialog() =
        capture("g4_cancelar_confirmacion", manage(PlusTestData.activeAnnual, dialog = true))

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }
}
