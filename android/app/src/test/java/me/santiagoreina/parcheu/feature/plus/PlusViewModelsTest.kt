package me.santiagoreina.parcheu.feature.plus

import android.app.Activity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.contract.EntitlementState
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.data.billing.FakeBillingRepository
import me.santiagoreina.parcheu.data.billing.PlansResult
import me.santiagoreina.parcheu.data.billing.PurchaseFailure
import me.santiagoreina.parcheu.data.billing.PurchaseState
import me.santiagoreina.parcheu.data.billing.RestoreResult
import me.santiagoreina.parcheu.data.billing.obfuscatedAccountId
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlusViewModelsTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).get()

    private fun paywall(billing: FakeBillingRepository, userId: String? = "user-1") =
        PaywallViewModel(billing) { userId }

    private fun loadedBilling(outcome: PurchaseState = PurchaseState.Idle) = FakeBillingRepository(
        plansResult = PlansResult.Loaded(PlusTestData.plans),
        purchaseOutcome = outcome
    )

    // ---- G1

    @Test
    fun annualIsSelectedByDefaultAndPlansLoad() = runTest {
        val vm = paywall(loadedBilling())
        val state = vm.state.value
        assertEquals("anual", state.selected)
        assertEquals(PlansUi.Loaded(PlusTestData.plans), state.plans)
    }

    @Test
    fun unavailablePlansShowAnErrorAndRetryReloads() = runTest {
        val billing = FakeBillingRepository(plansResult = PlansResult.BillingUnavailable)
        val vm = paywall(billing)
        assertEquals(PlansUi.Error(billingUnavailable = true), vm.state.value.plans)
        billing.plansResult = PlansResult.Loaded(PlusTestData.plans)
        vm.loadPlans()
        assertTrue(vm.state.value.plans is PlansUi.Loaded)
        billing.plansResult = PlansResult.ProductUnavailable
        vm.loadPlans()
        assertEquals(PlansUi.Error(billingUnavailable = false), vm.state.value.plans)
    }

    @Test
    fun subscribeBuysTheSelectedPlanWithTheHashedAccountId() = runTest {
        val billing = loadedBilling()
        val vm = paywall(billing)
        vm.onSelect("mensual")
        vm.onSubscribe(activity)
        assertTrue(
            "purchase:mensual:${obfuscatedAccountId("user-1")}" in billing.calls
        )
    }

    @Test
    fun subscribeWithoutAccountShowsAMessageAndNeverBuys() = runTest {
        val billing = loadedBilling()
        val vm = paywall(billing, userId = null)
        vm.onSubscribe(activity)
        assertEquals(PlusMessage.NoAccount, vm.state.value.message)
        assertFalse(vm.state.value.purchasing)
        assertTrue(billing.calls.none { it.startsWith("purchase") })
    }

    @Test
    fun cancelingInPlayShowsNoError() = runTest {
        // Play abre su hoja (AwaitingPlay) y el usuario la cierra: vuelve a Idle sin pasar por Failed.
        val billing = loadedBilling(PurchaseState.AwaitingPlay)
        val vm = paywall(billing)
        vm.onSubscribe(activity)
        assertTrue(vm.state.value.purchasing)
        billing.purchaseStateFlow.value = PurchaseState.Idle
        assertNull(vm.state.value.message)
        assertFalse(vm.state.value.purchasing)
    }

    @Test
    fun pendingPurchaseOpensG3AsVerificationPending() = runTest {
        val billing = loadedBilling(PurchaseState.VerificationPending)
        val vm = paywall(billing)
        vm.onSubscribe(activity)
        assertEquals(PaywallEffect.OpenSuccess(PLAN_PENDING), vm.effects.first())
    }

    @Test
    fun completedPurchaseOpensG3WithThePlanOfTheEntitlement() = runTest {
        val billing = loadedBilling(PurchaseState.Completed(PlusTestData.activeMonthly))
        val vm = paywall(billing)
        vm.onSelect("anual")
        vm.onSubscribe(activity)
        assertEquals(PaywallEffect.OpenSuccess("mensual"), vm.effects.first())
    }

    @Test
    fun failedPurchaseShowsAMessageAndAllowsRetry() = runTest {
        val failure = PurchaseFailure.Play(responseCode = 6)
        val billing = loadedBilling(PurchaseState.Failed(failure))
        val vm = paywall(billing)
        vm.onSubscribe(activity)
        assertEquals(PlusMessage.PurchaseFailed(failure), vm.state.value.message)
        assertFalse(vm.state.value.purchasing)
        assertEquals(PurchaseState.Idle, billing.purchaseState.value)
        vm.onDismissMessage()
        assertNull(vm.state.value.message)
    }

    @Test
    fun staleOutcomeDoesNotOpenG3OnEntry() = runTest {
        val billing = loadedBilling()
        billing.purchaseStateFlow.value = PurchaseState.Completed(PlusTestData.activeAnnual)
        val vm = paywall(billing)
        assertEquals(PurchaseState.Idle, billing.purchaseState.value)
        assertFalse(vm.state.value.purchasing)
    }

    @Test
    fun alreadyPlusOpensManageInsteadOfSelling() = runTest {
        val billing = loadedBilling()
        billing.entitlementResult = ApiResult.Success(PlusTestData.activeAnnual)
        val vm = paywall(billing)
        assertEquals(PaywallEffect.OpenManage, vm.effects.first())
    }

    @Test
    fun restoreWithActivePurchaseOpensManage() = runTest {
        val billing = loadedBilling()
        billing.restoreResult =
            RestoreResult.Restored(PlusTestData.activeAnnual, verifiedPurchases = 1)
        val vm = paywall(billing)
        vm.onRestore()
        assertEquals(PaywallEffect.OpenManage, vm.effects.first())
        assertTrue("restore" in billing.calls)
    }

    @Test
    fun restoreWithoutPurchasesSaysSoHonestly() = runTest {
        val billing = loadedBilling()
        val vm = paywall(billing)
        vm.onRestore()
        assertEquals(PlusMessage.NothingToRestore, vm.state.value.message)
        assertFalse(vm.state.value.restoring)
    }

    @Test
    fun restoreFailureShowsAMessage() = runTest {
        val billing = loadedBilling()
        billing.restoreResult = RestoreResult.Failed(PurchaseFailure.BillingUnavailable)
        val vm = paywall(billing)
        vm.onRestore()
        assertEquals(
            PlusMessage.RestoreFailed(PurchaseFailure.BillingUnavailable),
            vm.state.value.message
        )
    }

    // ---- G3

    @Test
    fun successShowsPlanPriceAndNextChargeFromTheApi() = runTest {
        val billing = loadedBilling()
        billing.entitlementFlow.value = PlusTestData.activeAnnual
        billing.purchaseStateFlow.value = PurchaseState.Completed(PlusTestData.activeAnnual)
        val vm = PurchaseSuccessViewModel("anual", billing)
        val state = vm.state.value
        assertEquals("$45.000", state.price)
        assertEquals("21 de octubre de 2027", state.nextCharge)
        assertTrue(state.annual)
        assertFalse(state.pending)
        assertEquals(PurchaseState.Idle, billing.purchaseState.value)
    }

    @Test
    fun successWithoutPlayPricesStillWorks() = runTest {
        val billing = FakeBillingRepository(plansResult = PlansResult.BillingUnavailable)
        billing.entitlementResult = ApiResult.Success(PlusTestData.activeMonthly)
        val vm = PurchaseSuccessViewModel("mensual", billing)
        assertNull(vm.state.value.price)
        assertEquals("21 de octubre de 2027", vm.state.value.nextCharge)
    }

    @Test
    fun pendingSuccessDoesNotPromiseADate() = runTest {
        val billing = loadedBilling()
        val vm = PurchaseSuccessViewModel(PLAN_PENDING, billing)
        assertTrue(vm.state.value.pending)
        assertNull(vm.state.value.nextCharge)
        assertTrue(billing.calls.none { it == "entitlement" || it == "plans" })
    }

    // ---- G4

    @Test
    fun manageLoadsEntitlementAndPrices() = runTest {
        val billing = loadedBilling()
        billing.entitlementResult = ApiResult.Success(PlusTestData.activeAnnual)
        val vm = ManageSubscriptionViewModel(billing)
        vm.load()
        val state = vm.state.value as ManageUiState.Loaded
        assertEquals(PlusTestData.activeAnnual, state.entitlement)
        assertEquals(PlusTestData.plans, state.plans)
    }

    @Test
    fun manageWithoutPlayStillShowsTheSubscription() = runTest {
        val billing = FakeBillingRepository(plansResult = PlansResult.BillingUnavailable)
        billing.entitlementResult = ApiResult.Success(PlusTestData.canceled)
        val vm = ManageSubscriptionViewModel(billing)
        vm.load()
        val state = vm.state.value as ManageUiState.Loaded
        assertNull(state.plans)
        assertEquals(EntitlementState.CANCELED, state.entitlement.state)
    }

    @Test
    fun manageErrorOnFirstLoadAndRetry() = runTest {
        val billing = loadedBilling()
        billing.entitlementResult = ApiResult.Failure(AppError.Network)
        val vm = ManageSubscriptionViewModel(billing)
        vm.load()
        assertEquals(ManageUiState.Error(AppError.Network), vm.state.value)
        billing.entitlementResult = ApiResult.Success(PlusTestData.free)
        vm.load()
        assertTrue(vm.state.value is ManageUiState.Loaded)
    }

    @Test
    fun manageRestoreCallsVerifyThroughTheRepositoryAndUpdatesTheEntitlement() = runTest {
        val billing = loadedBilling()
        billing.entitlementResult = ApiResult.Success(PlusTestData.free)
        billing.restoreResult =
            RestoreResult.Restored(PlusTestData.activeAnnual, verifiedPurchases = 1)
        val vm = ManageSubscriptionViewModel(billing)
        vm.load()
        vm.onRestore()
        val state = vm.state.value as ManageUiState.Loaded
        assertEquals(PlusTestData.activeAnnual, state.entitlement)
        assertEquals(PlusMessage.Restored, state.message)
        assertFalse(state.restoring)
        assertTrue("restore" in billing.calls)
    }

    @Test
    fun manageRestoreWithNothingToRestoreSaysSo() = runTest {
        val billing = loadedBilling()
        billing.entitlementResult = ApiResult.Success(PlusTestData.free)
        val vm = ManageSubscriptionViewModel(billing)
        vm.load()
        vm.onRestore()
        assertEquals(PlusMessage.NothingToRestore, (vm.state.value as ManageUiState.Loaded).message)
        vm.onDismissMessage()
        assertNull((vm.state.value as ManageUiState.Loaded).message)
    }
}
