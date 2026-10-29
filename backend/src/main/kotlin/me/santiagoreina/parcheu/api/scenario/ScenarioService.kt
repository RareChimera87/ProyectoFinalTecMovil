package me.santiagoreina.parcheu.api.scenario

import kotlinx.coroutines.CancellationException
import me.santiagoreina.parcheu.api.ai.AiGateway
import me.santiagoreina.parcheu.api.chat.ChatMemberRecord
import me.santiagoreina.parcheu.api.chat.ChatRecord
import me.santiagoreina.parcheu.api.chat.ChatRepository
import me.santiagoreina.parcheu.api.chat.ChatService
import me.santiagoreina.parcheu.api.chat.MessageHiddenHook
import me.santiagoreina.parcheu.api.chat.ScenarioEndedHook
import me.santiagoreina.parcheu.api.graph.EdgeEvent
import me.santiagoreina.parcheu.api.graph.EdgeService
import me.santiagoreina.parcheu.api.platform.Premium
import me.santiagoreina.parcheu.api.platform.errors.AiBudgetExceeded
import me.santiagoreina.parcheu.api.platform.errors.Blocked
import me.santiagoreina.parcheu.api.platform.errors.Forbidden
import me.santiagoreina.parcheu.api.platform.errors.NotFound
import me.santiagoreina.parcheu.api.platform.errors.ScenarioEnded
import me.santiagoreina.parcheu.api.platform.notifier.Notification
import me.santiagoreina.parcheu.api.platform.notifier.Notifier
import me.santiagoreina.parcheu.api.platform.tasks.QueueKind
import me.santiagoreina.parcheu.api.platform.tasks.TaskQueue
import me.santiagoreina.parcheu.api.platform.tasks.TaskRequest
import me.santiagoreina.parcheu.api.quota.QuotaService
import me.santiagoreina.parcheu.api.safety.moderation.MessageModerator
import me.santiagoreina.parcheu.api.safety.moderation.ModerationCategory
import me.santiagoreina.parcheu.api.safety.moderation.ModerationSeverity
import me.santiagoreina.parcheu.api.simulation.JudgeVerdict
import me.santiagoreina.parcheu.api.simulation.LLM_ATTEMPTS
import me.santiagoreina.parcheu.api.simulation.ScenarioDef
import me.santiagoreina.parcheu.api.simulation.Side
import me.santiagoreina.parcheu.api.simulation.SimulationJudge
import me.santiagoreina.parcheu.api.simulation.SimulationRecord
import me.santiagoreina.parcheu.api.simulation.SimulationRepository
import me.santiagoreina.parcheu.api.simulation.SimulationScoring
import me.santiagoreina.parcheu.api.simulation.StoredScenario
import me.santiagoreina.parcheu.api.simulation.StoredTranscript
import me.santiagoreina.parcheu.api.simulation.StoredTurn
import me.santiagoreina.parcheu.api.simulation.UsageTotals
import me.santiagoreina.parcheu.contract.ChatStatus
import me.santiagoreina.parcheu.contract.ChatType
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.NotificationKinds
import me.santiagoreina.parcheu.contract.Progress
import me.santiagoreina.parcheu.contract.ScenarioDto
import me.santiagoreina.parcheu.contract.ScenarioSessionDto
import me.santiagoreina.parcheu.contract.ScenarioSessionStatus
import me.santiagoreina.parcheu.contract.SimulationOrigin
import me.santiagoreina.parcheu.contract.SimulationStatus
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.util.UUID
import kotlin.time.Instant as KInstant

