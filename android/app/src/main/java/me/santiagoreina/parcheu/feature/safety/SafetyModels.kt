package me.santiagoreina.parcheu.feature.safety

import me.santiagoreina.parcheu.contract.ReportReason

/**
 * Persona a la que se reporta o bloquea. [id] es su uuid si hay conexión mutua o el `anonId` con el que
 * se la ve ([anonymous]); [name] es la etiqueta que ya muestra la pantalla ("Laura" o "Alguien de la UIS").
 */
data class SafetyTarget(
    val id: String,
    val name: String,
    val anonymous: Boolean = false,
    /** Chat desde el que se abre (F9, F3): da el contexto del reporte y permite silenciar. */
    val chatId: String? = null,
    /** Estado de silencio del chat, si lo hay. */
    val muted: Boolean = false,
    /** Simulación del contenido de la IA que se reporta (F8, transcripción, C4); nulo si el objetivo es una persona. */
    val simulationId: String? = null,
    /** Turno reportado: índice plano sobre los turnos de todos los escenarios, desde 0 (E8.7). */
    val turnIndex: Int? = null
) {
    /** Reporte de contenido generado por la IA (`kind = AI_CONTENT`): no tiene persona a quien bloquear. */
    val isAiContent: Boolean get() = simulationId != null

    /** Primer nombre para los textos ("Reportar a Laura"); "esta persona" si es anónima. */
    val shortName: String
        get() = if (anonymous) {
            "esta persona"
        } else {
            name.trim().substringBefore(' ').ifEmpty {
                "esta persona"
            }
        }
}

/** Motivo de H2 con su texto. AI_HARMFUL solo aparece en el reporte de contenido de la IA (E8.7). */
data class ReasonOption(val reason: ReportReason, val label: String)

/** Motivos de H2 en el orden del mockup 22b, más autolesión (ADR-0026). */
val ReportReasons: List<ReasonOption> = listOf(
    ReasonOption(ReportReason.HARASSMENT, "Acoso o mensajes ofensivos"),
    ReasonOption(ReportReason.UNWANTED_SEXUAL, "Contenido sexual que no pedí"),
    ReasonOption(ReportReason.UNDERAGE, "Parece menor de edad"),
    ReasonOption(ReportReason.FAKE_OR_SCAM, "Perfil falso o estafa"),
    ReasonOption(ReportReason.SELF_HARM, "Habla de hacerse daño"),
    ReasonOption(ReportReason.OTHER, "Otro motivo")
)

/** Motivos del reporte de contenido de la IA (E8.7): el motivo propio y "Otro motivo". */
val AiContentReasons: List<ReasonOption> = listOf(
    ReasonOption(ReportReason.AI_HARMFUL, "Contenido ofensivo, dañino o inapropiado"),
    ReasonOption(ReportReason.OTHER, "Otro motivo")
)

/** Motivos que ofrece H2 según el objetivo. */
fun SafetyTarget.reasons(): List<ReasonOption> =
    if (isAiContent) AiContentReasons else ReportReasons

/** Descripción de la acción de F2, C5, E3, D6 y D2 que abre H1 (criterio de E8.7). */
const val REPORT_OR_BLOCK_DESCRIPTION = "Reportar o bloquear"

/** Descripción de la acción de F8 y la transcripción que reporta contenido de la IA. */
const val REPORT_AI_CONTENT_DESCRIPTION = "Reportar contenido de la IA"

/** Largo máximo del detalle (lo valida también el servidor). */
const val MAX_REPORT_DETAILS = 1000

/** Texto de H2 (arquitectura.md, corrección 11: la promesa de 24 h se reemplazó). */
const val REPORT_PROMISE = "Tu reporte es confidencial. Lo revisamos lo antes posible; el " +
    "bloqueo es inmediato."
