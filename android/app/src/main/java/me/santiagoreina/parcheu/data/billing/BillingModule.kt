package me.santiagoreina.parcheu.data.billing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import me.santiagoreina.parcheu.data.auth.AuthRepository
import me.santiagoreina.parcheu.data.auth.SessionCleaner
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit

/**
 * Billing compartido por paywall, compra y gestión de la suscripción (E7.2-E7.4). El `BillingClient` no se
 * conecta hasta que una pantalla o el [BillingReconciler] (con sesión, al primer plano) lo pidan.
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
        single<BillingApi> { get<Retrofit>().create(BillingApi::class.java) }
        single<BillingRepository> {
            BillingRepositoryImpl(
                provider = get(),
                client = get(),
                api = get(),
                errorMapper = get(),
                // Main: el listener de Play y launchBillingFlow viven en el hilo principal.
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            )
        }
        // Cerrar sesión (E8.2) olvida el derecho a ParcheU+ para que no pase a la siguiente cuenta del proceso.
        single<SessionCleaner>(named("billing-entitlement")) {
            val repository = get<BillingRepository>()
            SessionCleaner { repository.clear() }
        }
        single {
            BillingReconciler(
                repository = get(),
                hasSession = { get<AuthRepository>().hasSession() },
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            )
        }
    }
