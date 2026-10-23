package me.santiagoreina.parcheu.api.platform.logging

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import org.slf4j.event.Level

/** Cabecera que inyecta Cloud Run / Cloud Trace: `TRACE_ID/SPAN_ID;o=1`. */
const val TRACE_HEADER = "X-Cloud-Trace-Context"

/** Extrae el traceId de `X-Cloud-Trace-Context`; nulo si falta o está vacío. */
fun parseTraceId(header: String?): String? =
    header?.substringBefore('/')?.substringBefore(';')?.trim()?.takeIf { it.isNotEmpty() }

/**
 * Registra cada petición y deja `traceId` y `route` en el MDC de todos los logs de esa petición
 * (el encoder JSON de logback.xml los emite como campos). `userId` (uuid interno) se agrega al MDC
 * cuando exista la sesión con base de datos (E2.1); nunca se registra el correo.
 */
fun Application.installCallLogging() {
    install(CallLogging) {
        level = Level.INFO
        disableDefaultColors()
        mdc("traceId") { call -> parseTraceId(call.request.headers[TRACE_HEADER]) }
        mdc("route") { call -> "${call.request.httpMethod.value} ${call.request.path()}" }
        // La sonda de disponibilidad no debe llenar el log.
        filter { call -> call.request.path() != "/v1/health" }
    }
}
