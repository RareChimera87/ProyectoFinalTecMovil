package me.santiagoreina.parcheu.api.platform

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.principal
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.routing.Route
import me.santiagoreina.parcheu.api.platform.auth.FirebaseUser
import kotlin.time.Duration.Companion.minutes

/**
 * Límite de peticiones por persona en las rutas `/v1` que disparan IA (B-M1): enviar respuestas del gemelo,
 * mensajes de chat (moderación), crear simulaciones y el acuerdo del modo escenario (juez). Es un techo
 * generoso, solo para cortar el abuso evidente: lo fino lo hacen los cupos y los topes de gasto (ADR-0010).
 *
 * La clave es el UID de Firebase del token (las rutas van dentro de `authenticate`), así que una persona
 * limitada no afecta a otra. El contador vive en memoria de cada instancia. Las rutas `/internal` (Cloud Tasks,
 * Scheduler) y las tareas en línea de `TASKS_MODE=inline` no pasan por aquí.
 */
val AI_RATE_LIMIT = RateLimitName("ai")

/** `AI_RATE_LIMIT_PER_MINUTE` (por defecto 30): peticiones por minuto y persona en las rutas de [aiRateLimited]. */
fun aiRateLimitPerMinute(vars: Map<String, String>): Int {
    val raw = vars["AI_RATE_LIMIT_PER_MINUTE"]?.takeIf { it.isNotBlank() } ?: return DEFAULT_AI_RATE_LIMIT_PER_MINUTE
    return raw.trim().toIntOrNull()?.takeIf { it >= 1 }
        ?: error("AI_RATE_LIMIT_PER_MINUTE inválido: '$raw' (se espera un entero >= 1)")
}

const val DEFAULT_AI_RATE_LIMIT_PER_MINUTE = 30

/** Instala el plugin `RateLimit` de Ktor con el proveedor [AI_RATE_LIMIT]; al pasarse responde 429 con `Retry-After`. */
fun Application.installAiRateLimit(perMinute: Int = aiRateLimitPerMinute(System.getenv())) {
    install(RateLimit) {
        register(AI_RATE_LIMIT) {
            rateLimiter(limit = perMinute, refillPeriod = 1.minutes)
            requestKey { call -> call.principal<FirebaseUser>()?.uid ?: "" }
        }
    }
}

/** Envuelve las rutas de [build] con el límite por persona [AI_RATE_LIMIT]. */
fun Route.aiRateLimited(build: Route.() -> Unit): Route = rateLimit(AI_RATE_LIMIT, build)
