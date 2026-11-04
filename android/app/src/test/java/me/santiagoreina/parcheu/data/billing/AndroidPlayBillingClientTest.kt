package me.santiagoreina.parcheu.data.billing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.android.billingclient.api.BillingClient.BillingResponseCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regresión de E7.0: con el cliente real, reconectar tras `endConnection()` no debe responder
 * SERVICE_DISCONNECTED (el BillingClient de Play queda CLOSED y no se puede reutilizar).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidPlayBillingClientTest {
    @Test
    fun startConnectionAfterEndConnectionUsesFreshClient() = runBlocking {
        val client =
            AndroidPlayBillingClient(ApplicationProvider.getApplicationContext<Context>())
        client.startConnection {}
        client.endConnection()

        val result = client.startConnection {}

        assertNotEquals(BillingResponseCode.SERVICE_DISCONNECTED, result.responseCode)
    }
}
