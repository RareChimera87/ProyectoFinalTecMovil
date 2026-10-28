package me.santiagoreina.parcheu.feature.matchqueue

import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.feature.conocer.match.MatchQueueViewModel
import me.santiagoreina.parcheu.feature.conocer.match.conocerMatchDataModule
import me.santiagoreina.parcheu.feature.modes.ModesRepository
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Dependencias de F1 (E4.4). El repositorio compartido con F2 viene de `conocerMatchDataModule`. */
val matchQueueModule =
    module {
        // `ModesRepository` viene de `modesModule`, que AppModules ya carga.
        includes(conocerMatchDataModule)
        viewModel { params ->
            val modes = get<ModesRepository>()
            MatchQueueViewModel(
                initialMode = params.getOrNull<Mode>(),
                repository = get(),
                loadActiveModes = { modes.activeModes() }
            )
        }
    }

/** Modos encendidos según `GET /v1/me`; `null` si no se pudo leer (F1 conserva entonces todos los modos). */
internal suspend fun ModesRepository.activeModes(): List<Mode>? =
    (me() as? ApiResult.Success)?.value?.modes?.filterValues { it }?.keys?.toList()
