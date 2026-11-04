package me.santiagoreina.parcheu.feature.parche.communities

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.santiagoreina.parcheu.contract.CommunityDto
import me.santiagoreina.parcheu.contract.PersonRef
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUButtonSize
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUCard
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUChildTopBar
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUPlusBadge
import me.santiagoreina.parcheu.designsystem.components.base.PillChip
import me.santiagoreina.parcheu.designsystem.components.base.TonalButton
import me.santiagoreina.parcheu.designsystem.components.compound.AvatarSpec
import me.santiagoreina.parcheu.designsystem.components.compound.EmptyState
import me.santiagoreina.parcheu.designsystem.components.compound.ErrorState
import me.santiagoreina.parcheu.designsystem.components.compound.LoadingDots
import me.santiagoreina.parcheu.designsystem.components.compound.LockedOverlay
import me.santiagoreina.parcheu.designsystem.components.compound.LockedPlaceholder
import me.santiagoreina.parcheu.designsystem.components.compound.StackedAvatars
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.parche.ParcheIcon
import me.santiagoreina.parcheu.feature.parche.fof.FofIcons
import me.santiagoreina.parcheu.feature.parche.parcheIconPainter

/** Etiquetas de prueba de D8. */
object CommunitiesTags {
    const val ROOT = "communities_root"
    const val LOCKED = "communities_locked"
    const val LIST = "communities_list"
    const val EMPTY = "communities_empty"

    fun card(id: String) = "community_card_$id"
}

/** Afinidad con la que se tiñen los avatares de una comunidad (el servidor no la manda por miembro). */
private const val MEMBER_AFFINITY = 70

/** "5 personas" / "1 persona". */
fun peopleText(size: Int): String = if (size == 1) "1 persona" else "$size personas"

/** Título del bloqueo: con el conteo si ya hay comunidades. */
fun communitiesLockedTitle(count: Int): String = when {
    count <= 0 -> "Descubre los grupos que se forman en tu red"
    count == 1 -> "Estás en 1 comunidad"
    else -> "Estás en $count comunidades"
}

/** Acciones de D8. */
data class CommunitiesCallbacks(
    val onBack: () -> Unit = {},
    val onUnlock: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onViewInGraph: (String) -> Unit = {}
)

/** D8 en sus estados: con comunidades, bloqueado (gratis) y vacío. Pantalla hija: sin NavBar. */
@Composable
fun CommunitiesScreen(
    state: CommunitiesUiState,
    callbacks: CommunitiesCallbacks,
    modifier: Modifier = Modifier,
    animate: Boolean = true
) {
    val colors = ParcheUTheme.colors
    Column(modifier = modifier.fillMaxSize().background(colors.bg).testTag(CommunitiesTags.ROOT)) {
        ParcheUChildTopBar(
            title = "Comunidades",
            navigationIcon = rememberVectorPainter(FofIcons.Back),
            navigationContentDescription = "Volver",
            onNavigationClick = callbacks.onBack,
            action = {
                if (state is CommunitiesUiState.Loaded ||
                    state is CommunitiesUiState.Empty
                ) {
                    ParcheUPlusBadge()
                }
            }
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (state) {
                CommunitiesUiState.Loading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingDots(animated = animate)
                    }
                is CommunitiesUiState.Error ->
                    ErrorState(
                        title = "No pudimos cargar tus comunidades",
                        message = state.error.communitiesMessage(),
                        retryLabel = "Reintentar",
                        onRetryClick = callbacks.onRetry,
                        modifier = Modifier.fillMaxSize()
                    )
                is CommunitiesUiState.Locked -> LockedContent(state, callbacks)
                CommunitiesUiState.Empty ->
                    EmptyState(
                        title = "Aún no tienes comunidades",
                        message = "Cada semana agrupamos a tus conexiones que también se conocen " +
                            "entre sí. Una comunidad necesita al menos 3 personas.",
                        modifier = Modifier.fillMaxSize().testTag(CommunitiesTags.EMPTY)
                    )
                is CommunitiesUiState.Loaded -> LoadedContent(state, callbacks)
            }
        }
    }
}

/** D8 bloqueado: el servidor solo manda el conteo; el borroso es decorativo. */
@Composable
private fun LockedContent(state: CommunitiesUiState.Locked, callbacks: CommunitiesCallbacks) {
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
            title = communitiesLockedTitle(state.count),
            actionLabel = "Ver ParcheU+",
            onActionClick = callbacks.onUnlock,
            lockIcon = parcheIconPainter(ParcheIcon.Lock),
            placeholder = LockedPlaceholder.List,
            modifier = Modifier.height(320.dp).testTag(CommunitiesTags.LOCKED)
        )
        Text(
            text = "Con ParcheU+ ves tus comunidades, quiénes las forman y las abres en tu grafo.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = colors.mu,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun LoadedContent(state: CommunitiesUiState.Loaded, callbacks: CommunitiesCallbacks) {
    val colors = ParcheUTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Tus parches",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = colors.tx
        )
        Column(
            modifier = Modifier.testTag(CommunitiesTags.LIST),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            state.content.items.forEach { community ->
                CommunityCard(community) { callbacks.onViewInGraph(community.id) }
            }
        }
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                painter = rememberVectorPainter(FofIcons.Shield),
                contentDescription = null,
                tint = colors.mu,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Las comunidades se recalculan cada lunes. Los vínculos de Pareja nunca " +
                    "cuentan, y quien oculta su perfil no aparece.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.mu,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/** CommunityCard: nombre, avatares apilados, tamaño, rasgos y "Ver en el grafo". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommunityCard(community: CommunityDto, onViewInGraph: () -> Unit) {
    val colors = ParcheUTheme.colors
    ParcheUCard(modifier = Modifier.fillMaxWidth().testTag(CommunitiesTags.card(community.id))) {
        Text(
            text = community.name,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = colors.tx,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StackedAvatars(
                avatars = community.members.map { it.toAvatar() },
                size = 36.dp,
                anonymousIcon = parcheIconPainter(ParcheIcon.Person)
            )
            Text(
                text = peopleText(community.size),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = colors.mu
            )
        }
        if (community.traits.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                community.traits.forEach { PillChip(text = it) }
            }
        }
        TonalButton(
            text = "Ver en el grafo",
            onClick = onViewInGraph,
            size = ParcheUButtonSize.Small,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Avatar de un miembro: con nombre si el servidor lo mandó con `id`; si no, anónimo. */
internal fun PersonRef.toAvatar(): AvatarSpec {
    val anonymous = label == ANON_LABEL || label.startsWith("$ANON_LABEL de la ")
    return AvatarSpec(
        name = if (anonymous) ANON_LABEL else label,
        affinity = MEMBER_AFFINITY,
        anonymous = anonymous
    )
}

private const val ANON_LABEL = "Alguien"
