package me.santiagoreina.parcheu.feature.parche.fof

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.santiagoreina.parcheu.contract.FofCandidate
import me.santiagoreina.parcheu.contract.NodeDto
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUCard
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUCardVariant
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUChildTopBar
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUPlusBadge
import me.santiagoreina.parcheu.designsystem.components.compound.AvatarInitials
import me.santiagoreina.parcheu.designsystem.components.compound.ErrorState
import me.santiagoreina.parcheu.designsystem.components.compound.InfoNote
import me.santiagoreina.parcheu.designsystem.components.compound.LoadingDots
import me.santiagoreina.parcheu.designsystem.components.compound.LockedOverlay
import me.santiagoreina.parcheu.designsystem.components.compound.LockedPlaceholder
import me.santiagoreina.parcheu.designsystem.graph.GraphFof
import me.santiagoreina.parcheu.designsystem.graph.PU_GRAPH_DEFAULT_SIZE
import me.santiagoreina.parcheu.designsystem.graph.PuGraph
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.parche.ParcheIcon
import me.santiagoreina.parcheu.feature.parche.parcheIconPainter
import me.santiagoreina.parcheu.feature.parche.toGraphNode

/** Etiquetas de prueba de D6/D7. */
object FofTags {
    const val ROOT = "fof_root"
    const val LOCKED = "fof_locked"
    const val LIST = "fof_list"
    const val EMPTY = "fof_empty"
    const val MESSAGE = "fof_message"

    fun card(anonId: String) = "fof_card_$anonId"
}

/** Cuántos amigos de amigos se dibujan en la capa de G0 (la lista los muestra todos). */
const val MAX_GRAPH_FOF = 8

/** Etiqueta con la que el servidor enmascara a una persona ANONYMOUS ("Alguien" o "Alguien de la UIS"). */
private const val ANON_LABEL = "Alguien"

/** true si el servidor mandó al candidato enmascarado (sin nombre). */
val FofCandidate.isAnonymous: Boolean
    get() = label == ANON_LABEL || label.startsWith("$ANON_LABEL de la ")

/**
 * Nodo de la capa fof de G0. [GraphFof.parentId] es `viaId`, el amigo en común por el que se llega
 * (su `NodeDto.id` en el ego-grafo): PuGraph dibuja desde él la línea punteada prt "2 4". Si el
 * servidor no lo manda o ese amigo no está entre los nodos, el fof conserva la posición radial.
 */
fun FofCandidate.toGraphFof(): GraphFof = GraphFof(
    id = anonId,
    parentId = viaId,
    name = if (isAnonymous) ANON_LABEL else label,
    initials = if (isAnonymous) "A" else label.firstOrNull()?.uppercase() ?: "A",
    affinity = estimatedAffinity,
    anonymous = isAnonymous
)

/**
 * Nombre corto del amigo en común [FofCandidate.viaId] entre [nodes] (el ego-grafo), o null si no
 * llegó o no está (p. ej. grafo guardado viejo). Una conexión directa siempre viene con nombre.
 */
fun FofCandidate.viaName(nodes: List<NodeDto>): String? {
    val id = viaId ?: return null
    val node = nodes.firstOrNull { it.id == id && !it.anonymous } ?: return null
    return node.label.trim().substringBefore(' ').takeIf { it.isNotEmpty() }
}

/**
 * Segunda línea de la tarjeta de D6 (mockup 09a): "Amigo de Valentina · afinidad estimada",
 * "Amigo de Valentina y 2 más · …" o, sin [viaName], "3 amigos en común · …".
 */
fun fofSubtitle(viaName: String?, mutuals: Int): String {
    val origin = when {
        viaName == null -> mutualsText(mutuals)
        mutuals <= 1 -> "Amigo de $viaName"
        else -> "Amigo de $viaName y ${mutuals - 1} más"
    }
    return "$origin · afinidad estimada"
}

/** Nota al pie de D6 sobre la estimación (ADR-0023); la real sale de la simulación de la presentación. */
const val FOF_ESTIMATE_NOTE =
    "La afinidad es una estimación por los rasgos de sus gemelos. Si te los presentan, la " +
        "simulación de la presentación calcula la real."

/** "1 amigo en común" / "3 amigos en común". */
fun mutualsText(mutuals: Int): String =
    if (mutuals == 1) "1 amigo en común" else "$mutuals amigos en común"

/** Acciones de D6/D7. */
data class FofCallbacks(
    val onBack: () -> Unit = {},
    val onUnlock: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onRequestIntroduction: (String) -> Unit = {}
)

/** D6 (ParcheU+) y D7 (gratis, borroso con candado). Pantalla hija: sin NavBar. */
@Composable
fun FofScreen(
    state: FofUiState,
    callbacks: FofCallbacks,
    modifier: Modifier = Modifier,
    animate: Boolean = true
) {
    val colors = ParcheUTheme.colors
    Column(modifier = modifier.fillMaxSize().background(colors.bg).testTag(FofTags.ROOT)) {
        ParcheUChildTopBar(
            title = "Amigos de amigos",
            navigationIcon = rememberVectorPainter(FofIcons.Back),
            navigationContentDescription = "Volver",
            onNavigationClick = callbacks.onBack,
            action = { if (state is FofUiState.Unlocked) ParcheUPlusBadge() }
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (state) {
                FofUiState.Loading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingDots(animated = animate)
                    }
                is FofUiState.Error ->
                    ErrorState(
                        title = "No pudimos cargar amigos de amigos",
                        message = state.error.fofMessage(),
                        retryLabel = "Reintentar",
                        onRetryClick = callbacks.onRetry,
                        modifier = Modifier.fillMaxSize()
                    )
                is FofUiState.Locked -> LockedContent(state, callbacks)
                is FofUiState.Unlocked -> UnlockedContent(state, callbacks)
            }
        }
    }
}

