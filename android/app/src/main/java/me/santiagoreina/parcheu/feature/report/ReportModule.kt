package me.santiagoreina.parcheu.feature.report

import me.santiagoreina.parcheu.feature.safety.safetyModule
import org.koin.dsl.module

/** Dependencias de [ReportRoute]: las de reportar y bloquear (E8.1, `feature/safety`). */
val reportModule = module {
    includes(safetyModule)
}
