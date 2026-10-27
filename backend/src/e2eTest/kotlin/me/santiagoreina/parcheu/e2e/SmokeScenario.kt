package me.santiagoreina.parcheu.e2e

import me.santiagoreina.parcheu.contract.AcceptTerms
import me.santiagoreina.parcheu.contract.Answer
import me.santiagoreina.parcheu.contract.Answers
import me.santiagoreina.parcheu.contract.Choice
import me.santiagoreina.parcheu.contract.ConsentsDto
import me.santiagoreina.parcheu.contract.Decision
import me.santiagoreina.parcheu.contract.DecisionResult
import me.santiagoreina.parcheu.contract.DecisionStatus
import me.santiagoreina.parcheu.contract.Enqueue
import me.santiagoreina.parcheu.contract.MatchChoice
import me.santiagoreina.parcheu.contract.MatchDto
import me.santiagoreina.parcheu.contract.MatchStatus
import me.santiagoreina.parcheu.contract.MeDto
import me.santiagoreina.parcheu.contract.MessageDto
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Page
import me.santiagoreina.parcheu.contract.QuestionDto
import me.santiagoreina.parcheu.contract.ReportDto
import me.santiagoreina.parcheu.contract.SendMessage
import me.santiagoreina.parcheu.contract.SessionDto
import me.santiagoreina.parcheu.contract.SimulationDto
import me.santiagoreina.parcheu.contract.SimulationStatus
import me.santiagoreina.parcheu.contract.SimulationSummary
import me.santiagoreina.parcheu.contract.TicketDto
import me.santiagoreina.parcheu.contract.TicketStatus
import me.santiagoreina.parcheu.contract.TwinAnswer
import me.santiagoreina.parcheu.contract.TwinDto
import me.santiagoreina.parcheu.contract.UpdateModes
import java.time.Duration
import java.util.UUID

/**
 * Ganchos para entornos sin Cloud Tasks (el autotest contra `ENV=local`, donde nadie ejecuta las tareas). En dev
 * real son los de [None]: Cloud Tasks entrega `build-persona` y `simulate` por su cuenta.
 */
interface SmokeHooks {
    /** Tras enviar las respuestas: la tarea `build-persona` de [userId] quedó encolada. */
    fun personaQueued(userId: String) {}

    /** Tras crear la simulación: la tarea `simulate` de [simulationId] quedó encolada. */
    fun simulationQueued(simulationId: String) {}

    /** Tras `DELETE /v1/me`: la tarea `purge-user` de [userId] quedó encolada. */
    fun accountDeleted(userId: String) {}

    object None : SmokeHooks
}

/** Plazos de cada espera; la suma debe quedar bajo los 5 minutos del criterio de aceptación. */
data class SmokeBudget(
    val total: Duration = Duration.ofMinutes(5),
    val persona: Duration = Duration.ofSeconds(90),
    val pairing: Duration = Duration.ofSeconds(30),
    val simulation: Duration = Duration.ofSeconds(150),
    val firestore: Duration = Duration.ofSeconds(30),
    val interval: Duration = Duration.ofSeconds(2),
)

/** Lo que el smoke comprobó, para el resumen del job. */
data class SmokeResult(
    val matchId: String,
    val simulationId: String,
    val score: Int,
    val chatId: String,
    val elapsed: Duration,
)

/**
 * Recorrido de punta a punta de dos usuarios de prueba contra una API desplegada (E10.6):
 * sesión -> respuestas del gemelo -> modos -> fila -> match -> simulación (Flash real) -> decisión mutua ->
 * mensaje visible en `chats/{id}/messages`. Al terminar, [cleanup] borra las dos cuentas (`DELETE /v1/me`:
 * la purga limpia Postgres, Firestore y Firebase Auth), que además quedaron marcadas `is_test`.
 */
