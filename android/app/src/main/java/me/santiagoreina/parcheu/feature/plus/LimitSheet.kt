package me.santiagoreina.parcheu.feature.plus

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.time.Instant
import me.santiagoreina.parcheu.R
import me.santiagoreina.parcheu.contract.QuotaKind
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUTextButton
import me.santiagoreina.parcheu.designsystem.components.base.PrimaryButton
import me.santiagoreina.parcheu.designsystem.components.base.SegmentedQuota
import me.santiagoreina.parcheu.designsystem.components.compound.ParcheUBottomSheetContent
import me.santiagoreina.parcheu.designsystem.mascot.TwinMascot
import me.santiagoreina.parcheu.designsystem.mascot.TwinMascotSize
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/** Píldoras de la barra de cupo agotado (decorativa: indica "cupo lleno", no un conteo). */
private const val LIMIT_SEGMENTS = 5

/**
 * G2 Límite alcanzado: hoja sobre un fondo atenuado con el cupo agotado de [kind] (simulaciones, match
 * al azar o informe mensual). [resetsAt] dice cuándo se renueva; sin él el texto dice "pronto". "Ver
 * ParcheU+" abre G1 y "Esperar" cierra. Con [fairUse] (la persona ya es Plus y llegó al uso justo diario) la hoja
 * no vende ParcheU+: dice que el cupo se renueva mañana y solo ofrece "Esperar". Se compone sin ventana modal para poder capturarla.
 */
@Composable
fun LimitSheet(
    kind: QuotaKind,
    resetsAt: Instant?,
    onSeePlus: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    fairUse: Boolean = false
) {
    val colors = ParcheUTheme.colors
    Box(modifier.fillMaxSize().background(colors.bg).testTag(PlusTags.LIMIT)) {
        // Scrim: tocar fuera de la hoja equivale a "Esperar".
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .clearAndSetSemantics { }
        )
        ParcheUBottomSheetContent(Modifier.align(Alignment.BottomCenter)) {
            TwinMascot(
                size = TwinMascotSize.M,
                error = false,
                animationsEnabled = false,
                modifier = Modifier.alpha(0.85f)
            )
            Text(
                stringResource(
                    if (fairUse) R.string.plus_limit_title_fair_use else limitTitle(kind)
                ),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                ),
                color = colors.tx,
                textAlign = TextAlign.Center
            )
            Text(
                if (fairUse) {
                    stringResource(
                        R.string.plus_limit_body_fair_use
                    )
                } else {
                    limitBody(kind, resetsAt)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.mu,
                textAlign = TextAlign.Center
            )
            SegmentedQuota(
                used = LIMIT_SEGMENTS,
                total = LIMIT_SEGMENTS,
                contentDescription = stringResource(R.string.plus_limit_quota_description),
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                if (!fairUse) {
                    PrimaryButton(
                        text = stringResource(R.string.plus_limit_see_plus),
                        onClick = onSeePlus,
                        modifier = Modifier.fillMaxWidth().testTag(PlusTags.LIMIT_SEE_PLUS)
                    )
                }
                ParcheUTextButton(
                    text = stringResource(
                        if (kind == QuotaKind.SIMULATION && !fairUse) {
                            R.string.plus_limit_wait_simulation
                        } else {
                            R.string.plus_limit_wait
                        }
                    ),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().testTag(PlusTags.LIMIT_WAIT)
                )
            }
        }
    }
}

private fun limitTitle(kind: QuotaKind): Int = when (kind) {
    QuotaKind.SIMULATION -> R.string.plus_limit_title_simulation
    QuotaKind.RANDOM_MATCH -> R.string.plus_limit_title_random_match
    QuotaKind.FULL_REPORT -> R.string.plus_limit_title_full_report
}

@Composable
private fun limitBody(kind: QuotaKind, resetsAt: Instant?): String = if (resetsAt != null) {
    stringResource(
        when (kind) {
            QuotaKind.SIMULATION -> R.string.plus_limit_body_simulation
            QuotaKind.RANDOM_MATCH -> R.string.plus_limit_body_random_match
            QuotaKind.FULL_REPORT -> R.string.plus_limit_body_full_report
        },
        formatWeekdayDate(resetsAt)
    )
} else {
    stringResource(
        when (kind) {
            QuotaKind.SIMULATION -> R.string.plus_limit_body_simulation_soon
            QuotaKind.RANDOM_MATCH -> R.string.plus_limit_body_random_match_soon
            QuotaKind.FULL_REPORT -> R.string.plus_limit_body_full_report_soon
        }
    )
}
