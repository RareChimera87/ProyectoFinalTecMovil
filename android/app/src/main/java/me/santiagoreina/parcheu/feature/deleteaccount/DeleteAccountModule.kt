package me.santiagoreina.parcheu.feature.deleteaccount

import me.santiagoreina.parcheu.data.account.accountDataModule
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Confirmación de borrado de cuenta (E8.3). El `AuthRepository` viene de `authModule`. */
val deleteAccountModule =
    module {
        includes(accountDataModule)
        viewModelOf(::DeleteAccountViewModel)
    }
