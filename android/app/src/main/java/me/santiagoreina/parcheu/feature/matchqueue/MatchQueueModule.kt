package me.santiagoreina.parcheu.feature.matchqueue

import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Usage
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.feature.conocer.match.MatchQueueViewModel
import me.santiagoreina.parcheu.feature.conocer.match.conocerMatchDataModule
import me.santiagoreina.parcheu.feature.home.HomeRepository
import me.santiagoreina.parcheu.feature.modes.ModesRepository
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Dependencias de F1 (E4.4). El repositorio compartido con F2 viene de `conocerMatchDataModule`. */
val matchQueueModule =
    module {
        // `ModesRepository` y `HomeRepository` vienen de `modesModule` y `homeModule`, que AppModules ya carga.
        includes(conocerMatchDataModule)
        viewModel { params ->
            val modes = get<ModesRepository>()
            val home = get<HomeRepository>()
            MatchQueueViewModel(
                initialMode = params.getOrNull<Mode>(),
                repository = get(),
                loadActiveModes = { modes.activeModes() },
                loadDailyQuota = { home.randomMatchQuota() }
            )
        }
    }

/** Modos encendidos según `GET /v1/me`; `null` si no se pudo leer (F1 conserva entonces todos los modos). */
internal suspend fun ModesRepository.activeModes(): List<Mode>? =
    (me() as? ApiResult.Success)?.value?.modes?.filterValues { it }?.keys?.toList()

/** Cupo diario de match al azar de `GET /v1/home` (el de C1); `null` si no se pudo leer. */
internal suspend fun HomeRepository.randomMatchQuota(): Usage? =
    (home() as? ApiResult.Success)?.value?.quota?.randomMatch
