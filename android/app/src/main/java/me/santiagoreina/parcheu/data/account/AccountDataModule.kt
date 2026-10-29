package me.santiagoreina.parcheu.data.account

import me.santiagoreina.parcheu.core.network.slowClientOf
import org.koin.dsl.module
import retrofit2.Retrofit

/**
 * Datos de la cuenta (E8.3). Lo incluyen `deleteAccountModule` y `exportDataModule`; Koin aplana los
 * `includes` y lo carga una sola vez, así AppModules.kt no cambia.
 */
val accountDataModule =
    module {
        // La exportación es lenta en el servidor: esta API usa el cliente con timeouts largos (slowClientOf).
        single<AccountApi> {
            get<Retrofit>().newBuilder().client(slowClientOf(get())).build().create(AccountApi::class.java)
        }
        single<AccountRepository> { AccountRepositoryImpl(get(), get()) }
    }
