package me.santiagoreina.parcheu.feature.scenariochat

import me.santiagoreina.parcheu.feature.conocer.scenario.conocerScenarioModule
import org.koin.dsl.module

/** Dependencias de [ScenarioChatRoute]: la feature vive en `feature/conocer/scenario` (E4.8). */
val scenarioChatModule = module {
    includes(conocerScenarioModule)
}
