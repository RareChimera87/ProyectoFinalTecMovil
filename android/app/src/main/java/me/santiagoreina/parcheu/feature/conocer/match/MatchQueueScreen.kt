package me.santiagoreina.parcheu.feature.conocer.match

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.designsystem.components.base.ModeSegment
import me.santiagoreina.parcheu.designsystem.components.base.ModeSegmentedControl
import me.santiagoreina.parcheu.designsystem.components.base.OutlineButton
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUCard
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUChildTopBar
import me.santiagoreina.parcheu.designsystem.components.base.PrimaryButton
import me.santiagoreina.parcheu.designsystem.components.compound.ErrorState
import me.santiagoreina.parcheu.designsystem.components.compound.ParcheUBottomSheet
import me.santiagoreina.parcheu.designsystem.components.compound.ParcheUBottomSheetContent
import me.santiagoreina.parcheu.designsystem.mascot.TwinMascot
import me.santiagoreina.parcheu.designsystem.mascot.TwinMascotSize
import me.santiagoreina.parcheu.designsystem.mascot.TwinMascotTone
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/** Etiquetas de prueba de F1. */
object MatchQueueTags {
    const val SEARCHING = "match_queue_searching"
    const val CANCEL = "match_queue_cancel"
    const val RETRY = "match_queue_retry"
    const val LIMIT_SHEET = "match_limit_sheet"
    const val LIMIT_SEE_PLUS = "match_limit_see_plus"
    const val LIMIT_DISMISS = "match_limit_dismiss"
}

/** F1 · Match al azar, en la fila (13a). Con [MatchQueueUiState.limitSheet] muestra además la hoja de límite. */
@Composable
fun MatchQueueScreen(
    state: MatchQueueUiState,
    onModeSelected: (Mode) -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOpenMatch: () -> Unit,
    onLimitSeePlus: () -> Unit,
    onLimitDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    animate: Boolean = true
) {
    val colors = ParcheUTheme.colors
    Column(modifier = modifier.fillMaxSize().background(colors.bg)) {
        ParcheUChildTopBar(
            title = MatchStrings.QUEUE_TITLE,
            navigationIcon = rememberCloseIcon(),
            navigationContentDescription = MatchStrings.CLOSE,
            onNavigationClick = onCancel
        )
        when (val phase = state.phase) {
            is QueuePhase.Error -> {
                // El selector sigue visible: un error de edad o de modo se arregla cambiando de modo.
                ModeSelector(state, onModeSelected, Modifier.padding(horizontal = 16.dp))
                ErrorState(
                    title = MatchStrings.QUEUE_TITLE,
                    message = MatchStrings.errorMessage(phase.error),
                    retryLabel = MatchStrings.RETRY,
                    onRetryClick = onRetry,
                    modifier = Modifier.testTag(MatchQueueTags.RETRY)
                )
            }
            QueuePhase.Expired ->
                ErrorState(
                    title = MatchStrings.EXPIRED_TITLE,
                    message = MatchStrings.EXPIRED_MESSAGE,
                    retryLabel = MatchStrings.SEARCH_AGAIN,
                    onRetryClick = onRetry
                )
            else -> QueueBody(state, onModeSelected, onCancel, onOpenMatch, animate)
        }
    }
    if (state.limitSheet != null) {
        ParcheUBottomSheet(onDismissRequest = onLimitDismiss) {
            MatchLimitSheetBody(onSeePlus = onLimitSeePlus, onDismiss = onLimitDismiss)
        }
    }
}

@Composable
private fun QueueBody(
    state: MatchQueueUiState,
    onModeSelected: (Mode) -> Unit,
    onCancel: () -> Unit,
    onOpenMatch: () -> Unit,
    animate: Boolean
) {
    val colors = ParcheUTheme.colors
    val matched = state.phase is QueuePhase.Matched
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ModeSelector(state, onModeSelected)
            Spacer(Modifier.height(40.dp))
            TwinMascot(
                size = TwinMascotSize.XL,
                tone = state.mode.mascotTone,
                talking = animate && !matched,
                animationsEnabled = animate
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = if (matched) {
                    MatchStrings.MATCHED_MESSAGE
                } else {
                    MatchStrings.searching(MatchStrings.modeName(state.mode))
                },
                style = MaterialTheme.typography.titleMedium,
                color = colors.tx,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .testTag(MatchQueueTags.SEARCHING)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = MatchStrings.COMPARING,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.mu,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            ParcheUCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    MatchStrings.TODAY,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.tx
                )
                Text(
                    MatchStrings.TODAY_COUNT,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.prt
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (matched) {
                PrimaryButton(MatchStrings.SEE_MATCH, onOpenMatch, Modifier.fillMaxWidth())
            } else {
                OutlineButton(
                    text = MatchStrings.CANCEL_SEARCH,
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth().testTag(MatchQueueTags.CANCEL)
                )
                Text(
                    MatchStrings.CANCEL_NOTE,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.mu
                )
            }
        }
    }
}

/** Selector de modo con los modos activos del usuario. */
@Composable
private fun ModeSelector(
    state: MatchQueueUiState,
    onModeSelected: (Mode) -> Unit,
    modifier: Modifier = Modifier
) {
    val modes = state.activeModes
    if (modes.size < 2) return
    ModeSegmentedControl(
        segments = modes.map { ModeSegment(MatchStrings.modeName(it), it.colors()) },
        selectedIndex = modes.indexOf(state.mode).coerceAtLeast(0),
        onSelect = { onModeSelected(modes[it]) },
        modifier = modifier.fillMaxWidth()
    )
}

/** Contenido de la hoja de límite de match diario; se expone aparte para capturarlo sin ventana. */
@Composable
fun MatchLimitSheetBody(onSeePlus: () -> Unit, onDismiss: () -> Unit) {
    val colors = ParcheUTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().testTag(MatchQueueTags.LIMIT_SHEET),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TwinMascot(size = TwinMascotSize.L, tone = TwinMascotTone.User, animationsEnabled = false)
        Text(
            MatchStrings.LIMIT_TITLE,
            style = MaterialTheme.typography.titleLarge,
            color = colors.tx,
            textAlign = TextAlign.Center
        )
        Text(
            MatchStrings.LIMIT_MESSAGE,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.mu,
            textAlign = TextAlign.Center
        )
        PrimaryButton(
            MatchStrings.SEE_PLUS,
            onSeePlus,
            Modifier.fillMaxWidth().testTag(MatchQueueTags.LIMIT_SEE_PLUS)
        )
        OutlineButton(
            MatchStrings.UNDERSTOOD,
            onDismiss,
            Modifier.fillMaxWidth().testTag(MatchQueueTags.LIMIT_DISMISS)
        )
    }
}

/** Hoja de límite dibujada sin ventana modal, para las capturas. */
@Composable
fun MatchLimitSheetPreview(modifier: Modifier = Modifier) {
    ParcheUBottomSheetContent(modifier) {
        MatchLimitSheetBody(onSeePlus = {}, onDismiss = {})
    }
}
