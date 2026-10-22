package me.santiagoreina.parcheu.data.results

import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.DecisionResult
import me.santiagoreina.parcheu.contract.DecisionStatus
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.PersonRef
import me.santiagoreina.parcheu.contract.Progress
import me.santiagoreina.parcheu.contract.QuotaDto
import me.santiagoreina.parcheu.contract.ReportDto
import me.santiagoreina.parcheu.contract.ScenarioTranscript
import me.santiagoreina.parcheu.contract.SimulationDto
import me.santiagoreina.parcheu.contract.SimulationStatus
import me.santiagoreina.parcheu.contract.SimulationSummary
import me.santiagoreina.parcheu.contract.Speaker
import me.santiagoreina.parcheu.contract.TranscriptDto
import me.santiagoreina.parcheu.contract.Turn
import me.santiagoreina.parcheu.contract.Usage
import me.santiagoreina.parcheu.core.network.ApiResult

/** Informe gratis (bloqueado) como lo envía el servidor: sin razones ni choques, con conteos. */
fun lockedReport(unlockAvailable: Boolean = true) = ReportDto(
    score = 82,
    summary = "Se entienden rápido para organizar trabajo en equipo.",
    locked = true,
    unlockAvailable = unlockAvailable,
    reasonsCount = 3,
    frictionsCount = 2,
)

/** Informe completo (Plus o desbloqueado). */
fun fullReport() = ReportDto(
    score = 82,
    summary = "Se entienden rápido para organizar trabajo en equipo.",
    reasons = listOf(
        "Las dos planean con tiempo y reparten tareas desde el primer día",
        "Se dicen las cosas directo y sin drama",
    ),
    frictions = listOf("Una trabaja de noche; la otra, temprano", "Ninguna quiere hacer la presentación"),
    locked = false,
    unlockAvailable = false,
    reasonsCount = 2,
    frictionsCount = 2,
)

fun resultSimulation(isDemo: Boolean = false, mode: Mode = Mode.ESTUDIO) = SimulationDto(
    summary = SimulationSummary(
        id = "sim-1",
        status = SimulationStatus.DONE,
        mode = mode,
        other = PersonRef(anonId = "a_1", label = "Alguien de la UIS", initials = "A", isDemo = isDemo),
        createdAt = Instant.parse("2026-10-20T15:00:00Z"),
        score = 82,
    ),
    progress = Progress(2, 8, 8),
    score = 82,
)

/** Cupo gratis de octubre: el informe vuelve el 1 de noviembre a las 00:00 en Bogotá. */
fun octoberQuota(plus: Boolean = false) = QuotaDto(
    simulations = Usage(1, 5, Instant.parse("2026-10-26T05:00:00Z")),
    randomMatch = Usage(0, 1, Instant.parse("2026-10-21T05:00:00Z")),
    fullReport = Usage(1, 1, Instant.parse("2026-11-01T05:00:00Z")),
    plus = plus,
)

fun sampleTranscript() = TranscriptDto(
    listOf(
        ScenarioTranscript(
            "Trabajo en grupo",
            listOf(Turn(Speaker.ME, "¿Repartimos por partes?"), Turn(Speaker.OTHER, "Dale, yo hago los datos.")),
        ),
    ),
)

/** Repositorio de resultados en memoria que registra las llamadas. */
class FakeResultsRepository : ResultsRepository {
    var simulationResult: ApiResult<SimulationDto> = ApiResult.Success(resultSimulation())
    var reportResult: ApiResult<ReportDto> = ApiResult.Success(lockedReport())
    var unlockResult: ApiResult<ReportDto> = ApiResult.Success(fullReport())
    var decideResult: ApiResult<DecisionResult> = ApiResult.Success(DecisionResult(DecisionStatus.WAITING))
    var transcriptResult: ApiResult<TranscriptDto> = ApiResult.Success(sampleTranscript())
    var quotaResult: ApiResult<QuotaDto> = ApiResult.Success(octoberQuota())

    var unlockCalls = 0
        private set
    val decisions = mutableListOf<Boolean>()
    var reportCalls = 0
        private set

    override suspend fun simulation(id: String) = simulationResult

    override suspend fun report(id: String): ApiResult<ReportDto> {
        reportCalls++
        return reportResult
    }

    override suspend fun unlock(id: String): ApiResult<ReportDto> {
        unlockCalls++
        return unlockResult
    }

    override suspend fun decide(id: String, connect: Boolean): ApiResult<DecisionResult> {
        decisions += connect
        return decideResult
    }

    override suspend fun transcript(id: String) = transcriptResult

    override suspend fun quota() = quotaResult
}
