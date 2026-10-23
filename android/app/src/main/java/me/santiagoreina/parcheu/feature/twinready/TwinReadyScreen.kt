package me.santiagoreina.parcheu.feature.twinready

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.designsystem.components.base.ModeChip
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUTextButton
import me.santiagoreina.parcheu.designsystem.components.base.PrimaryButton
import me.santiagoreina.parcheu.designsystem.components.compound.CelebrationBackground
import me.santiagoreina.parcheu.designsystem.mascot.TwinMascot
import me.santiagoreina.parcheu.designsystem.mascot.TwinMascotSize
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.modes.colors
import me.santiagoreina.parcheu.feature.modes.icon
import me.santiagoreina.parcheu.feature.modes.label

object TwinReadyTags {
    const val SCREEN = "twinready"
    const val VIEW_PARCHE = "twinready:parche"
    const val GO_HOME = "twinready:home"

    fun chip(name: String) = "twinready:chip:$name"
}

/** B5 'Gemelo listo': celebración con el gemelo, los modos activos y las dos salidas (D1 y C1). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TwinReadyScreen(
    state: TwinReadyUiState,
    onViewParche: () -> Unit,
    onGoHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ParcheUTheme.colors
    Column(modifier.fillMaxSize().background(colors.bg).testTag(TwinReadyTags.SCREEN)) {
        CelebrationBackground(Modifier.weight(1f)) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TwinMascot(size = TwinMascotSize.XL, contentDescription = "Tu gemelo")
                Text(
                    "¡Tu gemelo está listo!",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = colors.tx,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    "Ya puede simular conversaciones por ti. Entre más uses ParcheU, mejor te va a conocer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.mu,
                    textAlign = TextAlign.Center
                )
                if (state.modes.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(
                            8.dp,
                            Alignment.CenterHorizontally
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.modes.forEach { mode ->
                            ModeChip(
                                label = mode.label,
                                mode = mode.colors(),
                                icon = mode.icon(),
                                modifier = Modifier.testTag(TwinReadyTags.chip(mode.name))
                            )
                        }
                    }
                }
                // Al terminar B1 el gemelo siempre está en el nivel 1 con las 12 respuestas.
                Text(
                    "Nivel 1 · 12 respuestas",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.mu
                )
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(
                start = 24.dp,
                end = 24.dp,
                top = 12.dp,
                bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PrimaryButton(
                text = "Ver mi parche",
                onClick = onViewParche,
                modifier = Modifier.fillMaxWidth().testTag(TwinReadyTags.VIEW_PARCHE)
            )
            ParcheUTextButton(
                text = "Ir al inicio",
                onClick = onGoHome,
                modifier = Modifier.fillMaxWidth().testTag(TwinReadyTags.GO_HOME)
            )
        }
    }
}
