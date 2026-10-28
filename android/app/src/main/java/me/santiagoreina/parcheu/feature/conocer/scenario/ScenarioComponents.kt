package me.santiagoreina.parcheu.feature.conocer.scenario

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.contract.ScenarioSessionStatus
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUButtonSize
import me.santiagoreina.parcheu.designsystem.components.base.ParcheULinearProgress
import me.santiagoreina.parcheu.designsystem.components.base.PrimaryButton
import me.santiagoreina.parcheu.designsystem.components.base.TonalButton
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

object ScenarioTags {
    const val CARD = "scenario_card"
    const val TIMER = "scenario_timer"
    const val AGREE = "scenario_agree"
    const val RESULT = "scenario_result"
    const val STATUS = "scenario_status"
}

/**
 * Tarjeta del escenario fijada arriba de F3 (`ScenarioPinnedCard`, prc/onprc): título, consigna, temporizador y
 * barra con el tiempo que queda. El presupuesto va como texto del escenario.
 */
@Composable
fun ScenarioPinnedCard(state: ScenarioUiState, modifier: Modifier = Modifier) {
    val session = state.session ?: return
    val colors = ParcheUTheme.colors
    val total = totalSeconds(state)
    val fraction = if (total > 0) state.remainingSec.toFloat() / total else 0f
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(colors.prc, RoundedCornerShape(16.dp))
            .padding(14.dp)
            .testTag(ScenarioTags.CARD),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = session.title ?: "Escenario",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.onprc,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = ScenarioStrings.clock(state.remainingSec),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (state.remainingSec in 1..60) colors.coralt else colors.onprc,
                modifier = Modifier.testTag(ScenarioTags.TIMER)
            )
        }
        session.prompt?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = colors.onprc)
        }
        session.budgetCop?.takeIf { it > 0 }?.let {
            Text(
                text = ScenarioStrings.budget(it),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.onprc
            )
        }
        ParcheULinearProgress(
            progress = fraction,
            contentDescription = "Tiempo restante ${ScenarioStrings.clock(state.remainingSec)}",
            modifier = Modifier.semantics { contentDescription = "Tiempo restante" }
        )
    }
}

/** Total del temporizador: si no se conoce se asume lo que queda, para que la barra no se rompa. */
private fun totalSeconds(state: ScenarioUiState): Long = maxOf(state.remainingSec, state.totalSec)

/** Zona de acuerdo de F3 sobre la caja de texto: botón, espera o cierre según el estado de la sesión. */
@Composable
fun ScenarioAgreementBar(
    state: ScenarioUiState,
    onAgree: () -> Unit,
    onOpenResults: () -> Unit,
    modifier: Modifier = Modifier
) {
    val session = state.session ?: return
    val colors = ParcheUTheme.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        state.actionError?.let {
            Text(
                ScenarioStrings.error(it),
                style = MaterialTheme.typography.bodySmall,
                color = colors.coralt
            )
        }
        when (session.status) {
            ScenarioSessionStatus.OPEN -> {
                if (session.otherAgreed) {
                    Text(
                        ScenarioStrings.OTHER_AGREED,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.mintt,
                        modifier = Modifier.testTag(ScenarioTags.STATUS)
                    )
                }
                PrimaryButton(
                    text = ScenarioStrings.AGREE,
                    onClick = onAgree,
                    enabled = state.canAgree,
                    size = ParcheUButtonSize.Medium,
                    modifier = Modifier.fillMaxWidth().testTag(ScenarioTags.AGREE)
                )
            }
            ScenarioSessionStatus.WAITING_OTHER ->
                Text(
                    ScenarioStrings.WAITING_OTHER,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.mu,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.ln, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                        .testTag(ScenarioTags.STATUS)
                )
            ScenarioSessionStatus.JUDGING ->
                Text(
                    ScenarioStrings.JUDGING,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.mu,
                    modifier = Modifier.testTag(ScenarioTags.STATUS)
                )
            ScenarioSessionStatus.DONE ->
                if (session.simulationId != null) {
                    TonalButton(
                        text = ScenarioStrings.SEE_RESULT,
                        onClick = onOpenResults,
                        modifier = Modifier.fillMaxWidth().testTag(ScenarioTags.RESULT)
                    )
                } else {
                    Text(
                        ScenarioStrings.DONE_NO_RESULT,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.mu,
                        modifier = Modifier.testTag(ScenarioTags.STATUS)
                    )
                }
        }
    }
}
