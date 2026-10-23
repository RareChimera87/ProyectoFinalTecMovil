package me.santiagoreina.parcheu.feature.introduce

import me.santiagoreina.parcheu.feature.presentar.presentarModule
import org.koin.dsl.module

/** Dependencias de E1-E3 (E6.2, `feature/presentar`). Ya está registrado en AppModules.kt. */
val introduceModule = module {
    includes(presentarModule)
}
