package me.santiagoreina.parcheu.api.safety

import me.santiagoreina.parcheu.contract.HelpResource

/**
 * Líneas de ayuda que se muestran cuando el motivo del reporte es autolesión (ADR-0026). La vigencia
 * y la cobertura en Santander se verifican antes de la 0.2 (pendiente humano de E8.1).
 */
object HelpResources {
    val colombia: List<HelpResource> = listOf(
        HelpResource(
            name = "Línea 192, opción 4",
            phone = "192",
            description = "Línea nacional de salud mental del Ministerio de Salud. Marca 192 y elige la opción 4.",
        ),
        HelpResource(
            name = "Línea 106",
            phone = "106",
            description = "Línea de escucha y orientación en crisis emocional.",
        ),
        HelpResource(
            name = "Emergencias 123",
            phone = "123",
            description = "Si tú o alguien más está en peligro inmediato.",
        ),
    )
}
