package me.santiagoreina.parcheu.data.billing

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import me.santiagoreina.parcheu.ParcheUApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** El módulo de billing está en los módulos de la app y crea el cliente real de Play sin conectarlo. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BillingModuleTest {
    @Test
    fun resolvesASingleProviderBackedByPlayBilling() {
        ApplicationProvider.getApplicationContext<ParcheUApplication>()
        val koin = GlobalContext.get()

        val provider = koin.get<BillingClientProvider>()

        assertSame(provider, koin.get<BillingClientProvider>())
        assertTrue(koin.get<PlayBillingClient>() is AndroidPlayBillingClient)
        assertEquals(BillingConnectionState.Disconnected, provider.connectionState.value)
    }

    @Test
    fun resolvesASingleRepositoryWithoutConnecting() {
        ApplicationProvider.getApplicationContext<ParcheUApplication>()
        val koin = GlobalContext.get()

        val repository = koin.get<BillingRepository>()

        assertSame(repository, koin.get<BillingRepository>())
        assertTrue(repository is BillingRepositoryImpl)
        assertEquals(
            BillingConnectionState.Disconnected,
            koin.get<BillingClientProvider>().connectionState.value
        )
    }

    @Test
    fun closingKoinCancelsTheBillingScopes() {
        val app = koinApplication { modules(billingModule) }
        val names =
            listOf("billing-provider-scope", "billing-repository-scope", "billing-reconciler-scope")
        val scopes = names.map { app.koin.get<CoroutineScope>(named(it)) }
        assertTrue(scopes.all { it.isActive })

        app.close()

        assertTrue(scopes.none { it.isActive })
    }
}
