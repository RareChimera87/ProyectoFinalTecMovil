package me.santiagoreina.parcheu.api.platform.tasks

import me.santiagoreina.parcheu.api.platform.config.Env

/**
 * Cómo se ejecutan las tareas asíncronas (`TASKS_MODE`, ADR-0033).
 *
 * - [CLOUD]: Cloud Tasks real (dev/prod en GCP).
 * - [INLINE]: cola en proceso con reintentos, para el homeserver (una sola instancia, sin Cloud Tasks).
 * - [FAKE]: cola Fake que registra (y a veces ejecuta) las tareas; solo `ENV=local` y tests.
 */
enum class TasksMode {
    CLOUD,
    INLINE,
    FAKE,
    ;

    companion object {
        /** Vacío: [FAKE] en local y [CLOUD] en el resto (comportamiento previo). */
        fun fromEnv(env: Env, vars: Map<String, String>): TasksMode {
            val raw = vars["TASKS_MODE"]?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            val mode = when (raw) {
                null -> if (env == Env.LOCAL) FAKE else CLOUD
                "cloud" -> CLOUD
                "inline" -> INLINE
                "fake" -> FAKE
                else -> throw IllegalArgumentException("TASKS_MODE inválido '$raw': usa cloud, inline o fake")
            }
            if (mode == INLINE) {
                require(env != Env.PROD) { "TASKS_MODE=inline no se admite con ENV=prod: usa Cloud Tasks" }
                val cloudRun = listOf("K_SERVICE", "CLOUD_RUN_JOB").any { !vars[it].isNullOrBlank() }
                require(!cloudRun) { "TASKS_MODE=inline no se admite en Cloud Run (K_SERVICE/CLOUD_RUN_JOB): usa Cloud Tasks" }
            }
            require(mode != FAKE || env == Env.LOCAL) { "TASKS_MODE=fake solo se admite con ENV=local" }
            return mode
        }
    }
}
