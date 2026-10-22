package me.santiagoreina.parcheu.api.simulation

import kotlinx.coroutines.CancellationException
import me.santiagoreina.parcheu.api.ai.AiGateway
import me.santiagoreina.parcheu.api.graph.EdgeEvent
import me.santiagoreina.parcheu.api.graph.EdgeService
import me.santiagoreina.parcheu.api.platform.errors.AiBudgetExceeded
import me.santiagoreina.parcheu.api.quota.QuotaService
import me.santiagoreina.parcheu.contract.Progress
import me.santiagoreina.parcheu.contract.QuotaKind
import me.santiagoreina.parcheu.contract.SimulationStatus
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

/**
 * Ejecuta la tarea `/internal/tasks/simulate` (arquitectura.md §7.2, ADR-0009 y ADR-0019).
 *
 * - **Idempotente por id:** DONE no hace nada; FAILED solo asegura la devolución del cupo; RUNNING
 *   retoma desde el último turno o veredicto guardado (nunca repite turnos ni cobra de nuevo).
 * - **Caché:** antes del primer turno, una simulación DONE del mismo par, modo y versiones de gemelo
 *   en los últimos 7 días se copia (`cached_from`) sin llamar al LLM.
 * - **Fallos:** cada turno y cada juez se intentan [LLM_ATTEMPTS] veces; si se agotan, o si el
 *   presupuesto diario se acaba a mitad de camino, la simulación queda FAILED y el cupo se devuelve.
 *   Los errores de infraestructura (base de datos) se propagan para que Cloud Tasks reintente.
 */
