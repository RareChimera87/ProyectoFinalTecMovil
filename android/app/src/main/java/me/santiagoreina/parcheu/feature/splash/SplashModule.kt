package me.santiagoreina.parcheu.feature.splash

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Dependencias de A1 (E2.2). El `AuthRepository` viene de `authModule`. */
val splashModule =
    module {
        viewModelOf(::SplashViewModel)
    }
