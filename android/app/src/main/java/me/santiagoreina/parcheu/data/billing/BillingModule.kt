package me.santiagoreina.parcheu.data.billing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import me.santiagoreina.parcheu.data.auth.AuthRepository
import me.santiagoreina.parcheu.data.auth.SessionCleaner
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.dsl.onClose
import retrofit2.Retrofit

/**
 * Billing compartido por paywall, compra y gestión de la suscripción (E7.2-E7.4). El `BillingClient` no se
 * conecta hasta que una pantalla o el [BillingReconciler] (con sesión, al primer plano) lo pidan.
 */
val billingModule =
    module {
        // Los tres scopes viven tanto como el proceso: sus dueños son singletons de Koin que nadie recrea. Aun
        // así se cancelan con `onClose`, que corre si el contenedor se cierra (pruebas, `stopKoin`).
        single<CoroutineScope>(named("billing-provider-scope")) {
            CoroutineScope(SupervisorJob() + Dispatchers.Default)
        } onClose { it?.cancel() }
        // Main: el listener de Play y launchBillingFlow viven en el hilo principal.
        single<CoroutineScope>(named("billing-repository-scope")) {
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        } onClose { it?.cancel() }
        single<CoroutineScope>(named("billing-reconciler-scope")) {
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        } onClose { it?.cancel() }
        single<PlayBillingClient> { AndroidPlayBillingClient(androidContext()) }
        single {
            BillingClientProvider(
                client = get(),
                scope = get(named("billing-provider-scope"))
            )
        }
        single<BillingApi> { get<Retrofit>().create(BillingApi::class.java) }
        single<BillingRepository> {
            BillingRepositoryImpl(
                provider = get(),
                client = get(),
                api = get(),
                errorMapper = get(),
                scope = get(named("billing-repository-scope"))
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
                scope = get(named("billing-reconciler-scope"))
            )
        }
    }
