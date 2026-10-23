package me.santiagoreina.parcheu.data.billing

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.santiagoreina.parcheu.contract.EntitlementDto
import me.santiagoreina.parcheu.core.network.ApiResult

/**
 * [BillingRepository] en memoria para pruebas de ViewModels y capturas de G1-G4 (E7.3). Los estados se
 * fijan a mano con [entitlementFlow], [purchaseStateFlow], [plansResult] y [restoreResult]; [calls]
 * registra el orden ("plans", "purchase:<plan>:<accountId>", "restore", "entitlement", "clear").
 */
class FakeBillingRepository(
    var plansResult: PlansResult = PlansResult.BillingUnavailable,
    var restoreResult: RestoreResult = RestoreResult.Restored(FREE, verifiedPurchases = 0),
    var entitlementResult: ApiResult<EntitlementDto> = ApiResult.Success(FREE),
    /** Estado al que pasa [purchaseState] tras [purchase] (simula el desenlace de Play + verify). */
    var purchaseOutcome: PurchaseState = PurchaseState.Idle
) : BillingRepository {
    val entitlementFlow = MutableStateFlow<EntitlementDto?>(null)
    val purchaseStateFlow = MutableStateFlow<PurchaseState>(PurchaseState.Idle)
    override val entitlement: StateFlow<EntitlementDto?> = entitlementFlow
    override val purchaseState: StateFlow<PurchaseState> = purchaseStateFlow

    val calls = mutableListOf<String>()

    override suspend fun loadPlans(): PlansResult {
        calls += "plans"
        return plansResult
    }

    override suspend fun purchase(activity: Activity, basePlanId: String, userId: String) {
        calls += "purchase:$basePlanId:${obfuscatedAccountId(userId)}"
        purchaseStateFlow.value = purchaseOutcome
        (purchaseOutcome as? PurchaseState.Completed)?.let {
            entitlementFlow.value = it.entitlement
        }
    }

    override suspend fun restorePurchases(): RestoreResult {
        calls += "restore"
        (restoreResult as? RestoreResult.Restored)?.let { entitlementFlow.value = it.entitlement }
        return restoreResult
    }

    override suspend fun syncPurchases() {
        calls += "sync"
    }

    override suspend fun refreshEntitlement(): ApiResult<EntitlementDto> {
        calls += "entitlement"
        (entitlementResult as? ApiResult.Success)?.let { entitlementFlow.value = it.value }
        return entitlementResult
    }

    override fun acknowledgePurchaseState() {
        purchaseStateFlow.value = PurchaseState.Idle
    }

    override fun clear() {
        calls += "clear"
        entitlementFlow.value = null
        purchaseStateFlow.value = PurchaseState.Idle
    }
}
