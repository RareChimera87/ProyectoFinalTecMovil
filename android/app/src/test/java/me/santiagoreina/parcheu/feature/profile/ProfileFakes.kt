package me.santiagoreina.parcheu.feature.profile

import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.CatalogDto
import me.santiagoreina.parcheu.contract.IsoDate
import me.santiagoreina.parcheu.contract.MeDto
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Trait
import me.santiagoreina.parcheu.contract.TraitKey
import me.santiagoreina.parcheu.contract.TwinDto
import me.santiagoreina.parcheu.contract.TwinPoint
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.feature.basicprofile.BasicProfileSamples

/** Datos de ejemplo de C3 para pruebas y capturas. */
object ProfileSamples {
    val me =
        MeDto(
            id = "u-ana",
            displayName = "Ana Gómez",
            career = "Ingeniería Industrial",
            universityId = 1,
            interests = listOf("musica", "cine", "cafe"),
            birthDate = IsoDate("2004-05-17"),
            ageVerified = true,
            modes = mapOf(Mode.AMISTAD to true, Mode.PAREJA to false, Mode.ESTUDIO to true),
            createdAt = Instant.fromEpochSeconds(0)
        )

    val twin =
        TwinDto(
            traits =
            listOf(
                Trait(TraitKey.SOCIAL_ENERGY, 0.8),
                Trait(TraitKey.PLANNING, 0.55),
                Trait(TraitKey.HUMOR, 0.7)
            ),
            level = 6,
            xp = 340,
            xpNext = 500,
            version = 3,
            personaReady = true
        )

    private fun points(vararg values: Int) =
        values.mapIndexed { i, v -> TwinPoint("2026-%02d".format(1 + i / 4), v, level = 6) }

    val zero = emptyList<TwinPoint>()
    val one = points(42)
    val three = points(40, 52, 61)

    /** 16 semanas (4 meses): solo datos demo, nunca del servidor real. */
    val sixteen = points(30, 33, 35, 38, 41, 44, 45, 50, 52, 55, 58, 60, 63, 66, 70, 78)
}

class FakeProfileRepository(
    var meResult: ApiResult<MeDto> = ApiResult.Success(ProfileSamples.me),
    var twinResult: ApiResult<TwinDto> = ApiResult.Success(ProfileSamples.twin),
    var evolutionResult: ApiResult<List<TwinPoint>> = ApiResult.Success(ProfileSamples.zero),
    var catalogResult: ApiResult<CatalogDto> = ApiResult.Success(BasicProfileSamples.catalog)
) : ProfileRepository {
    var loads = 0
        private set

    override suspend fun me(): ApiResult<MeDto> {
        loads++
        return meResult
    }

    override suspend fun catalog() = catalogResult

    override suspend fun twin() = twinResult

    override suspend fun evolution() = evolutionResult
}
