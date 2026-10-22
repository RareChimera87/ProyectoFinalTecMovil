package me.santiagoreina.parcheu.designsystem.components.base

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.designsystem.theme.ModeColors
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/** Opción de [ModeSegmentedControl]: etiqueta y color del modo (el activo se pinta con el pleno y texto oscuro). */
data class ModeSegment(val label: String, val mode: ModeColors)

/**
 * Control segmentado por modo: 44dp con 2 modos (E1, radio 14) o 48dp con 3 (F1, radio 16). El activo usa
 * el color pleno del modo con texto #14111F.
 */
@Composable
fun ModeSegmentedControl(
    segments: List<ModeSegment>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    require(segments.size in 2..3) { "El control por modo admite 2 o 3 segmentos" }
    val three = segments.size == 3
    SegmentedFrame(
        labels = segments.map { it.label },
        activeContainers = segments.map { it.mode.solid },
        activeContents = segments.map { it.mode.onSolid },
        selectedIndex = selectedIndex,
        onSelect = onSelect,
        visualHeight = if (three) 48.dp else 44.dp,
        radius = if (three) 16.dp else 14.dp,
        modifier = modifier,
        enabled = enabled,
    )
}

/** Control segmentado neutro (C5: "Mi código" / "Escanear"): 44dp, activo prc/onprc. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    require(options.size >= 2) { "Un control segmentado necesita al menos 2 opciones" }
    val colors = ParcheUTheme.colors
    SegmentedFrame(
        labels = options,
        activeContainers = options.map { colors.prc },
        activeContents = options.map { colors.onprc },
        selectedIndex = selectedIndex,
        onSelect = onSelect,
        visualHeight = 44.dp,
        radius = 14.dp,
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
private fun SegmentedFrame(
    labels: List<String>,
    activeContainers: List<Color>,
    activeContents: List<Color>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    visualHeight: Dp,
    radius: Dp,
    modifier: Modifier,
    enabled: Boolean,
) {
    val colors = ParcheUTheme.colors
    val shape = RoundedCornerShape(radius)
    val innerShape = RoundedCornerShape(radius - 3.dp)
    // El alto del nodo es de 48dp (área táctil); lo que se dibuja mide visualHeight y va centrado.
    Box(
        modifier = modifier.fillMaxWidth().height(
            maxOf(visualHeight, MinTouchTarget),
        ).selectableGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxWidth().height(visualHeight).clip(shape).background(colors.s2))
        Row(Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 3.dp)) {
            labels.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = selected,
                            enabled = enabled,
                            role = Role.Tab,
                            onClick = { onSelect(index) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    val weight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                    val container = if (selected) activeContainers[index] else Color.Transparent
                    val content = if (selected) activeContents[index] else colors.mu
                    Box(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(visualHeight - 6.dp)
                            .clip(innerShape)
                            .background(if (enabled) container else container.disabled()),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = weight),
                            color = if (enabled) content else content.disabled(),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
