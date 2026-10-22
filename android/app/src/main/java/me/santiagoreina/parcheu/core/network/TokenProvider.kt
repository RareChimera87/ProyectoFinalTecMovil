package me.santiagoreina.parcheu.core.network

/**
 * Fuente del ID token de Firebase para `Authorization: Bearer`. E2.2 la implementa con
 * `FirebaseAuth.currentUser.getIdToken(forceRefresh)`; mientras tanto no hay sesión.
 */
interface TokenProvider {
    /** Token vigente (puede venir de caché); `null` si no hay sesión. */
    suspend fun currentToken(): String?

    /** Fuerza un token nuevo; `null` si no hay sesión o no se pudo refrescar. */
    suspend fun refreshToken(): String?

    /** UID de la cuenta con sesión (E2.2: `FirebaseAuth.currentUser?.uid`); `null` si no hay sesión. */
    suspend fun currentUserId(): String? = null
}

/** Sin sesión: las llamadas salen sin `Authorization` (E0.7, antes del login de E2.2). */
object NoSessionTokenProvider : TokenProvider {
    override suspend fun currentToken(): String? = null

    override suspend fun refreshToken(): String? = null
}
