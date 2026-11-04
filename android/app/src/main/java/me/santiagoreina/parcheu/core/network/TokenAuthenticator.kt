package me.santiagoreina.parcheu.core.network

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Ante un 401 pide un token nuevo **una sola vez** y reintenta la petición. Si el reintento vuelve a dar
 * 401 (o no hay token, o el host no es el de la API), se rinde y el 401 llega a la llamada, que lo traduce a `AppError.Unauthorized`.
 */
class TokenAuthenticator(
    private val tokenProvider: TokenProvider,
    private val apiHost: String,
    private val cleartextHosts: Set<String> = AuthInterceptor.EMULATOR_HOSTS
) : Authenticator {
    private val refreshLock = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (!canCarryToken(response.request.url, apiHost, cleartextHosts) ||
            response.priorResponse != null
        ) {
            return null
        }
        val sent = response.request.header(AUTHORIZATION)
        // OkHttp llama al autenticador en su hilo: el bloqueo es inevitable, pero la espera entre 401
        // simultáneos es de un Mutex (se libera también si el refresco lanza), no de un monitor.
        val token =
            runBlocking {
                refreshLock.withLock {
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
