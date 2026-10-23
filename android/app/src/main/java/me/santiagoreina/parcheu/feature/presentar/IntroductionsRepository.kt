package me.santiagoreina.parcheu.feature.presentar

import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.GraphDto
import me.santiagoreina.parcheu.contract.IntroResponse
import me.santiagoreina.parcheu.contract.Introduce
import me.santiagoreina.parcheu.contract.IntroductionDto
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.NodeDto
import me.santiagoreina.parcheu.contract.QuotaDto
import me.santiagoreina.parcheu.contract.ReportDto
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.core.network.apiCall
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Rutas que usan E1-E3 (arquitectura.md §5.2, E6.2). La base de Retrofit termina en `/`. */
interface IntroductionsApi {
    @GET("v1/graph")
    suspend fun graph(@Query("mode") mode: Mode): GraphDto

    @GET("v1/quota")
    suspend fun quota(): QuotaDto

    @POST("v1/introductions")
    suspend fun introduce(@Body body: Introduce): IntroductionDto

    @GET("v1/introductions/{id}")
    suspend fun introduction(@Path("id") id: String): IntroductionDto

    @POST("v1/introductions/{id}/response")
    suspend fun respond(@Path("id") id: String, @Body body: IntroResponse): IntroductionDto

    @POST("v1/simulations/{id}/report:unlock")
    suspend fun unlock(@Path("id") simulationId: String): ReportDto
}

/**
 * Datos de E1-E3. El servidor decide qué ve cada quien (la otra persona sale anónima si no la
 * conoces, un 'No, gracias' ajeno nunca se revela y el informe gratis llega sin razones): la app
 * pinta lo que llega.
 */
interface IntroductionsRepository {
    /** E1: amigos CONNECTED de [mode] (el ego-grafo ya excluye a bloqueados y cuentas borradas). */
    suspend fun friends(mode: Mode): ApiResult<List<FriendOption>>

    /** `GET /v1/quota`: simulaciones que quedan y si es ParcheU+. */
    suspend fun quota(): ApiResult<QuotaDto>

    /** `POST /v1/introductions`: consume una simulación de quien presenta. */
    suspend fun introduce(mode: Mode, userA: String, userB: String): ApiResult<IntroductionDto>

    suspend fun introduction(id: String): ApiResult<IntroductionDto>

    /** E2: enviar (`true`) o 'No por ahora'; E3: 'Me interesa' (`true`) o 'No, gracias'. */
    suspend fun respond(id: String, interested: Boolean): ApiResult<IntroductionDto>

    /** E2: usa el informe completo del mes (402 FULL_REPORT si ya se usó). */
    suspend fun unlock(simulationId: String): ApiResult<ReportDto>
}

/** Un amigo elegible en E1. */
data class FriendOption(
    val id: String,
    val label: String,
    val initials: String,
    val affinity: Int,
    val isDemo: Boolean = false
)

class RemoteIntroductionsRepository(
    private val api: IntroductionsApi,
    private val mapper: ApiErrorMapper
) : IntroductionsRepository {
    override suspend fun friends(mode: Mode): ApiResult<List<FriendOption>> =
        when (val result = apiCall(mapper) { api.graph(mode) }) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(friendsOf(result.value, mode))
        }

    override suspend fun quota() = apiCall(mapper) { api.quota() }

    override suspend fun introduce(mode: Mode, userA: String, userB: String) =
        apiCall(mapper) { api.introduce(Introduce(mode, userA, userB)) }

    override suspend fun introduction(id: String) = apiCall(mapper) { api.introduction(id) }

    override suspend fun respond(id: String, interested: Boolean) =
        apiCall(mapper) { api.respond(id, IntroResponse(interested)) }

    override suspend fun unlock(simulationId: String) = apiCall(mapper) { api.unlock(simulationId) }

    companion object {
        /**
         * Amigos de E1: nodos CONNECTED del modo con nombre (las conexiones mutuas llegan con su
         * `users.id`), ordenados por afinidad y nombre.
         */
        fun friendsOf(graph: GraphDto, mode: Mode): List<FriendOption> = graph.nodes
            .filter {
                it.mode == mode && it.state == EdgeState.CONNECTED &&
                    !it.anonymous
            }
            .distinctBy { it.id }
            .sortedWith(compareByDescending<NodeDto> { it.affinity }.thenBy { it.label })
            .map { FriendOption(it.id, it.label, it.initials, it.affinity, it.isDemo) }
    }
}
