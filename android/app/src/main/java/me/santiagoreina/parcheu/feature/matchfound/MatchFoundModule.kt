package me.santiagoreina.parcheu.feature.matchfound

import me.santiagoreina.parcheu.feature.conocer.match.MatchFoundViewModel
import me.santiagoreina.parcheu.feature.conocer.match.conocerMatchDataModule
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Dependencias de F2 (E4.4). Recibe el id del match como parámetro. */
val matchFoundModule =
    module {
        includes(conocerMatchDataModule)
        viewModel { params -> MatchFoundViewModel(matchId = params.get(), repository = get()) }
    }
