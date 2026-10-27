package me.santiagoreina.parcheu.data.auth

import me.santiagoreina.parcheu.core.network.TokenProvider
import me.santiagoreina.parcheu.push.PushTokenRegistrar
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
        single<TokenProvider> { FirebaseTokenProvider.fromSource(get<FirebaseAuthSource>()) }
        single<SessionApi> { get<Retrofit>().create(SessionApi::class.java) }
        single {
            SignOutFlow(
                // PushTokenRegistrar lo registra pushModule, aparte de la lista de features.
                push = PushUnregistration { getOrNull<PushTokenRegistrar>()?.unregister() },
                cleaners = getAll<SessionCleaner>(),
                firestore = FirebaseFirestoreSessionReset(androidContext()),
                platform = FirebasePlatformSignOut(get())
            )
        }
        single<AuthRepository> { FirebaseAuthRepository(get(), get(), get(), get()) }
    }
