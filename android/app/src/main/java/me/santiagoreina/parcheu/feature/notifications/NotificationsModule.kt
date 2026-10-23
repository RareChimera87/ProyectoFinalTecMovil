package me.santiagoreina.parcheu.feature.notifications

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit

/** Dependencias de C2 (E5.4): API de la bandeja, su repositorio y el ViewModel. */
val notificationsModule = module {
    single<NotificationsApi> { get<Retrofit>().create(NotificationsApi::class.java) }
    single<NotificationsRepository> { RemoteNotificationsRepository(get(), get()) }
    viewModel { NotificationsViewModel(get()) }
}
