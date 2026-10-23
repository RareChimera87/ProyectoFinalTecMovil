package me.santiagoreina.parcheu.feature.parche

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.NodeDetail
import me.santiagoreina.parcheu.contract.NodeDto
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.navigation.AppRoute
import me.santiagoreina.parcheu.navigation.Chat
import me.santiagoreina.parcheu.navigation.Results
import me.santiagoreina.parcheu.navigation.Simulation

/** Hoja D2 de un nodo. [detail] es nulo mientras carga o si la consulta falló. */
data class NodeSheet(
    val node: NodeDto,
    val detail: NodeDetail? = null,
    val loadingDetail: Boolean = true,
    val starting: Boolean = false,
    val actionError: AppError? = null
)

/** Estados de D1-D5: cargando (D4), vacío (D3), error (D5) y cargado (D1, con la hoja D2 opcional). */
sealed interface ParcheUiState {
    data object Loading : ParcheUiState

    data object Empty : ParcheUiState

    data class Error(val error: AppError) : ParcheUiState

    data class Loaded(
        val content: ParcheContent,
        val filters: ParcheFilters = ParcheFilters(),
        val sheet: NodeSheet? = null
    ) : ParcheUiState {
        /** Nodos que se dibujan tras el filtro de afinidad. */
        val nodes: List<NodeDto> get() = filters.visibleNodes(content.graph.nodes)

        /** Nodos que el filtro de modo atenúa. */
        val dimmedIds: Set<String> get() = filters.dimmedIds(content.graph.nodes)
    }
}

sealed interface ParcheEffect {
    data class Navigate(val route: AppRoute) : ParcheEffect
}

/**
 * D1-D5 (E3.4). Al abrir pinta al instante el último grafo guardado (si existe) y lo refresca en segundo
 * plano; sin grafo guardado muestra D4 hasta que llegue la respuesta. "Reintentar" vuelve a D4.
 */
class ParcheViewModel(private val repository: GraphRepository) : ViewModel() {
    private val _state = MutableStateFlow<ParcheUiState>(ParcheUiState.Loading)
    val state: StateFlow<ParcheUiState> = _state.asStateFlow()

    private val _effects = Channel<ParcheEffect>(Channel.BUFFERED)
    val effects: Flow<ParcheEffect> = _effects.receiveAsFlow()

    private var filters = ParcheFilters()
    private var loadJob: Job? = null
    private var started = false

    /** Primera carga; [community] llega de D8 ("Ver en el grafo"). Repetirla no hace nada. */
    fun start(community: String? = null) {
        if (started) return
        started = true
        filters = ParcheFilters(communityId = community)
        load(useCache = community == null)
    }

    /** "Reintentar" de D5. */
    fun retry() {
        _state.value = ParcheUiState.Loading
        load(useCache = false)
    }

    fun onModeFilter(mode: Mode?) = updateFilters { it.copy(mode = mode) }

    fun onAffinityFilter(enabled: Boolean) = updateFilters { it.copy(minAffinity = enabled) }

    /** Quitar la comunidad vuelve al grafo completo (nueva consulta). */
    fun onClearCommunity() {
        filters = filters.copy(communityId = null)
        load(useCache = false)
    }

    fun onNodeClick(id: String) {
        val loaded = _state.value as? ParcheUiState.Loaded ?: return
        val node = loaded.content.graph.nodes.firstOrNull { it.id == id } ?: return
        _state.value = loaded.copy(sheet = NodeSheet(node))
        viewModelScope.launch {
            val detail = (repository.nodeDetail(id) as? ApiResult.Success)?.value
            updateSheet(id) { it.copy(detail = detail, loadingDetail = false) }
        }
    }

    fun onDismissSheet() {
        (_state.value as? ParcheUiState.Loaded)?.let { _state.value = it.copy(sheet = null) }
    }

    /** "Simular de nuevo": crea la simulación y abre F4. */
    fun onSimulateAgain() {
        val sheet = (_state.value as? ParcheUiState.Loaded)?.sheet ?: return
        if (sheet.starting) return
        updateSheet(sheet.node.id) { it.copy(starting = true, actionError = null) }
        viewModelScope.launch {
            when (val result = repository.startSimulation(sheet.node.id, sheet.node.mode)) {
                is ApiResult.Success -> {
                    updateSheet(sheet.node.id) { it.copy(starting = false) }
                    _effects.send(ParcheEffect.Navigate(Simulation(result.value.id)))
                }
                is ApiResult.Failure ->
                    updateSheet(sheet.node.id) {
                        it.copy(starting = false, actionError = result.error)
                    }
            }
        }
    }

    /** "Chatear" (F9) con el id del chat que trae [NodeDetail]; sin él no navega. */
    fun onChat() {
        val chatId = (_state.value as? ParcheUiState.Loaded)?.sheet?.detail?.chatId ?: return
        viewModelScope.launch { _effects.send(ParcheEffect.Navigate(Chat(chatId))) }
    }

    /** "Ver resultado" de una simulación pendiente (F7/F8). */
    fun onViewResult() {
        val simulation =
            (_state.value as? ParcheUiState.Loaded)?.sheet?.detail?.lastSimulation ?: return
        viewModelScope.launch {
            _effects.send(ParcheEffect.Navigate(Results(simulation.id)))
        }
    }

    private fun updateFilters(change: (ParcheFilters) -> ParcheFilters) {
        filters = change(filters)
        (_state.value as? ParcheUiState.Loaded)?.let { _state.value = it.copy(filters = filters) }
    }

    private fun updateSheet(id: String, change: (NodeSheet) -> NodeSheet) {
        val loaded = _state.value as? ParcheUiState.Loaded ?: return
        val sheet = loaded.sheet?.takeIf { it.node.id == id } ?: return
        _state.value = loaded.copy(sheet = change(sheet))
    }

    private fun load(useCache: Boolean) {
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                val cached = if (useCache) repository.cached() else null
                if (cached != null) show(cached)
                when (val result = repository.graph(filters.communityId)) {
                    is ApiResult.Success -> show(result.value)
                    // Con grafo guardado a la vista, un fallo de red no lo reemplaza por D5.
                    is ApiResult.Failure -> if (cached == null) {
                        _state.value = ParcheUiState.Error(result.error)
                    }
                }
            }
    }

    private fun show(content: ParcheContent) {
        _state.value =
            if (content.graph.nodes.isEmpty() && filters.communityId == null) {
                ParcheUiState.Empty
            } else {
                val previous = _state.value as? ParcheUiState.Loaded
                ParcheUiState.Loaded(content, filters, previous?.sheet)
            }
    }
}
