package me.santiagoreina.parcheu.feature.parche.fof

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import me.santiagoreina.parcheu.contract.FofCandidate
import me.santiagoreina.parcheu.contract.FofDto
import me.santiagoreina.parcheu.contract.IntroRequest
import me.santiagoreina.parcheu.contract.NodeDto
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.core.network.apiCall
import me.santiagoreina.parcheu.feature.parche.GraphRepository
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/** Rutas de la API v1 que usan D6 y D7 (arquitectura.md §5.2). */
interface FofApi {
    @GET("v1/graph/fof")
    suspend fun fof(): FofDto

    /** "Pedir presentación" (E6.4): 202 sin cuerpo; gratis → 402 PLUS_REQUIRED. */
    @POST("v1/introductions/request")
    suspend fun requestIntroduction(@Body body: IntroRequest)
}

/**
 * Lo que D6/D7 necesitan: los amigos de amigos y las conexiones directas para dibujar G0 con la capa
 * fof. [plus] sale de la respuesta: el servidor solo manda `items` a ParcheU+ (gratis recibe `count`).
 */
data class FofContent(
    val count: Int,
    val candidates: List<FofCandidate>,
    val nodes: List<NodeDto>,
    val plus: Boolean
)

/** Datos de D6/D7. La UI depende solo de esta interfaz; las pruebas usan `FakeFofRepository`. */
interface FofRepository {
    /** `GET /v1/graph/fof` y el ego-grafo (de la red o, si falla, el último guardado). */
    suspend fun load(): ApiResult<FofContent>

    /** `POST /v1/introductions/request` con el `anonId` del candidato. */
    suspend fun requestIntroduction(anonId: String): ApiResult<Unit>
}

class FofRepositoryImpl(
    private val api: FofApi,
    private val graph: GraphRepository,
    private val mapper: ApiErrorMapper
) : FofRepository {
    override suspend fun load(): ApiResult<FofContent> = coroutineScope {
        val graphCall = async { graph.graph() }
        val fof =
            when (val result = apiCall(mapper) { api.fof() }) {
                is ApiResult.Failure -> {
                    graphCall.cancel()
                    return@coroutineScope result
                }
                is ApiResult.Success -> result.value
            }
        // El grafo es el fondo de la capa fof: si no llega se usa el guardado (o ninguno).
        val nodes =
            when (val result = graphCall.await()) {
                is ApiResult.Success -> result.value.graph.nodes
                is ApiResult.Failure -> graph.cached()?.graph?.nodes.orEmpty()
            }
        ApiResult.Success(
            FofContent(
                count = fof.count,
                candidates = fof.items.orEmpty(),
                nodes = nodes,
                plus = fof.items != null
            )
        )
    }

    override suspend fun requestIntroduction(anonId: String): ApiResult<Unit> =
        apiCall(mapper) { api.requestIntroduction(IntroRequest(anonId)) }
}
