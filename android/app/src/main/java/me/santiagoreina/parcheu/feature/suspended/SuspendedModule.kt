package me.santiagoreina.parcheu.feature.suspended

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Dependencias del bloqueo por suspensión. El `AuthRepository` viene de `authModule`. */
val suspendedModule = module {
    viewModelOf(::SuspendedViewModel)
}
