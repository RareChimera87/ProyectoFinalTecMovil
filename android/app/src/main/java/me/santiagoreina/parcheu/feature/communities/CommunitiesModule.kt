package me.santiagoreina.parcheu.feature.communities

import me.santiagoreina.parcheu.feature.parche.communities.communitiesFeatureModule
import org.koin.dsl.module

/** Dependencias de [CommunitiesRoute]: las de D8 (E6.4), ya registrado en AppModules.kt. */
val communitiesModule = module {
    includes(communitiesFeatureModule)
}
