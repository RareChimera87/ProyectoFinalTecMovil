package me.santiagoreina.parcheu.data.account

import org.koin.dsl.module
import retrofit2.Retrofit

/**
 * Datos de la cuenta (E8.3). Lo incluyen `deleteAccountModule` y `exportDataModule`; Koin aplana los
 * `includes` y lo carga una sola vez, así AppModules.kt no cambia.
 */
val accountDataModule =
    module {
        single<AccountApi> { get<Retrofit>().create(AccountApi::class.java) }
        single<AccountRepository> { AccountRepositoryImpl(get(), get()) }
    }
