package me.santiagoreina.parcheu.api.identity.retention

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Reglas de la política de retención de arquitectura.md §10.2 (ADR-0025) que aplica el job diario.
 * [label] es el valor de la etiqueta `regla` de la métrica `retention_purged_total`.
 */
enum class RetentionRule(val label: String) {
    /** `notifications` con más de 90 días: se borran. */
    NOTIFICATIONS("notifications"),

    /** `ai_usage.user_id` pasa a `null` a los 90 días; los agregados de `ai_usage_daily` se conservan. */
    AI_USAGE("ai_usage"),

    /** Documentos `simulations/{id}` de Firestore de simulaciones terminadas hace más de 30 días. */
    SIMULATION_DOCS("simulation_docs"),

    /** Documentos `matchTickets/{uid}` de Firestore vencidos hace más de 7 días. */
    MATCH_TICKET_DOCS("match_ticket_docs"),

    /** `reports.snapshot` pasa a `null` 180 días después de resolverse el reporte. */
    REPORT_SNAPSHOTS("report_snapshots"),

    /** Chats SCENARIO cerrados hace más de 30 días: se borran con sus mensajes (Postgres y Firestore). */
    SCENARIO_CHATS("scenario_chats"),

    /** `simulations.transcript` pasa a `null` a los 12 meses. */
    TRANSCRIPTS("transcripts"),

    /** `match_queue` vencida hace más de 7 días. */
    MATCH_QUEUE("match_queue"),

    /** `matches` vencidos (`EXPIRED`) hace más de 7 días. */
    MATCHES("matches"),

    /** Cuentas con `deleted_at` cuya purga falló: se reencola `purge-user` (no borra datos por sí misma). */
    STALLED_ACCOUNTS("stalled_accounts"),
}

/**
 * Plazos de retención y los instantes límite que de ellos salen. Un dato se purga cuando su fecha es
 * **estrictamente anterior** al límite: lo que cumple el plazo justo en este instante todavía se conserva.
 */
object RetentionPolicy {
    val NOTIFICATIONS: Duration = Duration.ofDays(90)
    val AI_USAGE: Duration = Duration.ofDays(90)
    val SIMULATION_DOCS: Duration = Duration.ofDays(30)
    val REPORT_SNAPSHOTS: Duration = Duration.ofDays(180)
    val SCENARIO_CHATS: Duration = Duration.ofDays(30)
    val MATCH_QUEUE: Duration = Duration.ofDays(7)
    val MATCHES: Duration = Duration.ofDays(7)

    /** El transcript se mide en meses calendario (12) en la zona de Bogotá, no en 365 días. */
    const val TRANSCRIPT_MONTHS = 12L

    fun cutoff(now: Instant, period: Duration): Instant = now.minus(period)

    fun transcriptCutoff(now: Instant, zone: ZoneId): Instant =
        ZonedDateTime.ofInstant(now, zone).minusMonths(TRANSCRIPT_MONTHS).toInstant()
}
