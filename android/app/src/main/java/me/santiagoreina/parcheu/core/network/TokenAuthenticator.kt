package me.santiagoreina.parcheu.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Ante un 401 pide un token nuevo **una sola vez** y reintenta la petición. Si el reintento vuelve a dar
 * 401 (o no hay token, o el host no es el de la API), se rinde y el 401 llega a la llamada, que lo traduce a `AppError.Unauthorized`.
 */
class TokenAuthenticator(private val tokenProvider: TokenProvider, private val apiHost: String) :
    Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.url.host != apiHost || response.priorResponse != null) return null
        val sent = response.request.header(AUTHORIZATION)
        val token =
            synchronized(this) {
                runBlocking {
                    // Si otro hilo ya refrescó mientras esperábamos, reutiliza ese token (un solo refresco).
                    val current = tokenProvider.currentToken()
                    if (current != null && sent != bearer(current)) {
                        current
                    } else {
                        tokenProvider.refreshToken()
                    }
                }
            } ?: return null
        return response.request
            .newBuilder()
            .header(AUTHORIZATION, bearer(token))
            .build()
    }
}
