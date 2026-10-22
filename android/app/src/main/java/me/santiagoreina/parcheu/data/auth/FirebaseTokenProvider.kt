package me.santiagoreina.parcheu.data.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await
import me.santiagoreina.parcheu.core.network.TokenProvider

/**
 * ID token de Firebase para `Authorization: Bearer` (arquitectura.md §2.5): `getIdToken(false)` usa la
 * caché del SDK y `getIdToken(true)` fuerza uno nuevo (lo pide `TokenAuthenticator` ante un 401).
 *
 * Distingue dos tipos de fallo para no cerrar la sesión por un problema transitorio:
 * - Red o demasiadas peticiones: lanza [IOException]; el interceptor la propaga y `apiCall` la traduce a
 *   `AppError.Network` (la sesión se conserva y se puede reintentar).
 * - Sesión inválida (usuario borrado o deshabilitado, credencial revocada) o sin usuario: devuelve `null`;
 *   la petición sale sin token, la API responde 401 y la app cierra la sesión.
 */
class FirebaseTokenProvider(private val idToken: suspend (forceRefresh: Boolean) -> String?) :
    TokenProvider {
    constructor(source: FirebaseAuthSource) : this({ forceRefresh ->
        source.authOrNull()?.currentUser?.getIdToken(forceRefresh)?.await()?.token
    })

    override suspend fun currentToken(): String? = token(forceRefresh = false)

    override suspend fun refreshToken(): String? = token(forceRefresh = true)

    private suspend fun token(forceRefresh: Boolean): String? = try {
        idToken(forceRefresh)
    } catch (e: CancellationException) {
        throw e
    } catch (e: FirebaseNetworkException) {
        throw IOException("Sin red al refrescar el token de Firebase", e)
    } catch (e: FirebaseTooManyRequestsException) {
        throw IOException("Firebase limitó los refrescos del token", e)
    } catch (_: FirebaseAuthInvalidUserException) {
        null
    } catch (_: FirebaseAuthInvalidCredentialsException) {
        null
    } catch (_: Exception) {
        // Cualquier otro fallo inesperado se trata como sesión sin token (la API decidirá con un 401).
        null
    }
}
