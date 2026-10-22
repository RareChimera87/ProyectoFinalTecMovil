package me.santiagoreina.parcheu.feature.modes

import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.IsoDate
import me.santiagoreina.parcheu.contract.MeDto
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.core.network.ApiResult

/** Usuario de prueba con la edad verificada en B0 (14-03-2005) y los modos dados. */
fun meDto(modes: Map<Mode, Boolean> = emptyMap(), ageVerified: Boolean = true, parejaConfirmed: Boolean = false) =
    MeDto(
        id = "u-ana",
        displayName = "Ana",
        birthDate = if (ageVerified) IsoDate("2005-03-14") else null,
        ageVerified = ageVerified,
        parejaConfirmed = parejaConfirmed,
        modes = Mode.entries.associateWith { modes[it] == true },
        createdAt = Instant.fromEpochSeconds(0),
    )

/** [ModesRepository] en memoria que registra cada `PUT /v1/me/modes`. */
class FakeModesRepository(var me: ApiResult<MeDto> = ApiResult.Success(meDto())) : ModesRepository {
    val updates = mutableListOf<Pair<Map<Mode, Boolean>, Boolean>>()
    var updateResult: ApiResult<MeDto>? = null

    override suspend fun me(): ApiResult<MeDto> = me

    override suspend fun updateModes(modes: Map<Mode, Boolean>, parejaConfirmed: Boolean): ApiResult<MeDto> {
        updates += modes to parejaConfirmed
        return updateResult ?: ApiResult.Success(
            (me as? ApiResult.Success)?.value?.copy(modes = modes, parejaConfirmed = parejaConfirmed)
                ?: meDto(modes),
        )
    }
}

/** 22-oct-2026: Ana (14-03-2005) tiene 21 años. */
val TODAY = CivilDate(2026, 10, 22)
