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

/** Lo único que puede hacer una cuenta de menor de edad (E2.6): ver su cuenta y borrarla. */
private val UNDERAGE_EXEMPT: Set<Pair<HttpMethod, String>> = setOf(
    HttpMethod.Get to "/v1/me",
    HttpMethod.Delete to "/v1/me",
)

internal fun isExemptFromUnderage(method: HttpMethod, path: String): Boolean =
    (method to path.trimEnd('/')) in UNDERAGE_EXEMPT

/**
 * Plugin de ruta que se instala una sola vez desde `IdentityRoutes` sobre la ruta autenticada de
 * `/v1`: cubre las rutas de todos los dominios sin tocar `Application.kt`. Corre después de la
 * autenticación; si el usuario no tiene TERMS y PRIVACY en la versión vigente responde 403
 * `TERMS_REQUIRED` (o `SUSPENDED` si está suspendido). Una cuenta de menor de edad (E2.6) recibe 403
 * `UNDERAGE` en todas las rutas, incluidas `session` y `terms`, salvo `GET` y `DELETE /v1/me`.
 */
internal val TermsGate = createRouteScopedPlugin("TermsGate", ::TermsGateConfig) {
    val service = pluginConfig.service
    on(AuthenticationChecked) { call ->
        val user = call.principal<FirebaseUser>() ?: return@on
        val method = call.request.httpMethod
        val path = call.request.path()
        when {
            isExemptFromUnderage(method, path) -> return@on
            isExemptFromTerms(method, path) -> service.requireNotUnderage(user)
            else -> service.requireFullAccess(user)
        }
    }
}
