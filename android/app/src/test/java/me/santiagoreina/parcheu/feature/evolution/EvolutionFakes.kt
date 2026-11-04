package me.santiagoreina.parcheu.feature.evolution

import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.HistoryDto
import me.santiagoreina.parcheu.contract.Moment
import me.santiagoreina.parcheu.contract.MomentKind
import me.santiagoreina.parcheu.contract.MonthSnapshot
import me.santiagoreina.parcheu.contract.PersonRef
import me.santiagoreina.parcheu.core.network.ApiResult

/** Datos de D9 del mockup 11: cuatro meses y tres momentos. */
object EvolutionSamples {
    val full =
        HistoryDto(
            months =
            listOf(
                MonthSnapshot("2026-07", connections = 2, newConnections = 2, nodes = 3),
                MonthSnapshot("2026-08", connections = 4, newConnections = 2, nodes = 6),
                MonthSnapshot("2026-09", connections = 6, newConnections = 2, nodes = 8),
                MonthSnapshot("2026-10", connections = 9, newConnections = 3, nodes = 12)
            ),
            moments =
            listOf(
                Moment(
                    MomentKind.FIRST_CONNECTION,
                    Instant.parse("2026-07-10T15:00:00Z"),
                    "Tu primera conexión",
                    other = PersonRef(id = "val", label = "Valentina", initials = "V")
                ),
                Moment(
                    MomentKind.HIGHEST_AFFINITY,
                    Instant.parse("2026-08-12T15:00:00Z"),
                    "Tu afinidad más alta: 92%",
                    value = 92
                ),
                Moment(
                    MomentKind.FIRST_COMMUNITY,
                    Instant.parse("2026-10-20T15:00:00Z"),
                    "Tu primera comunidad"
                )
            )
        )

    val tooEarly =
        HistoryDto(
            months = listOf(
                MonthSnapshot("2026-10", connections = 1, newConnections = 1, nodes = 2)
            ),
            moments = full.moments.take(1)
        )
}

/** [EvolutionRepository] en memoria. */
class FakeEvolutionRepository(
    var result: ApiResult<HistoryDto> = ApiResult.Success(EvolutionSamples.full)
) : EvolutionRepository {
    var loads = 0

    override suspend fun history(): ApiResult<HistoryDto> {
        loads++
        return result
    }
}
