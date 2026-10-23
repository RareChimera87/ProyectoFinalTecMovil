package me.santiagoreina.parcheu.data.billing

import com.android.billingclient.api.BillingResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SetupCoalescerTest {
    private val coalescer = SetupCoalescer()
    private var starts = 0

    private fun start() = CompletableDeferred<BillingResult>().also { starts++ }

    @Test
    fun cancelledCallerKeepsSetupAndNextCallerReusesIt() = runBlocking {
        val first =
            async(start = CoroutineStart.UNDISPATCHED) { coalescer.getOrStart(::start).await() }
        first.cancel()
        val setup = coalescer.getOrStart(::start)
        setup.complete(FakeBillingClient.billingResult(0))

        assertEquals(1, starts)
        assertEquals(0, setup.await().responseCode)
    }

    @Test
    fun startsNewSetupOnceTheLastOneFinished() {
        val first = coalescer.getOrStart(::start)
        first.complete(FakeBillingClient.billingResult(0))
        val second = coalescer.getOrStart(::start)

        assertEquals(2, starts)
        assert(first !== second)
        assertSame(second, coalescer.getOrStart(::start))
    }
}
