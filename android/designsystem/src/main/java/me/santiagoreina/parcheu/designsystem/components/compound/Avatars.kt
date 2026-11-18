package me.santiagoreina.parcheu.designsystem.components.compound

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.santiagoreina.parcheu.designsystem.theme.AFFINITY_ALPHA_UNMEASURED
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.designsystem.theme.affinityAlphaOrNeutral
import me.santiagoreina.parcheu.designsystem.theme.avatarColors

/** Descripción del avatar anónimo: no revela quién es (docs/mockups-inventario.md, AvatarInitials). */
const val ANONYMOUS_AVATAR_DESCRIPTION = "Alguien"

/** Tamaños de avatar usados por el inventario. */
object AvatarSizes {
    val Xs: Dp = 26.dp
    val Sm: Dp = 32.dp
    val Home: Dp = 34.dp
    val Md: Dp = 36.dp
    val List: Dp = 40.dp
    val Lg: Dp = 48.dp
    val Xl: Dp = 56.dp
    val Profile: Dp = 64.dp
    val Hero: Dp = 72.dp
}

/**
 * Hasta dos iniciales en mayúsculas: primera letra de las dos primeras palabras que empiezan por letra o
 * dígito. Se ignoran las que empiezan por puntuación o emoji, de modo que "Valentina (demo)" da "V" y no
 * "V(". Vacío si no queda ninguna palabra.
 */
fun initialsOf(name: String): String = name.trim().split(Regex("\\s+"))
    .filter { it.isNotEmpty() && Character.isLetterOrDigit(it.codePointAt(0)) }
    .take(2)
    .joinToString("") { String(Character.toChars(it.codePointAt(0))).uppercase() }

/**
 * Iniciales para mostrar a partir de las que manda el servidor: conserva solo letras y dígitos (el servidor
 * toma el primer carácter de cada palabra y con "Valentina (demo)" manda "V("). Si el servidor no mandó
 * nada se calculan de [name] con [initialsOf]; si mandó solo signos (p. ej. "?" de una persona oculta) o
 * no hay nombre, se devuelve [fallback].
 */
fun displayInitials(serverInitials: String, name: String, fallback: String = "?"): String {
    val clean = buildString {
        var i = 0
        while (i < serverInitials.length) {
            val cp = serverInitials.codePointAt(i)
            if (Character.isLetterOrDigit(cp)) appendCodePoint(cp)
            i += Character.charCount(cp)
        }
    }.uppercase()
    return when {
        clean.isNotEmpty() -> clean
        serverInitials.isBlank() -> initialsOf(name).ifEmpty { fallback }
        else -> fallback
    }
}

/**
 * Avatar de iniciales. El relleno es violeta con el alpha de [affinityAlphaOrNeutral] (50 o menos da .22;
 * 95 o más da 1; `null` = sin medir, alpha neutro [AFFINITY_ALPHA_UNMEASURED]) y el color del texto sigue
 * ADR-0030. Con [anonymous] se dibuja la variante anónima: s2, borde
 * discontinuo prt, icono de persona, **sin iniciales** y con la descripción [anonymousDescription]
 * ("Alguien"), de modo que ni la pantalla ni el lector revelan el nombre.
 *
 * [anonymousIcon] es el icono de persona (E1.2); si es nulo se dibuja una silueta propia.
 * [initialsEndInset] corre el contenido a la izquierda para centrarlo en la parte visible cuando otro
 * avatar tapa el borde derecho ([StackedAvatars]); [initialsSizeRatio] es el tamaño del texto respecto
 * al círculo.
 */
