package me.santiagoreina.parcheu.feature.twinready

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.santiagoreina.parcheu.designsystem.components.base.ModeChip
import me.santiagoreina.parcheu.designsystem.components.base.ModeChipStyle
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUTextButton
import me.santiagoreina.parcheu.designsystem.components.base.PrimaryButton
import me.santiagoreina.parcheu.designsystem.components.compound.CelebrationBackground
import me.santiagoreina.parcheu.designsystem.mascot.TwinMascotGlyph
import me.santiagoreina.parcheu.designsystem.mascot.TwinMascotSize
import me.santiagoreina.parcheu.designsystem.theme.ParcheUPalette
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.modes.colors
import me.santiagoreina.parcheu.feature.modes.icon
import me.santiagoreina.parcheu.feature.modes.label

object TwinReadyTags {
    const val SCREEN = "twinready"
    const val VIEW_PARCHE = "twinready:parche"
    const val GO_HOME = "twinready:home"
    const val LEVEL = "twinready:level"

    fun chip(name: String) = "twinready:chip:$name"
}

/**
 * B5 'Gemelo listo' (dirección A): sobre el fondo de celebración, un héroe violeta con tu P blanca grande, el
 * titular en Sora 800 y el texto; debajo, los modos activos (chips tonales) y el nivel. Abajo, las dos salidas
 * (D1 y C1) con el botón primario.
 */
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
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ReadyHero()
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
                                style = ModeChipStyle.Tonal,
                                modifier = Modifier.testTag(TwinReadyTags.chip(mode.name))
                            )
                        }
                    }
                }
                // Nivel real de `GET /v1/twin`; sin dato (cargando o con error) no se muestra nada.
                state.twinLevel?.let { level ->
                    Text(
                        "Nivel $level",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.tx,
                        modifier = Modifier.testTag(TwinReadyTags.LEVEL)
                    )
                }
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

/** Héroe de B5: la P blanca (ojo violeta) con un destello naranja de celebración, el titular y el texto. */
@Composable
private fun ReadyHero() {
    val colors = ParcheUTheme.colors
    val shape = ParcheUTheme.shapes.hero
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.hero, shape)
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            TwinMascotGlyph(
                width = TwinMascotSize.XL.width,
                color = colors.onHero,
                eyeColor = colors.hero,
                modifier = Modifier
                    .padding(end = 18.dp, top = 10.dp)
                    .semantics { contentDescription = "Tu gemelo" }
            )
            Text(
                "✦",
                color = ParcheUPalette.Orange,
                fontSize = 30.sp,
                modifier = Modifier.clearAndSetSemantics { }
            )
        }
        Text(
            "¡Tu gemelo está listo!",
            style = MaterialTheme.typography.headlineLarge,
            color = colors.onHero,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            "Ya puede simular conversaciones por ti. Entre más uses ParcheU, mejor te va a conocer.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onHero,
            textAlign = TextAlign.Center
        )
    }
}
