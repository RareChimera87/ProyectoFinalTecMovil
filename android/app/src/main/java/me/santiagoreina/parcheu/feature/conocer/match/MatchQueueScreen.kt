package me.santiagoreina.parcheu.feature.conocer.match

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Usage
import me.santiagoreina.parcheu.designsystem.R as DsR
import me.santiagoreina.parcheu.designsystem.components.base.ModeFilterChip
import me.santiagoreina.parcheu.designsystem.components.base.OutlineButton
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUCard
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUChildTopBar
import me.santiagoreina.parcheu.designsystem.components.base.PrimaryButton
import me.santiagoreina.parcheu.designsystem.components.compound.ErrorState
import me.santiagoreina.parcheu.designsystem.components.compound.IconCircle
import me.santiagoreina.parcheu.designsystem.mascot.TwinMascot
import me.santiagoreina.parcheu.designsystem.mascot.TwinMascotSize
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.simulation.FacingTwins

/** Etiquetas de prueba de F1. */
object MatchQueueTags {
    const val SEARCHING = "match_queue_searching"
    const val CANCEL = "match_queue_cancel"
    const val RETRY = "match_queue_retry"
    const val TODAY_COUNT = "match_queue_today_count"
}

/** F1 · Match al azar, en la fila (13a). El match diario agotado abre la G2 de E7.3 desde la ruta. */
@Composable
fun MatchQueueScreen(
    state: MatchQueueUiState,
    onModeSelected: (Mode) -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOpenMatch: () -> Unit,
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
                    illustration = {
                        TwinMascot(
                            size = TwinMascotSize.L,
                            tone = state.mode.mascotTone,
                            error = true
                        )
                    },
                    modifier = Modifier.testTag(MatchQueueTags.RETRY)
                )
            }
            QueuePhase.Expired ->
                ErrorState(
                    title = MatchStrings.EXPIRED_TITLE,
                    message = MatchStrings.EXPIRED_MESSAGE,
                    retryLabel = MatchStrings.SEARCH_AGAIN,
                    onRetryClick = onRetry,
                    illustration = {
                        TwinMascot(size = TwinMascotSize.L, tone = state.mode.mascotTone)
                    }
                )
            else -> QueueBody(state, onModeSelected, onCancel, onOpenMatch, animate)
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
    val used = state.phase is QueuePhase.AlreadyUsed
    val matched = state.phase is QueuePhase.Matched || used
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ParcheUTheme.spacing.blockGapTight)
        ) {
            ModeSelector(state, onModeSelected)
            SearchStage(state, matched = matched, used = used, animate = animate)
            // Cupo real del día (`GET /v1/home`); sin dato (cargando o con error) se omite la tarjeta.
            state.dailyQuota?.let { quota -> DailyQuotaCard(quota, used) }
            Spacer(Modifier.height(8.dp))
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
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

/**
 * Protagonista de F1: bloque tonal del modo (radio 24) con la mascota del modo grande conversando en el
 * centro de un radar que se abre. Con match, las dos P se miran (la otra espejada). Debajo, qué pasa en
 * Sora 800 y el apoyo.
 */
@Composable
private fun SearchStage(
    state: MatchQueueUiState,
    matched: Boolean,
    used: Boolean,
    animate: Boolean
) {
    val mode = state.mode.colors()
    Column(
        Modifier.fillMaxWidth()
            .clip(ParcheUTheme.shapes.hero)
            .background(mode.container)
            .padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(STAGE_SIZE), contentAlignment = Alignment.Center) {
            Radar(mode.solid, Modifier.matchParentSize())
            if (matched) {
                FacingTwins(otherColor = mode.solid, width = 84.dp, gap = 14.dp)
            } else {
                TwinMascot(
                    size = TwinMascotSize.XL,
                    tone = state.mode.mascotTone,
                    talking = animate,
                    animationsEnabled = animate
                )
            }
        }
        Text(
            text = if (used) {
                MatchStrings.ALREADY_USED_MESSAGE
            } else if (matched) {
                MatchStrings.MATCHED_MESSAGE
            } else {
                MatchStrings.searching(MatchStrings.modeName(state.mode))
            },
            style = MaterialTheme.typography.headlineMedium,
            color = mode.onContainer,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .testTag(MatchQueueTags.SEARCHING)
                .semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                }
        )
        Text(
            text = if (used) MatchStrings.ALREADY_USED_NOTE else MatchStrings.COMPARING,
            style = MaterialTheme.typography.bodyMedium,
            color = mode.onContainer,
            textAlign = TextAlign.Center
        )
    }
}

/** Anillos concéntricos del color del modo detrás de la mascota: el "radar" de la búsqueda. Decorativo. */
@Composable
private fun Radar(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.clearAndSetSemantics { }) {
        val center = this.center
        val max = size.minDimension / 2f
        val stroke = 2.dp.toPx()
        RADAR_RINGS.forEachIndexed { index, fraction ->
            drawCircle(
                color = color.copy(alpha = RADAR_ALPHAS[index]),
                radius = max * fraction - stroke,
                center = center,
                style = Stroke(stroke)
            )
        }
        drawCircle(color = color.copy(alpha = 0.10f), radius = max * 0.58f, center = center)
    }
}

/** Cupo del match al azar de hoy: la cuenta en Sora 800 grande y legible ("1 de 1" o "Sin límite diario"). */
@Composable
private fun DailyQuotaCard(quota: Usage, used: Boolean) {
    val colors = ParcheUTheme.colors
    ParcheUCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            IconCircle(icon = painterResource(DsR.drawable.ic_shuffle), size = 44.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    MatchStrings.TODAY,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.mu
                )
                Text(
                    MatchStrings.todayCount(quota, alreadyCounted = used),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.prt,
                    modifier = Modifier.testTag(MatchQueueTags.TODAY_COUNT)
                )
            }
        }
    }
}

/** Selector de modo con los modos activos del usuario: píldoras con el punto del color del modo. */
@Composable
private fun ModeSelector(
    state: MatchQueueUiState,
    onModeSelected: (Mode) -> Unit,
    modifier: Modifier = Modifier
) {
    val modes = state.activeModes
    if (modes.size < 2) return
    Row(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        modes.forEach { mode ->
            ModeFilterChip(
                label = MatchStrings.modeName(mode),
                selected = mode == state.mode,
                onClick = { onModeSelected(mode) },
                dotColor = mode.colors().solid
            )
        }
    }
}

private val STAGE_SIZE = 210.dp
private val RADAR_RINGS = listOf(1f, 0.8f)
private val RADAR_ALPHAS = listOf(0.22f, 0.36f)
