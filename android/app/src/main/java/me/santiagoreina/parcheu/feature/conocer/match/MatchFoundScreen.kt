package me.santiagoreina.parcheu.feature.conocer.match

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.contract.MatchChoice
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUChildTopBar
import me.santiagoreina.parcheu.designsystem.components.base.PillChip
import me.santiagoreina.parcheu.designsystem.components.base.PillChipVariant
import me.santiagoreina.parcheu.designsystem.components.base.PrimaryButton
import me.santiagoreina.parcheu.designsystem.components.compound.AvatarInitials
import me.santiagoreina.parcheu.designsystem.components.compound.ErrorState
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/** Etiquetas de prueba de F2. */
object MatchFoundTags {
    const val LABEL = "match_found_label"
    const val DEMO_BADGE = "match_found_demo_badge"
    const val SCENARIO_CARD = "match_found_scenario"
    const val SIMULATION_CARD = "match_found_simulation"
    const val START = "match_found_start"
    const val INTEREST = "match_found_interest"
}

/** F2 · Match encontrado (13b): perfil anónimo y tarjetas Escenario o Simulación. */
@Composable
fun MatchFoundScreen(
    state: MatchFoundUiState,
    onSelect: (MatchChoice) -> Unit,
    onStart: () -> Unit,
    onRetry: () -> Unit,
    onSearchAgain: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(ParcheUTheme.colors.bg)) {
        ParcheUChildTopBar(
            title = MatchStrings.QUEUE_TITLE,
            navigationIcon = rememberCloseIcon(),
            navigationContentDescription = MatchStrings.CLOSE,
            onNavigationClick = onClose
        )
        when (state) {
            MatchFoundUiState.Loading ->
                Text(
                    MatchStrings.LOADING,
                    modifier = Modifier.padding(24.dp),
                    color = ParcheUTheme.colors.mu
                )
            is MatchFoundUiState.Error ->
                ErrorState(
                    title = MatchStrings.QUEUE_TITLE,
                    message = MatchStrings.errorMessage(state.error),
                    retryLabel = MatchStrings.RETRY,
                    onRetryClick = onRetry
                )
            is MatchFoundUiState.Loaded ->
                if (state.expired) {
                    ErrorState(
                        title = MatchStrings.MATCH_EXPIRED_TITLE,
                        message = MatchStrings.MATCH_EXPIRED_MESSAGE,
                        retryLabel = MatchStrings.SEARCH_AGAIN,
                        onRetryClick = onSearchAgain
                    )
                } else {
                    LoadedBody(state, onSelect, onStart)
                }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun LoadedBody(
    state: MatchFoundUiState.Loaded,
    onSelect: (MatchChoice) -> Unit,
    onStart: () -> Unit
) {
    val colors = ParcheUTheme.colors
    val other = state.match.other
    val mode = state.match.mode
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                MatchStrings.FOUND,
                style = ParcheUTheme.textStyles.overline,
                color = mode.colors().text,
                modifier = Modifier.testTag(MatchFoundTags.LABEL)
            )
            AvatarInitials(name = other.label, affinity = 0, size = 88.dp, anonymous = true)
            Text(
                other.label,
                style = MaterialTheme.typography.titleLarge,
                color = colors.tx,
                textAlign = TextAlign.Center
            )
            Text(
                MatchStrings.ageRange(other.ageRange),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.mu
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                me.santiagoreina.parcheu.designsystem.components.base.ModeChip(
                    label = MatchStrings.modeName(mode),
                    mode = mode.colors()
                )
                if (other.isDemo) {
                    PillChip(
                        MatchStrings.DEMO_BADGE,
                        modifier = Modifier.testTag(MatchFoundTags.DEMO_BADGE),
                        variant = PillChipVariant.Neutral
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                other.interests.forEach {
                    PillChip(it, modifier = Modifier.testTag(MatchFoundTags.INTEREST))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                MatchStrings.HOW_TO_MEET,
                style = MaterialTheme.typography.titleSmall,
                color = colors.tx,
                modifier = Modifier.fillMaxWidth()
            )
            if (state.showScenario) {
                ChoiceCard(
                    title = MatchStrings.SCENARIO_TITLE,
                    body = MatchStrings.SCENARIO_BODY,
                    selected = state.selected == MatchChoice.SCENARIO,
                    enabled = !state.waitingOther && !state.submitting,
                    onClick = { onSelect(MatchChoice.SCENARIO) },
                    modifier = Modifier.testTag(MatchFoundTags.SCENARIO_CARD)
                )
            }
            ChoiceCard(
                title = MatchStrings.SIMULATION_TITLE,
                body = MatchStrings.SIMULATION_BODY,
                selected = state.selected == MatchChoice.SIMULATION,
                enabled = !state.waitingOther && !state.submitting,
                onClick = { onSelect(MatchChoice.SIMULATION) },
                modifier = Modifier.testTag(MatchFoundTags.SIMULATION_CARD)
            )
            state.actionError?.let {
                Text(
                    MatchStrings.errorMessage(it),
                    color = colors.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        PrimaryButton(
            text = when {
                state.waitingOther -> MatchStrings.WAITING_OTHER
                state.submitting -> MatchStrings.STARTING
                else -> MatchStrings.START
            },
            onClick = onStart,
            enabled = !state.waitingOther && !state.submitting,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .testTag(MatchFoundTags.START)
        )
    }
}

/** Tarjeta de elección (ChoiceCard del inventario): radio 20, borde 2 violeta y fondo prc si está marcada. */
@Composable
private fun ChoiceCard(
    title: String,
    body: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ParcheUTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) colors.prc else colors.s1, shape)
            .border(BorderStroke(2.dp, if (selected) colors.primary else colors.ln), shape)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) colors.onprc else colors.tx
        )
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) colors.onprc else colors.mu
        )
    }
}
