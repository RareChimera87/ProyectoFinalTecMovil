package me.santiagoreina.parcheu.api.graph.communities

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import me.santiagoreina.parcheu.api.platform.BogotaZone
import me.santiagoreina.parcheu.api.platform.tasks.QueueKind
import me.santiagoreina.parcheu.api.platform.tasks.TaskQueue
import me.santiagoreina.parcheu.api.platform.tasks.TaskRequest
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Job semanal de comunidades y tarea `name-community` (E6.4, arquitectura.md §6.3).
 *
 * - [runWeekly] (`POST /internal/jobs/communities`): lee las aristas, detecta comunidades
 *   ([CommunityDetector]), conserva ids y nombres por Jaccard con la corrida anterior, guarda y encola un
 *   `name-community` por cada comunidad sin nombre. Es idempotente: repetirlo la misma semana con los
 *   mismos datos no cambia nada.
 * - [runIfDue]: lo llama el programador en proceso del homeserver (cada hora): corre una vez por semana a
 *   partir del lunes 03:00 de Bogotá (o en cuanto arranque el servicio si ese momento ya pasó).
 * - [nameCommunity] (`POST /internal/tasks/name-community`, cola `ai-light`): nombra una comunidad con el
 *   modelo lite a partir de rasgos agregados; si ya tiene nombre no hace nada.
 */
class CommunityService(
    private val repo: CommunityRepository,
    private val tasks: TaskQueue,
    private val namer: CommunityNamer,
    private val clock: Clock,
    private val seed: Long = CommunityDetector.SEED,
) {
    private val log = LoggerFactory.getLogger(CommunityService::class.java)
    private val lastRunWeek = AtomicReference<LocalDate?>(null)
    private val lastRequeue = ConcurrentHashMap<UUID, Instant>()

    suspend fun runWeekly(): CommunityRunSummary {
        val week = weekStart(clock)
        val detected = CommunityDetector.detect(repo.connectedEdges(), seed)
        val plan = CommunityDetector.plan(week, detected, repo.all())
        repo.apply(week, plan)
        lastRunWeek.set(week)
        (plan.created + plan.kept.filter { it.name == CommunityDetector.PENDING_NAME }).forEach { enqueueName(it.id) }
        log.info(
            "Comunidades de la semana {}: {} ({} conservadas, {} nuevas, {} eliminadas)",
            week, detected.size, plan.kept.size, plan.created.size, plan.removed.size,
        )
        return CommunityRunSummary(week, detected.size, plan.kept.size, plan.created.size, plan.removed.size)
    }

    /** Corre [runWeekly] si ya pasó el lunes 03:00 (Bogotá) de esta semana y aún no se corrió; devuelve si corrió. */
    suspend fun runIfDue(): Boolean {
        val now = ZonedDateTime.now(clock.withZone(BogotaZone))
        val week = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        if (now.toLocalDateTime().isBefore(week.atTime(RUN_AT))) return false
        // Salidas tempranas: si el proceso se reinició o name-community agotó intentos tras runWeekly, las comunidades que
        // siguen sin nombre se reencolan, como máximo una vez al día cada una (nameCommunity es idempotente), para que un
        // fallo persistente del LLM no genere una llamada por hora y comunidad.
        if (lastRunWeek.get() == week) {
            requeuePendingNames()
            return false
        }
        if (repo.all().any { it.week >= week }) {
            lastRunWeek.set(week)
            requeuePendingNames()
            return false
        }
        runWeekly()
        return true
    }

    suspend fun nameCommunity(id: UUID) {
        val community = repo.find(id) ?: return
        if (community.name != CommunityDetector.PENDING_NAME) return
        val profile = profileOf(community)
        repo.rename(id, namer.name(profile))
    }

    /** Rasgos agregados y modos de [community]; sin ningún dato de persona. */
    suspend fun profileOf(community: StoredCommunity, members: Set<UUID> = community.members): CommunityProfile {
        val modes = repo.connectedEdges()
            .filter { it.a.id in members && it.b.id in members }
            .map { it.mode }
        return CommunityProfile.of(members.size, repo.traitsOf(members), modes)
    }

    private suspend fun requeuePendingNames() {
        val now = clock.instant()
        val pending = repo.all().filter { it.name == CommunityDetector.PENDING_NAME }.map { it.id }.toSet()
        lastRequeue.keys.retainAll(pending)
        pending.forEach { id ->
            val last = lastRequeue[id]
            if (last == null || !last.plus(REQUEUE_EVERY).isAfter(now)) {
                lastRequeue[id] = now
                enqueueName(id)
            }
        }
    }

    private suspend fun enqueueName(id: UUID) {
        try {
            tasks.enqueue(nameTask(id))
        } catch (e: Exception) {
            log.error("No se pudo encolar name-community para la comunidad {}", id, e)
        }
    }

    companion object {
        const val NAME_PATH = "/tasks/name-community"
        val RUN_AT: LocalTime = LocalTime.of(3, 0)

        /** Separación mínima entre dos reencolados de `name-community` de la misma comunidad. */
        val REQUEUE_EVERY: Duration = Duration.ofDays(1)

        /** Lunes de la semana de [clock] en Bogotá. */
        fun weekStart(clock: Clock): LocalDate =
            LocalDate.now(clock.withZone(BogotaZone)).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        fun nameTask(id: UUID) = TaskRequest(
            queue = QueueKind.AI_LIGHT,
            path = NAME_PATH,
            name = "name-community-$id",
            body = buildJsonObject { put("id", JsonPrimitive(id.toString())) }.toString(),
        )
    }
}
