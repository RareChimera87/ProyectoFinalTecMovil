package me.santiagoreina.parcheu.feature.basicprofile

import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.CatalogDto
import me.santiagoreina.parcheu.contract.Interest
import me.santiagoreina.parcheu.contract.IsoDate
import me.santiagoreina.parcheu.contract.MeDto
import me.santiagoreina.parcheu.contract.University
import me.santiagoreina.parcheu.contract.UpdateMe
import me.santiagoreina.parcheu.core.network.ApiResult

/** Datos de ejemplo de B0 para pruebas y capturas. */
object BasicProfileSamples {
    val catalog =
        CatalogDto(
            universities =
            listOf(
                University(1, "UIS", "Universidad Industrial de Santander"),
                University(2, "UNAB", "Universidad Autónoma de Bucaramanga"),
                University(12, "UNIPAZ", "Instituto Universitario de la Paz (UNIPAZ)"),
                University(99, "Otra", "Otra institución"),
            ),
            interests =
            listOf(
                Interest("musica", "Música", "music_note"),
                Interest("futbol", "Fútbol", "sports_soccer"),
                Interest("cine", "Cine", "movie"),
                Interest("lectura", "Lectura", "menu_book"),
                Interest("videojuegos", "Videojuegos", "sports_esports"),
                Interest("cafe", "Café", "local_cafe"),
                Interest("viajes", "Viajes", "flight"),
                Interest("programacion", "Programación", "code"),
                Interest("senderismo", "Senderismo", "hiking"),
                Interest("anime", "Anime", "animation"),
            ),
        )

    val newUser = MeDto(id = "u-ana", displayName = "Ana", createdAt = Instant.fromEpochSeconds(0))

    val savedUser =
        newUser.copy(
            birthDate = IsoDate("2004-05-17"),
            ageVerified = true,
            universityId = 1,
            career = "Ingeniería Industrial",
            interests = listOf("musica", "cine", "cafe"),
        )

    val complete =
        BasicProfileForm(
            birthDate = IsoDate("2004-05-17"),
            universityId = 1,
            career = "Ingeniería Industrial",
            interests = listOf("musica", "cine", "cafe"),
        )
}

/** [BasicProfileRepository] en memoria; [calls] registra el orden ("catalog", "me", "age:<fecha>", "update"). */
class FakeBasicProfileRepository(
    var catalogResult: ApiResult<CatalogDto> = ApiResult.Success(BasicProfileSamples.catalog),
    var meResult: ApiResult<MeDto> = ApiResult.Success(BasicProfileSamples.newUser),
) : BasicProfileRepository {
    var verifyResult: ApiResult<MeDto> = ApiResult.Success(BasicProfileSamples.savedUser)
    var updateResult: ApiResult<MeDto> = ApiResult.Success(BasicProfileSamples.savedUser)
    val calls = mutableListOf<String>()
    val updates = mutableListOf<UpdateMe>()

    override suspend fun catalog(): ApiResult<CatalogDto> {
        calls += "catalog"
        return catalogResult
    }

    override suspend fun me(): ApiResult<MeDto> {
        calls += "me"
        return meResult
    }

    override suspend fun verifyAge(birthDate: IsoDate): ApiResult<MeDto> {
        calls += "age:${birthDate.value}"
        return verifyResult
    }

    override suspend fun update(request: UpdateMe): ApiResult<MeDto> {
        calls += "update"
        updates += request
        return updateResult
    }
}
