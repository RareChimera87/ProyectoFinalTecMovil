package me.santiagoreina.parcheu.api.identity.deletion

import me.santiagoreina.parcheu.api.platform.db.tables.Notifications
import me.santiagoreina.parcheu.api.platform.db.tables.PersonalJsonPaths
import me.santiagoreina.parcheu.api.platform.db.tables.PurchaseEvents
import me.santiagoreina.parcheu.api.platform.db.tables.Reports
import me.santiagoreina.parcheu.api.platform.db.tables.Simulations
import org.jetbrains.exposed.v1.core.Column

/**
 * Parámetros con nombre de las reglas: `:user` (uuid), `:userText` (uuid como texto), `:pattern`
 * (`%<uuid>%`), `:hashPattern` (`%sha256(uuid)%`, el `obfuscatedAccountId` de Play), `:name` (nombre
 * visible o null) y `:chatRoutes` (arreglo `parcheu://chat/<id>` de sus chats).
 */
enum class PurgeParam { USER, USER_TEXT, PATTERN, HASH_PATTERN, NAME, CHAT_ROUTES }

/**
 * Cómo se limpia una columna jsonb con datos personales (registro [PersonalJsonPaths]) al borrar una
 * cuenta (arquitectura.md §10.2, ADR-0025). [sql] usa `?` y [params] dice qué va en cada uno, en orden.
 * Además de las filas ligadas por columna, cada regla busca el uuid en `col::text`: así una referencia
 * dentro del json que nadie previó también se limpia.
 */
data class JsonbPurgeRule(val column: Column<*>, val description: String, val sql: String, val params: List<PurgeParam>)

object JsonbPurgeRules {
    private fun Column<*>.ref(): String = "${table.tableName}.$name"

    /** Una regla por cada columna de [PersonalJsonPaths.personal]; `JsonbPurgeRulesTest` lo exige. */
    val rules: List<JsonbPurgeRule> = listOf(
        JsonbPurgeRule(
            Reports.snapshot,
            "Instantánea de reportes donde la persona es quien reporta, la reportada o aparece en los mensajes: a null",
            "update reports set snapshot = null where snapshot is not null and " +
                "(reporter_id = ? or target_id = ? or snapshot::text like ?)",
            listOf(PurgeParam.USER, PurgeParam.USER, PurgeParam.PATTERN),
        ),
        simulationRule(Simulations.transcript),
        simulationRule(Simulations.report),
        JsonbPurgeRule(
            Notifications.payload,
            "Avisos propios y de terceros que la nombran, la tienen como actor o citan sus mensajes: se borran",
            "delete from notifications where user_id = ? or payload->>'actorId' = ? or payload::text like ? " +
                "or (?::text is not null and payload->>'title' = ?::text) " +
                "or (kind = 'CHAT_MESSAGE' and payload->>'route' = any(?::text[]))",
            listOf(
                PurgeParam.USER, PurgeParam.USER_TEXT, PurgeParam.PATTERN,
                PurgeParam.NAME, PurgeParam.NAME, PurgeParam.CHAT_ROUTES,
            ),
        ),
        JsonbPurgeRule(
            PurchaseEvents.payload,
            "Eventos de compra (obligación contable): se conservan solo tipo y fecha del evento, sin datos de la cuenta",
            "update purchase_events set payload = jsonb_strip_nulls(jsonb_build_object(" +
                "'redacted', true, " +
                "'eventTimeMillis', payload->'eventTimeMillis', " +
                "'notificationType', payload#>'{subscriptionNotification,notificationType}')) " +
                "where payload->'redacted' is distinct from 'true'::jsonb and (" +
                "purchase_token in (select purchase_token from entitlements where user_id = ?) " +
                "or payload::text like ? or payload::text like ?)",
            listOf(PurgeParam.USER, PurgeParam.PATTERN, PurgeParam.HASH_PATTERN),
        ),
    )

    private fun simulationRule(column: Column<*>) = JsonbPurgeRule(
        column,
        "${column.ref()} de sus simulaciones (el otro conserva puntaje y fecha): a null",
        "update simulations set ${column.name} = null where ${column.name} is not null and " +
            "(user_a = ? or user_b = ? or requested_by = ? or ${column.name}::text like ?)",
        listOf(PurgeParam.USER, PurgeParam.USER, PurgeParam.USER, PurgeParam.PATTERN),
    )
}
