package me.santiagoreina.parcheu.designsystem.components.compound

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.designsystem.components.base.OutlineButton
import me.santiagoreina.parcheu.designsystem.components.base.PrimaryButton
import me.santiagoreina.parcheu.designsystem.theme.ParcheUPalette
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/**
 * Fondo de celebración (B5, G3): degradado radial naranja a violeta a transparente y seis piezas de
 * confeti fijas (determinista, sin partículas). Es decorativo: no aporta semántica.
 */
@Composable
fun CelebrationBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val colors = ParcheUTheme.colors
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.celebrationGradient.brush())
                .clearAndSetSemantics { }
        )
        Confetti()
        content()
    }
}

private data class ConfettiPiece(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val degrees: Float,
    val color: Color
)

private val ConfettiPieces = listOf(
    ConfettiPiece(0.12f, 0.10f, 10f, 18f, 24f, ParcheUPalette.Orange),
    ConfettiPiece(0.82f, 0.14f, 12f, 12f, 0f, ParcheUPalette.Violet),
    ConfettiPiece(0.28f, 0.30f, 8f, 16f, -38f, ParcheUPalette.Mint),
    ConfettiPiece(0.90f, 0.38f, 10f, 20f, 52f, ParcheUPalette.Coral),
    ConfettiPiece(0.08f, 0.52f, 12f, 12f, 0f, ParcheUPalette.ElectricBlue),
    ConfettiPiece(0.70f, 0.60f, 9f, 18f, -20f, ParcheUPalette.OrangeTint)
)

@Composable
private fun Confetti() {
    Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
        ConfettiPieces.forEach { piece ->
            val center = Offset(size.width * piece.x, size.height * piece.y)
            val w = piece.width.dp.toPx()
            val h = piece.height.dp.toPx()
            rotate(piece.degrees, pivot = center) {
                drawRoundRect(
                    color = piece.color,
                    topLeft = Offset(center.x - w / 2, center.y - h / 2),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
            }
        }
    }
}

/**
 * Pie de decisión (F7, F8, I3): "Ahora no" a la izquierda (contorno), el botón principal "Conectar" y, bajo
 * ambos, una nota corta de consentimiento. Ambos botones miden 52dp; el principal ocupa el espacio sobrante.
 */
@Composable
fun DecisionFooter(
    secondaryLabel: String,
    onSecondaryClick: () -> Unit,
    primaryLabel: String,
    onPrimaryClick: () -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null
) {
    val colors = ParcheUTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.bg)
            .padding(ParcheUTheme.spacing.footer),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineButton(text = secondaryLabel, onClick = onSecondaryClick)
            PrimaryButton(
                text = primaryLabel,
                onClick = onPrimaryClick,
                modifier = Modifier.weight(1f)
            )
        }
        if (note != null) {
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = colors.mu,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Tres puntos de 8dp que se encienden en secuencia (A1, carga). Con [animated] falso quedan fijos. */
@Composable
fun LoadingDots(
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    contentDescription: String = "Cargando"
) {
    val colors = ParcheUTheme.colors
    val transition = if (animated) rememberInfiniteTransition(label = "loadingDots") else null
    Row(
        modifier = modifier.semantics { this.contentDescription = contentDescription },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val alpha by (
                transition?.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 0.3f,
                    animationSpec = infiniteRepeatable(
                        animation = keyframes {
                            durationMillis = 1200
                            0.3f at (index * 200)
                            1f at (index * 200 + 300)
                            0.3f at (index * 200 + 600)
                        },
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "dot$index"
                ) ?: rememberUpdatedState(if (index == 0) 1f else 0.5f)
                )
            Box(
                Modifier
                    .size(8.dp)
                    .alpha(alpha)
                    .background(colors.prt, CircleShape)
                    .clearAndSetSemantics { }
            )
        }
    }
}

/**
 * Indicador de páginas del onboarding (A2-A4): la página activa es una píldora de 22 x 8dp y las demás
 * círculos de 8dp. [pageLabel] arma el anuncio de accesibilidad ("Página 2 de 3").
 */
@Composable
fun PageIndicator(
    count: Int,
    current: Int,
    modifier: Modifier = Modifier,
    pageLabel: (current: Int, count: Int) -> String = { c, n -> "Página $c de $n" }
) {
    require(count > 0) { "count debe ser positivo" }
    val colors = ParcheUTheme.colors
    val active = current.coerceIn(0, count - 1)
    Row(
        modifier = modifier.semantics { contentDescription = pageLabel(active + 1, count) },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            val selected = index == active
            Box(
                Modifier
                    .height(8.dp)
                    .width(if (selected) 22.dp else 8.dp)
                    .clip(CircleShape)
                    .background(if (selected) colors.prt else colors.ln, CircleShape)
                    .clearAndSetSemantics { }
            )
        }
    }
}