/**
 * Modo escenario (F3, E4.8; arquitectura.md §7.6). El chat SCENARIO lo crea la elección mutua del match (E4.2);
 * aquí viven el acuerdo, el cierre y la evaluación.
 *
 * - **Acuerdo:** cada persona marca 'Llegamos a un acuerdo' (`chat_members.agreed_at`); con las dos, o al
 *   vencer `ends_at`, el chat se cierra ([finalize]).
 * - **Una sola vez:** quien gana el `OPEN -> CLOSED` ([ChatRepository.closeIfOpen]) es el único que sigue; la
 *   evaluación se llama como el chat (`simulations.id = chats.id`), así que repetirla no duplica nada y la tarea
 *   `judge-scenario` se deduplica por nombre.
 * - **Juez:** el mismo [SimulationJudge] con la rúbrica del escenario. Solo recibe la consigna y la transcripción
 *   del chat (Persona A / Persona B): nunca nombres, perfiles, personas ni memorias de los gemelos.
 * - **Resultado:** una simulación con origen SCENARIO y el mismo informe, así F7/F8 funcionan sin cambios.
 */
class ScenarioService(
    private val chats: ChatRepository,
    private val chatService: ChatService,
    private val sims: SimulationRepository,
    private val ai: AiGateway,
    private val quota: QuotaService,
    private val edges: EdgeService,
    /** `internal` para que las pruebas de cableado comprueben que el servicio usa la cola real. */
    internal val tasks: TaskQueue,
    private val notifier: Notifier,
    private val clock: Clock,
    /** `MODERATION_ENABLED=false` también salta la moderación del texto del veredicto. */
    private val moderationEnabled: Boolean = true,
) {
    private val log = LoggerFactory.getLogger(ScenarioService::class.java)
    private val judge = SimulationJudge(ai)
    private val moderator = MessageModerator(ai)

    /** Conecta el 409 `SCENARIO_ENDED` del envío de mensajes con el cierre y el juez (una sola vez). */
    val endedHook = ScenarioEndedHook { chatId -> finalize(chatId) }

    /** Retira de la transcripción ya juzgada el texto de los mensajes que se ocultan después (B-B7). */
    val hiddenHook = MessageHiddenHook { chatId -> redactHidden(chatId) }

    // --- rutas -------------------------------------------------------------------------------

    /** `GET /v1/scenarios?mode=`: los escenarios del modo (o de todos); los premium salen `locked` para quien no es Plus. */
    @Premium("GET /v1/scenarios")
    suspend fun list(viewer: UUID, mode: Mode?): List<ScenarioDto> {
        val plus = quota.isPlus(viewer)
        return (mode?.let { listOf(it) } ?: Mode.entries).flatMap { sims.scenarios(it) }
            .map { ScenarioDto(id = it.id, title = it.title, isBasic = it.isBasic, locked = !it.isBasic && !plus) }
    }

    /**
     * `GET /v1/scenario-sessions/{chatId}`: estado de la sesión de [viewer]. Si el tiempo ya venció y nadie
     * escribió ni acordó, también cierra el chat y lanza al juez (el barrido lo haría igual a más tardar en un minuto).
     */
    suspend fun session(viewer: UUID, rawChatId: String): ScenarioSessionDto {
        val chat = scenarioChat(viewer, rawChatId)
        if (chat.status == ChatStatus.OPEN && (hasEnded(chat) || bothAgreed(chat))) finalize(chat.id)
        return sessionOf(viewer, requireNotNull(chats.chat(chat.id)))
    }

    /**
     * `POST /v1/scenario-sessions/{chatId}/agreement`: marca el acuerdo de [viewer]. Con uno solo el estado es
     * `WAITING_OTHER`; con los dos el chat se cierra y empieza la evaluación (`JUDGING`). Repetirlo no cambia nada.
     * Tras `ends_at` (o con el chat cerrado sin acuerdo de ambos) responde 409 `SCENARIO_ENDED`.
     */
    suspend fun agree(viewer: UUID, rawChatId: String): ScenarioSessionDto {
        val chat = scenarioChat(viewer, rawChatId)
        if (chat.members.any { it.userId != viewer && chats.blockedBetween(viewer, it.userId) }) throw Blocked()
        if (bothAgreed(chat)) {
            finalize(chat.id)
            return sessionOf(viewer, requireNotNull(chats.chat(chat.id)))
        }
        if (chat.status == ChatStatus.CLOSED || hasEnded(chat)) {
            if (chat.status == ChatStatus.OPEN) finalize(chat.id)
            throw ScenarioEnded()
        }
        val updated = chats.agree(chat.id, viewer, clock.instant()) ?: throw NotFound("El chat no existe")
        if (bothAgreed(updated)) finalize(chat.id)
        return sessionOf(viewer, requireNotNull(chats.chat(chat.id)))
    }

    // --- cierre ------------------------------------------------------------------------------

    /**
     * Cierra el chat y lanza la evaluación. Idempotente y seguro ante concurrencia: la evaluación se crea antes de
     * cerrar (así un fallo a medias la deja visible para el barrido) y el cierre lo gana una sola llamada.
     */
    suspend fun finalize(chatId: UUID) {
        val chat = chats.chat(chatId) ?: return
        if (chat.type != ChatType.SCENARIO || chat.status != ChatStatus.OPEN) return
        val scenario = chat.scenarioId?.let { id -> sims.scenarios(chat.mode).firstOrNull { it.id == id } }
        val hasMessages = chats.messages(chatId).isNotEmpty()
        if (scenario != null && hasMessages) ensureEvaluation(chat, scenario)
        if (!chats.closeIfOpen(chatId)) return
        log.info("Escenario {} cerrado ({})", chatId, if (bothAgreed(chat)) "acuerdo" else "tiempo")
        try {
            chatService.mirrorChat(chatId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("No se pudo replicar el cierre del chat {} en Firestore: {}", chatId, e.message)
        }
        if (scenario != null && hasMessages) enqueueJudge(chatId)
    }

    private suspend fun ensureEvaluation(chat: ChatRecord, scenario: ScenarioDef) {
        if (sims.find(chat.id) != null) return
        val a = chat.members[0].userId
        val b = chat.members[1].userId
        val record = SimulationRecord(
            id = chat.id,
            mode = chat.mode,
            userA = a,
            userB = b,
            requestedBy = null,
            origin = SimulationOrigin.SCENARIO,
            status = SimulationStatus.QUEUED,
            progress = Progress(scenarioIndex = 0, turn = 0, of = 1),
            score = null,
            report = null,
            // La transcripción se completa al evaluar, con el chat ya cerrado (nadie escribe después).
            transcript = StoredTranscript(listOf(StoredScenario(scenario.id, scenario.title))),
            twinVersionA = sims.twinProfile(a)?.version,
            twinVersionB = sims.twinProfile(b)?.version,
            cachedFrom = null,
            provider = null,
            usage = UsageTotals(),
            error = null,
            createdAt = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS),
            finishedAt = null,
        )
        try {
            sims.create(record)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Otra llamada la creó al mismo tiempo (llave primaria): lo importante es que exista.
            if (sims.find(chat.id) == null) throw e
        }
    }

    private suspend fun enqueueJudge(chatId: UUID) {
        try {
            tasks.enqueue(TaskRequest(QueueKind.SIMULATIONS, JUDGE_PATH, "judge-scenario-$chatId", """{"id":"$chatId"}"""))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // La evaluación queda QUEUED y el barrido la vuelve a encolar.
            log.error("No se pudo encolar judge-scenario del chat {}: lo reintenta el barrido", chatId, e)
        }
    }

    // --- tarea judge-scenario ------------------------------------------------------------------

    /**
     * `POST /internal/tasks/judge-scenario {id}` (cola `simulations`): evalúa la conversación humana del chat [id].
     * Idempotente: DONE o FAILED no hacen nada; un reintento tras una caída vuelve a evaluar. Los fallos del LLM se
     * reintentan [LLM_ATTEMPTS] veces y luego dejan la evaluación FAILED; los de base de datos se propagan para
     * que la cola reintente.
     */
    suspend fun judge(id: UUID) {
        val record = sims.find(id)?.takeIf { it.origin == SimulationOrigin.SCENARIO } ?: run {
            log.warn("judge-scenario: no hay una evaluación de escenario {}", id)
            return
        }
        if (record.status == SimulationStatus.DONE || record.status == SimulationStatus.FAILED) return
        val chat = chats.chat(id) ?: return
        if (chat.status == ChatStatus.OPEN) {
            // La tarea llegó antes del cierre (caída entre crear y cerrar): se cierra aquí si ya le tocaba.
            if (!hasEnded(chat) && !bothAgreed(chat)) return
            if (chats.closeIfOpen(id)) runCatching { chatService.mirrorChat(id) }
        }
        if (record.status == SimulationStatus.QUEUED) sims.markRunning(id)

        if (chats.blockedBetween(record.userA, record.userB) || listOf(record.userA, record.userB).any { sims.participant(it)?.deleted != false }) {
            sims.markFailed(id, "Una de las cuentas ya no existe o hay un bloqueo", clock.instant())
            return
        }
        val messages = chats.messages(id)
        val turns = messages.mapNotNull { m ->
            val side = when (m.senderId) {
                record.userA -> Side.A
                record.userB -> Side.B
                else -> return@mapNotNull null
            }
            StoredTurn(side, m.text)
        }
        if (turns.isEmpty()) {
            sims.markFailed(id, "Nadie escribió en el escenario", clock.instant())
            return
        }
        val stored = record.transcript.scenarios.firstOrNull() ?: run {
            sims.markFailed(id, "La evaluación no tiene escenario", clock.instant())
            return
        }
        val def = sims.scenarios(record.mode).firstOrNull { it.id == stored.scenarioId } ?: run {
            sims.markFailed(id, "El escenario ${stored.scenarioId} ya no existe", clock.instant())
            return
        }
        val result = try {
            attempt { judge.evaluate(record.mode, def, turns, billedTo = record.userA, humans = true) }
        } catch (e: AiBudgetExceeded) {
            sims.markFailed(id, "Presupuesto diario de IA agotado", clock.instant())
            return
        } catch (e: JudgeFailed) {
            sims.markFailed(id, e.message ?: "Falló el juez", clock.instant())
            return
        }
        val verdict = moderated(id, record.userA, result.verdict)
        val a = sims.twinProfile(record.userA)
        val b = sims.twinProfile(record.userB)
        val report = SimulationScoring.report(a?.traits, b?.traits, listOf(verdict))
        val transcript = StoredTranscript(listOf(stored.copy(turns = turns, finished = true, verdict = verdict)))
        // La arista va antes de DONE: si algo falla después, el reintento la vuelve a aplicar (idempotente).
        edges.apply(EdgeEvent.Simulated(record.userA, record.userB, record.mode, record.id, report.score))
        val done = sims.markDone(
            id, report.score, report, transcript, Progress(0, turns.size, turns.size),
            cachedFrom = null, provider = ai.provider.id, usage = result.usage, now = clock.instant(),
        )
        if (!done) return
        // Un mensaje pudo ocultarse mientras el juez trabajaba, después de leer la lista: se retira ahora.
        // Fail-open: la evaluación ya está DONE y el aviso a las dos personas no debe depender de esto.
        runCatching { redactHidden(id) }.onFailure { log.warn("judge-scenario: no se pudo reconciliar la transcripción de {}", id, it) }
        log.atInfo().setMessage("scenario_judged").addKeyValue("event", "scenario_judged").addKeyValue("chatId", id.toString()).log()
        listOf(record.userA, record.userB).forEach { notifyDone(it, id, report.score) }
    }

    /**
     * El texto libre del veredicto (resumen, razones, choques y notas) lo condiciona lo que escribieron las personas y
     * la otra lo lee sin más revisión: pasa por la moderación existente (una llamada lite). Un hallazgo HIGH que no sea
     * SELF_HARM lo sustituye por un texto genérico y conserva los puntajes; si la moderación falla se sigue con el
     * veredicto tal cual (fail-open, ADR-0026) y se registra `moderation_failed`.
     */
    private suspend fun moderated(id: UUID, billedTo: UUID, verdict: JudgeVerdict): JudgeVerdict {
        val text = (listOf(verdict.summary) + verdict.reasons + verdict.frictions + verdict.dimensions.mapNotNull { it.note })
            .filter { it.isNotBlank() }.joinToString("\n")
        if (!moderationEnabled || text.isEmpty()) return verdict
        val findings = try {
            moderator.review(billedTo, text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.atWarn().setMessage("moderation_failed").addKeyValue("event", "moderation_failed")
                .addKeyValue("chatId", id.toString()).addKeyValue("error", e.javaClass.simpleName).log()
            return verdict
        }
        if (findings.none { it.severity == ModerationSeverity.HIGH && it.category != ModerationCategory.SELF_HARM }) return verdict
        log.atWarn().setMessage("scenario_verdict_redacted").addKeyValue("event", "scenario_verdict_redacted")
            .addKeyValue("chatId", id.toString()).log()
        return verdict.copy(
            reasons = emptyList(), frictions = emptyList(), summary = REDACTED_SUMMARY,
            dimensions = verdict.dimensions.map { it.copy(note = null) },
        )
    }

    /**
     * `simulations.transcript` copia el texto de los mensajes al juzgar (solo los no ocultados de ese momento) y es lo
     * que sirve `GET /v1/simulations/{id}/transcript`. Si la moderación oculta un mensaje después, la transcripción
     * conservaba el texto: se reconcilia con los mensajes visibles del chat y se descartan los turnos que ya no están.
     * No toca el veredicto ni la puntuación (ya calculados). Idempotente; no hace nada si aún no está DONE.
     */
    private suspend fun redactHidden(chatId: UUID) {
        // Compare-and-set con reintento: si otro ocultado escribió entre la lectura y la escritura, se relee.
        repeat(REDACT_ATTEMPTS) {
            val record = sims.find(chatId) ?: return
            if (record.origin != SimulationOrigin.SCENARIO || record.status != SimulationStatus.DONE) return
            val visible = chats.messages(chatId)
                .mapNotNull { m -> (if (m.senderId == record.userA) Side.A else if (m.senderId == record.userB) Side.B else null)?.let { it to m.text } }
                .groupingBy { it }.eachCount().toMutableMap()
            var changed = false
            val scenarios = record.transcript.scenarios.map { sc ->
                val kept = sc.turns.filter { turn ->
                    val key = turn.speaker to turn.text
                    val left = visible[key] ?: 0
                    if (left > 0) visible[key] = left - 1
                    (left > 0).also { if (!it) changed = true }
                }
                sc.copy(turns = kept)
            }
            if (!changed || sims.replaceDoneTranscript(chatId, record.transcript, StoredTranscript(scenarios))) return
        }
        log.warn("judge-scenario: la transcripción de {} cambió en cada intento de reconciliarla", chatId)
    }

    private class JudgeFailed(message: String) : RuntimeException(message)

    private suspend fun <T> attempt(block: suspend () -> T): T {
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
                log.warn("judge-scenario: falló el juez (intento {} de {}): {}", n + 1, LLM_ATTEMPTS, e.message)
            }
        }
        throw JudgeFailed("Falló el juez tras $LLM_ATTEMPTS intentos: ${last?.message}")
    }

    private suspend fun notifyDone(userId: UUID, id: UUID, score: Int) {
        try {
            notifier.notify(
                userId,
                Notification(
                    kind = NotificationKinds.SIMULATION_READY,
                    title = "Ya está el resultado de su escenario",
                    body = "Afinidad: $score/100. Mira cómo les fue.",
                    route = "parcheu://results/$id",
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("judge-scenario: no se pudo avisar a {} del escenario {}: {}", userId, id, e.message)
        }
    }

    // --- barrido (job scenario-sweep) ------------------------------------------------------------

    /**
     * `POST /internal/jobs/scenario-sweep` (cada minuto; `InProcessScheduler` en el homeserver): cierra los chats
     * cuyo tiempo venció sin que nadie escribiera ni acordara y vuelve a encolar las evaluaciones que quedaron
     * sin terminar (la cola en proceso no sobrevive a un reinicio). Devuelve cuántos chats cerró y cuántas encoló.
     */
    suspend fun sweep(): SweepResult {
        val now = clock.instant()
        val expired = chats.expiredOpenScenarios(now, SWEEP_BATCH)
        expired.forEach { runCatching { finalize(it) }.onFailure { e -> log.warn("scenario-sweep: no se pudo cerrar {}", it, e) } }
        val pending = sims.unfinishedScenarios(now.minus(REQUEUE_MIN_AGE), SWEEP_BATCH)
        pending.forEach { enqueueJudge(it.id) }
        if (pending.isNotEmpty()) log.warn("scenario-sweep: {} evaluaciones sin terminar vueltas a encolar", pending.size)
        return SweepResult(closed = expired.size, requeued = pending.size)
    }

    data class SweepResult(val closed: Int, val requeued: Int)

    // --- internos ----------------------------------------------------------------------------

    /** Chat SCENARIO del que [viewer] es miembro. Un id inválido, un chat DIRECT o ajeno no existen para quien pregunta. */
    private suspend fun scenarioChat(viewer: UUID, rawChatId: String): ChatRecord {
        val id = runCatching { UUID.fromString(rawChatId) }.getOrNull() ?: throw NotFound("El chat no existe")
        val chat = chats.chat(id)?.takeIf { it.type == ChatType.SCENARIO } ?: throw NotFound("El chat no existe")
        if (chat.member(viewer) == null) throw Forbidden("No eres miembro de este chat")
        return chat
    }

    private fun hasEnded(chat: ChatRecord) = chat.endsAt?.isAfter(clock.instant()) == false

    private fun bothAgreed(chat: ChatRecord) = chat.members.isNotEmpty() && chat.members.all { it.agreedAt != null }

    private suspend fun sessionOf(viewer: UUID, chat: ChatRecord): ScenarioSessionDto {
        val me: ChatMemberRecord = checkNotNull(chat.member(viewer))
        val other = chat.members.firstOrNull { it.userId != viewer }
        val scenario = chat.scenarioId?.let { id -> sims.scenarios(chat.mode).firstOrNull { it.id == id } }
        val evaluation = sims.find(chat.id)?.takeIf { it.origin == SimulationOrigin.SCENARIO }
        var simulationId: String? = null
        val status = when {
            chat.status == ChatStatus.OPEN -> if (me.agreedAt != null) ScenarioSessionStatus.WAITING_OTHER else ScenarioSessionStatus.OPEN
            evaluation == null -> ScenarioSessionStatus.DONE
            evaluation.status == SimulationStatus.DONE -> {
                simulationId = evaluation.id.toString()
                ScenarioSessionStatus.DONE
            }
            evaluation.status == SimulationStatus.FAILED -> ScenarioSessionStatus.DONE
            else -> ScenarioSessionStatus.JUDGING
        }
        return ScenarioSessionDto(
            status = status,
            simulationId = simulationId,
            title = scenario?.title,
            prompt = scenario?.prompt,
            budgetCop = scenario?.budgetCop,
            endsAt = chat.endsAt?.let { KInstant.fromEpochSeconds(it.epochSecond, it.nano) },
            iAgreed = me.agreedAt != null,
            otherAgreed = other?.agreedAt != null,
        )
    }

    companion object {
        const val REDACTED_SUMMARY = "El juez no pudo redactar un resumen"
        const val JUDGE_PATH = "/tasks/judge-scenario"
        const val SWEEP_PATH = "/jobs/scenario-sweep"
        const val SWEEP_BATCH = 50
        private const val REDACT_ATTEMPTS = 3
        val REQUEUE_MIN_AGE: Duration = Duration.ofMinutes(2)
    }
}
