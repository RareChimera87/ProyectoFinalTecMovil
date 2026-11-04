package me.santiagoreina.parcheu.data.billing

import android.app.Activity
import com.android.billingclient.api.BillingClient.BillingResponseCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.contract.EntitlementDto
import me.santiagoreina.parcheu.contract.EntitlementState
import me.santiagoreina.parcheu.contract.VerifyPurchase
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.data.billing.FakeBillingClient.Companion.purchase
import me.santiagoreina.parcheu.data.billing.FakeBillingClient.Companion.subscription
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response

/** E7.2: compra → verify → entitlement, cancelación, pendientes, restaurar y `obfuscatedAccountId`. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BillingRepositoryImplTest {
    private val userId = "3f0e8c1a-6b2d-4e9f-9a7c-1d2e3f4a5b6c"
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    private class Harness(
        val repository: BillingRepositoryImpl,
        val client: FakeBillingClient,
        val api: FakeBillingApi,
        val states: List<PurchaseState>
    )

    private fun TestScope.harness(
        client: FakeBillingClient = FakeBillingClient(productDetails = listOf(subscription())),
        api: FakeBillingApi = FakeBillingApi()
    ): Harness {
        val repository =
            BillingRepositoryImpl(
                provider = BillingClientProvider(client, backgroundScope, initialBackoffMillis = 1),
                client = client,
                api = api,
                errorMapper = ApiErrorMapper(),
                scope = backgroundScope
            )
        val states = mutableListOf<PurchaseState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.purchaseState.collect { states += it }
        }
        runCurrent()
        return Harness(repository, client, api, states)
    }

    @Test
    fun loadsBothPlansFromPlay() = runTest {
        val h = harness()

        val result = h.repository.loadPlans()

        assertTrue(result is PlansResult.Loaded)
        assertEquals(
            "$3.750",
            (result as PlansResult.Loaded).plans.formattedAnnualMonthlyEquivalent()
        )
    }

    @Test
    fun plansAreUnavailableWhenPlayHasNoProduct() = runTest {
        val h = harness(client = FakeBillingClient())

        assertEquals(PlansResult.ProductUnavailable, h.repository.loadPlans())
    }

    @Test
    fun plansReportBillingUnavailableWhenPlayDoesNotConnect() = runTest {
        val h =
            harness(
                client = FakeBillingClient(listOf(BillingResponseCode.BILLING_UNAVAILABLE))
            )

        assertEquals(PlansResult.BillingUnavailable, h.repository.loadPlans())
    }

    @Test
    fun launchesTheChosenPlanWithHashedAccountIdNeverTheRawUserId() = runTest {
        val h = harness()

        h.repository.purchase(activity, PlusProduct.BASE_PLAN_ANNUAL, userId)

        val launch = h.client.launches.single()
        assertEquals(PlusProduct.PRODUCT_ID, launch.productId)
        assertEquals("token-anual", launch.offerToken)
        assertEquals(
            "53d33fd873210cc1f92da498b6ac2ef40b5088984ad30370081d475f1614917f",
            launch.obfuscatedAccountId
        )
        assertFalse(launch.obfuscatedAccountId.contains(userId))
        assertEquals(PurchaseState.AwaitingPlay, h.repository.purchaseState.value)
    }

    @Test
    fun completedPurchaseIsVerifiedAndRefreshesEntitlement() = runTest {
        val h = harness()
        h.repository.purchase(activity, PlusProduct.BASE_PLAN_MONTHLY, userId)

        h.client.emitPurchasesUpdate(BillingResponseCode.OK, listOf(purchase("tok-1")))
        runCurrent()

        assertEquals(listOf(VerifyPurchase("tok-1", PlusProduct.PRODUCT_ID)), h.api.verifyCalls)
        assertEquals(PLUS_ACTIVE, h.repository.entitlement.value)
        assertEquals(PurchaseState.Completed(PLUS_ACTIVE), h.repository.purchaseState.value)
        assertTrue(PurchaseState.Verifying in h.states)
    }

    @Test
    fun userCancellationReturnsToIdleWithoutError() = runTest {
        val h = harness()
        h.repository.purchase(activity, PlusProduct.BASE_PLAN_ANNUAL, userId)

        h.client.emitPurchasesUpdate(BillingResponseCode.USER_CANCELED)
        runCurrent()

        assertEquals(PurchaseState.Idle, h.repository.purchaseState.value)
        assertTrue(h.states.none { it is PurchaseState.Failed })
        assertTrue(h.api.verifyCalls.isEmpty())
    }

    @Test
    fun pendingPurchaseShowsVerificationPendingAndIsNotVerifiedYet() = runTest {
        val h = harness()
        h.repository.purchase(activity, PlusProduct.BASE_PLAN_ANNUAL, userId)

        h.client.emitPurchasesUpdate(
            BillingResponseCode.OK,
            listOf(purchase("tok-p", pending = true))
        )
        runCurrent()

        assertEquals(PurchaseState.VerificationPending, h.repository.purchaseState.value)
        assertTrue(h.api.verifyCalls.isEmpty())
        assertTrue(h.states.none { it is PurchaseState.Failed })

        // Cuando Play confirma el pago (con la app abierta) llega otra actualización y se verifica.
        h.client.emitPurchasesUpdate(BillingResponseCode.OK, listOf(purchase("tok-p")))
        runCurrent()

        assertEquals(listOf("tok-p"), h.api.verifyCalls.map { it.purchaseToken })
        assertEquals(PurchaseState.Completed(PLUS_ACTIVE), h.repository.purchaseState.value)
    }

    @Test
    fun paidPurchaseThatCannotBeVerifiedOfflineStaysPending() = runTest {
        val h = harness(api = FakeBillingApi.offline())

        h.client.emitPurchasesUpdate(BillingResponseCode.OK, listOf(purchase("tok-1")))
        runCurrent()

        assertEquals(PurchaseState.VerificationPending, h.repository.purchaseState.value)
    }

    @Test
    fun verificationRejectedByTheApiFails() = runTest {
        val api =
            FakeBillingApi().apply {
                verifyFailure =
                    HttpException(
                        Response.error<Any>(
                            403,
                            """{"code":"FORBIDDEN","message":"otra cuenta"}""".toResponseBody()
                        )
                    )
            }
        val h = harness(api = api)

        h.client.emitPurchasesUpdate(BillingResponseCode.OK, listOf(purchase("tok-1")))
        runCurrent()

        assertEquals(
            PurchaseState.Failed(PurchaseFailure.Verification(AppError.Forbidden)),
            h.repository.purchaseState.value
        )
    }

    @Test
    fun playErrorFailsWithItsCode() = runTest {
        val h = harness()

        h.client.emitPurchasesUpdate(BillingResponseCode.ERROR)
        runCurrent()

        assertEquals(
            PurchaseState.Failed(PurchaseFailure.Play(BillingResponseCode.ERROR)),
            h.repository.purchaseState.value
        )
    }

    @Test
    fun sheetThatCannotOpenFailsImmediately() = runTest {
        val h = harness()
        h.client.launchResponse = BillingResponseCode.BILLING_UNAVAILABLE

        h.repository.purchase(activity, PlusProduct.BASE_PLAN_ANNUAL, userId)

        assertEquals(
            PurchaseState.Failed(PurchaseFailure.Play(BillingResponseCode.BILLING_UNAVAILABLE)),
            h.repository.purchaseState.value
        )
    }

    @Test
    fun purchaseWithoutProductDetailsFailsWithoutOpeningPlay() = runTest {
        val h = harness(client = FakeBillingClient())

        h.repository.purchase(activity, PlusProduct.BASE_PLAN_ANNUAL, userId)

        assertTrue(h.client.launches.isEmpty())
        assertEquals(
            PurchaseState.Failed(PurchaseFailure.BillingUnavailable),
            h.repository.purchaseState.value
        )
    }

    @Test
    fun restoreVerifiesEachActivePurchase() = runTest {
        val h = harness()
        h.client.activePurchases =
            listOf(
                purchase("tok-a"),
                purchase("tok-b", acknowledged = true),
                purchase("tok-pending", pending = true),
                purchase("tok-otro", productId = "otro_producto")
            )

        val result = h.repository.restorePurchases()

        assertEquals(
            listOf("tok-a", "tok-b"),
            h.api.verifyCalls.map { it.purchaseToken }
        )
        assertTrue(h.api.verifyCalls.all { it.productId == PlusProduct.PRODUCT_ID })
        assertEquals(RestoreResult.Restored(PLUS_ACTIVE, verifiedPurchases = 2), result)
        assertEquals(PLUS_ACTIVE, h.repository.entitlement.value)
        assertEquals(PurchaseState.VerificationPending, h.repository.purchaseState.value)
    }

    @Test
    fun restoreWithoutPurchasesRefreshesEntitlementFromTheApi() = runTest {
        val h = harness()

        val result = h.repository.restorePurchases()

        assertTrue(h.api.verifyCalls.isEmpty())
        assertEquals(1, h.api.entitlementCalls)
        assertEquals(RestoreResult.Restored(FREE, verifiedPurchases = 0), result)
        assertEquals(FREE, h.repository.entitlement.value)
    }

    @Test
    fun alreadyOwnedRestoresInsteadOfFailing() = runTest {
        val h = harness()
        h.client.activePurchases = listOf(purchase("tok-own"))

        h.client.emitPurchasesUpdate(BillingResponseCode.ITEM_ALREADY_OWNED)
        runCurrent()

        assertEquals(listOf("tok-own"), h.api.verifyCalls.map { it.purchaseToken })
        assertEquals(PurchaseState.Completed(PLUS_ACTIVE), h.repository.purchaseState.value)
    }

    @Test
    fun alreadyOwnedWithoutPlusFromTheServerStaysPending() = runTest {
        // Play dice ITEM_ALREADY_OWNED, la compra se verifica pero el servidor no da Plus.
        val h = harness(api = FakeBillingApi(verifyResponse = FREE))
        h.client.activePurchases = listOf(purchase("tok-own"))

        h.client.emitPurchasesUpdate(BillingResponseCode.ITEM_ALREADY_OWNED)
        runCurrent()

        assertEquals(listOf("tok-own"), h.api.verifyCalls.map { it.purchaseToken })
        assertEquals(PurchaseState.VerificationPending, h.repository.purchaseState.value)
        assertFalse(h.states.any { it is PurchaseState.Completed })
    }

    @Test
    fun alreadyOwnedWithOnlyAPendingPurchaseKeepsVerificationPending() = runTest {
        // Nada que verificar (la compra sigue PENDING) y el derecho del servidor es plus=false.
        val h = harness()
        h.client.activePurchases = listOf(purchase("tok-cash", pending = true))

        h.client.emitPurchasesUpdate(BillingResponseCode.ITEM_ALREADY_OWNED)
        runCurrent()

        assertTrue(h.api.verifyCalls.isEmpty())
        assertEquals(PurchaseState.VerificationPending, h.repository.purchaseState.value)
        assertFalse(h.states.any { it is PurchaseState.Completed })
        assertEquals(FREE, h.repository.entitlement.value)
    }

    @Test
    fun refreshEntitlementPublishesTheServerState() = runTest {
        val grace = EntitlementDto(plus = true, state = EntitlementState.IN_GRACE_PERIOD)
        val h = harness(api = FakeBillingApi(entitlementResponse = grace))

        assertEquals(ApiResult.Success(grace), h.repository.refreshEntitlement())
        assertEquals(grace, h.repository.entitlement.value)

        h.repository.clear()
        assertEquals(null, h.repository.entitlement.value)
    }

    @Test
    fun serverErrorOnVerifyKeepsAPaidPurchasePending() = runTest {
        val api =
            FakeBillingApi().apply {
                verifyFailure =
                    HttpException(Response.error<Any>(503, "".toResponseBody()))
            }
        val h = harness(api = api)

        h.client.emitPurchasesUpdate(BillingResponseCode.OK, listOf(purchase("tok-503")))
        runCurrent()

        assertEquals(PurchaseState.VerificationPending, h.repository.purchaseState.value)
    }

    @Test
    fun notImplementedVerifyIsPendingNeverSuccess() = runTest {
        // Backend sin /v1/billing/verify todavía: 501 es 'verificación pendiente', no éxito.
        val api =
            FakeBillingApi().apply {
                verifyFailure = HttpException(Response.error<Any>(501, "".toResponseBody()))
            }
        val h = harness(api = api)

        h.client.emitPurchasesUpdate(BillingResponseCode.OK, listOf(purchase("tok-501")))
        runCurrent()

        assertEquals(PurchaseState.VerificationPending, h.repository.purchaseState.value)
        assertEquals(null, h.repository.entitlement.value)
    }

    @Test
    fun validationRejectionIsDefinitive() = runTest {
        val api =
            FakeBillingApi().apply {
                verifyFailure =
                    HttpException(
                        Response.error<Any>(
                            400,
                            """{"code":"VALIDATION","message":"token inválido"}""".toResponseBody()
                        )
                    )
            }
        val h = harness(api = api)

        h.client.emitPurchasesUpdate(BillingResponseCode.OK, listOf(purchase("tok-1")))
        runCurrent()

        assertEquals(
            PurchaseState.Failed(
                PurchaseFailure.Verification(AppError.Validation("token inválido"))
            ),
            h.repository.purchaseState.value
        )
    }

    @Test
    fun syncVerifiesAPendingPurchaseResolvedWhileTheAppWasClosed() = runTest {
        val h = harness()
        // La compra era PENDING; Play la confirmó sin app abierta y ahora llega como PURCHASED.
        h.client.activePurchases = listOf(purchase("tok-cash"))

        h.repository.syncPurchases()

        assertEquals(listOf("tok-cash"), h.api.verifyCalls.map { it.purchaseToken })
        assertEquals(PLUS_ACTIVE, h.repository.entitlement.value)
        // Sin compra en curso, el estado de compra no se toca.
        assertEquals(PurchaseState.Idle, h.repository.purchaseState.value)
    }

    @Test
    fun syncRetriesAVerificationThatFailedOnTheNetwork() = runTest {
        val api = FakeBillingApi.offline()
        val h = harness(api = api)
        h.client.emitPurchasesUpdate(BillingResponseCode.OK, listOf(purchase("tok-net")))
        runCurrent()
        assertEquals(PurchaseState.VerificationPending, h.repository.purchaseState.value)
        h.client.activePurchases = listOf(purchase("tok-net"))
        api.verifyFailure = null

        h.repository.syncPurchases()

        assertEquals(listOf("tok-net", "tok-net"), h.api.verifyCalls.map { it.purchaseToken })
        assertEquals(PurchaseState.Completed(PLUS_ACTIVE), h.repository.purchaseState.value)
        assertEquals(PLUS_ACTIVE, h.repository.entitlement.value)
    }

    @Test
    fun alreadyOwnedWithServerErrorKeepsThePurchasePending() = runTest {
        val api =
            FakeBillingApi().apply {
                verifyFailure = HttpException(Response.error<Any>(503, "".toResponseBody()))
            }
        val h = harness(api = api)
        h.client.activePurchases = listOf(purchase("tok-own"))

        h.client.emitPurchasesUpdate(BillingResponseCode.ITEM_ALREADY_OWNED)
        runCurrent()

        assertEquals(PurchaseState.VerificationPending, h.repository.purchaseState.value)
    }

    @Test
    fun syncCompletesAPendingPurchaseTheServerAlreadyVerified() = runTest {
        val api = FakeBillingApi.offline()
        val h = harness(api = api)
        h.client.emitPurchasesUpdate(BillingResponseCode.OK, listOf(purchase("tok-lost")))
        runCurrent()
        assertEquals(PurchaseState.VerificationPending, h.repository.purchaseState.value)
        // El servidor sí la reconoció; Play ya la reporta reconocida.
        h.client.activePurchases = listOf(purchase("tok-lost", acknowledged = true))
        api.verifyFailure = null
        api.entitlementResponse = PLUS_ACTIVE

        h.repository.syncPurchases()

        assertEquals(PurchaseState.Completed(PLUS_ACTIVE), h.repository.purchaseState.value)
    }

    @Test
    fun syncSkipsAcknowledgedPendingAndOtherProductPurchases() = runTest {
        val h = harness()
        h.client.activePurchases =
            listOf(
                purchase("tok-ack", acknowledged = true),
                purchase("tok-pending", pending = true),
                purchase("tok-otro", productId = "otro_producto")
            )

        h.repository.syncPurchases()

        assertTrue(h.api.verifyCalls.isEmpty())
        // Nada por reconocer: solo se refresca el derecho desde el servidor.
        assertEquals(1, h.api.entitlementCalls)
        assertEquals(PurchaseState.Idle, h.repository.purchaseState.value)
    }

    @Test
    fun syncIsSilentWhenPlayIsUnavailableOrFailsAndWhenVerifyFails() = runTest {
        val unavailable =
            harness(client = FakeBillingClient(listOf(BillingResponseCode.BILLING_UNAVAILABLE)))
        unavailable.repository.syncPurchases()
        assertEquals(0, unavailable.client.purchasesQueryCalls)

        val queryFails = harness()
        queryFails.client.purchasesQueryResponse = BillingResponseCode.ERROR
        queryFails.repository.syncPurchases()
        assertTrue(queryFails.api.verifyCalls.isEmpty())

        val offline = harness(api = FakeBillingApi.offline())
        offline.client.activePurchases = listOf(purchase("tok-1"))
        offline.repository.syncPurchases()
        assertEquals(PurchaseState.Idle, offline.repository.purchaseState.value)
    }
}
