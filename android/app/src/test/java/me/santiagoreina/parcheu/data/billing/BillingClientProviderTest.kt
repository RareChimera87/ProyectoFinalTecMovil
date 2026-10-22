package me.santiagoreina.parcheu.data.billing

import com.android.billingclient.api.BillingClient.BillingResponseCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** E7.0: conexión con backoff exponencial (máx. 3 reintentos) y `ProductDetails` de `parcheu_plus`. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BillingClientProviderTest {
    private fun TestScope.provider(client: FakeBillingClient) = BillingClientProvider(
        client = client,
        scope = backgroundScope,
        initialBackoffMillis = 1_000
    )

    @Test
    fun connectsOnFirstAttemptWithoutWaiting() = runTest {
        val client = FakeBillingClient()
        val provider = provider(client)

        assertTrue(provider.connect())
        assertEquals(1, client.connectionAttempts)
        assertEquals(0L, currentTime)
        assertEquals(BillingConnectionState.Connected, provider.connectionState.value)
    }

    @Test
    fun retriesWithExponentialBackoffUntilConnected() = runTest {
        val client =
            FakeBillingClient(
                connectionResponses =
                listOf(
                    BillingResponseCode.SERVICE_UNAVAILABLE,
                    BillingResponseCode.NETWORK_ERROR,
                    BillingResponseCode.OK
                )
            )
        val provider = provider(client)

        assertTrue(provider.connect())
        assertEquals(3, client.connectionAttempts)
        // Esperas de 1 s y 2 s antes del segundo y del tercer intento.
        assertEquals(3_000L, currentTime)
        assertEquals(BillingConnectionState.Connected, provider.connectionState.value)
    }

    @Test
    fun givesUpAfterThreeRetries() = runTest {
        val client =
            FakeBillingClient(connectionResponses = listOf(BillingResponseCode.SERVICE_UNAVAILABLE))
        val provider = provider(client)

        assertFalse(provider.connect())
        // Un intento + 3 reintentos, con esperas de 1, 2 y 4 s.
        assertEquals(4, client.connectionAttempts)
        assertEquals(7_000L, currentTime)
        assertEquals(
            BillingConnectionState.Unavailable(BillingResponseCode.SERVICE_UNAVAILABLE),
            provider.connectionState.value
        )
    }

    @Test
    fun doesNotRetryNonTransientErrors() = runTest {
        val client =
            FakeBillingClient(connectionResponses = listOf(BillingResponseCode.BILLING_UNAVAILABLE))
        val provider = provider(client)

        assertFalse(provider.connect())
        assertEquals(1, client.connectionAttempts)
        assertEquals(0L, currentTime)
        assertEquals(
            BillingConnectionState.Unavailable(BillingResponseCode.BILLING_UNAVAILABLE),
            provider.connectionState.value
        )
    }

    @Test
    fun reconnectsWhenPlayDropsTheService() = runTest {
        val client = FakeBillingClient()
        val provider = provider(client)
        provider.connect()

        client.simulateServiceDisconnected()
        // La reconexión corre en backgroundScope, que advanceUntilIdle no espera.
        runCurrent()

        assertEquals(2, client.connectionAttempts)
        assertEquals(BillingConnectionState.Connected, provider.connectionState.value)
    }

    @Test
    fun exposesPlusProductDetailsWhenTheyExist() = runTest {
        val client =
            FakeBillingClient(
                productDetails = listOf(
                    FakeBillingClient.subscription(),
                    FakeBillingClient.subscription("otro")
                )
            )
        val provider = provider(client)

        val details = provider.refreshPlusProductDetails()

        assertEquals(PlusProduct.PRODUCT_ID, details?.productId)
        assertEquals(details, provider.plusProductDetails.value)
        val basePlans = details?.subscriptionOfferDetails?.map { it.basePlanId }
        assertEquals(listOf(PlusProduct.BASE_PLAN_MONTHLY, PlusProduct.BASE_PLAN_ANNUAL), basePlans)
        val annual = details?.subscriptionOfferDetails?.first {
            it.basePlanId ==
                PlusProduct.BASE_PLAN_ANNUAL
        }
        assertEquals("P1Y", annual?.pricingPhases?.pricingPhaseList?.single()?.billingPeriod)
        assertEquals(
            45_000_000_000L,
            annual?.pricingPhases?.pricingPhaseList?.single()?.priceAmountMicros
        )
    }

    @Test
    fun exposesNullWhenPlusDoesNotExistYet() = runTest {
        val client = FakeBillingClient(productDetails = emptyList())
        val provider = provider(client)

        assertNull(provider.refreshPlusProductDetails())
        assertNull(provider.plusProductDetails.value)
        assertEquals(1, client.queryCalls)
    }

    @Test
    fun reconnectsOnceWhenQueryFindsTheServiceDisconnected() = runTest {
        val client =
            FakeBillingClient(
                productDetails = listOf(FakeBillingClient.subscription()),
                queryResponses = mutableListOf(BillingResponseCode.SERVICE_DISCONNECTED)
            )
        val provider = provider(client)

        val details = provider.refreshPlusProductDetails()

        assertEquals(PlusProduct.PRODUCT_ID, details?.productId)
        assertEquals(2, client.connectionAttempts)
        assertEquals(2, client.queryCalls)
    }

    @Test
    fun doesNotQueryWhenItCannotConnect() = runTest {
        val client =
            FakeBillingClient(
                connectionResponses = listOf(BillingResponseCode.BILLING_UNAVAILABLE),
                productDetails = listOf(FakeBillingClient.subscription())
            )
        val provider = provider(client)

        assertNull(provider.refreshPlusProductDetails())
        assertEquals(0, client.queryCalls)
    }

    @Test
    fun disconnectEndsTheConnection() = runTest {
        val client = FakeBillingClient()
        val provider = provider(client)
        provider.connect()

        provider.disconnect()

        assertEquals(1, client.endConnectionCalls)
        assertEquals(BillingConnectionState.Disconnected, provider.connectionState.value)
    }
}
