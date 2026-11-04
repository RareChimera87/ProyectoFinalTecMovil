package me.santiagoreina.parcheu.feature.presentar

import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.IntroductionDto
import me.santiagoreina.parcheu.contract.IntroductionStatus
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.PersonRef
import me.santiagoreina.parcheu.contract.QuotaDto
import me.santiagoreina.parcheu.contract.ReportDto
import me.santiagoreina.parcheu.contract.Usage
import me.santiagoreina.parcheu.core.network.ApiResult

val SAMPLE_FRIENDS = listOf(
    FriendOption("u-vale", "Valentina Ruiz", "VR", 95),
    FriendOption("u-sebas", "Sebas Gómez", "SG", 67),
    FriendOption("u-juli", "Juli Mora", "JM", 43),
    FriendOption("u-nico", "Nico Prada", "NP", 22)
)

fun person(id: String?, label: String, anonId: String? = null) =
    PersonRef(id = id, anonId = anonId, label = label, initials = label.take(1))

/** Informe gratis de E2/E3: sin razones (el servidor las omite), con conteos. */
fun introLockedReport(unlockAvailable: Boolean = true) = ReportDto(
    score = 81,
    summary = "Comparten planes de domingo y el gusto por los conciertos.",
    locked = true,
    unlockAvailable = unlockAvailable,
    reasonsCount = 2,
    frictionsCount = 1
)

fun introFullReport(chatId: String? = null) = ReportDto(
    score = 81,
    summary = "Comparten planes de domingo y el gusto por los conciertos.",
    reasons = listOf(
        "Los dos van a conciertos y comparten playlists",
        "Prefieren planes al aire libre el domingo"
    ),
    frictions = listOf("Uno madruga y el otro no"),
    locked = false,
    unlockAvailable = false,
    reasonsCount = 2,
    frictionsCount = 1,
    chatId = chatId
)

/** E2: lo que ve quien presenta. */
fun introducerView(status: IntroductionStatus, report: ReportDto? = introLockedReport()) =
    IntroductionDto(
        id = "intro-1",
        mode = Mode.AMISTAD,
        introducer = person("u-mari", "Mari González"),
        other = person("u-vale", "Valentina"),
        status = status,
        simulationId = "intro-1",
        report = report.takeIf {
            status != IntroductionStatus.SIMULATING &&
                status != IntroductionStatus.FAILED
        },
        introduced = listOf(person("u-vale", "Valentina"), person("u-sebas", "Sebas"))
    )

/** E3: lo que ve Sebas. */
fun proposalView(
    status: IntroductionStatus = IntroductionStatus.PROPOSED,
    myResponse: Boolean? = null,
    report: ReportDto = introLockedReport()
) = IntroductionDto(
    id = "intro-1",
    mode = Mode.AMISTAD,
    introducer = person("u-mari", "Mari González"),
    other = if (status == IntroductionStatus.CONNECTED) {
        person("u-vale", "Valentina")
    } else {
        person(null, "Alguien de la UIS", anonId = "a_vale")
    },
    status = status,
    simulationId = "intro-1",
    report = report,
    myResponse = myResponse
)

fun weekQuota(used: Int = 2, plus: Boolean = false) = QuotaDto(
    simulations = Usage(used, if (plus) 20 else 5, Instant.parse("2026-10-26T05:00:00Z")),
    randomMatch = Usage(0, 1, Instant.parse("2026-10-24T05:00:00Z")),
    fullReport = Usage(0, 1, Instant.parse("2026-11-01T05:00:00Z")),
    plus = plus
)

/** Repositorio en memoria: cada respuesta se configura y cada llamada queda registrada. */
class FakeIntroductionsRepository : IntroductionsRepository {
    var friendsResult: (
        Mode
    ) -> ApiResult<List<FriendOption>> = { ApiResult.Success(SAMPLE_FRIENDS) }
    var quotaResult: ApiResult<QuotaDto> = ApiResult.Success(weekQuota())
    var introduceResult: ApiResult<IntroductionDto> = ApiResult.Success(
        introducerView(IntroductionStatus.SIMULATING)
    )
    var introductionResults: ArrayDeque<ApiResult<IntroductionDto>> = ArrayDeque()
    var introductionFallback: ApiResult<IntroductionDto> = ApiResult.Success(
        introducerView(IntroductionStatus.READY)
    )
    var respondResult: (
        Boolean
    ) -> ApiResult<IntroductionDto> = {
        ApiResult.Success(introducerView(IntroductionStatus.PROPOSED))
    }
    var unlockResult: ApiResult<ReportDto> = ApiResult.Success(introFullReport())

    val friendCalls = mutableListOf<Mode>()
    val introduceCalls = mutableListOf<Triple<Mode, String, String>>()
    val respondCalls = mutableListOf<Boolean>()
    var introductionCalls = 0
    var unlockCalls = 0

    override suspend fun friends(mode: Mode): ApiResult<List<FriendOption>> {
        friendCalls += mode
        return friendsResult(mode)
    }

    override suspend fun quota(): ApiResult<QuotaDto> = quotaResult

    override suspend fun introduce(
        mode: Mode,
        userA: String,
        userB: String
    ): ApiResult<IntroductionDto> {
        introduceCalls += Triple(mode, userA, userB)
        return introduceResult
    }

    override suspend fun introduction(id: String): ApiResult<IntroductionDto> {
        introductionCalls++
        return introductionResults.removeFirstOrNull() ?: introductionFallback
    }

    override suspend fun respond(id: String, interested: Boolean): ApiResult<IntroductionDto> {
        respondCalls += interested
        return respondResult(interested)
    }

    override suspend fun unlock(simulationId: String): ApiResult<ReportDto> {
        unlockCalls++
        return unlockResult
    }
}
