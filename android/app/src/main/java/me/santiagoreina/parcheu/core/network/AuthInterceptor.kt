package me.santiagoreina.parcheu.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

internal const val AUTHORIZATION = "Authorization"

internal fun bearer(token: String): String = "Bearer $token"

/**
 * ¿Puede esta petición llevar el ID token? Solo si va a [apiHost] **y** por HTTPS; la única excepción es
 * HTTP hacia [cleartextHosts] (el emulador, `10.0.2.2`), para que el ID token nunca viaje en claro por la
 * red (A-B1). Otros hosts (fotos de Google, Hosting) nunca lo reciben.
 */
internal fun canCarryToken(url: HttpUrl, apiHost: String, cleartextHosts: Set<String>): Boolean =
    url.host == apiHost && (url.isHttps || url.host in cleartextHosts)

/**
 * Agrega `Authorization: Bearer <ID token>` a las peticiones a [apiHost] si hay sesión y van cifradas
 * (ver [canCarryToken]).
 */
class AuthInterceptor(
    private val tokenProvider: TokenProvider,
    private val apiHost: String,
    private val cleartextHosts: Set<String> = EMULATOR_HOSTS
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!canCarryToken(request.url, apiHost, cleartextHosts) ||
            request.header(AUTHORIZATION) != null
        ) {
            return chain.proceed(request)
        }
        // OkHttp ejecuta los interceptores en sus propios hilos: bloquear aquí no toca el hilo principal.
        val token = runBlocking { tokenProvider.currentToken() } ?: return chain.proceed(request)
        return chain.proceed(request.newBuilder().header(AUTHORIZATION, bearer(token)).build())
    }

    companion object {
        /** Único host al que se permite el token por HTTP: el host de la máquina vista desde el emulador. */
        val EMULATOR_HOSTS = setOf("10.0.2.2")
    }
}
