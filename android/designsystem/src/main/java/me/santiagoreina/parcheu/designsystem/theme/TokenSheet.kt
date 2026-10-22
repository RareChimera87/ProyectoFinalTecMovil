package me.santiagoreina.parcheu.designsystem.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Hoja de tokens (equivalente a S0 del inventario): paleta, modos, degradados, tipografía, escala de
 * afinidad y radios del tema activo. Sirve para la captura Roborazzi de E1.1 y para revisar el tema lado
 * a lado con los mockups. No es una pantalla de la app.
 */
@Composable
fun TokenSheet(modifier: Modifier = Modifier) {
    val colors = ParcheUTheme.colors
    val spacing = ParcheUTheme.spacing
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .background(colors.bg)
            .padding(spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(spacing.blockGap),
    ) {
        Text(
            text =
            buildAnnotatedString {
                withStyle(SpanStyle(color = colors.wordmark)) { append("Parche") }
                withStyle(SpanStyle(color = colors.accent.solid)) { append("U") }
            },
            style = MaterialTheme.typography.displayMedium,
        )
        Overline(if (colors.isDark) "Sistema · tema oscuro" else "Sistema · tema claro")

        Section("Superficies y texto") {
            SwatchRow(
                listOf(
                    "bg" to colors.bg,
                    "s1" to colors.s1,
                    "s2" to colors.s2,
                    "ln" to colors.ln,
                    "prc" to colors.prc,
                ),
            )
            SwatchRow(
                listOf(
                    "primary" to colors.primary,
                    "prt" to colors.prt,
                    "onprc" to colors.onprc,
                    "tx" to colors.tx,
                    "mu" to colors.mu,
                ),
            )
        }

        Section("Modos, acento y error") {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                ModeTag("Amistad", colors.friendship)
                ModeTag("Pareja", colors.couple)
                ModeTag("Estudio", colors.study)
                ModeTag("Plus", colors.accent)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Text("mintt", color = colors.mintt, style = MaterialTheme.typography.labelLarge)
                Text("coralt", color = colors.coralt, style = MaterialTheme.typography.labelLarge)
                Text("bluet", color = colors.bluet, style = MaterialTheme.typography.labelLarge)
                Text("ort", color = colors.ort, style = MaterialTheme.typography.labelLarge)
                Text("error", color = colors.error, style = MaterialTheme.typography.labelLarge)
            }
        }

        Section("Degradados") {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                GradientBox(
                    "plus",
                    Modifier.background(colors.plusGradient.brush(), ParcheUTheme.shapes.card),
                )
                GradientBox(
                    "paywall",
                    Modifier.background(
                        colors.paywallHeroGradient.brush(),
                        ParcheUTheme.shapes.card,
                    ),
                )
                GradientBox(
                    "celebración",
                    Modifier
                        .background(colors.s1, ParcheUTheme.shapes.card)
                        .background(colors.celebrationGradient.brush(), ParcheUTheme.shapes.card),
                )
            }
        }

        Section("Tipografía") {
            TypeSample("Sora 31 · 87 %", MaterialTheme.typography.displaySmall)
            TypeSample("Sora 22 · Tu parche", MaterialTheme.typography.headlineSmall)
            TypeSample("Sora 19 · App bar", MaterialTheme.typography.titleLarge)
            TypeSample("Inter 16 · Cuerpo de texto", MaterialTheme.typography.bodyLarge)
            TypeSample("Inter 15/600 · Botón", MaterialTheme.typography.labelLarge)
            TypeSample("Inter 13 · Secundario", MaterialTheme.typography.bodySmall, colors.mu)
        }

        Section("Afinidad") {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                listOf(50, 64, 72, 80, 88, 95).forEach { affinity ->
                    AffinityAvatar(affinity)
                }
            }
        }

        Section("Radios") {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                val shapes = ParcheUTheme.shapes
                RadiusSample("7", shapes.checkbox)
                RadiusSample("12", shapes.buttonCompact)
                RadiusSample("16", shapes.button)
                RadiusSample("20", shapes.card)
                RadiusSample("28", shapes.heroLarge)
                RadiusSample("999", shapes.pill)
            }
        }
    }
}

@Composable
private fun Overline(text: String) {
    Text(
        text = text.uppercase(),
        style = ParcheUTheme.textStyles.overline,
        color = ParcheUTheme.colors.mu,
    )
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .background(ParcheUTheme.colors.s1, ParcheUTheme.shapes.card)
            .padding(ParcheUTheme.spacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(ParcheUTheme.spacing.sm),
    ) {
        Overline(title)
        content()
    }
}

@Composable
private fun SwatchRow(swatches: List<Pair<String, Color>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(ParcheUTheme.spacing.xs)) {
        swatches.forEach { (name, color) ->
            Column(modifier = Modifier.width(52.dp)) {
                Box(
                    modifier =
                    Modifier
                        .size(52.dp, 36.dp)
                        .background(color, ParcheUTheme.shapes.buttonCompact)
                        .border(1.dp, ParcheUTheme.colors.ln, ParcheUTheme.shapes.buttonCompact),
                )
                Spacer(Modifier.height(ParcheUTheme.spacing.xxs))
                Text(
                    name,
                    style = MaterialTheme.typography.labelSmall,
                    color = ParcheUTheme.colors.tx,
                )
                Text(
                    color.hex(),
                    style = MaterialTheme.typography.labelSmall,
                    color = ParcheUTheme.colors.mu,
                )
            }
        }
    }
}

@Composable
private fun ModeTag(label: String, mode: ModeColors) {
    Box(
        modifier =
        Modifier
            .height(ParcheUSizes.ModeChipMedium)
            .background(mode.solid, ParcheUTheme.shapes.pill)
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = mode.onSolid)
    }
}

@Composable
private fun GradientBox(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(width = 88.dp, height = 56.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(ParcheUTheme.spacing.xs),
            style = MaterialTheme.typography.labelSmall,
            color = if (label ==
                "celebración"
            ) {
                ParcheUTheme.colors.tx
            } else {
                ParcheUTheme.colors.white
            },
        )
    }
}

@Composable
private fun TypeSample(text: String, style: TextStyle, color: Color = ParcheUTheme.colors.tx) {
    Text(text = text, style = style, color = color)
}

@Composable
private fun AffinityAvatar(affinity: Int) {
    val avatar = avatarColors(affinityAlpha(affinity), ParcheUTheme.isDark)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(40.dp).background(avatar.fill, ParcheUTheme.shapes.avatar),
            contentAlignment = Alignment.Center,
        ) {
            Text("LP", style = ParcheUTheme.textStyles.initials, color = avatar.content)
        }
        Text(
            "$affinity",
            style = MaterialTheme.typography.labelSmall,
            color = ParcheUTheme.colors.mu,
        )
    }
}

@Composable
private fun RadiusSample(label: String, shape: Shape) {
    Box(
        modifier =
        Modifier
            .size(40.dp)
            .background(ParcheUTheme.colors.prc, shape)
            .border(1.dp, ParcheUTheme.colors.prt, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = ParcheUTheme.colors.onprc)
    }
}

private fun Color.hex(): String = "#%06X".format(toArgb() and 0xFFFFFF)

@Preview(name = "Hoja de tokens · oscuro", widthDp = 360, heightDp = 1200)
@Composable
private fun TokenSheetDarkPreview() {
    ParcheUTheme(darkTheme = true) { TokenSheet() }
}

@Preview(name = "Hoja de tokens · claro", widthDp = 360, heightDp = 1200)
@Composable
private fun TokenSheetLightPreview() {
    ParcheUTheme(darkTheme = false) { TokenSheet() }
}
