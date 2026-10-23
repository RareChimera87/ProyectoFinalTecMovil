package me.santiagoreina.parcheu.data.billing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Billing compartido por paywall, compra y gestión de la suscripción (E7.2-E7.4). `single` es perezoso:
 * no se crea ni conecta el `BillingClient` hasta que una pantalla lo pida.
 */
val billingModule =
    module {
        single<PlayBillingClient> { AndroidPlayBillingClient(androidContext()) }
        single {
            BillingClientProvider(
                client = get(),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            )
        }
    }
