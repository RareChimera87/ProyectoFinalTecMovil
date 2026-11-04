package me.santiagoreina.parcheu.push

import me.santiagoreina.parcheu.core.network.TokenProvider
import me.santiagoreina.parcheu.data.auth.SessionCleaner
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit

/**
 * Dependencias del push. `AppModules.kt` está congelado desde E0.7; este módulo lo registra
 * `ParcheUApplication` junto a `appModules` (una línea) para no tocar la lista de features.
 */
val pushModule = module {
    single<PushApi> { get<Retrofit>().create(PushApi::class.java) }
    single<PushTokenStore> { SharedPrefsPushTokenStore(androidContext()) }
    // Cerrar sesión (E8.2) borra el token FCM y la cuenta registrada; el DELETE al servidor lo hace SignOutFlow antes.
    single<SessionCleaner>(named("push-token")) { pushTokenCleaner(get()) }
    single {
        val tokens = get<TokenProvider>()
        PushTokenRegistrar(get(), get(), get(), currentUser = { tokens.currentUserId() })
    }
}