@Composable
fun AvatarInitials(
    name: String,
    affinity: Int?,
    modifier: Modifier = Modifier,
    size: Dp = AvatarSizes.List,
    anonymous: Boolean = false,
    anonymousIcon: Painter? = null,
    anonymousDescription: String = ANONYMOUS_AVATAR_DESCRIPTION,
    initialsEndInset: Dp = 0.dp,
    initialsSizeRatio: Float = 0.38f
) {
    val colors = ParcheUTheme.colors
    if (anonymous) {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(colors.s2, CircleShape)
                .dashedBorder(colors.prt, 1.5.dp, size / 2)
                .clearAndSetSemantics { contentDescription = anonymousDescription },
            contentAlignment = Alignment.Center
        ) {
            if (anonymousIcon != null) {
                Icon(
                    painter = anonymousIcon,
                    contentDescription = null,
                    tint = colors.prt,
                    modifier = Modifier.padding(end = initialsEndInset).size(size * 0.55f)
                )
            } else {
                PersonSilhouette(
                    colors.prt,
                    Modifier.padding(end = initialsEndInset).size(size * 0.55f)
                )
            }
        }
    } else {
        val palette = avatarColors(affinityAlphaOrNeutral(affinity), colors.isDark)
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(palette.fill, CircleShape)
                .clearAndSetSemantics { contentDescription = name },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initialsOf(name),
                modifier = Modifier.padding(end = initialsEndInset),
                style = ParcheUTheme.textStyles.initials.copy(
                    fontSize = (size.value * initialsSizeRatio).coerceAtLeast(10f).sp,
                    lineHeight = (size.value * 0.48f).coerceAtLeast(12f).sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = palette.content,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PersonSilhouette(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        drawCircle(
            color,
            radius = size.width * 0.22f,
            center = Offset(
                size.width / 2,
                size.height * 0.3f
            )
        )
        drawArc(
            color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(size.width * 0.1f, size.height * 0.58f),
            size = Size(size.width * 0.8f, size.height * 0.8f)
        )
    }
}

/** Proporción del texto de las iniciales respecto al círculo en [StackedAvatars] (0,38 suelto). */
private const val STACKED_INITIALS_RATIO = 0.31f

/** Datos de un avatar dentro de [StackedAvatars]. */
@Immutable
data class AvatarSpec(val name: String, val affinity: Int?, val anonymous: Boolean = false)

/**
 * Avatares apilados con solape (-10dp; -12dp en E3) y borde de 2-3dp del color de la superficie que hay
 * debajo, para separarlos. Si hay más que [maxVisible] se agrega un círculo "+N".
 */
@Composable
fun StackedAvatars(
    avatars: List<AvatarSpec>,
    modifier: Modifier = Modifier,
    size: Dp = AvatarSizes.Sm,
    overlap: Dp = 10.dp,
    borderWidth: Dp = 2.dp,
    borderColor: Color = ParcheUTheme.colors.s1,
    maxVisible: Int = 4,
    anonymousIcon: Painter? = null
) {
    val colors = ParcheUTheme.colors
    val visible = avatars.take(maxVisible)
    val extra = avatars.size - visible.size
    // El fondo opaco debajo de cada círculo evita que el relleno con alpha deje ver al de al lado.
    val ring = Modifier
        .border(borderWidth, borderColor, CircleShape)
        .background(borderColor, CircleShape)
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(-overlap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        visible.forEachIndexed { index, avatar ->
            // El siguiente círculo tapa los últimos [overlap] dp: las iniciales se centran en la parte
            // que queda a la vista y bajan un poco de tamaño para que no se corten ("VR", "AM").
            val covered = index < visible.lastIndex || extra > 0
            AvatarInitials(
                name = avatar.name,
                affinity = avatar.affinity,
                anonymous = avatar.anonymous,
                anonymousIcon = anonymousIcon,
                size = size,
                modifier = ring,
                initialsEndInset = if (covered) overlap else 0.dp,
                initialsSizeRatio = STACKED_INITIALS_RATIO
            )
        }
        if (extra > 0) {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(colors.s2, CircleShape)
                    .then(ring),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+$extra",
                    style = ParcheUTheme.textStyles.initials.copy(
                        fontSize = (size.value * 0.34f).sp
                    ),
                    color = colors.tx
                )
            }
        }
    }
}

/**
 * Anillo de afinidad (E2, F7, F8, I3): radio 50,5 en 112dp o 54 en 120dp, trazo 11-12, extremos
 * redondeados y el porcentaje en Sora 29-31/700. Nunca usa rojo ni verde: pista ln y arco violeta (prt en
 * oscuro, violeta en claro para llegar a 3:1 de contraste gráfico).
 */
@Composable
fun AffinityRing(
    affinity: Int,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    label: String = "Afinidad $affinity por ciento"
) {
    val colors = ParcheUTheme.colors
    val diameter = if (large) 120.dp else 112.dp
    val stroke = if (large) 12.dp else 11.dp
    val fraction = affinity.coerceIn(0, 100) / 100f
    Box(
        modifier = modifier.size(diameter).semantics(mergeDescendants = true) {
            contentDescription = label
        },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(diameter)) {
            val strokePx = stroke.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            drawArc(
                color = colors.ln,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(strokePx)
            )
            if (fraction > 0f) {
                drawArc(
                    color = if (colors.isDark) colors.prt else colors.primary,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(strokePx, cap = StrokeCap.Round)
                )
            }
        }
        Text(
            text = "$affinity%",
            style = ParcheUTheme.textStyles.price.copy(
                fontSize = (if (large) 31 else 29).sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold
            ),
            color = colors.tx,
            modifier = Modifier.width(diameter - stroke * 2),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1
        )
    }
}
