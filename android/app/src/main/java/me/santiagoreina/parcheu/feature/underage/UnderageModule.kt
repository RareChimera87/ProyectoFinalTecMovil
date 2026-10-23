package me.santiagoreina.parcheu.feature.underage

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Dependencias del bloqueo por edad (E2.6). El `AuthRepository` viene de `authModule`. */
val underageModule = module {
    viewModelOf(::UnderageViewModel)
}
