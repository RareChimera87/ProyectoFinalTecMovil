package me.santiagoreina.parcheu.feature.privacysettings

import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.PrivacyDto
import me.santiagoreina.parcheu.contract.Visibility
import me.santiagoreina.parcheu.core.network.ApiResult

/** Estado de H3 como lo deja el onboarding: Amistad y Estudio activos y aprendiendo, Pareja apagado. */
fun privacyDto(
    visibility: Visibility = Visibility.ANONYMOUS,
    modes: Map<Mode, Boolean> = mapOf(Mode.AMISTAD to true, Mode.ESTUDIO to true),
    learning: Map<Mode, Boolean> = mapOf(Mode.AMISTAD to true, Mode.ESTUDIO to true)
) = PrivacyDto(
    visibility = visibility,
    modes = Mode.entries.associateWith { modes[it] == true },
    learning = Mode.entries.associateWith { learning[it] == true }
)

/** [PrivacySettingsRepository] en memoria que replica al servidor y registra cada `PATCH`. */
class FakePrivacySettingsRepository(var current: PrivacyDto = privacyDto()) :
    PrivacySettingsRepository {
    var loadResult: ApiResult<PrivacyDto>? = null
    var updateResult: ApiResult<PrivacyDto>? = null
    val updates = mutableListOf<Pair<Visibility?, Map<Mode, Boolean>?>>()

    override suspend fun privacy(): ApiResult<PrivacyDto> = loadResult ?: ApiResult.Success(current)

    override suspend fun update(
        visibility: Visibility?,
        learning: Map<Mode, Boolean>?
    ): ApiResult<PrivacyDto> {
        updates += visibility to learning
        updateResult?.let { return it }
        current = current.copy(
            visibility = visibility ?: current.visibility,
            learning = current.learning + learning.orEmpty()
        )
        return ApiResult.Success(current)
    }
}
