package me.santiagoreina.parcheu.feature.presentar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.designsystem.mascot.MascotMouth
import me.santiagoreina.parcheu.designsystem.mascot.TwinMascotGlyph
import me.santiagoreina.parcheu.designsystem.theme.ModeColors
import me.santiagoreina.parcheu.designsystem.theme.ParcheUPalette

/**
 * Las dos P frente a frente sobre un héroe del color del modo (E2, E3): la de la izquierda mira a la
 * derecha y la otra va con `mirrored`. Cuerpo blanco (decorativo), ojo del pleno del modo y el
 * nombre en la tinta del modo (≥ 5,3:1). [dimmed] las apaga (simulación fallida); [talking] abre la boca. Los
 * nombres ya están en el overline del héroe, así que para TalkBack es decorativo.
 */
@Composable
internal fun FacingMascots(
    first: String,
    second: String,
    mode: ModeColors,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    talking: Boolean = false
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp).clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom
    ) {
        PairMascot(first, mode, mirrored = false, dimmed = dimmed, talking = talking)
        PairMascot(second, mode, mirrored = true, dimmed = dimmed, talking = talking)
    }
}

@Composable
private fun PairMascot(
    name: String,
    mode: ModeColors,
    mirrored: Boolean,
    dimmed: Boolean,
    talking: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TwinMascotGlyph(
            width = 56.dp,
            color = if (dimmed) ParcheUPalette.White.copy(alpha = 0.45f) else ParcheUPalette.White,
            eyeColor = mode.solid,
            mouth = if (talking) MascotMouth.Talking else MascotMouth.Quiet,
            mirrored = mirrored
        )
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = mode.onSolid,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp).widthIn(max = 150.dp)
        )
    }
}
