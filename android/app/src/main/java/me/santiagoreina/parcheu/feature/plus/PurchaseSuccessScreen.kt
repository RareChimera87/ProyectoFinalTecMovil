package me.santiagoreina.parcheu.feature.plus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.R
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUCard
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUTextButton
import me.santiagoreina.parcheu.designsystem.components.base.PrimaryButton
import me.santiagoreina.parcheu.designsystem.components.compound.CelebrationBackground
import me.santiagoreina.parcheu.designsystem.mascot.TwinMascot
import me.santiagoreina.parcheu.designsystem.mascot.TwinMascotSize
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/** Acciones de G3. */
data class PurchaseSuccessActions(val onExplore: () -> Unit = {}, val onHome: () -> Unit = {})

/**
 * G3 Compra exitosa: celebración con el plan, el precio y el próximo cobro que devolvió la API (se omiten
 * si aún no se conocen); variante mensual y 'verificación pendiente' (sin celebración: el pago aún no se
 * confirmó, así que no se promete nada que todavía no existe).
 */
@Composable
fun PurchaseSuccessScreen(
    state: PurchaseSuccessUiState,
    actions: PurchaseSuccessActions,
    modifier: Modifier = Modifier
) {
    val colors = ParcheUTheme.colors
    if (state.pending) {
        Column(
            modifier.fillMaxSize().background(colors.bg).testTag(PlusTags.SUCCESS_PENDING),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                Modifier.weight(1f).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically)
            ) {
                TwinMascot(size = TwinMascotSize.XL, animationsEnabled = false)
                Text(
                    stringResource(R.string.plus_pending_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.tx,
                    textAlign = TextAlign.Center
                )
                Text(
                    stringResource(R.string.plus_pending_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.mu,
                    textAlign = TextAlign.Center
                )
                Text(
                    stringResource(R.string.plus_pending_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.mu,
                    textAlign = TextAlign.Center
                )
            }
            PrimaryButton(
                text = stringResource(R.string.plus_success_home),
                onClick = actions.onHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(ParcheUTheme.spacing.footerCelebration)
                    .testTag(PlusTags.SUCCESS_HOME)
            )
        }
        return
    }
    Box(modifier.fillMaxSize().background(colors.bg).testTag(PlusTags.SUCCESS)) {
        CelebrationBackground {
            Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically)
                ) {
                    Box {
                        TwinMascot(size = TwinMascotSize.XL, animationsEnabled = false)
                        Icon(
                            painter = rememberPlusIcon(PlusIcons.Star),
                            contentDescription = null,
                            tint = colors.ort,
                            modifier = Modifier.size(36.dp).align(Alignment.TopEnd)
                        )
                    }
                    Text(
                        stringResource(R.string.plus_success_title),
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.tx,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = successSubtitle(state),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.mu,
                        textAlign = TextAlign.Center
                    )
                    ParcheUCard(Modifier.fillMaxWidth()) {
                        Benefit(
                            rememberPlusIcon(PlusIcons.AllInclusive),
                            R.string.plus_success_benefit_unlimited
                        )
                        Benefit(
                            rememberPlusIcon(PlusIcons.Hub),
                            R.string.plus_success_benefit_graph
                        )
                        Benefit(
                            rememberPlusIcon(PlusIcons.Description),
                            R.string.plus_success_benefit_report
                        )
                    }
                }
                Column(
                    Modifier.fillMaxWidth().padding(ParcheUTheme.spacing.footerCelebration),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PrimaryButton(
                        text = stringResource(R.string.plus_success_explore),
                        onClick = actions.onExplore,
                        modifier = Modifier.fillMaxWidth().testTag(PlusTags.SUCCESS_EXPLORE)
                    )
                    ParcheUTextButton(
                        text = stringResource(R.string.plus_success_home),
                        onClick = actions.onHome,
                        modifier = Modifier.fillMaxWidth().testTag(PlusTags.SUCCESS_HOME)
                    )
                }
            }
        }
    }
}

@Composable
private fun successSubtitle(state: PurchaseSuccessUiState): String {
    val plan =
        when {
            state.price != null && state.annual ->
                stringResource(R.string.plus_success_plan_annual, state.price)
            state.price != null -> stringResource(R.string.plus_success_plan_monthly, state.price)
            state.annual -> stringResource(R.string.plus_success_plan_annual_nopr)
            else -> stringResource(R.string.plus_success_plan_monthly_nopr)
        }
    val charge = state.nextCharge?.let { stringResource(R.string.plus_success_next_charge, it) }
    return listOfNotNull("$plan.", charge).joinToString(" ")
}

@Composable
private fun Benefit(icon: Painter, text: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = ParcheUTheme.colors.prt,
            modifier = Modifier.size(20.dp)
        )
        Text(
            stringResource(text),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Normal),
            color = ParcheUTheme.colors.tx
        )
    }
}
