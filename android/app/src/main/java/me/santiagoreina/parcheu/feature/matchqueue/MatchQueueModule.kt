package me.santiagoreina.parcheu.feature.matchqueue

import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.feature.conocer.match.MatchQueueViewModel
import me.santiagoreina.parcheu.feature.conocer.match.conocerMatchDataModule
import me.santiagoreina.parcheu.feature.modes.ModesRepository
import me.santiagoreina.parcheu.feature.modes.modesModule
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Dependencias de F1 (E4.4). El repositorio compartido con F2 viene de `conocerMatchDataModule`. */
val matchQueueModule =
    module {
        includes(conocerMatchDataModule, modesModule)
        viewModel { params ->
            val modes = get<ModesRepository>()
            MatchQueueViewModel(
                initialMode = params.getOrNull<Mode>(),
                repository = get(),
                loadActiveModes = {
                    (modes.me() as? ApiResult.Success)?.value?.modes?.filterValues {
                        it
                    }?.keys?.toList()
                }
            )
        }
    }
