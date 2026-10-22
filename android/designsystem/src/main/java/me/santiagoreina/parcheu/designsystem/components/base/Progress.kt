package me.santiagoreina.parcheu.designsystem.components.base

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/** Barra de progreso lineal de 6dp, radio 3: pista s2 y relleno violeta. [contentDescription] la describe a TalkBack. */
@Composable
fun ParcheULinearProgress(progress: Float, contentDescription: String, modifier: Modifier = Modifier) {
    val colors = ParcheUTheme.colors
    val value = progress.coerceIn(0f, 1f)
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier =
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(shape)
            .background(colors.s2)
            .semantics {
                this.contentDescription = contentDescription
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
            },
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(value).clip(shape).background(colors.primary))
    }
}

/** Cuota en píldoras de 8dp (radio 4): [used] llenas en violeta y el resto en s2. */
@Composable
fun SegmentedQuota(used: Int, total: Int, contentDescription: String, modifier: Modifier = Modifier) {
    require(total > 0) { "La cuota necesita al menos un segmento" }
    val colors = ParcheUTheme.colors
    val filled = used.coerceIn(0, total)
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                progressBarRangeInfo =
                    ProgressBarRangeInfo(filled.toFloat(), 0f..total.toFloat(), total - 1)
            },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(total) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (index < filled) colors.primary else colors.s2),
            )
        }
    }
}

/** Tamaños de la insignia ParcheU+: 22dp en general y 26dp en la pantalla de suscripción (G4). */
enum class PlusBadgeSize(val height: androidx.compose.ui.unit.Dp) {
    Regular(22.dp),
    Large(26.dp),
}

/** Píldora "ParcheU+" con borde degradado violeta a naranja; el "+" va en el texto de acento. */
@Composable
fun ParcheUPlusBadge(modifier: Modifier = Modifier, size: PlusBadgeSize = PlusBadgeSize.Regular) {
    val colors = ParcheUTheme.colors
    val shape = ParcheUTheme.shapes.pill
    val label =
        buildAnnotatedString {
            withStyle(SpanStyle(color = colors.tx)) { append("ParcheU") }
            withStyle(SpanStyle(color = colors.ort)) { append("+") }
        }
    Box(
        modifier =
        modifier
            .height(size.height)
            .clip(shape)
            .border(1.5.dp, colors.plusGradient.brush(), shape)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = ParcheUTheme.textStyles.initials.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 14.sp,
            ),
            maxLines = 1,
        )
    }
}
