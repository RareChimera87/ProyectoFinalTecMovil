package me.santiagoreina.parcheu.api.simulation.report

import me.santiagoreina.parcheu.api.chat.ChatService
import me.santiagoreina.parcheu.api.graph.EdgeEvent
import me.santiagoreina.parcheu.api.graph.EdgeService
import me.santiagoreina.parcheu.api.graph.EdgeSource
import me.santiagoreina.parcheu.api.platform.errors.Blocked
import me.santiagoreina.parcheu.api.platform.errors.Conflict
import me.santiagoreina.parcheu.api.platform.errors.NotFound
import me.santiagoreina.parcheu.api.platform.errors.QuotaExceeded
import me.santiagoreina.parcheu.api.platform.notifier.Notification
import me.santiagoreina.parcheu.api.platform.notifier.Notifier
import me.santiagoreina.parcheu.api.quota.QuotaService
import me.santiagoreina.parcheu.api.simulation.PersonPresenter
import me.santiagoreina.parcheu.api.simulation.SimulationRecord
import me.santiagoreina.parcheu.api.simulation.SimulationRepository
import me.santiagoreina.parcheu.api.simulation.StoredReport
import me.santiagoreina.parcheu.contract.Decision
import me.santiagoreina.parcheu.contract.DecisionResult
import me.santiagoreina.parcheu.contract.DecisionStatus
import me.santiagoreina.parcheu.contract.NotificationKinds
import me.santiagoreina.parcheu.contract.QuotaKind
import me.santiagoreina.parcheu.contract.ReportDto
import me.santiagoreina.parcheu.contract.SimulationStatus
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

/**
 * Informe y decisión mutua de una simulación (backlog E4.6, arquitectura.md §5.2).
 *
 * - **Redacción en el servidor:** un usuario gratis que no desbloqueó el informe recibe solo puntaje,
 *   resumen y conteos; `reasons`, `frictions` y `dimensions` no viajan (el borroso de F7 es decorativo).
 * - **Desbloqueo:** gasta el informe completo del mes (`usage_events` FULL_REPORT con
 *   `ref_id = report:<simulación>:<usuario>`) y queda en `report_unlocks`. Plus no gasta nada.
 * - **Decisión:** se guarda una vez por persona en `connection_decisions` y no toca la arista: la fila
 *   de `edges` es una sola por par y modo (ADR-0003), así que marcarla PENDING le diría a quien aún no
 *   decidió (o dijo "Ahora no") que la otra persona quiere conectar, y eso es de Plus (E6.6). Si las dos
 *   conectan: arista CONNECTED, chat DIRECT con [ChatService] y aviso a ambas; el nombre se revela solo
 *   entonces. "Ahora no" no avisa a nadie ni se revela: quien conecta después ve WAITING.
 * - **409 puro:** repetir una decisión ya resuelta no escribe nada (ni reabre un chat cerrado por un
 *   bloqueo, E8.1). La única excepción es el par completo sin chat (la conexión falló a medias).
 */
