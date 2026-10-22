package me.santiagoreina.parcheu.feature.parche

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp

/**
 * Iconos de D1-D5 dibujados con trazos simples (24 dp, tintables). Los vectores de Material Symbols
 * Rounded llegan con la biblioteca de iconos del sistema de diseño; mientras tanto esta feature no
 * depende de ella y cada icono tiene su equivalente de los mockups en el nombre.
 */
enum class ParcheIcon(private val build: ImageVector.Builder.() -> Unit) {
    Timeline({
        line {
            moveTo(4f, 17f)
            lineTo(9f, 11f)
            lineTo(13f, 14f)
            lineTo(20f, 6f)
        }
        dot(4f, 17f)
        dot(9f, 11f)
        dot(13f, 14f)
        dot(20f, 6f)
    }),
    PersonAdd({
        circle(10f, 8f, 4f)
        line {
            moveTo(3f, 20f)
            curveTo(3f, 15.5f, 6.5f, 14f, 10f, 14f)
            curveTo(12f, 14f, 13.5f, 14.4f, 14.5f, 15f)
        }
        line {
            moveTo(18f, 14f)
            lineTo(18f, 20f)
            moveTo(15f, 17f)
            lineTo(21f, 17f)
        }
    }),
    Tune({
        line {
            moveTo(4f, 7f)
            lineTo(20f, 7f)
            moveTo(4f, 17f)
            lineTo(20f, 17f)
        }
        circle(9f, 7f, 2.2f, filled = true)
        circle(15f, 17f, 2.2f, filled = true)
    }),
    Bubbles({
        circle(9f, 14f, 5f)
        circle(17f, 7f, 3f)
    }),
    Apps({
        circle(7f, 7f, 2f, filled = true)
        circle(17f, 7f, 2f, filled = true)
        circle(7f, 17f, 2f, filled = true)
        circle(17f, 17f, 2f, filled = true)
    }),
    Friends({
        circle(9f, 9f, 3f)
        circle(17f, 10f, 2.4f)
        line {
            moveTo(3f, 20f)
            curveTo(3f, 15f, 15f, 15f, 15f, 20f)
        }
    }),
    Heart({
        line {
            moveTo(12f, 20f)
            curveTo(4f, 14f, 3f, 10f, 5f, 7.5f)
            curveTo(7f, 5.5f, 10.5f, 6f, 12f, 8.5f)
            curveTo(13.5f, 6f, 17f, 5.5f, 19f, 7.5f)
            curveTo(21f, 10f, 20f, 14f, 12f, 20f)
            close()
        }
    }),
    School({
        line {
            moveTo(2f, 9f)
            lineTo(12f, 4f)
            lineTo(22f, 9f)
            lineTo(12f, 14f)
            close()
            moveTo(6f, 11.5f)
            lineTo(6f, 16f)
            curveTo(8f, 18.5f, 16f, 18.5f, 18f, 16f)
            lineTo(18f, 11.5f)
        }
    }),
    Forum({
        line {
            moveTo(4f, 5f)
            lineTo(20f, 5f)
            lineTo(20f, 15f)
            lineTo(9f, 15f)
            lineTo(4f, 19f)
            close()
        }
    }),
    Chat({
        line {
            moveTo(4f, 6f)
            curveTo(4f, 4.9f, 4.9f, 4f, 6f, 4f)
            lineTo(18f, 4f)
            curveTo(19.1f, 4f, 20f, 4.9f, 20f, 6f)
            lineTo(20f, 15f)
            curveTo(20f, 16.1f, 19.1f, 17f, 18f, 17f)
            lineTo(8f, 17f)
            lineTo(4f, 21f)
            close()
        }
    }),
    Replay({
        line {
            moveTo(19f, 12f)
            curveTo(19f, 15.9f, 15.9f, 19f, 12f, 19f)
            curveTo(8.1f, 19f, 5f, 15.9f, 5f, 12f)
            curveTo(5f, 8.1f, 8.1f, 5f, 12f, 5f)
            curveTo(14f, 5f, 15.8f, 5.8f, 17f, 7.2f)
            moveTo(17.5f, 3.5f)
            lineTo(17.5f, 7.5f)
            lineTo(13.5f, 7.5f)
        }
    }),
    CloudOff({
        line {
            moveTo(7f, 18f)
            curveTo(4.2f, 18f, 3f, 15f, 4.5f, 13f)
            curveTo(5.2f, 12f, 6.2f, 11.6f, 7.3f, 11.6f)
            curveTo(8.2f, 8.5f, 12f, 6.5f, 15f, 8.5f)
            moveTo(17f, 11.1f)
            curveTo(20f, 11.5f, 21.4f, 15f, 19.5f, 17f)
            curveTo(19f, 17.6f, 18.2f, 18f, 17.5f, 18f)
            lineTo(11f, 18f)
            moveTo(3f, 3f)
            lineTo(21f, 21f)
        }
    }),
    Lock({
        line {
            moveTo(6f, 11f)
            lineTo(18f, 11f)
            lineTo(18f, 20f)
            lineTo(6f, 20f)
            close()
            moveTo(8.5f, 11f)
            lineTo(8.5f, 8f)
            curveTo(8.5f, 3.5f, 15.5f, 3.5f, 15.5f, 8f)
            lineTo(15.5f, 11f)
        }
    }),
    Person({
        circle(12f, 8f, 4f)
        line {
            moveTo(4f, 21f)
            curveTo(4f, 14.5f, 20f, 14.5f, 20f, 21f)
        }
    });

    internal fun vector(): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(build).build()
}

@Composable
fun parcheIconPainter(icon: ParcheIcon): Painter = rememberVectorPainter(icon.vector())

private val Ink = SolidColor(Color.Black)

private fun ImageVector.Builder.line(block: PathBuilder.() -> Unit) = path(
    fill = null,
    stroke = Ink,
    strokeLineWidth = 2f,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
    pathBuilder = block
)

private fun ImageVector.Builder.dot(cx: Float, cy: Float) = circle(cx, cy, 1.6f, filled = true)

private fun ImageVector.Builder.circle(cx: Float, cy: Float, r: Float, filled: Boolean = false) =
    path(
        fill = if (filled) Ink else null,
        stroke = if (filled) null else Ink,
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(cx - r, cy)
        arcToRelative(r, r, 0f, true, true, 2 * r, 0f)
        arcToRelative(r, r, 0f, true, true, -2 * r, 0f)
        close()
    }
