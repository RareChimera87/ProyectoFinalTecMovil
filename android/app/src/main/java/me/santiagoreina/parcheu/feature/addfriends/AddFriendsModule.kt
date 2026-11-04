package me.santiagoreina.parcheu.feature.addfriends

import me.santiagoreina.parcheu.feature.invites.invitesModule
import org.koin.dsl.module

/** Dependencias de [AddFriendsRoute] (C5): las de las invitaciones por QR y código (E6.1, `feature/invites`). */
val addFriendsModule =
    module {
        includes(invitesModule)
    }