class ReportService(
    private val simulations: SimulationRepository,
    private val repo: ReportRepository,
    private val quota: QuotaService,
    private val edges: EdgeService,
    private val chat: ChatService,
    private val notifier: Notifier,
    private val people: PersonPresenter,
    private val clock: Clock,
) : ReportAccess {
    private val log = LoggerFactory.getLogger(ReportService::class.java)

    /** `GET /v1/simulations/{id}/report`. */
    suspend fun report(viewer: UUID, id: String): ReportDto {
        val record = finished(viewer, id)
        return build(viewer, record, checkNotNull(record.report))
    }

    /**
     * `POST /v1/simulations/{id}/report:unlock`. Idempotente: si ya estaba desbloqueado (o es Plus)
     * devuelve el informe completo sin cobrar. Sin cupo → 402 `QUOTA_EXCEEDED(kind=FULL_REPORT)`.
     */
    suspend fun unlock(viewer: UUID, id: String): ReportDto {
        val record = finished(viewer, id)
        if (!hasFullAccess(viewer, record.id)) {
            val refId = "report:${record.id}:$viewer"
            quota.consume(viewer, QuotaKind.FULL_REPORT, refId)
            try {
                repo.recordUnlock(viewer, record.id, clock.instant())
            } catch (e: CancellationException) {
                quota.refund(QuotaKind.FULL_REPORT, refId)
                throw e
            } catch (e: Exception) {
                quota.refund(QuotaKind.FULL_REPORT, refId)
                throw e
            }
        }
        return build(viewer, record, checkNotNull(record.report))
    }

    /** `POST /v1/simulations/{id}/decision`. Repetir la decisión → 409 CONFLICT. */
    suspend fun decide(viewer: UUID, id: String, body: Decision): DecisionResult {
        val record = finished(viewer, id)
        val other = record.otherThan(viewer)
        if (simulations.blockedEitherWay(viewer, other)) throw Blocked()
        val saved = when (val outcome = repo.decide(record.id, viewer, body.connect, clock.instant())) {
            DecideOutcome.AlreadyDecided -> {
                // Reintento de quien completó el par cuando la conexión falló a medias (arista/chat/avisos):
                // si todavía no hay chat DIRECT, se repara de forma idempotente en vez de responder 409.
                // Con el chat ya creado (abierto o cerrado) el 409 es puro: solo lecturas, nada se reabre ni
                // se reescribe. Si el espejo de Firestore falló al conectar, lo repara el primer `send`.
                val decisions = repo.decisions(record.id)
                val mutual = body.connect && decisions[viewer] == true && decisions[other] == true
                val incomplete = mutual && chat.findDirect(viewer, other, record.mode) == null
                if (!incomplete) throw Conflict("Ya decidiste sobre esta simulación")
                return DecisionResult(DecisionStatus.CONNECTED, connect(record, viewer, other).toString())
            }
            is DecideOutcome.Saved -> outcome.all
        }
        val otherChoice = saved[other]
        // Las decisiones unilaterales no tocan la arista (§5.2): ni el interés de quien conecta llega al grafo
        // de la otra persona (contenido de Plus, E6.6) ni un "Ahora no" llega al de quien conectó.
        return when {
            !body.connect -> DecisionResult(DecisionStatus.DECLINED)
            // Nadie más decidió, o la otra persona dijo "Ahora no" (no se revela): WAITING en ambos casos.
            otherChoice != true -> DecisionResult(DecisionStatus.WAITING)
            else -> {
                // Solo la decisión que completa el par llega aquí (repo.decide las serializa): un único
                // chat, una única arista CONNECTED y un único aviso por persona.
                val chatId = connect(record, viewer, other)
                DecisionResult(DecisionStatus.CONNECTED, chatId.toString())
            }
        }
    }

    /** ¿[viewer] ve el informe completo (y la transcripción)? Plus o informe desbloqueado. */
    override suspend fun hasFullAccess(viewer: UUID, simulationId: UUID): Boolean =
        quota.isPlus(viewer) || repo.isUnlocked(viewer, simulationId)

    private suspend fun connect(record: SimulationRecord, viewer: UUID, other: UUID): UUID {
        edges.apply(EdgeEvent.Connected(viewer, other, record.mode, EdgeSource.DECISION))
        val direct = chat.createDirectTolerant(viewer, other, record.mode)
        listOf(viewer, other).forEach { user ->
            try {
                val name = people.present(user, record.otherThan(user)).takeIf { it.id != null }?.label
                notifier.notify(
                    user,
                    Notification(
                        kind = NotificationKinds.NEW_CONNECTION,
                        title = "¡Conectaron!",
                        body = name?.let { "Tú y $it quieren conectar. Ya pueden hablar." } ?: "Las dos personas quieren conectar. Ya pueden hablar.",
                        route = "parcheu://chat/${direct.id}",
                        actorId = record.otherThan(user),
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // El aviso no deshace la conexión: el chat ya existe y aparece en la lista.
                log.warn("No se pudo avisar la conexión de la simulación {} a {}: {}", record.id, user, e.message)
            }
        }
        return direct.id
    }

    private suspend fun build(viewer: UUID, record: SimulationRecord, stored: StoredReport): ReportDto {
        val full = hasFullAccess(viewer, record.id)
        val other = record.otherThan(viewer)
        val decisions = repo.decisions(record.id)
        val mine = decisions[viewer]
        val mutual = mine == true && decisions[other] == true
        val otherName = if (mutual) people.present(viewer, other).takeIf { it.id != null }?.label else null
        val chatId = if (mutual && !simulations.blockedEitherWay(viewer, other)) {
            // Solo lectura: GET no escribe en Postgres ni en Firestore. El chat lo crea la decisión.
            chat.findDirect(viewer, other, record.mode)?.id?.toString()
        } else {
            null
        }
        return ReportDto(
            score = stored.score,
            summary = stored.summary,
            reasons = stored.reasons.takeIf { full },
            frictions = stored.frictions.takeIf { full },
            dimensions = stored.dimensions.takeIf { full },
            locked = !full,
            unlockAvailable = !full && canUnlock(viewer),
            myDecision = mine,
            // Solo con conexión mutua: un "Ahora no" ajeno nunca se revela (y saber que decidió es de Plus, E6.6).
            otherDecided = mutual,
            otherName = otherName,
            reasonsCount = stored.reasons.size,
            frictionsCount = stored.frictions.size,
            chatId = chatId,
        )
    }

    private fun canUnlock(viewer: UUID): Boolean = try {
        quota.ensureAvailable(viewer, QuotaKind.FULL_REPORT)
        true
    } catch (_: QuotaExceeded) {
        false
    }

    /** Simulación DONE con informe en la que participa [viewer]; si no, 404 (no revela si existe). */
    private suspend fun finished(viewer: UUID, rawId: String): SimulationRecord {
        val id = runCatching { UUID.fromString(rawId) }.getOrNull() ?: throw NotFound()
        val record = simulations.find(id) ?: throw NotFound()
        if (record.sideOf(viewer) == null) throw NotFound()
        if (record.status != SimulationStatus.DONE || record.report == null) throw NotFound("El informe todavía no está listo")
        return record
    }
}

/** Lo que la transcripción (E4.3) consulta para saber si alguien ve el contenido completo. */
fun interface ReportAccess {
    suspend fun hasFullAccess(viewer: UUID, simulationId: UUID): Boolean
}
