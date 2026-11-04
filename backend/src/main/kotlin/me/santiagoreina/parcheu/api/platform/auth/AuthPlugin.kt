package me.santiagoreina.parcheu.api.platform.auth

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer

/** Nombres de los proveedores de autenticación; las rutas se agrupan con `authenticate(...)`. */
object AuthProviders {
    /** ID token de Firebase en `Authorization: Bearer`. Falla con 401. */
    const val FIREBASE = "firebase"

    /** Token OIDC de `sa-tasks` para las rutas `/internal`. Falla con 403 (ver ErrorHandling). */
    const val OIDC_INTERNAL = "oidc-internal"
}

/**
 * Instala los dos proveedores. La carga del usuario desde la base de datos y la comprobación de
 * `suspended_until`/`deleted_at` (arquitectura.md §3.2) llegan con el esquema (E0.4) y la sesión (E2.1):
 * se agregan en el bloque `authenticate` del proveedor `firebase`, sin tocar las rutas.
 */
fun Application.installAuth(tokenVerifier: TokenVerifier, oidcVerifier: OidcVerifier) {
    install(Authentication) {
        bearer(AuthProviders.FIREBASE) {
            authenticate { credential -> verifyFirebase(tokenVerifier, credential.token) }
        }
        bearer(AuthProviders.OIDC_INTERNAL) {
            authenticate { credential -> oidcVerifier.verify(credential.token) }
        }
    }
}

/**
 * Un ID token vive hasta una hora y sin más seguiría valiendo tras `revokeRefreshTokens`. Para no pagar una
 * llamada a Firebase en cada petición, la revocación se comprueba solo cuando el token trae el claim `admin`
 * (el único privilegio que se quita y debe cortar la sesión de inmediato, `scripts/grant-admin.sh`): una
 * llamada extra por petición de un admin, ninguna para el resto.
 */
internal suspend fun verifyFirebase(verifier: TokenVerifier, token: String): FirebaseUser? {
    val user = verifier.verify(token) ?: return null
    return if (user.admin) verifier.verify(token, checkRevoked = true) else user
}
