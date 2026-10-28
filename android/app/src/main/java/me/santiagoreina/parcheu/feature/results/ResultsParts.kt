package me.santiagoreina.parcheu.feature.results

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.santiagoreina.parcheu.designsystem.components.base.OutlineButton
import me.santiagoreina.parcheu.designsystem.components.base.PrimaryButton
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.designsystem.theme.affinityAlpha

/*
 * Piezas de F7/F8/I3: AffinityRing, informe bloqueado (LockedOverlay) y DecisionFooter. Son
 * versiones locales anteriores a los compuestos de E1.4 y los iconos de E1.2; cambiarlas por los
 * de `designsystem` queda como paso aparte (docs/pendientes-humanos.md, E4.6).
 */

private fun icon(name: String, path: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
        .build()

/** Iconos Material Symbols (24dp) de la pantalla de resultados. */
internal object ResultsIcons {
    val Lock = icon(
        "lock",
        "M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z"
    )
    val ArrowForward =
        icon("arrow_forward", "M12 4l-1.41 1.41L16.17 11H4v2h12.17l-5.58 5.59L12 20l8-8z")
    val Connect = icon(
        "person_add",
        "M15 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm-9-2V7H4v3H1v2h3v3h2v-3h3v-2H6zm9 4c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"
    )
    val Reason = icon(
        "check_circle",
        "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"
    )
    val Friction = icon(
        "error",
        "M11 15h2v2h-2zm0-8h2v6h-2zm.99-5C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8z"
    )
    val Hourglass = icon(
        "hourglass_empty",
        "M6 2v6h.01L6 8.01 10 12l-4 4 .01.01H6V22h12v-5.99h-.01L18 16l-4-4 4-3.99-.01-.01H18V2H6zm10 14.5V20H8v-3.5l4-4 4 4zm-4-5l-4-4V4h8v3.5l-4 4z"
    )
    val Chat = icon(
        "chat_bubble",
        "M20 2H4c-1.1 0-1.99.9-1.99 2L2 22l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2z"
    )
}

/**
 * Afinidad en texto para el título ('Tú y Laura · afinidad alta'). Mismos cortes del grafo
 * (≥85, 70-84).
 */
internal fun affinityWord(score: Int): String = when {
    score >= 85 -> "muy alta"
    score >= 70 -> "alta"
    score >= 50 -> "media"
    else -> "baja"
}

/**
 * AffinityRing: aro de 120dp, trazo 12, fondo s2 y avance en violeta con la opacidad de la
 * afinidad.
 */
@Composable
internal fun AffinityRing(score: Int, modifier: Modifier = Modifier, size: Dp = 120.dp) {
    val colors = ParcheUTheme.colors
    val progress = score.coerceIn(0, 100) / 100f
    val ring = colors.primary.copy(alpha = affinityAlpha(score))
    Box(
        modifier.size(size).semantics { contentDescription = "$score % de afinidad" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(size)) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                colors.s2,
                0f,
                360f,
                false,
                Offset(inset, inset),
                arcSize,
                style = Stroke(stroke)
            )
            drawArc(
                ring,
                -90f,
                360f * progress,
                false,
                Offset(inset, inset),
                arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Text(
            buildAnnotatedString {
                append("$score")
                withStyle(SpanStyle(fontSize = 16.sp)) { append(" %") }
            },
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 31.sp,
                letterSpacing = (-1).sp
            ),
            color = colors.tx
        )
    }
}

/** Fila de razón o choque: icono 20 y texto 14. */
@Composable
internal fun ReportRow(text: String, icon: Painter, tint: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = ParcheUTheme.colors.tx)
    }
}

/** Título de tarjeta: 12/600 mayúsculas en mu. */
@Composable
internal fun CardOverline(text: String) {
    Text(text.uppercase(), style = ParcheUTheme.textStyles.overline, color = ParcheUTheme.colors.mu)
}

/**
 * LockedOverlay de F7: tarjetas de relleno (barras, no texto: el servidor no envía el contenido)
 * con blur 6 y opacidad .55, y encima el candado, el título y las acciones.
 */
@Composable
internal fun LockedReport(
    reasonsCount: Int,
    frictionsCount: Int,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit
) {
    val colors = ParcheUTheme.colors
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).testTag(ResultsTags.LOCKED)) {
        // Las tarjetas difuminadas llenan lo que mida el contenido superpuesto (que crece con la fuente).
        Column(
            Modifier.matchParentSize().blur(6.dp).alpha(0.55f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PlaceholderCard(
                "Por qué son compatibles",
                reasonsCount.coerceAtLeast(2),
                Modifier.weight(1f)
            )
            PlaceholderCard("Dónde chocarían", frictionsCount.coerceAtLeast(2), Modifier.weight(1f))
        }
        Column(
            Modifier.fillMaxWidth().heightIn(min = LOCKED_MIN_HEIGHT).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
        ) {
            Box(
                Modifier.size(44.dp)
                    .clip(CircleShape)
                    .background(colors.s1)
                    .border(1.5.dp, colors.ln, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    rememberResultsIcon(ResultsIcons.Lock),
                    contentDescription = null,
                    tint = colors.tx,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = colors.tx,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 270.dp)
            )
            Text(
                message,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = colors.mu,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 270.dp)
            )
            Spacer(Modifier.height(4.dp))
            actions()
        }
    }
}

/**
 * Alto mínimo del bloque: cabe el candado, el texto y dos botones encima de las tarjetas de
 * relleno.
 */
private val LOCKED_MIN_HEIGHT = 320.dp

@Composable
private fun PlaceholderCard(title: String, rows: Int, modifier: Modifier = Modifier) {
    val colors = ParcheUTheme.colors
    Column(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.s1)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CardOverline(title)
        repeat(rows.coerceAtMost(3)) { index ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(20.dp).clip(CircleShape).background(colors.s2))
                Box(
                    Modifier.fillMaxWidth(if (index % 2 == 0) 0.85f else 0.6f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.s2)
                )
            }
        }
    }
}

/**
 * DecisionFooter: 'Ahora no' (1) + 'Conectar' (1,4) y la nota de que el otro no sabrá el
 * 'Ahora no'.
 */
@Composable
internal fun DecisionFooter(
    note: String,
    enabled: Boolean,
    onDecline: () -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxWidth().padding(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = 16.dp
        ).testTag(ResultsTags.FOOTER),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineButton(
                text = "Ahora no",
                onClick = onDecline,
                enabled = enabled,
                modifier = Modifier.weight(1f).testTag(ResultsTags.DECLINE)
            )
            PrimaryButton(
                text = "Conectar",
                onClick = onConnect,
                enabled = enabled,
                icon = rememberResultsIcon(ResultsIcons.Connect),
                modifier = Modifier.weight(1.4f).testTag(ResultsTags.CONNECT)
            )
        }
        Text(
            note,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = ParcheUTheme.colors.mu,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun rememberResultsIcon(vector: ImageVector): Painter =
    androidx.compose.ui.graphics.vector.rememberVectorPainter(vector)
