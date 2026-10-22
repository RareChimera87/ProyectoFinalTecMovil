package me.santiagoreina.parcheu.designsystem.components.compound

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.designsystem.theme.ParcheURadius
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/**
 * Matriz de módulos de un código QR ya codificado (cuadrada, `true` = módulo oscuro). El codificador
 * (ZXing o similar) vive en la feature de invitaciones (E6.1); el sistema de diseño solo dibuja.
 */
@Immutable
class QrMatrix(val size: Int, private val modules: BooleanArray) {
    init {
        require(size > 0) { "El tamaño debe ser positivo" }
        require(modules.size == size * size) {
            "Se esperaban ${size * size} módulos y llegaron ${modules.size}"
        }
    }

    operator fun get(row: Int, column: Int): Boolean = modules[row * size + column]

    override fun equals(other: Any?): Boolean =
        other is QrMatrix && other.size == size && other.modules.contentEquals(modules)

    override fun hashCode(): Int = 31 * size + modules.contentHashCode()
}

/** Tinta del nombre del QR y de los módulos: #1A1626 sobre blanco en ambos temas (el lector exige contraste alto). */
private val QrInk = Color(0xFF1A1626)

/**
 * Tarjeta del código QR (C5): fondo blanco fijo, radio 24, QR de 200dp con el logo de la app al centro y
 * el nombre #1A1626 debajo. Los colores no cambian con el tema porque un QR claro sobre oscuro no se lee.
 */
@Composable
fun QRCard(
    matrix: QrMatrix,
    name: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    logo: Painter? = null
) {
    val shape = RoundedCornerShape(ParcheURadius.Hero)
    Column(
        modifier = modifier
            .clip(shape)
            .background(Color.White, shape)
            .padding(20.dp)
            .semantics(mergeDescendants = true) { this.contentDescription = contentDescription },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(200.dp).clearAndSetSemantics { }) {
                val cell = size.width / matrix.size
                for (row in 0 until matrix.size) {
                    for (column in 0 until matrix.size) {
                        if (matrix[row, column]) {
                            drawRect(
                                color = QrInk,
                                topLeft = Offset(column * cell, row * cell),
                                size = Size(cell + 0.5f, cell + 0.5f)
                            )
                        }
                    }
                }
            }
            if (logo != null) {
                Box(
                    Modifier
                        .size(44.dp)
                        .background(Color.White, RoundedCornerShape(10.dp))
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = logo,
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = QrInk
        )
    }
}

/**
 * Gráfica de línea de la precisión del gemelo (C3), 296 x 96dp: [points] en 0..100, curva suave en prt
 * (violeta en claro), relleno degradado, puntos y cuadrícula ln. [contentDescription] resume la tendencia
 * para el lector de pantalla, porque el dibujo solo no basta.
 */
@Composable
fun LineChart(
    points: List<Float>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    startLabel: String? = null,
    endLabel: String? = null
) {
    require(points.size >= 2) { "La gráfica necesita al menos dos puntos" }
    val colors = ParcheUTheme.colors
    val line = if (colors.isDark) colors.prt else colors.primary
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            this.contentDescription = contentDescription
        },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Canvas(Modifier.fillMaxWidth().height(96.dp).clearAndSetSemantics { }) {
            val padX = 6.dp.toPx()
            val padY = 8.dp.toPx()
            val w = size.width - padX * 2
            val h = size.height - padY * 2
            listOf(0f, 0.5f, 1f).forEach {
                val y = padY + h * it
                drawLine(
                    colors.ln,
                    Offset(padX, y),
                    Offset(size.width - padX, y),
                    strokeWidth = 1.dp.toPx()
                )
            }
            val coords = points.mapIndexed { i, v ->
                Offset(
                    padX + w * i / (points.size - 1),
                    padY + h * (1f - v.coerceIn(0f, 100f) / 100f)
                )
            }
            val curve = Path().apply {
                moveTo(coords.first().x, coords.first().y)
                for (i in 1 until coords.size) {
                    val a = coords[i - 1]
                    val b = coords[i]
                    val midX = (a.x + b.x) / 2f
                    cubicTo(midX, a.y, midX, b.y, b.x, b.y)
                }
            }
            val area = Path().apply {
                addPath(curve)
                lineTo(coords.last().x, size.height - padY)
                lineTo(coords.first().x, size.height - padY)
                close()
            }
            drawPath(
                area,
                Brush.verticalGradient(
                    listOf(line.copy(alpha = 0.28f), line.copy(alpha = 0f)),
                    startY = padY,
                    endY = size.height - padY
                )
            )
            drawPath(
                curve,
                line,
                style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            coords.forEach {
                drawCircle(colors.bg, radius = 4.5.dp.toPx(), center = it)
                drawCircle(line, radius = 3.dp.toPx(), center = it)
            }
        }
        if (startLabel != null || endLabel != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    startLabel.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.mu
                )
                Text(
                    endLabel.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.mu
                )
            }
        }
    }
}
