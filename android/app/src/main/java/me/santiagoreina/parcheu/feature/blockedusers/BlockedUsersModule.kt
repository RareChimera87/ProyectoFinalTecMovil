package me.santiagoreina.parcheu.feature.blockedusers

import me.santiagoreina.parcheu.feature.safety.safetyModule
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Dependencias de [BlockedUsersRoute]: el repositorio de reportar y bloquear (E8.1) y su ViewModel (E8.7). */
val blockedUsersModule = module {
    includes(safetyModule)
    viewModel { BlockedUsersViewModel(get()) }
}
