package me.santiagoreina.parcheu.api.simulation

import kotlinx.coroutines.CancellationException
import me.santiagoreina.parcheu.api.ai.AiGateway
import me.santiagoreina.parcheu.api.platform.errors.AgeRequired
import me.santiagoreina.parcheu.api.platform.errors.Blocked
import me.santiagoreina.parcheu.api.platform.errors.NotFound
import me.santiagoreina.parcheu.api.platform.errors.PlusRequired
import me.santiagoreina.parcheu.api.platform.errors.ValidationFailed
import me.santiagoreina.parcheu.api.platform.tasks.QueueKind
import me.santiagoreina.parcheu.api.platform.tasks.TaskQueue
import me.santiagoreina.parcheu.api.platform.tasks.TaskRequest
import me.santiagoreina.parcheu.api.quota.QuotaService
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Page
import me.santiagoreina.parcheu.contract.QuotaKind
import me.santiagoreina.parcheu.contract.ScenarioTranscript
import me.santiagoreina.parcheu.contract.SimulationDto
import me.santiagoreina.parcheu.contract.SimulationOrigin
import me.santiagoreina.parcheu.contract.SimulationStatus
import me.santiagoreina.parcheu.contract.SimulationSummary
import me.santiagoreina.parcheu.contract.Speaker
import me.santiagoreina.parcheu.contract.StartSimulation
import me.santiagoreina.parcheu.contract.TranscriptDto
import me.santiagoreina.parcheu.contract.Turn
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.time.Instant as KInstant

/**
 * API de simulaciones (backlog E4.3, arquitectura.md §5.2 y §7.2): aceptar, listar, consultar y
 * transcripción. El trabajo pesado lo hace [SimulationEngine] desde Cloud Tasks.
 */
