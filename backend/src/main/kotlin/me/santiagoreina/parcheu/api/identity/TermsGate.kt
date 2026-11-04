package me.santiagoreina.parcheu.api.identity

import io.ktor.http.HttpMethod
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.auth.principal
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import me.santiagoreina.parcheu.api.platform.auth.FirebaseUser

internal class TermsGateConfig {
    lateinit var service: IdentityService
}

/** Rutas que funcionan sin términos vigentes (backlog E2.1): sesión, aceptar términos, ver y borrar la cuenta. */
private val EXEMPT: Set<Pair<HttpMethod, String>> = setOf(
    HttpMethod.Post to "/v1/session",
    HttpMethod.Post to "/v1/me/terms",
    HttpMethod.Get to "/v1/me",
    HttpMethod.Delete to "/v1/me",
)

internal fun isExemptFromTerms(method: HttpMethod, path: String): Boolean =
    (method to path.trimEnd('/')) in EXEMPT

/**
 * Las rutas de `/v1/admin` con el claim `admin` no pasan por la puerta (B-B3): el claim basta para moderar y la persona
 * admin puede no tener cuenta en la app o no haber aceptado términos (`AdminRoutes.adminUserId` admite null).
 * Cada ruta sigue exigiendo el claim con `requireAdmin`; sin él, la puerta aplica como a cualquiera.
 */
internal fun isAdminRoute(user: FirebaseUser, path: String): Boolean =
    user.admin && path.trimEnd('/').startsWith("/v1/admin/")

/**
 * Lo único que puede hacer una cuenta de menor de edad (E2.6): ver su cuenta, descargar sus datos y
 * borrarla. `GET /v1/me/export` va aquí porque el bloqueo por edad lleva a 'Borrar cuenta' →
 * 'Descargar mis datos primero' (E8.3): el derecho de acceso (Ley 1581) no depende de la edad ni de los términos.
 */
private val UNDERAGE_EXEMPT: Set<Pair<HttpMethod, String>> = setOf(
    HttpMethod.Get to "/v1/me",
    HttpMethod.Get to "/v1/me/export",
    HttpMethod.Delete to "/v1/me",
)

internal fun isExemptFromUnderage(method: HttpMethod, path: String): Boolean =
    (method to path.trimEnd('/')) in UNDERAGE_EXEMPT

/**
 * Lo que una cuenta suspendida o baneada (E8.5: BAN es una suspensión hasta 2999) sigue pudiendo hacer
 * para salir de la app y ejercer sus derechos (Ley 1581): ver sus ajustes de privacidad y quitar el token
 * FCM (cerrar sesión). `DELETE /v1/me` y `GET /v1/me/export` ya son exentas (E8.3). Siguen pasando por la
 * puerta de edad y de términos; solo se salta la suspensión.
 */
private val SUSPENSION_EXEMPT: Set<Pair<HttpMethod, String>> = setOf(
    HttpMethod.Get to "/v1/me/privacy",
    HttpMethod.Delete to "/v1/me/fcm-token",
)

internal fun isExemptFromSuspension(method: HttpMethod, path: String): Boolean =
    (method to path.trimEnd('/')) in SUSPENSION_EXEMPT

/**
 * Plugin de ruta que se instala una sola vez desde `IdentityRoutes` sobre la ruta autenticada de
 * `/v1`: cubre las rutas de todos los dominios sin tocar `Application.kt`. Corre después de la
 * autenticación; si el usuario no tiene TERMS y PRIVACY en la versión vigente responde 403
 * `TERMS_REQUIRED` (o `SUSPENDED` si está suspendido). Una cuenta de menor de edad (E2.6) recibe 403
 * `UNDERAGE` en todas las rutas, incluidas `session` y `terms`, salvo `GET` y `DELETE /v1/me` y `GET /v1/me/export`. Una cuenta suspendida solo conserva las rutas de [SUSPENSION_EXEMPT].
 */
internal val TermsGate = createRouteScopedPlugin("TermsGate", ::TermsGateConfig) {
    val service = pluginConfig.service
    on(AuthenticationChecked) { call ->
        val user = call.principal<FirebaseUser>() ?: return@on
        val method = call.request.httpMethod
        val path = call.request.path()
        when {
            isAdminRoute(user, path) -> return@on
            isExemptFromUnderage(method, path) -> return@on
            isExemptFromTerms(method, path) -> service.requireNotUnderage(user)
            isExemptFromSuspension(method, path) -> service.requireFullAccess(user, ignoreSuspension = true)
            else -> service.requireFullAccess(user)
        }
    }
}
