package me.santiagoreina.parcheu.feature.parche.fof

import me.santiagoreina.parcheu.contract.FofCandidate
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.feature.parche.ParcheSamples

/** Datos de D6 del mockup 09a: el grafo de D1 más amigos de amigos (con su amigo en común). */
object FofSamples {
    val candidates =
        listOf(
            FofCandidate(
                "a_alguien000000000000001",
                "Alguien de la UIS",
                87,
                2,
                Mode.AMISTAD,
                viaId = "val"
            ),
            FofCandidate(
                "a_felipe0000000000000002",
                "Felipe R.",
                84,
                1,
                Mode.ESTUDIO,
                viaId = "and"
            ),
            FofCandidate("a_alguien000000000000003", "Alguien", 71, 1, Mode.AMISTAD, viaId = "seb")
        )

    val plus =
        FofContent(count = 3, candidates = candidates, nodes = ParcheSamples.nodes, plus = true)

    val plusEmpty = plus.copy(count = 0, candidates = emptyList())

    val free =
        FofContent(count = 12, candidates = emptyList(), nodes = ParcheSamples.nodes, plus = false)
}

/** [FofRepository] en memoria. */
class FakeFofRepository(
    var loadResult: ApiResult<FofContent> = ApiResult.Success(FofSamples.plus)
) : FofRepository {
    var requestResult: ApiResult<Unit> = ApiResult.Success(Unit)
    val requested = mutableListOf<String>()
    var loads = 0

    override suspend fun load(): ApiResult<FofContent> {
        loads++
        return loadResult
    }

    override suspend fun requestIntroduction(anonId: String): ApiResult<Unit> {
        requested += anonId
        return requestResult
    }
}