/** D7: el bloqueo no recibe ningún dato real (el servidor solo manda el conteo). */
@Composable
private fun LockedContent(state: FofUiState.Locked, callbacks: FofCallbacks) {
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
            title = lockedTitle(state.count),
            actionLabel = "Ver ParcheU+",
            onActionClick = callbacks.onUnlock,
            lockIcon = parcheIconPainter(ParcheIcon.Lock),
            placeholder = LockedPlaceholder.Graph,
            modifier = Modifier.height(320.dp).testTag(FofTags.LOCKED)
        )
        Text(
            text =
            "Con ParcheU+ ves a los amigos de tus amigos que son compatibles contigo y puedes " +
                "pedir que te los presenten.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = colors.mu,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        ParcheUCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "GRATIS TIENES",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp
                ),
                color = colors.mu
            )
            IncludedRow("Tu grafo con tus contactos directos")
            IncludedRow("Presentar a dos amigos")
        }
    }
}

/** Título del bloqueo: con el conteo si hay candidatos. */
fun lockedTitle(count: Int): String = when {
    count <= 0 -> "Mira a las personas afines a 2 pasos de ti"
    count == 1 -> "Hay 1 persona a 2 pasos de ti"
    else -> "Hay $count personas a 2 pasos de ti"
}

@Composable
private fun IncludedRow(text: String) {
    val colors = ParcheUTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = rememberVectorPainter(FofIcons.Check),
            contentDescription = null,
            tint = colors.prt,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = colors.tx,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}

/** D6: G0 con la capa fof y la lista de candidatos. */
@Composable
private fun UnlockedContent(state: FofUiState.Unlocked, callbacks: FofCallbacks) {
    val content = state.content
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            val graphSize = minOf(PU_GRAPH_DEFAULT_SIZE, maxWidth - 32.dp)
            PuGraph(
                nodes = content.nodes.map { it.toGraphNode() },
                size = graphSize,
                fof = content.candidates.take(MAX_GRAPH_FOF).map { it.toGraphFof() }
            )
        }
        Column(
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            state.message?.let {
                InfoNote(text = it, modifier = Modifier.testTag(FofTags.MESSAGE))
            }
            if (content.candidates.isEmpty()) {
                EmptyFof()
            } else {
                Column(
                    modifier = Modifier.testTag(FofTags.LIST),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    content.candidates.forEach { candidate ->
                        FofCard(
                            candidate = candidate,
                            viaName = candidate.viaName(content.nodes),
                            requested = candidate.anonId in state.requested,
                            sending = state.sending == candidate.anonId,
                            onRequest = { callbacks.onRequestIntroduction(candidate.anonId) }
                        )
                    }
                }
            }
            FootNote(FOF_ESTIMATE_NOTE)
            FootNote("Los vínculos de Pareja nunca aparecen en el grafo de otras personas.")
        }
    }
}

@Composable
private fun EmptyFof() {
    val colors = ParcheUTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag(FofTags.EMPTY),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "Aún no hay amigos de amigos",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = colors.tx,
            textAlign = TextAlign.Center
        )
        Text(
            "Cuando tus amigos conecten con más gente, aquí verás a quienes te quedan a 2 pasos.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = colors.mu,
            textAlign = TextAlign.Center
        )
    }
}

/** FriendOfFriendCard: avatar, "Alguien · 87 % afín", "Amigo de …" y "Pedir presentación". */
@Composable
private fun FofCard(
    candidate: FofCandidate,
    viaName: String?,
    requested: Boolean,
    sending: Boolean,
    onRequest: () -> Unit
) {
    val colors = ParcheUTheme.colors
    val name = if (candidate.isAnonymous) ANON_LABEL else candidate.label
    ParcheUCard(
        variant = ParcheUCardVariant.Suggestion,
        modifier = Modifier.fillMaxWidth().testTag(FofTags.card(candidate.anonId))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AvatarInitials(
                name = name,
                affinity = candidate.estimatedAffinity,
                size = 40.dp,
                anonymous = candidate.isAnonymous,
                anonymousIcon = parcheIconPainter(ParcheIcon.Person)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "$name · ${candidate.estimatedAffinity} % afín",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = colors.tx,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = fofSubtitle(viaName, candidate.mutuals),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                    color = colors.mu,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val enabled = !requested && !sending
            val actionDescription = introActionDescription(name, viaName, requested, sending)
            Text(
                text = when {
                    requested -> "Pedida"
                    sending -> "Pidiendo…"
                    else -> "Pedir presentación"
                },
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = if (enabled) colors.prt else colors.mu,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(max = 96.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = enabled, role = Role.Button, onClick = onRequest)
                    // TalkBack: el botón dice a quién se refiere (todos los de la lista decían lo mismo).
                    .semantics { contentDescription = actionDescription }
                    .heightIn(min = 48.dp)
                    .wrapContentHeight()
                    .padding(horizontal = 4.dp)
            )
        }
    }
}

/** Descripción de TalkBack del botón de [FofCard]: acción, candidato y amigo en común. */
internal fun introActionDescription(
    name: String,
    viaName: String?,
    requested: Boolean,
    sending: Boolean
): String {
    val via = viaName?.let { ", amigo de $it" }.orEmpty()
    return when {
        requested -> "Presentación pedida de $name$via"
        sending -> "Pidiendo presentación de $name$via"
        else -> "Pedir presentación de $name$via"
    }
}

@Composable
private fun FootNote(text: String) {
    val colors = ParcheUTheme.colors
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            painter = rememberVectorPainter(FofIcons.Shield),
            contentDescription = null,
            tint = colors.mu,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = colors.mu,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
