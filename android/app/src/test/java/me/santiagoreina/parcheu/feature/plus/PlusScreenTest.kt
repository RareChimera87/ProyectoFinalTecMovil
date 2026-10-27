package me.santiagoreina.parcheu.feature.plus

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.QuotaKind
import me.santiagoreina.parcheu.data.billing.PurchaseFailure
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Estados de G1-G4 y sus acciones (E7.3). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class PlusScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun paywall(state: PaywallUiState, actions: PaywallActions = PaywallActions()) =
        composeRule.setContent {
            ParcheUTheme(darkTheme = true) { PaywallScreen(state, actions) }
        }

    private val loaded = PaywallUiState(plans = PlansUi.Loaded(PlusTestData.plans))

    // ---- G1

    @Test
    fun annualIsSelectedByDefaultAndShowsPricesFromProductDetails() {
        paywall(loaded)
        composeRule.onNodeWithTag(PlusTags.PLAN_ANNUAL).assertIsSelected()
        composeRule.onNodeWithTag(PlusTags.PLAN_MONTHLY).assertIsNotSelected()
        composeRule.onNodeWithText("$45.000").assertIsDisplayed()
        composeRule.onNodeWithText("$3.750/mes").assertIsDisplayed()
        composeRule.onNodeWithText("Ahorras $15.000 al año").assertIsDisplayed()
        composeRule.onNodeWithText("$5.000").assertIsDisplayed()
        composeRule.onNodeWithText("Suscribirme por $45.000/año").assertIsDisplayed()
    }

    @Test
    fun otherPricesRenderOtherTexts() {
        paywall(PaywallUiState(plans = PlansUi.Loaded(PlusTestData.otherPlans)))
        composeRule.onNodeWithText("$4.500/mes").assertIsDisplayed()
        composeRule.onNodeWithText("Ahorras $18.000 al año").assertIsDisplayed()
        composeRule.onNodeWithText("Suscribirme por $54.000/año").assertIsDisplayed()
    }

    @Test
    fun noFalseMonthsFreeBadge() {
        paywall(loaded)
        composeRule.onAllNodesWithText("2 meses gratis").assertCountEquals(0)
    }

    @Test
    fun selectingMonthlyReportsItAndChangesTheCta() {
        var picked = ""
        val state = loaded.copy(selected = "mensual")
        paywall(state, PaywallActions(onSelect = { picked = it }))
        composeRule.onNodeWithTag(PlusTags.PLAN_MONTHLY).assertIsSelected()
        composeRule.onNodeWithText("Suscribirme por $5.000/mes").assertIsDisplayed()
        composeRule.onNodeWithTag(PlusTags.PLAN_ANNUAL).performClick()
        assertEquals("anual", picked)
    }

    @Test
    fun comparisonTableIsShown() {
        paywall(loaded)
        composeRule.onNodeWithTag(PlusTags.COMPARISON).assertIsDisplayed()
        composeRule.onNodeWithText("Reportar y bloquear").assertIsDisplayed()
    }

    @Test
    fun loadingPricesDisablesSubscribe() {
        paywall(PaywallUiState())
        composeRule.onNodeWithTag(PlusTags.PLANS_LOADING).assertIsDisplayed()
        composeRule.onNodeWithTag(PlusTags.SUBSCRIBE).assertIsNotEnabled()
        composeRule.onNodeWithText("Cargando precios…").assertIsDisplayed()
    }

    @Test
    fun errorShowsRetryAndNoCheckout() {
        var retried = false
        paywall(
            PaywallUiState(plans = PlansUi.Error(billingUnavailable = true)),
            PaywallActions(onRetry = { retried = true })
        )
        composeRule.onNodeWithTag(PlusTags.PLANS_ERROR).assertIsDisplayed()
        composeRule.onNodeWithTag(PlusTags.SUBSCRIBE).assertIsNotEnabled()
        composeRule.onNodeWithText("Reintentar").performClick()
        assertTrue(retried)
    }

    @Test
    fun subscribeCloseAndRestoreAreWired() {
        var subscribed = 0
        var closed = 0
        var restored = 0
        paywall(
            loaded,
            PaywallActions(
                onSubscribe = { subscribed++ },
                onClose = { closed++ },
                onRestore = { restored++ }
            )
        )
        composeRule.onNodeWithTag(PlusTags.SUBSCRIBE).assertIsEnabled().performClick()
        composeRule.onNodeWithTag(PlusTags.CLOSE).performClick()
        composeRule.onNodeWithTag(PlusTags.RESTORE).performClick()
        assertEquals(listOf(1, 1, 1), listOf(subscribed, closed, restored))
    }

    @Test
    fun purchaseInProgressDisablesSubscribe() {
        paywall(loaded.copy(purchasing = true))
        composeRule.onNodeWithTag(PlusTags.SUBSCRIBE).assertIsNotEnabled()
    }

    @Test
    fun messagesAreShownAndDismissed() {
        var dismissed = false
        paywall(
            loaded.copy(message = PlusMessage.PurchaseFailed(PurchaseFailure.Play(6))),
            PaywallActions(onDismissMessage = { dismissed = true })
        )
        composeRule.onNodeWithTag(PlusTags.MESSAGE).assertIsDisplayed()
        composeRule.onNodeWithText("Entendido").performClick()
        assertTrue(dismissed)
    }

    // ---- G2

    private fun limit(
        kind: QuotaKind,
        resetsAt: Instant?,
        seePlus: () -> Unit = {
        },
        wait: () -> Unit = {}
    ) = composeRule.setContent {
        ParcheUTheme(darkTheme = true) { LimitSheet(kind, resetsAt, seePlus, wait) }
    }

    @Test
    fun simulationLimitSheetNamesTheRenewalDay() {
        limit(QuotaKind.SIMULATION, Instant.parse("2026-11-09T05:00:00Z"))
        composeRule.onNodeWithText("Ya usaste tus simulaciones de esta semana").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Se renuevan el lunes 9 de noviembre. Mientras tanto puedes chatear con tu parche y seguir entrenando a tu gemelo."
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Espero al lunes").assertIsDisplayed()
    }

    @Test
    fun dailyMatchAndMonthlyReportHaveTheirOwnSheets() {
        limit(QuotaKind.RANDOM_MATCH, null)
        composeRule.onNodeWithText("Ya usaste tu match al azar de hoy").assertIsDisplayed()
    }

    @Test
    fun monthlyReportSheet() {
        limit(QuotaKind.FULL_REPORT, null)
        composeRule.onNodeWithText("Ya usaste tu informe completo de este mes").assertIsDisplayed()
    }

    @Test
    fun limitSheetActions() {
        var seePlus = 0
        var waited = 0
        limit(QuotaKind.SIMULATION, null, { seePlus++ }, { waited++ })
        composeRule.onNodeWithTag(PlusTags.LIMIT_SEE_PLUS).performClick()
        composeRule.onNodeWithTag(PlusTags.LIMIT_WAIT).performClick()
        assertEquals(listOf(1, 1), listOf(seePlus, waited))
    }

    // ---- G3

    private fun success(
        state: PurchaseSuccessUiState,
        actions: PurchaseSuccessActions = PurchaseSuccessActions()
    ) = composeRule.setContent {
        ParcheUTheme(darkTheme = true) { PurchaseSuccessScreen(state, actions) }
    }

    @Test
    fun annualSuccessShowsPlanAndNextCharge() {
        success(
            PurchaseSuccessUiState("anual", price = "$45.000", nextCharge = "21 de octubre de 2027")
        )
        composeRule.onNodeWithText("¡Ya eres ParcheU+!").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Plan anual · $45.000. Tu próximo cobro es el 21 de octubre de 2027."
        )
            .assertIsDisplayed()
        composeRule.onNodeWithTag(PlusTags.SUCCESS_EXPLORE).assertIsDisplayed()
    }

    @Test
    fun monthlySuccess() {
        success(
            PurchaseSuccessUiState(
                "mensual",
                price = "$5.000",
                nextCharge = "21 de noviembre de 2026"
            )
        )
        composeRule.onNodeWithText(
            "Plan mensual · $5.000. Tu próximo cobro es el 21 de noviembre de 2026."
        )
            .assertIsDisplayed()
    }

    @Test
    fun successWithoutDataOmitsWhatIsUnknown() {
        success(PurchaseSuccessUiState("anual"))
        composeRule.onNodeWithText("Plan anual.").assertIsDisplayed()
    }

    @Test
    fun pendingVerificationDoesNotCelebrate() {
        success(PurchaseSuccessUiState(PLAN_PENDING))
        composeRule.onNodeWithTag(PlusTags.SUCCESS_PENDING).assertIsDisplayed()
        composeRule.onNodeWithText("Estamos verificando tu pago").assertIsDisplayed()
        composeRule.onAllNodesWithTag(PlusTags.SUCCESS).assertCountEquals(0)
        composeRule.onAllNodesWithTag(PlusTags.SUCCESS_EXPLORE).assertCountEquals(0)
    }

    @Test
    fun successActions() {
        var explored = 0
        var home = 0
        success(
            PurchaseSuccessUiState("anual"),
            PurchaseSuccessActions(onExplore = { explored++ }, onHome = { home++ })
        )
        composeRule.onNodeWithTag(PlusTags.SUCCESS_EXPLORE).performClick()
        composeRule.onNodeWithTag(PlusTags.SUCCESS_HOME).performClick()
        assertEquals(listOf(1, 1), listOf(explored, home))
    }

    // ---- G4

    private fun manage(
        state: ManageUiState,
        actions: ManageSubscriptionActions = ManageSubscriptionActions(),
        dialog: Boolean = false,
        onDialog: (Boolean) -> Unit = {}
    ) = composeRule.setContent {
        ParcheUTheme(darkTheme = true) {
            ManageSubscriptionScreen(
                state,
                actions,
                showCancelDialog = dialog,
                onShowCancelDialog = onDialog
            )
        }
    }

    private fun loadedManage(e: me.santiagoreina.parcheu.contract.EntitlementDto) =
        ManageUiState.Loaded(e, PlusTestData.plans)

    @Test
    fun activeAnnualShowsPlanRenewalAndChange() {
        manage(loadedManage(PlusTestData.activeAnnual))
        composeRule.onNodeWithText("Plan anual · $45.000").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Se renueva el 21 de octubre de 2027\nPagas con Google Play"
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(PlusTags.MANAGE_CHANGE).assertIsDisplayed()
        composeRule.onNodeWithTag(PlusTags.MANAGE_CANCEL).assertIsDisplayed()
        composeRule.onNodeWithText("$5.000/mes desde la próxima renovación").assertIsDisplayed()
    }

    @Test
    fun activeMonthlyHasNoChangeToMonthly() {
        manage(loadedManage(PlusTestData.activeMonthly))
        composeRule.onNodeWithText("Plan mensual · $5.000").assertIsDisplayed()
        composeRule.onAllNodesWithTag(PlusTags.MANAGE_CHANGE).assertCountEquals(0)
        composeRule.onNodeWithTag(PlusTags.MANAGE_CANCEL).assertIsDisplayed()
    }

    @Test
    fun canceledKeepsPlusUntilTheEndAndOffersReactivation() {
        manage(loadedManage(PlusTestData.canceled))
        composeRule.onNodeWithText("Cancelada").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Termina el 21 de octubre de 2027\nPagas con Google Play"
        ).assertIsDisplayed()
        composeRule.onAllNodesWithTag(PlusTags.MANAGE_CANCEL).assertCountEquals(0)
        composeRule.onNodeWithText("Reactivar en Google Play").assertIsDisplayed()
    }

    @Test
    fun graceExplainsThePaymentProblem() {
        manage(loadedManage(PlusTestData.grace))
        composeRule.onNodeWithText("Pago pendiente").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Tienes ParcheU+ hasta el 21 de octubre de 2027 mientras Google Play reintenta el cobro"
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(PlusTags.MANAGE_FIX).assertIsDisplayed()
    }

    @Test
    fun inactiveOffersToSubscribeAndNoCancel() {
        var subscribed = false
        manage(
            loadedManage(PlusTestData.free),
            ManageSubscriptionActions(onSubscribe = { subscribed = true })
        )
        composeRule.onNodeWithText("No tienes ParcheU+").assertIsDisplayed()
        composeRule.onAllNodesWithTag(PlusTags.MANAGE_CANCEL).assertCountEquals(0)
        composeRule.onNodeWithTag(PlusTags.MANAGE_SUBSCRIBE).performClick()
        assertTrue(subscribed)
    }

    @Test
    fun cancelAsksForConfirmationThenOpensPlay() {
        var openedPlay = 0
        var dialog = false
        manage(
            loadedManage(PlusTestData.activeAnnual),
            ManageSubscriptionActions(onOpenPlay = { openedPlay++ }),
            dialog = true,
            onDialog = { dialog = it }
        )
        composeRule.onNodeWithTag(PlusTags.CANCEL_DIALOG, useUnmergedTree = true).assertExists()
        assertEquals(0, openedPlay)
        composeRule.onNodeWithTag(PlusTags.CANCEL_CONFIRM).performClick()
        assertEquals(1, openedPlay)
        assertEquals(false, dialog)
    }

    @Test
    fun cancelRowRequestsTheDialogAndDoesNotOpenPlayDirectly() {
        var openedPlay = 0
        var dialog = false
        manage(
            loadedManage(PlusTestData.activeAnnual),
            ManageSubscriptionActions(onOpenPlay = { openedPlay++ }),
            onDialog = { dialog = it }
        )
        composeRule.onNodeWithTag(PlusTags.MANAGE_CANCEL).performClick()
        assertTrue(dialog)
        assertEquals(0, openedPlay)
    }

    @Test
    fun restoreAndBackAreWired() {
        var restored = 0
        var back = 0
        manage(
            loadedManage(PlusTestData.activeAnnual),
            ManageSubscriptionActions(onRestore = { restored++ }, onBack = { back++ })
        )
        composeRule.onNodeWithTag(PlusTags.MANAGE_RESTORE).performClick()
        composeRule.onNodeWithContentDescription("Volver").performClick()
        assertEquals(listOf(1, 1), listOf(restored, back))
    }

    @Test
    fun loadingAndErrorStates() {
        manage(ManageUiState.Loading)
        composeRule.onNodeWithTag(PlusTags.MANAGE_LOADING).assertIsDisplayed()
    }

    @Test
    fun errorOffersRetry() {
        var retried = false
        manage(
            ManageUiState.Error(me.santiagoreina.parcheu.core.error.AppError.Network),
            ManageSubscriptionActions(onRetry = { retried = true })
        )
        composeRule.onNodeWithTag(PlusTags.MANAGE_ERROR).assertIsDisplayed()
        composeRule.onNodeWithText("Reintentar").performClick()
        assertTrue(retried)
    }
}
