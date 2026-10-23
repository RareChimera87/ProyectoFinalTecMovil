package me.santiagoreina.parcheu.feature.twinready

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Dependencias de B5 (E2.5). `ModesRepository` viene de `modesModule`. */
val twinReadyModule =
    module {
        viewModelOf(::TwinReadyViewModel)
    }
