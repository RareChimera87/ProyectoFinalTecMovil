package me.santiagoreina.parcheu.data.billing

import android.app.Activity
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.Purchase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.santiagoreina.parcheu.contract.EntitlementDto
import me.santiagoreina.parcheu.contract.VerifyPurchase
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.core.network.apiCall

/**
 * [BillingRepository] real: [BillingClientProvider] (E7.0) para conectar y consultar, [PlayBillingClient]
 * para comprar y restaurar, y [BillingApi] para verificar. Escucha `onPurchasesUpdated` en [scope] desde
 * que se crea, así que una compra pendiente que Play resuelve con la app abierta también se verifica.
 */
class BillingRepositoryImpl(
    private val provider: BillingClientProvider,
    private val client: PlayBillingClient,
    private val api: BillingApi,
    private val errorMapper: ApiErrorMapper,
    scope: CoroutineScope
) : BillingRepository {
    private val _entitlement = MutableStateFlow<EntitlementDto?>(null)
    override val entitlement: StateFlow<EntitlementDto?> = _entitlement.asStateFlow()

    private val _purchaseState = MutableStateFlow<PurchaseState>(PurchaseState.Idle)
    override val purchaseState: StateFlow<PurchaseState> = _purchaseState.asStateFlow()

    /** Serializa las verificaciones (el listener y "restaurar" pueden coincidir). */
    private val verifyMutex = Mutex()

    init {
        scope.launch { client.purchaseUpdates.collect { onPurchasesUpdated(it) } }
    }

    override suspend fun loadPlans(): PlansResult {
        if (!provider.connect()) return PlansResult.BillingUnavailable
        val details = provider.refreshPlusProductDetails() ?: return PlansResult.ProductUnavailable
        val plans = PlusPlans.from(details) ?: return PlansResult.ProductUnavailable
        return PlansResult.Loaded(plans)
    }

    override suspend fun purchase(activity: Activity, basePlanId: String, userId: String) {
        val accountId = obfuscatedAccountId(userId)
        if (!provider.connect()) return fail(PurchaseFailure.BillingUnavailable)
        val details =
            provider.plusProductDetails.value
                ?: provider.refreshPlusProductDetails()
                ?: return fail(PurchaseFailure.BillingUnavailable)
        val plan =
            PlusPlans.from(details)?.plan(basePlanId)
                ?: return fail(PurchaseFailure.BillingUnavailable)
        _purchaseState.value = PurchaseState.AwaitingPlay
        val launch = client.launchSubscriptionFlow(activity, details, plan.offerToken, accountId)
        // Si la hoja abrió, el desenlace llega por onPurchasesUpdated; si no, se trata aquí mismo.
        if (launch.responseCode != BillingResponseCode.OK) {
            handleResponse(launch.responseCode, emptyList())
        }
    }

    override suspend fun restorePurchases(): RestoreResult {
        if (!provider.connect()) return RestoreResult.Failed(PurchaseFailure.BillingUnavailable)
        val result = client.querySubscriptionPurchases()
        val code = result.billingResult.responseCode
        if (code != BillingResponseCode.OK) return RestoreResult.Failed(PurchaseFailure.Play(code))
        val plus = result.purchasesList.filter { PlusProduct.PRODUCT_ID in it.products }
        var verified = 0
        var latest: EntitlementDto? = null
        for (purchase in plus.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }) {
            when (val outcome = verify(purchase)) {
                is ApiResult.Success -> {
                    verified++
                    latest = outcome.value
                }

                is ApiResult.Failure ->
                    return RestoreResult.Failed(PurchaseFailure.Verification(outcome.error))
            }
        }
        if (plus.any { it.purchaseState == Purchase.PurchaseState.PENDING }) {
            _purchaseState.value = PurchaseState.VerificationPending
        }
        val entitlement =
            latest ?: when (val fresh = refreshEntitlement()) {
                is ApiResult.Success -> fresh.value
                is ApiResult.Failure ->
                    return RestoreResult.Failed(PurchaseFailure.Verification(fresh.error))
            }
        return RestoreResult.Restored(entitlement, verified)
    }

    override suspend fun refreshEntitlement(): ApiResult<EntitlementDto> =
        apiCall(errorMapper) { api.entitlement() }.also {
            if (it is ApiResult.Success) _entitlement.value = it.value
        }

    override fun acknowledgePurchaseState() {
        _purchaseState.value = PurchaseState.Idle
    }

    override fun clear() {
        _entitlement.value = null
        _purchaseState.value = PurchaseState.Idle
    }

    private suspend fun onPurchasesUpdated(update: PurchasesUpdate) {
        handleResponse(update.billingResult.responseCode, update.purchases)
    }

    private suspend fun handleResponse(code: Int, purchases: List<Purchase>) {
        when (code) {
            BillingResponseCode.OK -> handlePurchases(purchases)
            // Cerrar la hoja de Play no es un error: se vuelve al paywall sin mensaje.
            BillingResponseCode.USER_CANCELED -> _purchaseState.value = PurchaseState.Idle
            // Ya la tiene (p. ej. comprada en otro dispositivo o sin verificar): se restaura.
            BillingResponseCode.ITEM_ALREADY_OWNED ->
                when (val restored = restorePurchases()) {
                    is RestoreResult.Restored ->
                        _purchaseState.value = PurchaseState.Completed(restored.entitlement)
                    is RestoreResult.Failed -> fail(restored.failure)
                }

            else -> fail(PurchaseFailure.Play(code))
        }
    }

    private suspend fun handlePurchases(purchases: List<Purchase>) {
        val plus = purchases.filter { PlusProduct.PRODUCT_ID in it.products }
        if (plus.isEmpty()) {
            _purchaseState.value = PurchaseState.Idle
            return
        }
        for (purchase in plus) {
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> verifyAndPublish(purchase)
                Purchase.PurchaseState.PENDING ->
                    _purchaseState.value = PurchaseState.VerificationPending
                else -> Unit
            }
        }
    }

    private suspend fun verifyAndPublish(purchase: Purchase) {
        _purchaseState.value = PurchaseState.Verifying
        _purchaseState.value =
            when (val outcome = verify(purchase)) {
                is ApiResult.Success -> PurchaseState.Completed(outcome.value)
                // Pagada pero sin verificar por red: no es un error para la persona; se reintenta al
                // restaurar (Play la reembolsa a los 3 días si nadie la reconoce, ADR-0013).
                is ApiResult.Failure ->
                    if (outcome.error == AppError.Network) {
                        PurchaseState.VerificationPending
                    } else {
                        PurchaseState.Failed(PurchaseFailure.Verification(outcome.error))
                    }
            }
    }

    /** `POST /v1/billing/verify`; si responde, el derecho devuelto pasa a ser el vigente. */
    private suspend fun verify(purchase: Purchase): ApiResult<EntitlementDto> =
        verifyMutex.withLock {
            apiCall(errorMapper) {
                api.verify(VerifyPurchase(purchase.purchaseToken, PlusProduct.PRODUCT_ID))
            }.also { if (it is ApiResult.Success) _entitlement.value = it.value }
        }

    private fun fail(failure: PurchaseFailure) {
        _purchaseState.value = PurchaseState.Failed(failure)
    }
}
