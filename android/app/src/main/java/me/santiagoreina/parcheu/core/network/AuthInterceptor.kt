package me.santiagoreina.parcheu.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

internal const val AUTHORIZATION = "Authorization"

internal fun bearer(token: String): String = "Bearer $token"

/** Agrega `Authorization: Bearer <ID token>` a cada petición si hay sesión. */
class AuthInterceptor(private val tokenProvider: TokenProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(AUTHORIZATION) != null) return chain.proceed(request)
        // OkHttp ejecuta los interceptores en sus propios hilos: bloquear aquí no toca el hilo principal.
        val token = runBlocking { tokenProvider.currentToken() } ?: return chain.proceed(request)
        return chain.proceed(request.newBuilder().header(AUTHORIZATION, bearer(token)).build())
    }
}
