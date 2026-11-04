package me.santiagoreina.parcheu.feature.evolution

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.santiagoreina.parcheu.contract.HistoryDto
import me.santiagoreina.parcheu.contract.Moment
import me.santiagoreina.parcheu.contract.MonthSnapshot
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUCard
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUChildTopBar
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUPlusBadge
import me.santiagoreina.parcheu.designsystem.components.compound.ErrorState
import me.santiagoreina.parcheu.designsystem.components.compound.LoadingDots
import me.santiagoreina.parcheu.designsystem.components.compound.LockedOverlay
import me.santiagoreina.parcheu.designsystem.components.compound.LockedPlaceholder
import me.santiagoreina.parcheu.designsystem.graph.GraphEdgeState
import me.santiagoreina.parcheu.designsystem.graph.GraphMode
import me.santiagoreina.parcheu.designsystem.graph.GraphNode
import me.santiagoreina.parcheu.designsystem.graph.PuGraph
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.parche.ParcheIcon
import me.santiagoreina.parcheu.feature.parche.fof.FofIcons
import me.santiagoreina.parcheu.feature.parche.parcheIconPainter

/** Etiquetas de prueba de D9. */
object EvolutionTags {
    const val ROOT = "evolution_root"
    const val LOCKED = "evolution_locked"
    const val TOO_EARLY = "evolution_too_early"
    const val MONTHS = "evolution_months"
    const val MOMENTS = "evolution_moments"
    const val DETAIL = "evolution_detail"

    fun month(month: String) = "evolution_month_$month"
}

/** Meses que se muestran como mucho (los más recientes). */
const val MAX_MONTH_CARDS = 12

/** Nodos que dibuja el mini grafo de cada mes. */
private const val MAX_MINI_NODES = 12

/** Acciones de D9. */
data class EvolutionCallbacks(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onUnlock: () -> Unit = {},
    val onMonth: (String) -> Unit = {}
)

/** D9 en sus estados: con historial, menos de 2 meses y bloqueado. Pantalla hija: sin NavBar. */
@Composable
fun EvolutionScreen(
    state: EvolutionUiState,
    callbacks: EvolutionCallbacks,
    modifier: Modifier = Modifier,
    animate: Boolean = true
) {
    val colors = ParcheUTheme.colors
    Column(modifier = modifier.fillMaxSize().background(colors.bg).testTag(EvolutionTags.ROOT)) {
        ParcheUChildTopBar(
            title = "Así creció tu parche",
            navigationIcon = rememberVectorPainter(FofIcons.Back),
            navigationContentDescription = "Volver",
            onNavigationClick = callbacks.onBack,
            action = { if (state !is EvolutionUiState.Locked) ParcheUPlusBadge() }
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (state) {
                EvolutionUiState.Loading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingDots(animated = animate)
                    }
                is EvolutionUiState.Error ->
                    ErrorState(
                        title = "No pudimos cargar tu evolución",
                        message = state.error.evolutionMessage(),
                        retryLabel = "Reintentar",
                        onRetryClick = callbacks.onRetry,
                        modifier = Modifier.fillMaxSize()
                    )
                EvolutionUiState.Locked -> LockedContent(callbacks)
                is EvolutionUiState.TooEarly -> TooEarlyContent(state.history)
                is EvolutionUiState.Content -> HistoryContent(state, callbacks)
            }
        }
    }
}

/** Bloqueado (gratis): sin ningún dato real, solo el placeholder decorativo. */
@Composable
private fun LockedContent(callbacks: EvolutionCallbacks) {
    val colors = ParcheUTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        LockedOverlay(
            title = "Mira cómo creció tu parche mes a mes",
            actionLabel = "Ver ParcheU+",
            onActionClick = callbacks.onUnlock,
            lockIcon = parcheIconPainter(ParcheIcon.Lock),
            placeholder = LockedPlaceholder.Graph,
            modifier = Modifier.height(320.dp).testTag(EvolutionTags.LOCKED)
        )
        Text(
            text = "Con ParcheU+ ves una línea de tiempo con tus conexiones de cada mes y los " +
                "momentos que marcaron tu red.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = colors.mu,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Menos de 2 meses de historia: todavía no hay línea de tiempo, pero sí los momentos que ya ocurrieron. */
@Composable
private fun TooEarlyContent(history: HistoryDto) {
    val colors = ParcheUTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(
                vertical = 24.dp
            ).testTag(EvolutionTags.TOO_EARLY),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "Tu parche apenas empieza",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                ),
                color = colors.tx,
                textAlign = TextAlign.Center
            )
            Text(
                "Vuelve cuando lleves un par de meses: aquí verás cómo crece tu red mes a mes.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = colors.mu,
                textAlign = TextAlign.Center
            )
        }
        if (history.moments.isNotEmpty()) Moments(history.moments)
    }
}

