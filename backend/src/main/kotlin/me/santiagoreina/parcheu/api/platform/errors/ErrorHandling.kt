package me.santiagoreina.parcheu.api.platform.errors

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.santiagoreina.parcheu.contract.ApiError
import me.santiagoreina.parcheu.contract.ErrorCode
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("me.santiagoreina.parcheu.api.errors")

/** Ruta aún sin implementar (stub de E0.3): responde 501. */
class NotImplementedYet(val feature: String) : RuntimeException("Pendiente de implementar: $feature")

/**
 * Traduce excepciones a [ApiError]. Los status sin código en el contrato (500 y 501) llevan un
 * cuerpo `{"message": ...}` simple; el cliente solo debe tratarlos como fallo del servidor.
 */
fun Application.installErrorHandling() {
    install(StatusPages) {
        exception<DomainException> { call, e ->
            call.respond(HttpStatusCode.fromValue(e.code.httpStatus), e.toApiError())
        }
        exception<NotImplementedYet> { call, e ->
            call.respond(HttpStatusCode.NotImplemented, buildJsonObject { put("message", e.message ?: "No implementado") })
        }
        exception<BadRequestException> { call, e ->
            call.respond(HttpStatusCode.BadRequest, ApiError(ErrorCode.VALIDATION, e.rootMessage()))
        }
        exception<SerializationException> { call, e ->
            call.respond(HttpStatusCode.BadRequest, ApiError(ErrorCode.VALIDATION, e.message ?: "Cuerpo inválido"))
        }
        exception<Throwable> { call, e ->
            log.error("Error no controlado en {} {}", call.request.local.method.value, call.request.local.uri, e)
            call.respond(HttpStatusCode.InternalServerError, buildJsonObject { put("message", "Error interno") })
        }
        status(HttpStatusCode.Unauthorized) { call, _ ->
            if (call.request.path().startsWith("/internal")) {
                // Quien llama a /internal es un servicio (Tasks, Scheduler, Pub/Sub): 403, no 401.
                call.respond(HttpStatusCode.Forbidden, ApiError(ErrorCode.FORBIDDEN, "Token OIDC inválido"))
            } else {
                call.respond(HttpStatusCode.Unauthorized, ApiError(ErrorCode.UNAUTHENTICATED, "Falta un token válido"))
            }
        }
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respond(HttpStatusCode.NotFound, ApiError(ErrorCode.NOT_FOUND, "Ruta no encontrada"))
        }
    }
}

private fun BadRequestException.rootMessage(): String =
    (generateSequence<Throwable>(this) { it.cause }.last().message ?: message ?: "Petición inválida")
