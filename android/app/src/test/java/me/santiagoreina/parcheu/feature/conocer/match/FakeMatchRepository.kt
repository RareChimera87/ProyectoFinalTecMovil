package me.santiagoreina.parcheu.feature.conocer.match

import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import me.santiagoreina.parcheu.contract.AnonProfile
import me.santiagoreina.parcheu.contract.MatchChoice
import me.santiagoreina.parcheu.contract.MatchDto
import me.santiagoreina.parcheu.contract.MatchStatus
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.TicketDto
import me.santiagoreina.parcheu.contract.TicketStatus
import me.santiagoreina.parcheu.core.network.ApiResult

val TEST_EXPIRES: Instant = Instant.parse("2026-11-04T12:00:00Z")

fun waitingTicket(mode: Mode = Mode.AMISTAD) = TicketDto(TicketStatus.WAITING, mode, TEST_EXPIRES)

fun matchedTicket(matchId: String = "m1", mode: Mode = Mode.AMISTAD) =
    TicketDto(TicketStatus.MATCHED, mode, TEST_EXPIRES, matchId)

fun testMatch(
    scenarioAvailable: Boolean = true,
    isDemo: Boolean = false,
    status: MatchStatus = MatchStatus.PENDING_CHOICE,
    myChoice: MatchChoice? = null,
    mode: Mode = Mode.AMISTAD
) = MatchDto(
    id = "m1",
    mode = mode,
    other = AnonProfile(
        anonId = "anon-1",
        label = "Alguien de la UIS",
        ageRange = "20-22",
        university = "UIS",
        interests = listOf("Rock en español", "Senderismo", "Series"),
        isDemo = isDemo
    ),
    status = status,
    myChoice = myChoice,
    decideBy = Instant.parse("2026-11-04T12:10:00Z"),
    scenarioAvailable = scenarioAvailable
)

/** Repositorio en memoria: las respuestas y los cambios del ticket se controlan desde la prueba. */
class FakeMatchRepository : MatchRepository {
    var enqueueResult: ApiResult<TicketDto> = ApiResult.Success(waitingTicket())
    var cancelResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var matchResults: MutableList<ApiResult<MatchDto>> =
        mutableListOf(ApiResult.Success(testMatch()))
    var chooseResult: ApiResult<MatchDto> =
        ApiResult.Success(
            testMatch(myChoice = MatchChoice.SIMULATION, status = MatchStatus.SIMULATION)
        )

    val tickets = MutableSharedFlow<TicketDto?>(replay = 1)
    val enqueued = mutableListOf<Mode>()
    var cancelCalls = 0
    val chosen = mutableListOf<MatchChoice>()
    var matchCalls = 0

    override suspend fun enqueue(mode: Mode): ApiResult<TicketDto> {
        enqueued += mode
        return enqueueResult
    }

    override suspend fun cancel(): ApiResult<Unit> {
        cancelCalls++
        return cancelResult
    }

    override fun observeTicket(): Flow<TicketDto?> = tickets

    override suspend fun match(id: String): ApiResult<MatchDto> {
        matchCalls++
        return if (matchResults.size > 1) matchResults.removeAt(0) else matchResults.first()
    }

    override suspend fun choose(id: String, choice: MatchChoice): ApiResult<MatchDto> {
        chosen += choice
        return chooseResult
    }
}
