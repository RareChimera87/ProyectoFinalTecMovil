package me.santiagoreina.parcheu.designsystem.components.compound

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUTextButton
import me.santiagoreina.parcheu.designsystem.components.base.PrimaryButton
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/**
 * Estado vacío centrado: ilustración opcional (por ejemplo el gemelo), título Sora 22/700, mensaje y un CTA
 * de 52dp opcional (D3-D5, F5, F6).
 */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    illustration: (@Composable () -> Unit)? = null,
    ctaLabel: String? = null,
    onCtaClick: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
) {
    StateLayout(
        title = title,
        message = message,
        modifier = modifier,
        illustration = illustration,
        ctaLabel = ctaLabel,
        onCtaClick = onCtaClick,
        secondaryLabel = secondaryLabel,
        onSecondaryClick = onSecondaryClick,
    )
}

/**
 * Estado de error con reintento. Igual que [EmptyState] pero [retryLabel] es obligatorio: un error sin
 * salida no debería existir (docs/arquitectura.md, `AppError`).
 */
@Composable
fun ErrorState(
    title: String,
    message: String,
    retryLabel: String,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
    illustration: (@Composable () -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
) {
    StateLayout(
        title = title,
        message = message,
        modifier = modifier,
        illustration = illustration,
        ctaLabel = retryLabel,
        onCtaClick = onRetryClick,
        secondaryLabel = secondaryLabel,
        onSecondaryClick = onSecondaryClick,
    )
}

@Composable
private fun StateLayout(
    title: String,
    message: String,
    modifier: Modifier,
    illustration: (@Composable () -> Unit)?,
    ctaLabel: String?,
    onCtaClick: (() -> Unit)?,
    secondaryLabel: String?,
    onSecondaryClick: (() -> Unit)?,
) {
    val colors = ParcheUTheme.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        illustration?.invoke()
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = colors.tx,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.mu,
            textAlign = TextAlign.Center,
        )
        if (ctaLabel != null && onCtaClick != null) {
            PrimaryButton(text = ctaLabel, onClick = onCtaClick, modifier = Modifier.fillMaxWidth())
        }
        if (secondaryLabel != null && onSecondaryClick != null) {
            ParcheUTextButton(text = secondaryLabel, onClick = onSecondaryClick)
        }
    }
}

/**
 * Esqueleto del grafo mientras carga (D3-D5): anillos concéntricos y nodos grises en s2. Pulsa entre .5 y 1
 * de opacidad si [animated]; las capturas lo apagan para que sean deterministas.
 */
@Composable
fun GraphSkeleton(
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    contentDescription: String = "Cargando tu parche",
) {
    val colors = ParcheUTheme.colors
    val alpha by if (animated) {
        rememberInfiniteTransition(label = "graphSkeleton").animateFloat(
            initialValue = 0.5f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "alpha",
        )
    } else {
        androidx.compose.runtime.rememberUpdatedState(0.75f)
    }
    Box(
        modifier = modifier.fillMaxSize().semantics { this.contentDescription = contentDescription },
    ) {
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
            val center = Offset(size.width / 2, size.height / 2)
            val base = size.minDimension / 2
            listOf(0.34f, 0.64f, 0.94f).forEach {
                drawCircle(
                    colors.ln,
                    radius = base * it,
                    center = center,
                    style = Stroke(1.5.dp.toPx()),
                    alpha = alpha,
                )
            }
            drawCircle(colors.s2, radius = 26.dp.toPx(), center = center, alpha = alpha)
            listOf(
                0.34f to 30f,
                0.34f to 150f,
                0.34f to 270f,
                0.64f to 60f,
                0.64f to 140f,
                0.64f to 220f,
                0.64f to 300f,
                0.94f to 20f,
                0.94f to 100f,
                0.94f to 190f,
                0.94f to 330f,
            ).forEach { (ring, degrees) ->
                val radians = Math.toRadians(degrees.toDouble())
                val p = Offset(
                    center.x + (base * ring * kotlin.math.cos(radians)).toFloat(),
                    center.y + (base * ring * kotlin.math.sin(radians)).toFloat(),
                )
                drawCircle(colors.s2, radius = 15.dp.toPx(), center = p, alpha = alpha)
            }
        }
    }
}