@Composable
private fun HistoryContent(state: EvolutionUiState.Content, callbacks: EvolutionCallbacks) {
    val months = state.history.months.takeLast(MAX_MONTH_CARDS)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier.testTag(EvolutionTags.MONTHS),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            months.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { snapshot ->
                        MonthCard(
                            snapshot = snapshot,
                            selected = snapshot.month == state.selected,
                            onClick = { callbacks.onMonth(snapshot.month) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
        months.firstOrNull { it.month == state.selected }?.let { MonthDetail(it) }
        if (state.history.moments.isNotEmpty()) Moments(state.history.moments)
    }
}

/** MonthSnapshotCard: mini grafo con las conexiones del mes, su nombre y el conteo. */
@Composable
private fun MonthCard(
    snapshot: MonthSnapshot,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ParcheUTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(colors.s1)
            .then(if (selected) Modifier.border(1.5.dp, colors.prt, shape) else Modifier)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                this.selected = selected
                contentDescription =
                    "${monthName(snapshot.month)}, ${connectionsText(snapshot.connections)}"
            }
            .padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 12.dp)
            .testTag(EvolutionTags.month(snapshot.month)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // El mini grafo es decorativo: se oculta a TalkBack (sus nodos son de relleno, sin datos reales).
        Box(Modifier.clearAndSetSemantics {}) {
            PuGraph(
                nodes = miniNodes(snapshot.connections),
                size = 140.dp,
                centerLabel = "Tú"
            )
            // El Canvas del grafo consume los toques: esta capa (sin semántica) los reenvía a la tarjeta.
            Box(Modifier.matchParentSize().clickable(onClick = onClick))
        }
        Text(
            text = monthName(snapshot.month),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = colors.tx
        )
        Text(
            text = connectionsText(snapshot.connections),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
            color = colors.mu
        )
    }
}

/** Nodos neutrales para el mini grafo: el historial solo trae conteos, nunca personas. */
internal fun miniNodes(connections: Int): List<GraphNode> =
    List(connections.coerceIn(0, MAX_MINI_NODES)) { i ->
        GraphNode(
            id = "mini_$i",
            name = "Alguien",
            initials = "?",
            affinity = 60,
            mode = GraphMode.AMISTAD,
            state = GraphEdgeState.CONNECTED,
            anonymous = true
        )
    }

@Composable
private fun MonthDetail(snapshot: MonthSnapshot) {
    val colors = ParcheUTheme.colors
    ParcheUCard(modifier = Modifier.fillMaxWidth().testTag(EvolutionTags.DETAIL)) {
        Text(
            text = monthName(snapshot.month),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = colors.tx
        )
        Text(
            text = "${newConnectionsText(
                snapshot.newConnections
            )} · ${personasText(snapshot.nodes)} en tu parche",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = colors.mu
        )
    }
}

fun newConnectionsText(n: Int): String = if (n == 1) "1 conexión nueva" else "$n conexiones nuevas"

fun personasText(n: Int): String = if (n == 1) "1 persona" else "$n personas"

/** MomentsTimeline: línea vertical con un punto por momento, el más reciente arriba. */
@Composable
private fun Moments(moments: List<Moment>) {
    val colors = ParcheUTheme.colors
    Text(
        text = "MOMENTOS",
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp
        ),
        color = colors.mu,
        modifier = Modifier.padding(top = 4.dp)
    )
    val ordered = moments.sortedByDescending { it.at }
    Column(Modifier.fillMaxWidth().testTag(EvolutionTags.MOMENTS)) {
        ordered.forEachIndexed { i, moment ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Box(Modifier.width(12.dp).fillMaxHeight()) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp)
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(
                                if (i ==
                                    ordered.lastIndex
                                ) {
                                    Color.Transparent
                                } else {
                                    colors.ln
                                }
                            )
                    )
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp)
                            .size(12.dp)
                            .background(if (i == 0) colors.prt else colors.mu, CircleShape)
                    )
                }
                Text(
                    text = "${shortMonthOf(moment.at)} · ${moment.label}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = colors.tx,
                    modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 10.dp)
                )
            }
        }
    }
}
