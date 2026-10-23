package me.santiagoreina.parcheu.api.identity.deletion

import me.santiagoreina.parcheu.api.platform.db.tables.Notifications
import me.santiagoreina.parcheu.api.platform.db.tables.PersonalJsonPaths
import me.santiagoreina.parcheu.api.platform.db.tables.PurchaseEvents
import me.santiagoreina.parcheu.api.platform.db.tables.Reports
import me.santiagoreina.parcheu.api.platform.db.tables.Simulations
import me.santiagoreina.parcheu.contract.NotificationKinds
import org.jetbrains.exposed.v1.core.Column

/**
 * Parámetros con nombre de las reglas: `:user` (uuid), `:userText` (uuid como texto), `:pattern`
 * (`%<uuid>%`), `:hashPattern` (`%sha256(uuid)%`, el `obfuscatedAccountId` de Play), `:name` (nombre
 * visible o null), `:chatRoutes` (arreglo `parcheu://chat/<id>` de sus chats) y `:counterparts` (uuid[] de
 * quienes compartieron con ella chat, arista, simulación o match).
 */
enum class PurgeParam { USER, USER_TEXT, PATTERN, HASH_PATTERN, NAME, CHAT_ROUTES, COUNTERPARTS }

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
        // El nombre visible es texto libre: solo cuenta en avisos cuyo título es el nombre de una persona
        // (mensajes y solicitudes) y que recibió alguien que la conoce. Así no se borran avisos de terceros
        // sin relación con un amigo homónimo, ni avisos del sistema de nadie aunque se llame como su título.
        JsonbPurgeRule(
            Notifications.payload,
            "Avisos propios y de sus contrapartes que la nombran, la tienen como actor o citan sus mensajes: se borran",
            "delete from notifications where user_id = ? or payload->>'actorId' = ? or payload::text like ? " +
                "or (?::text is not null and kind in ('${NotificationKinds.CHAT_MESSAGE}', '${NotificationKinds.CONNECT_REQUEST}') " +
                "and payload->>'title' = ?::text and user_id = any(?::uuid[])) " +
                "or (kind = '${NotificationKinds.CHAT_MESSAGE}' and payload->>'route' = any(?::text[])) " +
                // INTRODUCTION_REQUEST (E6.2) cita en el cuerpo a la persona candidata, que no es actor ni destino:
                // se identifica por la solicitud (aún existe, el CASCADE de users viene después), no por el nombre.
                "or (kind = '${NotificationKinds.INTRODUCTION_REQUEST}' and exists (select 1 from intro_requests r " +
                "where r.target_user_id = ? and r.via_user_id = notifications.user_id and r.requester_id::text = payload->>'actorId'))",
            listOf(
                PurgeParam.USER, PurgeParam.USER_TEXT, PurgeParam.PATTERN,
                PurgeParam.NAME, PurgeParam.NAME, PurgeParam.COUNTERPARTS, PurgeParam.CHAT_ROUTES, PurgeParam.USER,
            ),
        ),
        JsonbPurgeRule(
            PurchaseEvents.payload,
            "Eventos de compra (obligación contable) de todos sus tokens: se conservan solo tipo y fecha, sin token ni cuenta",
            "update purchase_events set payload = case when payload->'redacted' is not distinct from 'true'::jsonb then payload " +
                "else jsonb_strip_nulls(jsonb_build_object(" +
                "'redacted', true, " +
                "'eventTimeMillis', payload->'eventTimeMillis', " +
                "'notificationType', payload#>'{subscriptionNotification,notificationType}')) end, " +
                "purchase_token = null, user_id = null " +
                "where user_id = ? " +
                "or purchase_token in (select purchase_token from entitlements where user_id = ?) " +
                "or purchase_token in (select purchase_token from purchase_events where user_id = ?) " +
                "or payload::text like ? or payload::text like ?",
            listOf(PurgeParam.USER, PurgeParam.USER, PurgeParam.USER, PurgeParam.PATTERN, PurgeParam.HASH_PATTERN),
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
