package me.santiagoreina.parcheu.api.platform.db.tables

import org.jetbrains.exposed.v1.core.Column

/**
 * Clasificación de las columnas jsonb (arquitectura.md §4, ADR-0025). La purga de cuentas (E8.3) y
 * la retención (E8.8) limpian las de [personal]. Un test falla si aparece una columna jsonb sin
 * clasificar: al agregar una, ponla en una de las dos listas.
 */
object PersonalJsonPaths {
    /** Contienen datos personales: se limpian al borrar la cuenta o al vencer su plazo. */
    val personal: List<Column<*>> = listOf(
        Reports.snapshot,
        Simulations.transcript,
        Simulations.report,
        Notifications.payload,
        PurchaseEvents.payload,
    )

    /** Sin datos personales. */
    val nonPersonal: List<Column<*>> = listOf(
        Scenarios.rubric,
        Questions.weights,
        Simulations.progress,
    )
}
