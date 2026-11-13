package me.santiagoreina.parcheu.feature.conocer.match

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.contract.MatchChoice
import me.santiagoreina.parcheu.designsystem.R as DsR
import me.santiagoreina.parcheu.designsystem.components.base.ModeChip
import me.santiagoreina.parcheu.designsystem.components.base.ModeChipStyle
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUChildTopBar
import me.santiagoreina.parcheu.designsystem.components.base.PillChip
import me.santiagoreina.parcheu.designsystem.components.base.PillChipVariant
import me.santiagoreina.parcheu.designsystem.components.base.PrimaryButton
import me.santiagoreina.parcheu.designsystem.components.compound.ErrorState
import me.santiagoreina.parcheu.designsystem.components.compound.HeroBlock
import me.santiagoreina.parcheu.designsystem.components.compound.HeroBlockDefaults
import me.santiagoreina.parcheu.designsystem.components.compound.HeroTitleSize
import me.santiagoreina.parcheu.designsystem.theme.ParcheUSizes
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.safety.ReportOrBlockAction
import me.santiagoreina.parcheu.feature.simulation.FacingTwins

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
    modifier: Modifier = Modifier,
    onReportOrBlock: () -> Unit = {}
) {
    Column(modifier = modifier.fillMaxSize().background(ParcheUTheme.colors.bg)) {
        ParcheUChildTopBar(
            title = MatchStrings.QUEUE_TITLE,
            navigationIcon = rememberCloseIcon(),
            navigationContentDescription = MatchStrings.CLOSE,
            onNavigationClick = onClose,
            action = {
                // E8.7: reportar o bloquear a la persona del match, siempre accesible y gratis.
                if (state is MatchFoundUiState.Loaded && !state.expired) {
                    ReportOrBlockAction(onClick = onReportOrBlock)
                }
            }
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
                        onRetryClick = onSearchAgain,
                        illustration = {
                            FacingTwins(
                                otherColor = state.match.mode.colors().solid,
                                width = 64.dp,
                                gap = 44.dp,
                                error = true
                            )
                        }
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
    val modeColors = mode.colors()
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ParcheUTheme.spacing.blockGapTight)
        ) {
            // Las dos P se miran de pie sobre el héroe del modo: la tuya violeta y la de la otra persona
            // espejada, del color del modo. El héroe dice a quién encontramos (anónimo) y en qué modo.
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FacingTwins(otherColor = modeColors.solid, width = 60.dp, gap = 28.dp)
                HeroBlock(
                    title = other.label,
                    overline = MatchStrings.FOUND,
                    subtitle = MatchStrings.ageRange(other.ageRange),
                    titleSize = HeroTitleSize.Headline,
                    colors = HeroBlockDefaults.modeColors(modeColors),
                    badge = {
                        ModeChip(
                            label = MatchStrings.modeName(mode),
                            mode = modeColors,
                            style = ModeChipStyle.Tonal
                        )
                    },
                    modifier = Modifier.testTag(MatchFoundTags.LABEL)
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (other.isDemo) {
                    PillChip(
                        MatchStrings.DEMO_BADGE,
                        modifier = Modifier.testTag(MatchFoundTags.DEMO_BADGE),
                        variant = PillChipVariant.Affinity
                    )
                }
                other.interests.forEach {
                    PillChip(it, modifier = Modifier.testTag(MatchFoundTags.INTEREST))
                }
            }
            Text(
                MatchStrings.HOW_TO_MEET,
                style = MaterialTheme.typography.titleMedium,
                color = colors.tx,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).semantics { heading() }
            )
            Column(
                Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(ParcheUTheme.spacing.blockGapTight)
            ) {
                if (state.showScenario) {
                    ChoiceCard(
                        title = MatchStrings.SCENARIO_TITLE,
                        body = MatchStrings.SCENARIO_BODY,
                        selected = state.selected == MatchChoice.SCENARIO,
                        enabled = !state.waitingOther && !state.submitting,
                        onClick = { onSelect(MatchChoice.SCENARIO) },
                        modifier = Modifier.testTag(MatchFoundTags.SCENARIO_CARD)
                    ) {
                        Icon(
                            painterResource(DsR.drawable.ic_forum),
                            contentDescription = null,
                            tint = it,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                ChoiceCard(
                    title = MatchStrings.SIMULATION_TITLE,
                    body = MatchStrings.SIMULATION_BODY,
                    selected = state.selected == MatchChoice.SIMULATION,
                    enabled = !state.waitingOther && !state.submitting,
                    onClick = { onSelect(MatchChoice.SIMULATION) },
                    modifier = Modifier.testTag(MatchFoundTags.SIMULATION_CARD)
                ) {
                    FacingTwins(
                        otherColor = modeColors.solid,
                        width = ParcheUSizes.QuotaMascot,
                        gap = 0.dp
                    )
                }
            }
            state.actionError?.let {
                Text(
                    MatchStrings.errorMessage(it),
                    color = colors.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(4.dp))
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

/**
 * Tarjeta de elección (ChoiceCard): radio 18, ilustración a la izquierda, título y apoyo, y un radio a la
 * derecha. Marcada: fondo prc y borde de 2 dp violeta; sin marcar: s1 con borde ln.
 */
@Composable
private fun ChoiceCard(
    title: String,
    body: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    illustration: @Composable (tint: Color) -> Unit
) {
    val colors = ParcheUTheme.colors
    val shape = ParcheUTheme.shapes.card
    Row(
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
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier.size(width = 56.dp, height = 48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) colors.s1 else colors.s2),
            contentAlignment = Alignment.Center
        ) {
            illustration(colors.prt)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
        RadioDot(selected)
    }
}

/** Radio visual de 22 dp: aro ln o, marcado, aro y centro violeta. La semántica la da la tarjeta. */
@Composable
private fun RadioDot(selected: Boolean) {
    val colors = ParcheUTheme.colors
    Box(
        Modifier.size(22.dp)
            .border(2.dp, if (selected) colors.prt else colors.mu, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Box(Modifier.size(11.dp).background(colors.prt, CircleShape))
    }
}
