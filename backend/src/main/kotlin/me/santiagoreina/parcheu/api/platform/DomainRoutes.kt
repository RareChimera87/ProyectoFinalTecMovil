package me.santiagoreina.parcheu.api.platform

import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import me.santiagoreina.parcheu.api.platform.errors.NotImplementedYet

/**
 * Contrato de cada dominio. `Application.kt` registra la lista de [DomainRoutes] de `AppContainer`
 * una sola vez; cada feature rellena el archivo `XRoutes.kt` de su dominio (y lo cablea en
 * `AppContainer.kt`) sin tocar `Application.kt`.
 */
interface DomainRoutes {
    /** Rutas bajo `/v1`, protegidas por el ID token de Firebase. */
    fun Route.v1() {}

    /** Rutas bajo `/internal`, protegidas por el token OIDC de `sa-tasks`. */
    fun Route.internal() {}
}

/** Rutas de arquitectura.md §5.2 aún sin lógica: responden 501. Se reemplazan al implementar. */
fun Route.stubGet(path: String) = get(path) { notImplemented() }
fun Route.stubPost(path: String) = post(path) { notImplemented() }
fun Route.stubPut(path: String) = put(path) { notImplemented() }
fun Route.stubPatch(path: String) = patch(path) { notImplemented() }
fun Route.stubDelete(path: String) = delete(path) { notImplemented() }

private fun RoutingContext.notImplemented(): Nothing =
    throw NotImplementedYet("${call.request.local.method.value} ${call.request.local.uri}")
