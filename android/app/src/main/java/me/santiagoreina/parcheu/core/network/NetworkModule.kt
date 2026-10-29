package me.santiagoreina.parcheu.core.network

import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import me.santiagoreina.parcheu.BuildConfig
import me.santiagoreina.parcheu.contract.ContractJson
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Tiempo para abrir la conexión; en red móvil mala es mejor fallar pronto y dejar reintentar. */
const val CONNECT_TIMEOUT_SECONDS = 10L

/** Silencio máximo entre dos lecturas del cuerpo de la respuesta. */
const val READ_TIMEOUT_SECONDS = 30L

/** Silencio máximo entre dos escrituras del cuerpo de la petición. */
const val WRITE_TIMEOUT_SECONDS = 30L

/** Tope de la llamada completa (conexión, envío y respuesta), aunque los datos lleguen goteando. */
const val CALL_TIMEOUT_SECONDS = 60L

/** Lectura y escritura del cliente lento (ver [slowClientOf]). */
const val SLOW_IO_TIMEOUT_SECONDS = 120L

/** Tope de la llamada completa del cliente lento. */
const val SLOW_CALL_TIMEOUT_SECONDS = 180L

/**
 * Cliente HTTP con sesión: token y refresco ante 401 solo para [apiHost]. Los timeouts son los de arriba
 * (los de OkHttp por defecto no limitan la llamada completa). Un endpoint lento usa [slowClientOf].
 */
fun createOkHttpClient(
    tokenProvider: TokenProvider,
    apiHost: String,
    debugLogging: Boolean = false): OkHttpClient = OkHttpClient
    .Builder()
    .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .addInterceptor(AuthInterceptor(tokenProvider, apiHost))
    .authenticator(TokenAuthenticator(tokenProvider, apiHost))
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

/**
 * Variante del cliente para los endpoints síncronos lentos (hoy solo `GET /v1/me/export`, que el servidor
 * arma completo antes de responder; ninguno de simulación o escenario lo es: responden 202 o leen estado).
 * Comparte el pool de conexiones, los interceptores y el autenticador de [client]; solo cambian los
 * timeouts de lectura, escritura y llamada. No hay timeout por petición en OkHttp una vez iniciada la
 * llamada, por eso se usa otro cliente y no una cabecera.
 */
fun slowClientOf(client: OkHttpClient): OkHttpClient = client
    .newBuilder()
    .readTimeout(SLOW_IO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .writeTimeout(SLOW_IO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .callTimeout(SLOW_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .build()

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
 * propio módulo. El [TokenProvider] lo aporta `authModule` (E2.2, sesión de Firebase).
 */
val networkModule =
    module {
        single<Json> { ContractJson }
        single { ApiErrorMapper(get()) }
        single {
            createOkHttpClient(
                get(),
                apiHost = BuildConfig.API_URL.toHttpUrl().host,
                debugLogging = BuildConfig.DEBUG
            )
        }
        single { createRetrofit(BuildConfig.API_URL, get(), get()) }
    }
