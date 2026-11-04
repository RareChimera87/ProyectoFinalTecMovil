package me.santiagoreina.parcheu.feature.notifications.requests

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit

/** Dependencias de la sección 'Quién quiere conectar contigo' de C2 (E6.6). */
val connectRequestsModule = module {
    single<ConnectRequestsApi> { get<Retrofit>().create(ConnectRequestsApi::class.java) }
    single<ConnectRequestsRepository> { RemoteConnectRequestsRepository(get(), get()) }
    viewModel { ConnectRequestsViewModel(get()) }
}