class SimulationService(
    private val repo: SimulationRepository,
    private val quota: QuotaService,
    private val ai: AiGateway,
    private val tasks: TaskQueue,
    private val people: PersonPresenter,
    private val mirror: SimulationMirror,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(SimulationService::class.java)

    /**
     * `POST /v1/simulations`: valida modo activo en ambos, edad si es Pareja, bloqueos y gemelos;
     * comprueba el presupuesto de IA (503 `AI_BUDGET` **sin** cobrar), cobra el cupo con
     * `ref_id = id de la simulación` y encola `simulate`.
     */
    suspend fun start(requester: UUID, body: StartSimulation): SimulationSummary {
        val other = runCatching { UUID.fromString(body.otherUserId.trim()) }.getOrNull()
            ?: throw ValidationFailed("otherUserId debe ser el id de una persona de tu parche")
        val record = create(requester, requester, other, body.mode, SimulationOrigin.PARCHE, chargeQuota = true)
        return summaryOf(requester, record)
    }

    /**
     * Crea y encola una simulación. [requestedBy] es quien la pidió (null si la origina el sistema);
     * con [chargeQuota] cobra una `SIMULATION` a [requestedBy]. Lo usan `POST /v1/simulations`
     * (PARCHE) y, en sus features, el match al azar (E4.2, RANDOM sin cupo semanal) y las
     * presentaciones (E6.4).
     */
    suspend fun create(
        requestedBy: UUID?,
        userA: UUID,
        userB: UUID,
        mode: Mode,
        origin: SimulationOrigin,
        chargeQuota: Boolean,
    ): SimulationRecord {
        if (userA == userB) throw ValidationFailed("No puedes simularte contigo")
        val a = repo.participant(userA)?.takeIf { !it.deleted } ?: throw NotFound("Usuario no encontrado")
        val b = repo.participant(userB)?.takeIf { !it.deleted } ?: throw NotFound("Usuario no encontrado")
        if (repo.blockedEitherWay(userA, userB)) throw Blocked()
        if (mode !in a.activeModes || mode !in b.activeModes) throw ValidationFailed("El modo ${mode.name} debe estar activo para los dos")
        if (mode == Mode.PAREJA) {
            if (a.isDemo || b.isDemo) throw ValidationFailed("Los usuarios demo no participan en Pareja")
            if (!a.ageVerified || !a.parejaConfirmed || !b.ageVerified || !b.parejaConfirmed) throw AgeRequired()
        }
        val twinA = repo.twinProfile(userA)
        val twinB = repo.twinProfile(userB)
        if (twinA == null || !twinA.personaReady) throw ValidationFailed("Tu gemelo todavía no está listo")
        if (twinB == null || !twinB.personaReady) throw ValidationFailed("El gemelo de la otra persona todavía no está listo")

        // ADR-0010: con el presupuesto agotado se responde 503 antes de cobrar el cupo.
        ai.ensureBudget()

        val id = UUID.randomUUID()
        val plus = requestedBy?.let { quota.isPlus(it) } ?: false
        val scenarios = SimulationScoring.pickScenarios(repo.scenarios(mode), plus, id)
        if (chargeQuota) quota.consume(checkNotNull(requestedBy) { "Cobrar cupo exige quien la pidió" }, QuotaKind.SIMULATION, id.toString())
        val record = SimulationRecord(
            id = id,
            mode = mode,
            userA = userA,
            userB = userB,
            requestedBy = requestedBy,
            origin = origin,
            status = SimulationStatus.QUEUED,
            progress = initialProgress(),
            score = null,
            report = null,
            transcript = StoredTranscript(scenarios.map { StoredScenario(it.id, it.title) }),
            twinVersionA = twinA.version,
            twinVersionB = twinB.version,
            cachedFrom = null,
            provider = null,
            usage = UsageTotals(),
            error = null,
            // Postgres guarda microsegundos: así el cursor de la lista es exacto.
            createdAt = clock.instant().truncatedTo(ChronoUnit.MICROS),
            finishedAt = null,
        )
        try {
            repo.create(record)
            tasks.enqueue(TaskRequest(QueueKind.SIMULATIONS, "/tasks/simulate", "simulate-$id", """{"id":"$id"}"""))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("No se pudo encolar la simulación {}: se devuelve el cupo", id, e)
            if (chargeQuota) quota.refund(QuotaKind.SIMULATION, id.toString())
            runCatching { repo.markFailed(id, "No se pudo encolar: ${e.message}", clock.instant()) }
            throw e
        }
        runCatching {
            mirror.publish(SimulationMirrorDoc(id, listOf(a.firebaseUid, b.firebaseUid), SimulationStatus.QUEUED, record.progress, null, clock.instant()))
        }.onFailure { log.warn("No se pudo crear simulations/{} en Firestore: {}", id, it.message) }
        return repo.find(id) ?: record
    }

    /** `GET /v1/simulations`: las simulaciones del usuario, más recientes primero. */
    suspend fun list(viewer: UUID, cursor: String?, limit: Int?): Page<SimulationSummary> {
        val size = (limit ?: DEFAULT_PAGE).also { if (it !in 1..MAX_PAGE) throw ValidationFailed("limit va de 1 a $MAX_PAGE") }
        val before = cursor?.takeIf { it.isNotBlank() }?.let { SimulationCursor.parse(it) ?: throw ValidationFailed("cursor inválido") }
        val rows = repo.listFor(viewer, before, size + 1)
        val page = rows.take(size)
        val next = if (rows.size > size) page.last().let { SimulationCursor(it.createdAt, it.id).encode() } else null
        return Page(page.map { summaryOf(viewer, it) }, next)
    }

    /** `GET /v1/simulations/{id}`: resumen, progreso y puntaje (si DONE). */
    suspend fun get(viewer: UUID, id: String): SimulationDto {
        val record = visible(viewer, id)
        return SimulationDto(summaryOf(viewer, record), record.progress, record.score.takeIf { record.status == SimulationStatus.DONE })
    }

    /** `GET /v1/simulations/{id}/transcript` (ParcheU+): turnos como ME/OTHER desde quien consulta. */
    suspend fun transcript(viewer: UUID, id: String): TranscriptDto {
        val record = visible(viewer, id)
        if (!quota.isPlus(viewer)) throw PlusRequired("La transcripción completa es de ParcheU+")
        val me = checkNotNull(record.sideOf(viewer))
        return TranscriptDto(
            record.transcript.scenarios.map { s ->
                ScenarioTranscript(s.title, s.turns.map { Turn(if (it.speaker == me) Speaker.ME else Speaker.OTHER, it.text) })
            },
        )
    }

    private suspend fun visible(viewer: UUID, rawId: String): SimulationRecord {
        val id = runCatching { UUID.fromString(rawId) }.getOrNull() ?: throw NotFound()
        val record = repo.find(id) ?: throw NotFound()
        if (record.sideOf(viewer) == null) throw NotFound()
        return record
    }

    private suspend fun summaryOf(viewer: UUID, record: SimulationRecord) = SimulationSummary(
        id = record.id.toString(),
        status = record.status,
        mode = record.mode,
        other = people.present(viewer, record.otherThan(viewer)),
        createdAt = KInstant.fromEpochSeconds(record.createdAt.epochSecond, record.createdAt.nano),
        origin = record.origin,
        score = record.score.takeIf { record.status == SimulationStatus.DONE },
    )

    companion object {
        const val DEFAULT_PAGE = 20
        const val MAX_PAGE = 50
    }
}
