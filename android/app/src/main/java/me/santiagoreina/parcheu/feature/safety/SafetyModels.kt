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
) {
    /** Primer nombre para los textos ("Reportar a Laura"); "esta persona" si es anónima. */
    val shortName: String
        get() = if (anonymous) "esta persona" else name.trim().substringBefore(' ').ifEmpty { "esta persona" }
}

/** Motivo de H2 con su texto. AI_HARMFUL no aparece: es para contenido de la IA (E8.7). */
data class ReasonOption(val reason: ReportReason, val label: String)

/** Motivos de H2 en el orden del mockup 22b, más autolesión (ADR-0026). */
val ReportReasons: List<ReasonOption> = listOf(
    ReasonOption(ReportReason.HARASSMENT, "Acoso o mensajes ofensivos"),
    ReasonOption(ReportReason.UNWANTED_SEXUAL, "Contenido sexual que no pedí"),
    ReasonOption(ReportReason.UNDERAGE, "Parece menor de edad"),
    ReasonOption(ReportReason.FAKE_OR_SCAM, "Perfil falso o estafa"),
    ReasonOption(ReportReason.SELF_HARM, "Habla de hacerse daño"),
    ReasonOption(ReportReason.OTHER, "Otro motivo"),
)

/** Largo máximo del detalle (lo valida también el servidor). */
const val MAX_REPORT_DETAILS = 1000

/** Texto de H2 (arquitectura.md, corrección 11: la promesa de 24 h se reemplazó). */
const val REPORT_PROMISE = "Tu reporte es confidencial. Lo revisamos lo antes posible; el bloqueo es inmediato."
