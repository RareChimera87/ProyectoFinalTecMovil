package me.santiagoreina.parcheu.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.designsystem.mascot.TwinMascot
import me.santiagoreina.parcheu.designsystem.mascot.TwinMascotSize
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/**
 * Objeto de contexto que acompaña a la mascota en los vacíos y errores (dirección A, "Parche Vivo"):
 * la ilustración dice de qué va el estado sin texto extra.
 */
enum class StateProp {
    /** Globo de chat: aún no hay conversaciones. */
    Chat,

    /** Mapa doblado: sin conexión, la mascota no encuentra el camino. */
    Map,

    /** Taza: el cupo de IA se agotó y toca esperar un rato. */
    Cup,

    /** Cuaderno: algo salió mal o no hay nada anotado todavía. */
    Notebook
}

/** El objeto que corresponde a un error: mapa sin conexión, taza con el cupo de IA y cuaderno en el resto. */
fun AppError.stateProp(): StateProp = when (this) {
    AppError.Network -> StateProp.Map
    is AppError.AiBudget -> StateProp.Cup
    else -> StateProp.Notebook
}

/**
 * Ilustración de `EmptyState`/`ErrorState`: la P que mira (gris y apagada si [error]) junto a un objeto de
 * contexto. Es decorativa: el título y el mensaje del estado ya dicen lo que pasa.
 */
@Composable
fun StateIllustration(prop: StateProp, modifier: Modifier = Modifier, error: Boolean = false) {
    val colors = ParcheUTheme.colors
    val ink = if (error) colors.mu else colors.prt
    val fill = if (error) colors.s2 else colors.prc
    Row(
        modifier.clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        TwinMascot(size = TwinMascotSize.L, error = error, animationsEnabled = false)
        Canvas(Modifier.padding(bottom = 2.dp).size(48.dp)) {
            when (prop) {
                StateProp.Chat -> drawChatBubble(ink, fill)
                StateProp.Map -> drawMap(ink, fill)
                StateProp.Cup -> drawCup(ink, fill)
                StateProp.Notebook -> drawNotebook(ink, fill)
            }
        }
    }
}

private fun DrawScope.outline() = Stroke(
    width = 2.5.dp.toPx(),
    cap = StrokeCap.Round,
    join = StrokeJoin.Round
)

private fun DrawScope.drawChatBubble(ink: Color, fill: Color) {
    val w = size.width
    val h = size.height
    val path =
        Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = w * 0.06f,
                    top = h * 0.14f,
                    right = w * 0.94f,
                    bottom = h * 0.72f,
                    cornerRadius = CornerRadius(w * 0.2f)
                )
            )
            moveTo(w * 0.24f, h * 0.7f)
            lineTo(w * 0.16f, h * 0.9f)
            lineTo(w * 0.42f, h * 0.72f)
        }
    drawPath(path, fill)
    drawPath(path, ink, style = outline())
    val r = w * 0.055f
    listOf(0.32f, 0.5f, 0.68f).forEach { x -> drawCircle(ink, r, Offset(w * x, h * 0.43f)) }
}

private fun DrawScope.drawMap(ink: Color, fill: Color) {
    val w = size.width
    val h = size.height
    val xs = listOf(0.06f, 0.36f, 0.64f, 0.94f).map { it * w }
    val path =
        Path().apply {
            moveTo(xs[0], h * 0.24f)
            lineTo(xs[1], h * 0.14f)
            lineTo(xs[2], h * 0.24f)
            lineTo(xs[3], h * 0.14f)
            lineTo(xs[3], h * 0.8f)
            lineTo(xs[2], h * 0.9f)
            lineTo(xs[1], h * 0.8f)
            lineTo(xs[0], h * 0.9f)
            close()
        }
    drawPath(path, fill)
    drawPath(path, ink, style = outline())
    val stroke = 2.dp.toPx()
    drawLine(ink, Offset(xs[1], h * 0.14f), Offset(xs[1], h * 0.8f), stroke)
    drawLine(ink, Offset(xs[2], h * 0.24f), Offset(xs[2], h * 0.9f), stroke)
    // Ruta punteada que no llega a ningún lado.
    listOf(0.2f to 0.66f, 0.3f to 0.56f, 0.44f to 0.5f, 0.56f to 0.44f).forEach { (x, y) ->
        drawCircle(ink, w * 0.035f, Offset(w * x, h * y))
    }
    drawCircle(ink, w * 0.07f, Offset(w * 0.76f, h * 0.4f), style = Stroke(stroke))
}

private fun DrawScope.drawCup(ink: Color, fill: Color) {
    val w = size.width
    val h = size.height
    val body =
        Path().apply {
            moveTo(w * 0.1f, h * 0.42f)
            lineTo(w * 0.7f, h * 0.42f)
            lineTo(w * 0.64f, h * 0.84f)
            quadraticTo(w * 0.62f, h * 0.92f, w * 0.54f, h * 0.92f)
            lineTo(w * 0.26f, h * 0.92f)
            quadraticTo(w * 0.18f, h * 0.92f, w * 0.16f, h * 0.84f)
            close()
        }
    drawPath(body, fill)
    drawPath(body, ink, style = outline())
    drawArc(
        color = ink,
        startAngle = -90f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(w * 0.58f, h * 0.5f),
        size = Size(w * 0.26f, h * 0.24f),
        style = outline()
    )
    // Vapor.
    listOf(0.28f, 0.48f).forEach { x ->
        val steam =
            Path().apply {
                moveTo(w * x, h * 0.32f)
                quadraticTo(w * (x - 0.07f), h * 0.22f, w * x, h * 0.14f)
                quadraticTo(w * (x + 0.07f), h * 0.07f, w * x, 0f)
            }
        drawPath(steam, ink, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawNotebook(ink: Color, fill: Color) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        fill,
        topLeft = Offset(w * 0.16f, h * 0.08f),
        size = Size(w * 0.7f, h * 0.84f),
        cornerRadius = CornerRadius(w * 0.08f)
    )
    drawRoundRect(
        ink,
        topLeft = Offset(w * 0.16f, h * 0.08f),
        size = Size(w * 0.7f, h * 0.84f),
        cornerRadius = CornerRadius(w * 0.08f),
        style = outline()
    )
    val stroke = 2.dp.toPx()
    listOf(0.26f, 0.42f, 0.58f, 0.74f).forEach { y ->
        drawLine(ink, Offset(w * 0.08f, h * y), Offset(w * 0.24f, h * y), stroke, StrokeCap.Round)
    }
    listOf(0.32f, 0.48f, 0.64f).forEach { y ->
        drawLine(ink, Offset(w * 0.36f, h * y), Offset(w * 0.74f, h * y), stroke, StrokeCap.Round)
    }
}