class SmokeScenario(
    private val api: ApiClient,
    private val auth: TestAuth,
    private val reader: ChatReader,
    private val hooks: SmokeHooks = SmokeHooks.None,
    private val budget: SmokeBudget = SmokeBudget(),
    private val runId: String = UUID.randomUUID().toString().take(8),
    private val log: (String) -> Unit = { println("[e2e] $it") },
    private val nanoTime: () -> Long = System::nanoTime,
) {
    /** Una persona de prueba. El `uid` empieza con `e2e-`: así se reconocen sus cuentas si algún día queda alguna. */
    inner class Player(val label: String, val uid: String, val answerFor: (Int) -> TwinAnswer) {
        lateinit var token: String
        lateinit var me: MeDto
        var created = false
    }

    val ana = Player("ana", "e2e-$runId-a") { i -> if (i % 3 == 0) TwinAnswer.B else TwinAnswer.A }
    val beto = Player("beto", "e2e-$runId-b") { i -> if (i % 4 == 0) TwinAnswer.DEPENDS else TwinAnswer.B }
    private val players get() = listOf(ana, beto)

    private val started = nanoTime()
    private val deadline get() = started + budget.total.toNanos()

    private fun step(name: String, block: () -> Any?) {
        val t0 = nanoTime()
        block()
        log("$name (${Duration.ofNanos(nanoTime() - t0).toMillis()} ms)")
        check(nanoTime() < deadline) { "El smoke superó el presupuesto de ${budget.total.toMinutes()} min en el paso '$name'" }
    }

    fun run(): SmokeResult {
        var matchId = ""
        var simulationId = ""
        var score = 0
        var chatId = ""

        step("sesión y términos de las dos personas") {
            players.forEach { p ->
                p.token = auth.idToken(p.uid)
                val session: SessionDto = api.post("/v1/session", p.token)
                p.created = true
                p.me = session.me
                api.post<AcceptTerms, ConsentsDto>("/v1/me/terms", p.token, AcceptTerms(session.legalTextVersion))
            }
            check(ana.me.id != beto.me.id) { "Las dos sesiones devolvieron el mismo usuario" }
        }

        step("modos: solo Amistad") {
            players.forEach { p ->
                val me: MeDto = api.put("/v1/me/modes", p.token, UpdateModes(mapOf(Mode.AMISTAD to true)))
                check(me.modes[Mode.AMISTAD] == true) { "${p.label}: Amistad no quedó activa" }
            }
        }

        step("respuestas del gemelo y persona lista") {
            val questions: List<QuestionDto> = api.get("/v1/twin/questions", ana.token)
            check(questions.size >= 12) { "El catálogo trae ${questions.size} preguntas y se esperaban 12" }
            val chosen = questions.sortedBy { it.id }.take(12)
            players.forEach { p ->
                val items = chosen.mapIndexed { i, q -> Answer(q.id, p.answerFor(i)) }
                api.post<Answers, TwinDto>("/v1/twin/answers", p.token, Answers(items))
                hooks.personaQueued(p.me.id)
            }
            players.forEach { p ->
                pollUntil<TwinDto>("el gemelo de ${p.label} esté listo (personaReady)", budget.persona, budget.interval) {
                    api.get<TwinDto>("/v1/twin", p.token).takeIf { it.personaReady }
                }
            }
        }

        step("fila: las dos personas entran y se emparejan entre sí") {
            api.post<Enqueue, TicketDto>("/v1/match/queue", ana.token, Enqueue(Mode.AMISTAD))
            api.post<Enqueue, TicketDto>("/v1/match/queue", beto.token, Enqueue(Mode.AMISTAD))
            val tickets = players.map { p ->
                pollUntil("el ticket de ${p.label} tenga match", budget.pairing, budget.interval) {
                    api.get<TicketDto>("/v1/match/queue", p.token).takeIf { it.status == TicketStatus.MATCHED && it.matchId != null }
                }
            }
            matchId = tickets[0].matchId!!
            // Las cuentas is_test solo se emparejan entre sí; si no coinciden, quedó una cuenta de prueba de otra corrida.
            check(tickets[1].matchId == matchId) { "Ana y Beto quedaron en matches distintos (${tickets[0].matchId} y ${tickets[1].matchId})" }
            val view: MatchDto = api.get("/v1/matches/$matchId", beto.token)
            check(view.status == MatchStatus.PENDING_CHOICE) { "El match debía esperar la elección y está ${view.status}" }
        }

        step("elección: simulación") {
            val chosen: MatchDto = api.post("/v1/matches/$matchId/choice", ana.token, Choice(MatchChoice.SIMULATION))
            check(chosen.status == MatchStatus.SIMULATION) { "Elegir simulación dejó el match en ${chosen.status}" }
            val other: MatchDto = api.get("/v1/matches/$matchId", beto.token)
            check(other.status == MatchStatus.SIMULATION) { "Beto ve el match en ${other.status}" }
            val sim = pollUntil("la simulación del match aparezca en la lista de Ana", budget.pairing, budget.interval) {
                api.get<Page<SimulationSummary>>("/v1/simulations", ana.token).items.firstOrNull { it.mode == Mode.AMISTAD }
            }
            simulationId = sim.id
            hooks.simulationQueued(simulationId)
        }

        step("simulación real hasta DONE") {
            val done = pollUntil("la simulación $simulationId termine", budget.simulation, budget.interval) {
                val sim = api.get<SimulationDto>("/v1/simulations/$simulationId", ana.token)
                when (sim.summary.status) {
                    SimulationStatus.DONE -> sim
                    SimulationStatus.FAILED -> throw PollFatal("terminó FAILED (cupo devuelto: ${sim.summary.refunded})")
                    else -> null
                }
            }
            score = checkNotNull(done.score) { "La simulación terminó sin puntaje" }
            check(score in 0..100) { "Puntaje fuera de rango: $score" }
            val report: ReportDto = api.get("/v1/simulations/$simulationId/report", ana.token)
            check(report.score == score && report.summary.isNotBlank()) { "El informe no coincide con la simulación: $report" }
        }

        step("decisión mutua y chat") {
            val first: DecisionResult = api.post("/v1/simulations/$simulationId/decision", ana.token, Decision(connect = true))
            check(first.status == DecisionStatus.WAITING) { "La primera decisión debía quedar WAITING y fue ${first.status}" }
            val second: DecisionResult = api.post("/v1/simulations/$simulationId/decision", beto.token, Decision(connect = true))
            check(second.status == DecisionStatus.CONNECTED) { "La decisión mutua debía conectar y fue ${second.status}" }
            chatId = checkNotNull(second.chatId) { "La conexión no creó un chat" }
            val report: ReportDto = api.get("/v1/simulations/$simulationId/report", ana.token)
            check(report.chatId == chatId) { "Ana ve otro chat (${report.chatId}) que Beto ($chatId)" }
        }

        step("mensaje visible en Firestore") {
            val text = "smoke-$runId"
            val sent: MessageDto = api.post("/v1/chats/$chatId/messages", ana.token, SendMessage(UUID.randomUUID().toString(), text))
            check(sent.text == text) { "El servidor devolvió otro texto: ${sent.text}" }
            pollUntil("el mensaje '$text' sea visible para Beto en chats/$chatId/messages", budget.firestore, budget.interval) {
                reader.visibleTexts(chatId, beto.token).takeIf { text in it }
            }
        }

        return SmokeResult(matchId, simulationId, score, chatId, Duration.ofNanos(nanoTime() - started))
    }

    /**
     * Borra las cuentas creadas (`DELETE /v1/me` -> 202) y comprueba que dejaron de existir para la API. La purga de
     * Postgres, Firestore y Firebase Auth la ejecuta Cloud Tasks (`purge-user`); si esa tarea fallara, el barrido
     * horario `purge-sweep` la reintenta. Seguro de llamar aunque `run` fallara a medias.
     */
    fun cleanup() {
        val failures = mutableListOf<String>()
        players.filter { it.created }.forEach { p ->
            try {
                val response = api.call("DELETE", "/v1/me", p.token)
                check(response.ok) { "DELETE /v1/me respondió ${response.status}: ${response.body.take(200)}" }
                hooks.accountDeleted(p.me.id)
                pollUntil("la cuenta de ${p.label} deje de responder en GET /v1/me", Duration.ofSeconds(30), budget.interval) {
                    api.call("GET", "/v1/me", p.token).takeIf { !it.ok }
                }
                log("limpieza: ${p.label} (${p.uid}) borrada")
            } catch (e: Throwable) {
                failures += "${p.label} (${p.uid}): ${e.message}"
            }
        }
        check(failures.isEmpty()) { "No se pudo limpiar a las cuentas de prueba: ${failures.joinToString("; ")}" }
    }
}
