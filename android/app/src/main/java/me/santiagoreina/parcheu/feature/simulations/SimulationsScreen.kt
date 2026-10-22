package me.santiagoreina.parcheu.feature.simulations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.contract.SimulationStatus
import me.santiagoreina.parcheu.contract.SimulationSummary
import me.santiagoreina.parcheu.designsystem.components.base.OutlineButton
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUCard
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUChildTopBar
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUTextButton
import me.santiagoreina.parcheu.designsystem.components.base.PrimaryButton
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.simulation.SimulationIcons
import me.santiagoreina.parcheu.feature.simulation.SimulationMascot
import me.santiagoreina.parcheu.feature.simulation.errorMessage
import me.santiagoreina.parcheu.feature.simulation.forMode
import me.santiagoreina.parcheu.feature.simulation.label
import me.santiagoreina.parcheu.feature.simulation.rememberIcon

/** Etiquetas de prueba de F6 y la lista. */
object SimulationsTags {
    const val EMPTY = "simulations:empty"
    const val LIST = "simulations:list"
    const val ERROR = "simulations:error"
    const val LOAD_MORE = "simulations:loadMore"

    fun item(id: String) = "simulations:item:$id"
}

/** F6 (vacío), la lista 'Ver todas las simulaciones' y sus estados de carga y error. */
@Composable
fun SimulationsScreen(
    state: SimulationsUiState,
    onBack: () -> Unit,
    onOpen: (SimulationSummary) -> Unit,
    onPickFromParche: () -> Unit,
    onRandomMatch: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(ParcheUTheme.colors.bg)) {
        ParcheUChildTopBar(
            title = "Simulaciones",
            navigationIcon = rememberIcon(SimulationIcons.ArrowBack),
            navigationContentDescription = "Volver",
            onNavigationClick = onBack,
        )
        when (state) {
            SimulationsUiState.Loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ParcheUTheme.colors.primary)
                }
            is SimulationsUiState.Empty -> EmptyContent(
                state.available,
                onPickFromParche,
                onRandomMatch,
            )
            is SimulationsUiState.Loaded -> ListContent(state, onOpen, onLoadMore)
            is SimulationsUiState.Error -> ErrorContent(state, onRetry)
        }
    }
}

@Composable
private fun EmptyContent(available: Int?, onPickFromParche: () -> Unit, onRandomMatch: () -> Unit) {
    val colors = ParcheUTheme.colors
    Column(
        Modifier.fillMaxSize().testTag(SimulationsTags.EMPTY).padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        SimulationMascot(colors.primary, 84.dp)
        Text(
            "Aún no tienes simulaciones",
            style = MaterialTheme.typography.headlineSmall,
            color = colors.tx,
            textAlign = TextAlign.Center,
        )
        val quota =
            when (available) {
                null -> ""
                1 -> " Tienes 1 disponible esta semana."
                else -> " Tienes $available disponibles esta semana."
            }
        Text(
            "Elige a alguien de tu parche o prueba un match al azar.$quota",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.mu,
            textAlign = TextAlign.Center,
        )
        PrimaryButton(
            text = "Elegir de mi parche",
            onClick = onPickFromParche,
            icon = rememberIcon(SimulationIcons.Hub),
            modifier = Modifier.fillMaxWidth(),
        )
        ParcheUTextButton(text = "Match al azar", onClick = onRandomMatch)
    }
}

@Composable
private fun ListContent(state: SimulationsUiState.Loaded, onOpen: (SimulationSummary) -> Unit, onLoadMore: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().testTag(SimulationsTags.LIST),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(state.items, key = { it.id }) { item -> SimulationRow(item, onOpen) }
        if (state.nextCursor != null) {
            item {
                OutlineButton(
                    text = if (state.loadingMore) "Cargando…" else "Ver más",
                    onClick = onLoadMore,
                    enabled = !state.loadingMore,
                    modifier = Modifier.fillMaxWidth().testTag(SimulationsTags.LOAD_MORE),
                )
            }
        }
    }
}

@Composable
private fun SimulationRow(item: SimulationSummary, onOpen: (SimulationSummary) -> Unit) {
    val colors = ParcheUTheme.colors
    ParcheUCard(onClick = {
        onOpen(item)
    }, modifier = Modifier.fillMaxWidth().testTag(SimulationsTags.item(item.id))) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(colors.forMode(item.mode).solid),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    item.other.initials,
                    style = ParcheUTheme.textStyles.initials,
                    color = colors.onMode,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    item.other.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.tx,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${item.mode.label()} · ${statusText(item)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.mu,
                )
            }
        }
    }
}

private fun statusText(item: SimulationSummary): String = when (item.status) {
    SimulationStatus.DONE -> item.score?.let { "Resultado $it %" } ?: "Lista"
    SimulationStatus.FAILED -> "No se completó"
    SimulationStatus.QUEUED,
    SimulationStatus.RUNNING,
    -> "En curso"
}

@Composable
private fun ErrorContent(state: SimulationsUiState.Error, onRetry: () -> Unit) {
    val colors = ParcheUTheme.colors
    Column(
        Modifier.fillMaxSize().testTag(SimulationsTags.ERROR).padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(
            "No pudimos cargar tus simulaciones",
            style = MaterialTheme.typography.headlineSmall,
            color = colors.tx,
            textAlign = TextAlign.Center,
        )
        Text(
            errorMessage(state.error),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.mu,
            textAlign = TextAlign.Center,
        )
        PrimaryButton(text = "Reintentar", onClick = onRetry, modifier = Modifier.fillMaxWidth())
    }
}
