package me.santiagoreina.parcheu.feature.friendsoffriends

import me.santiagoreina.parcheu.feature.parche.fof.fofModule
import org.koin.dsl.module

/** Dependencias de [FriendsOfFriendsRoute]: las de D6/D7 (E6.3), ya registrado en AppModules.kt. */
val friendsOfFriendsModule = module {
    includes(fofModule)
}
