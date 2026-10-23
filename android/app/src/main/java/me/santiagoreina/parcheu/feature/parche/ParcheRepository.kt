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
    /** El grafo guardado, solo si pertenece a [ownerUid]; si es de otra persona se descarta. */
    suspend fun load(ownerUid: String): ParcheContent?

    suspend fun save(content: ParcheContent, ownerUid: String)

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
    private val mapper: ApiErrorMapper,
    /** Uid de la sesión vigente; `null` si no hay. La caché es de esa persona y de nadie más. */
    private val currentUid: () -> String?
) : GraphRepository {
    override suspend fun cached(): ParcheContent? {
        val uid = currentUid() ?: return null
        return runCatching { cache.load(uid) }.getOrNull()
    }

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
        val uid = currentUid()
        if (community == null && uid != null) runCatching { cache.save(content, uid) }
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
private val OWNER_KEY = stringPreferencesKey("parche_graph_owner")

/** Guarda [ParcheContent] como JSON en DataStore Preferences. */
class DataStoreGraphCache(private val store: DataStore<Preferences>, private val json: Json) :
    GraphCache {
    override suspend fun load(ownerUid: String): ParcheContent? {
        val prefs = store.data.first()
        if (prefs[OWNER_KEY] != ownerUid) {
            // Grafo de otra persona (o sin dueño): no se muestra y se borra.
            if (prefs[GRAPH_KEY] != null) clear()
            return null
        }
        return prefs[GRAPH_KEY]?.let {
            runCatching { json.decodeFromString<ParcheContent>(it) }.getOrNull()
        }
    }

    override suspend fun save(content: ParcheContent, ownerUid: String) {
        store.edit {
            it[GRAPH_KEY] = json.encodeToString(ParcheContent.serializer(), content)
            it[OWNER_KEY] = ownerUid
        }
    }

    override suspend fun clear() {
        store.edit {
            it.remove(GRAPH_KEY)
            it.remove(OWNER_KEY)
        }
    }
}
