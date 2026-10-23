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
            authenticate { credential -> tokenVerifier.verify(credential.token) }
        }
        bearer(AuthProviders.OIDC_INTERNAL) {
            authenticate { credential -> oidcVerifier.verify(credential.token) }
        }
    }
}
