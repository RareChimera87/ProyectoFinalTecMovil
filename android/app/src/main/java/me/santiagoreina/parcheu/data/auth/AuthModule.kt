package me.santiagoreina.parcheu.data.auth

import me.santiagoreina.parcheu.core.network.TokenProvider
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import retrofit2.Retrofit

/**
 * Sesión con Firebase (E2.2). Se carga después de `networkModule` y reemplaza su `TokenProvider` sin
 * sesión por [FirebaseTokenProvider].
 */
val authModule =
    module {
        single { FirebaseAuthSource(androidContext()) }
        single<TokenProvider> { FirebaseTokenProvider(get<FirebaseAuthSource>()) }
        single<SessionApi> { get<Retrofit>().create(SessionApi::class.java) }
        single<AuthRepository> { FirebaseAuthRepository(get(), get(), get()) }
    }
