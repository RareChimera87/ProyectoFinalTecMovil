package me.santiagoreina.parcheu.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Degradado lineal con la semántica de CSS `linear-gradient(<ángulo>deg, ...)`: 0° apunta hacia arriba,
 * 90° a la derecha, y la línea del degradado cubre las esquinas del rectángulo. Admite paradas mayores
 * que 1 (el hero del paywall termina en 130 %).
 */
@Immutable
data class ParcheUGradient(val angleDegrees: Float, val stops: List<Pair<Float, Color>>) {
    init {
        require(stops.size >= 2) { "Un degradado necesita al menos dos paradas" }
    }

    fun brush(): Brush = CssLinearGradientBrush(this)
}

/** Degradado radial centrado (celebraciones). Las paradas van de 0 (centro) a 1 (borde). */
@Immutable
data class ParcheURadialGradient(val stops: List<Pair<Float, Color>>) {
    fun brush(): Brush = Brush.radialGradient(colorStops = stops.toTypedArray())
}

private class CssLinearGradientBrush(private val gradient: ParcheUGradient) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val (start, end) = cssGradientLine(gradient.angleDegrees, size, gradient.stops.last().first)
        val maxStop = gradient.stops.last().first.coerceAtLeast(1f)
        return LinearGradientShader(
            from = start,
            to = end,
            colors = gradient.stops.map { it.second },
            colorStops = gradient.stops.map { it.first / maxStop },
        )
    }

    override fun equals(other: Any?): Boolean = other is CssLinearGradientBrush && other.gradient == gradient

    override fun hashCode(): Int = gradient.hashCode()
}

/**
 * Extremos de la línea de un degradado CSS para [size]. Si la última parada pasa de 1, la línea se alarga
 * en esa proporción y las paradas se normalizan.
 */
internal fun cssGradientLine(angleDegrees: Float, size: Size, lastStop: Float = 1f): Pair<Offset, Offset> {
    val radians = Math.toRadians(angleDegrees.toDouble())
    val dx = sin(radians).toFloat()
    val dy = -cos(radians).toFloat()
    val length = abs(size.width * dx) + abs(size.height * dy)
    val center = Offset(size.width / 2f, size.height / 2f)
    val half = Offset(dx, dy) * (length / 2f)
    val start = center - half
    val end = start + Offset(dx, dy) * (length * lastStop.coerceAtLeast(1f))
    return start to end
}
