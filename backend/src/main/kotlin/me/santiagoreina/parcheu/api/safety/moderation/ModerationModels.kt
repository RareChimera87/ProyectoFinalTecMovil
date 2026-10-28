package me.santiagoreina.parcheu.api.safety.moderation

import me.santiagoreina.parcheu.contract.ReportReason

/** Categorías que revisa el modelo lite (E8.6, ADR-0026). */
enum class ModerationCategory(val reason: ReportReason) {
    HARASSMENT(ReportReason.HARASSMENT),
    SEXUAL(ReportReason.UNWANTED_SEXUAL),
    HATE(ReportReason.HARASSMENT),
    THREAT(ReportReason.HARASSMENT),
    SELF_HARM(ReportReason.SELF_HARM),
    SPAM(ReportReason.FAKE_OR_SCAM),
    PERSONAL_DATA(ReportReason.OTHER),

    /** Indicios de que quien escribe es menor de 18 (la app es 18+). */
    POSSIBLE_MINOR(ReportReason.UNDERAGE),
    ;

    companion object {
        fun parse(raw: String?): ModerationCategory? = entries.firstOrNull { it.name == raw?.trim()?.uppercase() }
    }
}

/** Severidad que asigna el modelo; solo [HIGH] dispara acciones (ocultar, reportar, recursos). */
enum class ModerationSeverity {
    NONE,
    LOW,
    HIGH,
    ;

    companion object {
        fun parse(raw: String?): ModerationSeverity? = entries.firstOrNull { it.name == raw?.trim()?.uppercase() }
    }
}

data class ModerationFinding(val category: ModerationCategory, val severity: ModerationSeverity)

/** Qué hizo [ModerationService.moderate] con un mensaje; las pruebas y el log lo usan. */
enum class ModerationOutcome {
    /** El mensaje no existe, es del sistema o ya estaba oculto. */
    SKIPPED,

    /** Sin hallazgos de severidad alta. */
    CLEAN,

    /** Se tomó al menos una acción (ocultar, reporte AUTO o recursos de ayuda). */
    ACTED,

    /** El LLM falló: el mensaje sigue visible (fail-open). */
    FAILED,
}
