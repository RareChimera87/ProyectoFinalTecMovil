package me.santiagoreina.parcheu.core.network

import kotlinx.serialization.json.Json
import me.santiagoreina.parcheu.BuildConfig
import me.santiagoreina.parcheu.contract.ContractJson
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Cliente HTTP con sesión: token en cada petición y refresco ante 401. */
fun createOkHttpClient(tokenProvider: TokenProvider, debugLogging: Boolean = false): OkHttpClient =
    OkHttpClient
        .Builder()
        .addInterceptor(AuthInterceptor(tokenProvider))
        .authenticator(TokenAuthenticator(tokenProvider))
        .apply {
            if (debugLogging) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level =
                            HttpLoggingInterceptor.Level.BASIC
                    }
                )
            }
        }.build()

/** Retrofit con el JSON del contrato (`ignoreUnknownKeys`, `explicitNulls = false`). */
fun createRetrofit(baseUrl: String, client: OkHttpClient, json: Json = ContractJson): Retrofit =
    Retrofit
        .Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

/**
 * Red compartida. Cada feature crea su servicio con `get<Retrofit>().create(XApi::class.java)` en su
 * propio módulo. E2.2 reemplaza el [TokenProvider] por el de Firebase.
 */
val networkModule =
    module {
        single<Json> { ContractJson }
        single { ApiErrorMapper(get()) }
        single<TokenProvider> { NoSessionTokenProvider }
        single { createOkHttpClient(get(), debugLogging = BuildConfig.DEBUG) }
        single { createRetrofit(BuildConfig.API_URL, get(), get()) }
    }
