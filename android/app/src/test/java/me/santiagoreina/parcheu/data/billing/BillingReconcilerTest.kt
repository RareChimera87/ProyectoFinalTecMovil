package me.santiagoreina.parcheu.data.billing

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** E7.2: la conciliación al primer plano solo corre con sesión. */
@OptIn(ExperimentalCoroutinesApi::class)
class BillingReconcilerTest {
    private val owner =
        object : LifecycleOwner {
            override val lifecycle: Lifecycle = LifecycleRegistry.createUnsafe(this)
        }

    @Test
    fun syncsWhenTheAppReturnsToForegroundWithASession() = runTest {
        val repository = FakeBillingRepository()
        val reconciler = BillingReconciler(repository, hasSession = { true }, scope = this)

        reconciler.onStart(owner)
        advanceUntilIdle()

        assertEquals(listOf("sync"), repository.calls)
    }

    @Test
    fun doesNothingWithoutASession() = runTest {
        val repository = FakeBillingRepository()
        val reconciler = BillingReconciler(repository, hasSession = { false }, scope = this)

        reconciler.onStart(owner)
        advanceUntilIdle()

        assertNull(reconciler.reconcile())
        assertEquals(emptyList<String>(), repository.calls)
    }
}
