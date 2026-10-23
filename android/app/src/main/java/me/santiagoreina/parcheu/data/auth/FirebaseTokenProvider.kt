package me.santiagoreina.parcheu.data.auth

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await
import me.santiagoreina.parcheu.core.network.TokenProvider

/**
 * ID token de Firebase para `Authorization: Bearer` (arquitectura.md §2.5): `getIdToken(false)` usa la
 * caché del SDK y `getIdToken(true)` fuerza uno nuevo (lo pide `TokenAuthenticator` ante un 401).
 */
class FirebaseTokenProvider(private val source: FirebaseAuthSource) : TokenProvider {
    override suspend fun currentToken(): String? = token(forceRefresh = false)

    override suspend fun refreshToken(): String? = token(forceRefresh = true)

    private suspend fun token(forceRefresh: Boolean): String? {
        val user = source.authOrNull()?.currentUser ?: return null
        return try {
            user.getIdToken(forceRefresh).await().token
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Sin red o sesión revocada: la petición sale sin token y la API responde 401.
            null
        }
    }
}
