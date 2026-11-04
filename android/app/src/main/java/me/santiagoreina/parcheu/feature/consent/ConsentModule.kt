package me.santiagoreina.parcheu.feature.consent

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import retrofit2.Retrofit

/** Dependencias de B4 (E2.5). `SessionApi` viene de `authModule`. */
val consentModule =
    module {
        single<ConsentApi> { get<Retrofit>().create(ConsentApi::class.java) }
        single<ConsentRepository> { ApiConsentRepository(get(), get(), get()) }
        viewModelOf(::ConsentViewModel)
    }
