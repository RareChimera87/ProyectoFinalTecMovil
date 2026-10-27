package me.santiagoreina.parcheu.api.platform.auth

/**
 * Usuario autenticado por un ID token de Firebase. `uid` es el UID de Firebase. [admin] es el claim
 * `admin` del token (E8.5): solo lo otorga `scripts/grant-admin.sh`, nunca el cliente.
 */
data class FirebaseUser(
    val uid: String,
    val email: String?,
    val name: String?,
    /**
     * Cuenta de prueba (E10.6): el ID token trae el claim `is_test`, que solo puede fijar quien firma el token
     * personalizado con las credenciales del proyecto (Admin SDK). Marca `users.is_test` al crear la cuenta.
     */
    val isTest: Boolean = false,
    val admin: Boolean = false,
)

/** Identidad de servicio autenticada por un token OIDC de Google (Tasks, Scheduler, Pub/Sub). */
data class ServiceCaller(val email: String)

/** Verifica ID tokens de Firebase. Devuelve null si el token no es válido. */
interface TokenVerifier {
    suspend fun verify(token: String): FirebaseUser?
}

/** Verifica tokens OIDC de Google para las rutas `/internal`. Devuelve null si no es válido. */
interface OidcVerifier {
    suspend fun verify(token: String): ServiceCaller?
}