class SimulationEngine(
    private val repo: SimulationRepository,
    private val ai: AiGateway,
    private val quota: QuotaService,
    private val edges: EdgeService,
    private val mirror: SimulationMirror,
    private val notifier: SimulationNotifier,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(SimulationEngine::class.java)
    private val judge = SimulationJudge(ai)

    /** Un paso del LLM agotó sus intentos. */
    private class StepFailed(message: String) : RuntimeException(message)

    /** Otra entrega de la misma tarea ya guardó este paso. */
    private class AlreadyAdvanced : RuntimeException()

    suspend fun run(id: UUID) {
        var record = repo.find(id) ?: run {
            log.warn("simulate: la simulación {} no existe", id)
            return
        }
        when (record.status) {
            SimulationStatus.DONE -> return
            SimulationStatus.FAILED -> {
                // Un fallo anterior pudo cortarse antes de devolver el cupo: devolver es idempotente.
                quota.refund(QuotaKind.SIMULATION, id.toString())
                return
            }
            SimulationStatus.QUEUED -> {
                repo.markRunning(id)
                record = repo.find(id) ?: return
                if (record.status != SimulationStatus.RUNNING) return
            }
            SimulationStatus.RUNNING -> Unit
        }
        val participants = participantsOf(record)
        publish(record, participants, SimulationStatus.RUNNING, record.progress, null)
        try {
            execute(record, participants)
        } catch (e: AlreadyAdvanced) {
            log.info("simulate: otra entrega avanzó la simulación {}; esta se detiene", id)
        } catch (e: StepFailed) {
            fail(record, participants, e.message ?: "Fallo del LLM")
        } catch (e: AiBudgetExceeded) {
            fail(record, participants, "Presupuesto diario de IA agotado")
        }
    }

    private suspend fun execute(start: SimulationRecord, participants: List<String>) {
        val a = repo.twinProfile(start.userA)
        val b = repo.twinProfile(start.userB)
        if (a?.persona == null || b?.persona == null || !a.personaReady || !b.personaReady) {
            throw StepFailed("Uno de los gemelos ya no tiene persona lista")
        }
        if (start.transcript.steps == 0 && serveFromCache(start, participants, a.version, b.version)) return

        val defs = repo.scenarios(start.mode).associateBy { it.id }
        val agents = mapOf(
            Side.A to TwinAgent(ai, Side.A, start.userA, a.persona, repo.acceptedMemories(start.userA, start.mode), start.mode),
            Side.B to TwinAgent(ai, Side.B, start.userB, b.persona, repo.acceptedMemories(start.userB, start.mode), start.mode),
        )
        val billedTo = start.requestedBy ?: start.userA
        var transcript = start.transcript
        var progress = start.progress
        for (i in transcript.scenarios.indices) {
            var scenario = transcript.scenarios[i]
            val def = defs[scenario.scenarioId] ?: throw StepFailed("El escenario ${scenario.scenarioId} ya no existe")
            while (!scenario.finished && scenario.turns.size < MAX_TURNS_PER_SCENARIO) {
                val side = if (scenario.turns.size % 2 == 0) Side.A else Side.B
                val reply = attempt("turno ${scenario.turns.size + 1} del escenario ${i + 1}") {
                    agents.getValue(side).reply(def, scenario.turns)
                }
                val turns = scenario.turns + StoredTurn(side, reply.text)
                scenario = scenario.copy(turns = turns, finished = reply.ended || turns.size >= MAX_TURNS_PER_SCENARIO)
                progress = Progress(scenarioIndex = i, turn = turns.size, of = MAX_TURNS_PER_SCENARIO)
                transcript = save(start, transcript, i, scenario, progress, reply.usage)
                publish(start, participants, SimulationStatus.RUNNING, progress, null)
            }
            if (scenario.verdict == null) {
                val result = attempt("juez del escenario ${i + 1}") { judge.evaluate(start.mode, def, scenario.turns, billedTo) }
                scenario = scenario.copy(finished = true, verdict = result.verdict)
                transcript = save(start, transcript, i, scenario, progress, result.usage)
            }
        }
        val report = SimulationScoring.report(a.traits, b.traits, transcript.scenarios.map { checkNotNull(it.verdict) })
        finish(start, participants, report, transcript, progress, cachedFrom = null, provider = ai.provider.id)
    }

    private suspend fun serveFromCache(record: SimulationRecord, participants: List<String>, versionA: Int, versionB: Int): Boolean {
        val va = record.twinVersionA ?: versionA
        val vb = record.twinVersionB ?: versionB
        val since = clock.instant().minus(CACHE_TTL)
        val hit = repo.findCached(record.userA, va, record.userB, vb, record.mode, since, excluding = record.id) ?: return false
        val report = hit.report ?: return false
        val transcript = if (hit.userA == record.userA) hit.transcript else hit.transcript.swapped()
        log.info("simulate: {} sale de la caché de {}", record.id, hit.cachedFrom ?: hit.id)
        finish(record, participants, report, transcript, hit.progress, cachedFrom = hit.cachedFrom ?: hit.id, provider = hit.provider)
        return true
    }

    private suspend fun finish(
        record: SimulationRecord,
        participants: List<String>,
        report: StoredReport,
        transcript: StoredTranscript,
        progress: Progress,
        cachedFrom: UUID?,
        provider: String?,
    ) {
        // La arista va antes de DONE: si algo falla después, el reintento la vuelve a aplicar (idempotente).
        edges.apply(EdgeEvent.Simulated(record.userA, record.userB, record.mode, record.id, report.score))
        val done = repo.markDone(record.id, report.score, report, transcript, progress, cachedFrom, provider, UsageTotals(), clock.instant())
        if (!done) return
        log.info("simulate: {} DONE con puntaje {}", record.id, report.score)
        publish(record, participants, SimulationStatus.DONE, progress, report.score)
        notifyBoth(record, SimulationStatus.DONE, report.score)
    }

    private suspend fun fail(record: SimulationRecord, participants: List<String>, reason: String) {
        log.warn("simulate: {} FAILED: {}", record.id, reason)
        if (repo.markFailed(record.id, reason, clock.instant())) {
            quota.refund(QuotaKind.SIMULATION, record.id.toString())
            val current = repo.find(record.id)?.progress ?: record.progress
            publish(record, participants, SimulationStatus.FAILED, current, null)
            notifyBoth(record, SimulationStatus.FAILED, null)
        } else {
            quota.refund(QuotaKind.SIMULATION, record.id.toString())
        }
    }

    private suspend fun save(
        record: SimulationRecord,
        transcript: StoredTranscript,
        index: Int,
        scenario: StoredScenario,
        progress: Progress,
        usage: UsageTotals,
    ): StoredTranscript {
        val next = transcript.copy(scenarios = transcript.scenarios.toMutableList().also { it[index] = scenario })
        if (!repo.saveStep(record.id, transcript.steps, next, progress, usage)) throw AlreadyAdvanced()
        return next
    }

    private suspend fun <T> attempt(what: String, block: suspend () -> T): T {
        var last: Exception? = null
        repeat(LLM_ATTEMPTS) { n ->
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiBudgetExceeded) {
                throw e
            } catch (e: Exception) {
                last = e
                log.warn("simulate: falló el {} (intento {} de {}): {}", what, n + 1, LLM_ATTEMPTS, e.message)
            }
        }
        throw StepFailed("Falló el $what tras $LLM_ATTEMPTS intentos: ${last?.message}")
    }

    private suspend fun participantsOf(record: SimulationRecord): List<String> =
        listOf(record.userA, record.userB).mapNotNull { repo.participant(it)?.firebaseUid }

    private suspend fun publish(record: SimulationRecord, participants: List<String>, status: SimulationStatus, progress: Progress, score: Int?) {
        try {
            mirror.publish(SimulationMirrorDoc(record.id, participants, status, progress, score, clock.instant()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("simulate: no se pudo actualizar simulations/{} en Firestore: {}", record.id, e.message)
        }
    }

    private suspend fun notifyBoth(record: SimulationRecord, status: SimulationStatus, score: Int?) {
        listOf(record.userA, record.userB).forEach { user ->
            try {
                notifier.finished(user, record.id, status, score)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("simulate: no se pudo avisar a {} de la simulación {}: {}", user, record.id, e.message)
            }
        }
    }
}
