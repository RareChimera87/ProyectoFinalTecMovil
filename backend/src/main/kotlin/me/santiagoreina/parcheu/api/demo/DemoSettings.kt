package me.santiagoreina.parcheu.api.demo

import me.santiagoreina.parcheu.api.matching.MatchingFlags
import me.santiagoreina.parcheu.api.platform.config.Env
import java.time.Duration

/**
 * Configuración de los usuarios demo (ADR-0027), leída de variables de entorno:
 *
 * - `DEMO_USERS_ENABLED`: la misma bandera de la fila ([MatchingFlags]). Por defecto `true` en local y dev y
 *   `false` en prod; la prueba cerrada la enciende a propósito y la 1.0 la apaga.
 * - `DEMO_CONNECT_PROBABILITY` (0..1, por defecto 0,7): probabilidad de que un demo diga "Conectar" tras una
 *   simulación. Es determinista por simulación (ver [DemoConnectPolicy]).
 * - `DEMO_TICKET_DAYS` (1..30, por defecto 7): vigencia de los tickets de fila que siembra `seed-demo`.
 */
data class DemoSettings(
    val enabled: Boolean,
    val connectProbability: Double = DEFAULT_CONNECT_PROBABILITY,
    val ticketTtl: Duration = Duration.ofDays(DEFAULT_TICKET_DAYS),
) {
    init {
        require(connectProbability in 0.0..1.0) { "DEMO_CONNECT_PROBABILITY debe estar entre 0 y 1" }
        require(!ticketTtl.isNegative && !ticketTtl.isZero) { "La vigencia del ticket demo debe ser positiva" }
    }

    companion object {
        const val DEFAULT_CONNECT_PROBABILITY = 0.7
        const val DEFAULT_TICKET_DAYS = 7L

        fun fromEnv(env: Env, vars: Map<String, String> = System.getenv()): DemoSettings {
            val probability = vars["DEMO_CONNECT_PROBABILITY"]?.takeIf { it.isNotBlank() }?.let { raw ->
                raw.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.0..1.0 }
                    ?: error("DEMO_CONNECT_PROBABILITY inválida: '$raw' (se espera un número entre 0 y 1)")
            } ?: DEFAULT_CONNECT_PROBABILITY
            val days = vars["DEMO_TICKET_DAYS"]?.takeIf { it.isNotBlank() }?.let { raw ->
                raw.trim().toLongOrNull()?.takeIf { it in 1..30 } ?: error("DEMO_TICKET_DAYS inválido: '$raw' (se espera 1..30)")
            } ?: DEFAULT_TICKET_DAYS
            return DemoSettings(
                enabled = MatchingFlags.fromEnv(env, vars).demoUsersEnabled,
                connectProbability = probability,
                ticketTtl = Duration.ofDays(days),
            )
        }
    }
}
