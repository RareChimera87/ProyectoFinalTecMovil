package me.santiagoreina.parcheu.api.platform.errors

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.CannotTransformContentToTypeException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.contentType
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
            // Un cuerpo que no deserializa llega aquí con la SerializationException como causa: su mensaje
            // lleva nombres de clases y campos internos, así que no se devuelve (B-B6).
            val message = if (e.hasCause<SerializationException>()) {
                log.warn("Cuerpo inválido en {} {}: {}", call.request.local.method.value, call.request.local.uri, e.rootMessage())
                BODY_INVALID
            } else {
                e.rootMessage()
            }
            call.respond(HttpStatusCode.BadRequest, ApiError(ErrorCode.VALIDATION, message))
        }
        exception<SerializationException> { call, e ->
            log.warn("Cuerpo inválido en {} {}: {}", call.request.local.method.value, call.request.local.uri, e.message)
            call.respond(HttpStatusCode.BadRequest, ApiError(ErrorCode.VALIDATION, BODY_INVALID))
        }
        // Errores de cliente propios de Ktor que no derivan de BadRequestException: sin estos
        // handlers caerían en el catch-all y responderían 500 (issue #49). StatusPages elige el
        // handler de la clase más específica, así que 415 y 413 ganan a ContentTransformationException.
        exception<UnsupportedMediaTypeException> { call, e ->
            call.respond(
                HttpStatusCode.UnsupportedMediaType,
                ApiError(ErrorCode.VALIDATION, e.message ?: "Tipo de contenido no soportado"),
            )
        }
        exception<PayloadTooLargeException> { call, _ ->
            call.respond(HttpStatusCode.PayloadTooLarge, ApiError(ErrorCode.VALIDATION, "El cuerpo es demasiado grande"))
        }
        // Ktor 3 lanza CannotTransformContentToTypeException (no UnsupportedMediaTypeException) cuando
        // el Content-Type no tiene conversor, p. ej. text/plain: eso es un 415. Sin Content-Type o con
        // JSON, el cuerpo ausente o no convertible es un 400.
        exception<CannotTransformContentToTypeException> { call, _ ->
            val tipo = call.request.contentType()
            if (tipo != ContentType.Any && !tipo.match(ContentType.Application.Json)) {
                call.respond(
                    HttpStatusCode.UnsupportedMediaType,
                    ApiError(ErrorCode.VALIDATION, "Tipo de contenido no soportado: ${tipo.withoutParameters()}; usa application/json"),
                )
            } else {
                call.respond(HttpStatusCode.BadRequest, ApiError(ErrorCode.VALIDATION, "Falta el cuerpo o no es válido"))
            }
        }
        exception<ContentTransformationException> { call, e ->
            log.warn("Cuerpo inválido en {} {}: {}", call.request.local.method.value, call.request.local.uri, e.message)
            call.respond(HttpStatusCode.BadRequest, ApiError(ErrorCode.VALIDATION, BODY_INVALID))
        }
        exception<NotFoundException> { call, e ->
            call.respond(HttpStatusCode.NotFound, ApiError(ErrorCode.NOT_FOUND, e.message ?: "No encontrado"))
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
        status(HttpStatusCode.TooManyRequests) { _ ->
            // Límite por persona de las rutas de IA (B-M1). El contrato no tiene un código para 429: como 500 y 501,
            // cuerpo `{"message"}` simple; el plugin `RateLimit` ya puso `Retry-After` en segundos.
            if (content is OutgoingContent.NoContent) {
                call.respond(HttpStatusCode.TooManyRequests, buildJsonObject { put("message", "Demasiadas solicitudes seguidas; espera un momento") })
            }
        }
        status(HttpStatusCode.NotFound) { _ ->
            // Solo el 404 sin cuerpo (ninguna ruta coincidió, o una ruta respondió un 404 vacío) se
            // convierte en ApiError; un 404 que una ruta ya respondió con su ApiError se deja intacto.
            if (content is OutgoingContent.NoContent) {
                call.respond(HttpStatusCode.NotFound, ApiError(ErrorCode.NOT_FOUND, "Ruta no encontrada"))
            }
        }
    }
}

private const val BODY_INVALID = "Cuerpo de la petición inválido"

private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean =
    generateSequence(this) { it.cause }.any { it is T }

private fun BadRequestException.rootMessage(): String =
    (generateSequence<Throwable>(this) { it.cause }.last().message ?: message ?: "Petición inválida")
