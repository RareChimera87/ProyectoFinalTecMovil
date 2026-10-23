package me.santiagoreina.parcheu.data.simulation

import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Page
import me.santiagoreina.parcheu.contract.PersonRef
import me.santiagoreina.parcheu.contract.Progress
import me.santiagoreina.parcheu.contract.QuotaDto
import me.santiagoreina.parcheu.contract.SimulationDto
import me.santiagoreina.parcheu.contract.SimulationStatus
import me.santiagoreina.parcheu.contract.SimulationSummary
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult

fun summary(
    id: String = "sim-1",
    status: SimulationStatus = SimulationStatus.RUNNING,
    refunded: Boolean = false,
    otherId: String? = "user-2",
    score: Int? = null,
    mode: Mode = Mode.ESTUDIO
) = SimulationSummary(
    id = id,
    status = status,
    mode = mode,
    other = PersonRef(id = otherId, label = "Laura", initials = "LP"),
    createdAt = Instant.parse("2026-11-03T15:00:00Z"),
    score = score,
    refunded = refunded
)

fun snapshot(
    status: SimulationStatus = SimulationStatus.RUNNING,
    scenarioIndex: Int = 0,
    turn: Int = 0,
    of: Int = 5
) = SimulationSnapshot(status, Progress(scenarioIndex, turn, of), score = null)

/** Repositorio de simulaciones en memoria que cuenta las llamadas a la API. */
class FakeSimulationRepository : SimulationRepository {
    var getResult: ApiResult<SimulationDto> =
        ApiResult.Success(SimulationDto(summary(), Progress(0, 0, 5)))
    var startResult: ApiResult<SimulationSummary> = ApiResult.Success(summary(id = "sim-2"))
    var pages: Map<String?, ApiResult<Page<SimulationSummary>>> =
        mapOf(null to ApiResult.Success(Page()))
    var quotaResult: ApiResult<QuotaDto> = ApiResult.Failure(AppError.Network)

    var getCalls = 0
        private set
    var startCalls = mutableListOf<Pair<String, Mode>>()
        private set

    override suspend fun get(id: String): ApiResult<SimulationDto> {
        getCalls++
        return getResult
    }

    override suspend fun list(cursor: String?) = pages.getValue(cursor)

    override suspend fun start(otherUserId: String, mode: Mode): ApiResult<SimulationSummary> {
        startCalls += otherUserId to mode
        return startResult
    }

    override suspend fun quota() = quotaResult
}

/** Canal en vivo de prueba: con `replay = 1` se comporta como Firestore, que entrega el estado actual al suscribirse. */
class FakeLiveSource : SimulationLiveSource {
    val flow = MutableSharedFlow<SimulationSnapshot>(replay = 1, extraBufferCapacity = 8)

    override fun observe(id: String): Flow<SimulationSnapshot> = flow
}
