package me.santiagoreina.parcheu.feature.matchqueue

import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.feature.conocer.match.MatchQueueViewModel
import me.santiagoreina.parcheu.feature.conocer.match.conocerMatchDataModule
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Dependencias de F1 (E4.4). El repositorio compartido con F2 viene de `conocerMatchDataModule`. */
val matchQueueModule =
    module {
        includes(conocerMatchDataModule)
        viewModel { params ->
            MatchQueueViewModel(initialMode = params.getOrNull<Mode>(), repository = get())
        }
    }
