package me.santiagoreina.parcheu.push

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import retrofit2.Retrofit

/**
 * Dependencias del push. `AppModules.kt` está congelado desde E0.7; este módulo lo registra
 * `ParcheUApplication` junto a `appModules` (una línea) para no tocar la lista de features.
 */
val pushModule = module {
    single<PushApi> { get<Retrofit>().create(PushApi::class.java) }
    single<PushTokenStore> { SharedPrefsPushTokenStore(androidContext()) }
    single { PushTokenRegistrar(get(), get(), get()) }
}
