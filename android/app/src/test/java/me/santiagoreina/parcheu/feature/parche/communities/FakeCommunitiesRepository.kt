package me.santiagoreina.parcheu.feature.parche.communities

import me.santiagoreina.parcheu.contract.CommunityDto
import me.santiagoreina.parcheu.contract.PersonRef
import me.santiagoreina.parcheu.core.network.ApiResult

/** Datos de D8: dos comunidades con miembros con nombre y anónimos. */
object CommunitiesSamples {
    val items =
        listOf(
            CommunityDto(
                id = "c1",
                name = "Los del tinto de las 7",
                members =
                listOf(
                    PersonRef(id = "val", label = "Valentina R.", initials = "VR"),
                    PersonRef(id = "and", label = "Andrés M.", initials = "AM"),
                    PersonRef(
                        anonId = "a_alguien000000000000001",
                        label = "Alguien de la UIS",
                        initials = "A"
                    ),
                    PersonRef(id = "seb", label = "Sebastián P.", initials = "SP")
                ),
                size = 5,
                traits = listOf("muy sociables", "organizados")
            ),
            CommunityDto(
                id = "c2",
                name = "Parche de biblioteca",
                members = listOf(
                    PersonRef(id = "lau", label = "Laura G.", initials = "LG"),
                    PersonRef(
                        anonId = "a_alguien000000000000002",
                        label = "Alguien",
                        initials = "A"
                    )
                ),
                size = 3,
                traits = listOf("ambiciosos con la U")
            )
        )

    val plus = CommunitiesContent(count = 2, items = items, plus = true)
    val plusEmpty = CommunitiesContent(count = 0, items = emptyList(), plus = true)
    val free = CommunitiesContent(count = 2, items = emptyList(), plus = false)
}

/** [CommunitiesRepository] en memoria. */
class FakeCommunitiesRepository(
    var loadResult: ApiResult<CommunitiesContent> = ApiResult.Success(CommunitiesSamples.plus)
) : CommunitiesRepository {
    var loads = 0

    override suspend fun load(): ApiResult<CommunitiesContent> {
        loads++
        return loadResult
    }
}
