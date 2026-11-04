package me.santiagoreina.parcheu.api.identity.retention

import kotlinx.coroutines.CancellationException
import me.santiagoreina.parcheu.api.platform.BogotaZone
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Lo que purgó una ejecución del job, por regla. Las reglas que fallaron van en [failed]. */
data class RetentionReport(val purged: Map<RetentionRule, Int>, val failed: Map<RetentionRule, String> = emptyMap()) {
    val total: Int get() = purged.values.sum()
}

/** Alguna regla falló: las demás sí se aplicaron. El job responde 500 y el siguiente ciclo reintenta (todo es idempotente). */
class RetentionFailed(val report: RetentionReport) :
    RuntimeException("Fallaron las reglas de retención: ${report.failed.keys.joinToString { it.label }}")

/**
 * Job diario de retención (E8.8, ADR-0025): `POST /internal/jobs/retention` (Cloud Scheduler, 03:30 Bogotá)
 * o el [me.santiagoreina.parcheu.api.platform.jobs.InProcessScheduler] del homeserver. Aplica la tabla de
 * arquitectura.md §10.2 con el reloj inyectado. Las reglas son independientes: una que falla no impide
 * las demás, y todas son idempotentes (repetir el job no cambia nada que ya esté purgado).
 *
 * La métrica `retention_purged_total{regla}` es el contador [purgedTotal] y un log INFO por regla con
 * `event = retention_purged`, `regla` y `count` (misma convención que `report_created`; no lleva datos personales).
 */
class RetentionService(
    private val repository: RetentionRepository,
    private val mirror: RetentionMirror,
    /** Reencola las purgas de cuentas atascadas (`AccountDeletionService.sweepStalled`) y devuelve cuántas. */
    private val stalledAccounts: suspend () -> Int,
    private val clock: Clock,
    private val zone: ZoneId = BogotaZone,
) {
    private val log = LoggerFactory.getLogger(RetentionService::class.java)
    private val totals = ConcurrentHashMap<RetentionRule, AtomicLong>()

    /** `retention_purged_total{regla}` acumulado desde que arrancó el proceso. */
    fun purgedTotal(rule: RetentionRule): Long = totals[rule]?.get() ?: 0L

    suspend fun run(): RetentionReport {
        val now = clock.instant()
        val purged = LinkedHashMap<RetentionRule, Int>()
        val failed = LinkedHashMap<RetentionRule, String>()

        suspend fun rule(rule: RetentionRule, block: suspend () -> Int) {
            try {
                val count = block()
                purged[rule] = count
                if (count > 0) {
                    totals.computeIfAbsent(rule) { AtomicLong() }.addAndGet(count.toLong())
                    log.atInfo()
                        .setMessage(PURGED_EVENT)
                        .addKeyValue("event", PURGED_EVENT)
                        .addKeyValue("regla", rule.label)
                        .addKeyValue("count", count)
                        .log()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed[rule] = e.message ?: e.javaClass.simpleName
                log.error("Falló la regla de retención {}", rule.label, e)
            }
        }

        rule(RetentionRule.NOTIFICATIONS) {
            repository.deleteNotificationsBefore(RetentionPolicy.cutoff(now, RetentionPolicy.NOTIFICATIONS))
        }
        rule(RetentionRule.AI_USAGE) {
            repository.anonymizeAiUsageBefore(RetentionPolicy.cutoff(now, RetentionPolicy.AI_USAGE))
        }
        rule(RetentionRule.SIMULATION_DOCS) {
            mirror.deleteSimulationsBefore(RetentionPolicy.cutoff(now, RetentionPolicy.SIMULATION_DOCS))
        }
        rule(RetentionRule.MATCH_TICKET_DOCS) {
            mirror.deleteMatchTicketsExpiredBefore(RetentionPolicy.cutoff(now, RetentionPolicy.MATCH_QUEUE))
        }
        rule(RetentionRule.REPORT_SNAPSHOTS) {
            repository.clearReportSnapshotsResolvedBefore(RetentionPolicy.cutoff(now, RetentionPolicy.REPORT_SNAPSHOTS))
        }
        rule(RetentionRule.SCENARIO_CHATS) { purgeScenarioChats(RetentionPolicy.cutoff(now, RetentionPolicy.SCENARIO_CHATS)) }
        rule(RetentionRule.TRANSCRIPTS) {
            repository.clearTranscriptsBefore(RetentionPolicy.transcriptCutoff(now, zone))
        }
        rule(RetentionRule.MATCH_QUEUE) {
            repository.deleteMatchQueueExpiredBefore(RetentionPolicy.cutoff(now, RetentionPolicy.MATCH_QUEUE))
        }
        rule(RetentionRule.MATCHES) {
            repository.deleteExpiredMatchesBefore(RetentionPolicy.cutoff(now, RetentionPolicy.MATCHES))
        }
        rule(RetentionRule.STALLED_ACCOUNTS) { stalledAccounts() }

        val report = RetentionReport(purged, failed)
        log.info("Retención: {} registros purgados, {} reglas con error", report.total, failed.size)
        if (failed.isNotEmpty()) throw RetentionFailed(report)
        return report
    }

    /**
     * Primero Firestore y después Postgres: si el borrado del espejo falla, el chat sigue en Postgres y el
     * siguiente ciclo lo vuelve a encontrar. Por tandas para no cargar todo en memoria.
     */
    private suspend fun purgeScenarioChats(cutoff: java.time.Instant): Int {
        var total = 0
        repeat(MAX_CHAT_BATCHES) {
            val ids = repository.closedScenarioChats(cutoff, CHAT_BATCH)
            if (ids.isEmpty()) return total
            mirror.deleteChats(ids)
            total += repository.deleteChats(ids)
            if (ids.size < CHAT_BATCH) return total
        }
        return total
    }

    companion object {
        const val JOB_PATH = "/jobs/retention"
        const val PURGED_EVENT = "retention_purged"
        private const val CHAT_BATCH = 200
        private const val MAX_CHAT_BATCHES = 50
    }
}
