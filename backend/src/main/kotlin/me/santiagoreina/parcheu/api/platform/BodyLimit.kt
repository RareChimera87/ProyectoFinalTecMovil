package me.santiagoreina.parcheu.api.platform

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.bodylimit.RequestBodyLimit
import io.ktor.server.request.path

/** Tope del cuerpo en `/v1`: los textos del contrato miden como mucho 1000 caracteres y la petición mayor (`twin/answers`, 12 respuestas) pesa menos de 1 KB. */
const val V1_BODY_LIMIT_BYTES: Long = 64L * 1024

/** Tope fuera de `/v1` (`/internal`: tareas de Cloud Tasks y el push de Pub/Sub de RTDN, de pocos KB). */
const val DEFAULT_BODY_LIMIT_BYTES: Long = 256L * 1024

/**
 * Rechaza cuerpos grandes con 413 antes de leerlos enteros (los lee `receive`, que lanza `PayloadTooLargeException`;
 * `ErrorHandling` la mapea a `ApiError(VALIDATION)`). Evita que una petición con un cuerpo enorme agote la memoria
 * de la instancia (arquitectura.md §10).
 */
fun Application.installBodyLimit() {
    install(RequestBodyLimit) {
        bodyLimit { call -> if (call.request.path().startsWith("/v1")) V1_BODY_LIMIT_BYTES else DEFAULT_BODY_LIMIT_BYTES }
    }
}
