package me.santiagoreina.parcheu.feature.parche

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import me.santiagoreina.parcheu.contract.EntitlementDto
import me.santiagoreina.parcheu.contract.GraphDto
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.NodeDetail
import me.santiagoreina.parcheu.contract.SimulationSummary
import me.santiagoreina.parcheu.contract.StartSimulation
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.core.network.apiCall
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Rutas de la API v1 que usa D1-D5 (arquitectura.md §5.2). La base de Retrofit termina en `/`. */
interface GraphApi {
    @GET("v1/graph")
    suspend fun graph(@Query("community") community: String? = null): GraphDto

    @GET("v1/graph/nodes/{id}")
    suspend fun node(@Path("id") id: String): NodeDetail

    @GET("v1/billing/entitlement")
    suspend fun entitlement(): EntitlementDto

    @POST("v1/simulations")
    suspend fun startSimulation(@Body body: StartSimulation): SimulationSummary
}

/** Último grafo guardado (DataStore) para pintar D1 al instante. */
interface GraphCache {
    suspend fun load(): ParcheContent?

    suspend fun save(content: ParcheContent)

    /** Cerrar sesión (E8.2) borra el grafo guardado. */
    suspend fun clear()
}

/** Datos de D1-D5. La UI depende solo de esta interfaz; las pruebas usan `FakeGraphRepository`. */
interface GraphRepository {
    /** Último grafo sin filtrar guardado, o `null` si no hay. */
    suspend fun cached(): ParcheContent?

    /** `GET /v1/graph` (+ plan). Con [community] nulo guarda el resultado para el próximo arranque. */
    suspend fun graph(community: String? = null): ApiResult<ParcheContent>

    /** `GET /v1/graph/nodes/{id}` (D2). */
    suspend fun nodeDetail(id: String): ApiResult<NodeDetail>

    /** `POST /v1/simulations` ("Simular de nuevo"). */
    suspend fun startSimulation(otherUserId: String, mode: Mode): ApiResult<SimulationSummary>
}

class GraphRepositoryImpl(
    private val api: GraphApi,
    private val cache: GraphCache,
    private val mapper: ApiErrorMapper
) : GraphRepository {
    override suspend fun cached(): ParcheContent? = runCatching { cache.load() }.getOrNull()

    override suspend fun graph(community: String?): ApiResult<ParcheContent> {
        val graph =
            when (val result = apiCall(mapper) { api.graph(community) }) {
                is ApiResult.Failure -> return result
                is ApiResult.Success -> result.value
            }
        // Si falla la consulta del plan se conserva el último valor conocido en vez de mostrar el bloqueo.
        val plus =
            when (val result = apiCall(mapper) { api.entitlement() }) {
                is ApiResult.Success -> result.value.plus
                is ApiResult.Failure -> cached()?.plus ?: false
            }
        val content = ParcheContent(graph, plus)
        if (community == null) runCatching { cache.save(content) }
        return ApiResult.Success(content)
    }

    override suspend fun nodeDetail(id: String): ApiResult<NodeDetail> =
        apiCall(mapper) { api.node(id) }

    override suspend fun startSimulation(
        otherUserId: String,
        mode: Mode
    ): ApiResult<SimulationSummary> =
        apiCall(mapper) { api.startSimulation(StartSimulation(otherUserId, mode)) }
}

private val GRAPH_KEY = stringPreferencesKey("parche_graph_v1")

/** Guarda [ParcheContent] como JSON en DataStore Preferences. */
class DataStoreGraphCache(private val store: DataStore<Preferences>, private val json: Json) :
    GraphCache {
    override suspend fun load(): ParcheContent? = store.data.first()[GRAPH_KEY]?.let {
        runCatching { json.decodeFromString<ParcheContent>(it) }.getOrNull()
    }

    override suspend fun save(content: ParcheContent) {
        store.edit { it[GRAPH_KEY] = json.encodeToString(ParcheContent.serializer(), content) }
    }

    override suspend fun clear() {
        store.edit { it.remove(GRAPH_KEY) }
    }
}
