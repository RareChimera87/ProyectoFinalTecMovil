package me.santiagoreina.parcheu.feature.invites

import me.santiagoreina.parcheu.data.auth.FirebaseAuthSource
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit

/** Dependencias de C5 (E6.1). Lo incluye `addFriendsModule`, que ya está en AppModules.kt. */
val invitesModule =
    module {
        single<InvitesApi> { get<Retrofit>().create(InvitesApi::class.java) }
        single<InvitesRepository> { InvitesRepositoryImpl(get(), get()) }
        viewModel { params ->
            val auth = get<FirebaseAuthSource>()
            InvitesViewModel(
                repository = get(),
                initialCode = params.getOrNull<String>(),
                displayName = { auth.authOrNull()?.currentUser?.displayName }
            )
        }
    }
