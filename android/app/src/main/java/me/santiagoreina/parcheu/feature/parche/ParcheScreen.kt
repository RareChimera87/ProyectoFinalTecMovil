package me.santiagoreina.parcheu.feature.parche

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.designsystem.components.base.ModeFilterChip
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUIconButton
import me.santiagoreina.parcheu.designsystem.components.base.ParcheURootTopBar
import me.santiagoreina.parcheu.designsystem.components.base.SecondaryFilterChip
import me.santiagoreina.parcheu.designsystem.components.base.StatCard
import me.santiagoreina.parcheu.designsystem.graph.PU_GRAPH_DEFAULT_SIZE
import me.santiagoreina.parcheu.designsystem.graph.PuGraph
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.presentar.PresentarIcons
import me.santiagoreina.parcheu.feature.presentar.rememberPresentarIcon

/** Acciones que D1-D5 le pide al ViewModel y a la navegación. */
data class ParcheCallbacks(
    val onMode: (Mode?) -> Unit = {},
    val onAffinity: (Boolean) -> Unit = {},
    val onCommunity: () -> Unit = {},
    val onNodeClick: (String) -> Unit = {},
    val onDismissSheet: () -> Unit = {},
    val onSimulateAgain: () -> Unit = {},
    val onChat: () -> Unit = {},
    val onViewResult: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onTimeline: () -> Unit = {},
    val onAddFriends: () -> Unit = {},
    val onRandomMatch: () -> Unit = {},
    val onUnlock: () -> Unit = {},
    /** E1 · Presentar a dos amigos (E6.2). */
    val onIntroduce: () -> Unit = {},
    val onFriendsOfFriends: () -> Unit = {}
)

/** D1-D5 e I2 (según el tema). La NavBar la pone `ParcheUApp`. */
@Composable
fun ParcheScreen(
    state: ParcheUiState,
    callbacks: ParcheCallbacks,
    modifier: Modifier = Modifier,
    now: Instant = Clock.System.now(),
    animate: Boolean = true
) {
    val colors = ParcheUTheme.colors
    val openSheet = (state as? ParcheUiState.Loaded)?.sheet
    Box(modifier = modifier.fillMaxSize().background(colors.bg)) {
        Column(Modifier.fillMaxSize()) {
            ParcheURootTopBar(title = "Tu parche") {
                ParcheUIconButton(
                    icon = rememberPresentarIcon(PresentarIcons.People),
                    contentDescription = "Presentar a dos amigos",
                    onClick = callbacks.onIntroduce
                )
                ParcheUIconButton(
                    icon = parcheIconPainter(ParcheIcon.Timeline),
                    contentDescription = "Evolución de tu red",
                    onClick = callbacks.onTimeline
                )
                ParcheUIconButton(
                    icon = parcheIconPainter(ParcheIcon.PersonAdd),
                    contentDescription = "Agregar amigos",
                    onClick = callbacks.onAddFriends
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state) {
                    ParcheUiState.Loading -> GraphSkeleton(animate = animate)
                    ParcheUiState.Empty ->
                        ParcheEmptyState(callbacks.onAddFriends, callbacks.onRandomMatch)
                    is ParcheUiState.Error -> ParcheErrorState(state.error, callbacks.onRetry)
                    is ParcheUiState.Loaded -> LoadedContent(state, callbacks)
                }
            }
        }
        openSheet?.let {
            NodeDetailSheet(
                sheet = it,
                now = now,
                onDismiss = callbacks.onDismissSheet,
                onSimulateAgain = callbacks.onSimulateAgain,
                onChat = callbacks.onChat,
                onViewResult = callbacks.onViewResult
            )
        }
    }
}

@Composable
private fun LoadedContent(state: ParcheUiState.Loaded, callbacks: ParcheCallbacks) {
    val filters = state.filters
    val stats = state.content.graph.stats
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.horizontalScroll(
                    rememberScrollState()
                ).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModeFilterChip(
                    label = "Todos",
                    selected = filters.mode == null,
                    onClick = { callbacks.onMode(null) },
                    icon = parcheIconPainter(ParcheIcon.Apps)
                )
                Mode.entries.forEach { mode ->
                    ModeFilterChip(
                        label = mode.label(),
                        selected = filters.mode == mode,
                        onClick = { callbacks.onMode(mode) },
                        icon = parcheIconPainter(mode.icon())
                    )
                }
            }
            Row(
                modifier = Modifier.horizontalScroll(
                    rememberScrollState()
                ).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModeFilterChip(
                    label = "Afinidad 70 %+",
                    selected = filters.minAffinity,
                    onClick = { callbacks.onAffinity(!filters.minAffinity) },
                    icon = parcheIconPainter(ParcheIcon.Tune)
                )
                if (filters.communityId != null) {
                    ModeFilterChip(
                        label = "Comunidad",
                        selected = true,
                        onClick = callbacks.onCommunity,
                        icon = parcheIconPainter(ParcheIcon.Bubbles)
                    )
                } else {
                    SecondaryFilterChip(
                        label = "Comunidad",
                        onClick = callbacks.onCommunity,
                        icon = parcheIconPainter(ParcheIcon.Bubbles)
                    )
                }
            }
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                val graphSize = minOf(PU_GRAPH_DEFAULT_SIZE, maxWidth - 32.dp)
                PuGraph(
                    nodes = state.nodes.map { it.toGraphNode() },
                    size = graphSize,
                    selectedId = state.sheet?.node?.id,
                    dimmedIds = state.dimmedIds,
                    onNodeClick = callbacks.onNodeClick
                )
            }
            GraphEdgeLegend()
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard(
                        "${stats.connections}",
                        "conexiones",
                        Modifier.weight(1f).fillMaxHeight()
                    )
                    StatCard(
                        "${stats.simulated + stats.pending}",
                        "simulaciones abiertas",
                        Modifier.weight(1f).fillMaxHeight()
                    )
                }
                if (!state.content.plus) {
                    LockedFeatureBanner("Amigos de amigos y comunidades", callbacks.onUnlock)
                } else {
                    // D6 no tiene entrada en el mockup para ParcheU+ (inventario §6.2): fila propia (E6.3).
                    FriendsOfFriendsEntry(callbacks.onFriendsOfFriends)
                }
            }
        }
    }
}
