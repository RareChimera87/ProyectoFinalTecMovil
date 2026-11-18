package me.santiagoreina.parcheu.designsystem.components.compound

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.santiagoreina.parcheu.designsystem.theme.ModeColors
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.designsystem.theme.SoraFontFamily

/** Colores de un [HeroBlock]: relleno, contenido (texto e iconos) y "apagado" (mascotas gastadas). */
@Immutable
data class HeroBlockColors(val container: Color, val content: Color, val quiet: Color)

object HeroBlockDefaults {
    /** Violeta pleno con blanco encima (5,15:1) en ambos temas: el héroe por defecto. */
    @Composable
    fun colors(): HeroBlockColors {
        val c = ParcheUTheme.colors
        return HeroBlockColors(container = c.hero, content = c.onHero, quiet = c.heroQuiet)
    }

    /**
     * Héroe del color pleno de un modo (Amistad, Pareja, Estudio) con tinta #14111F encima (≥ 5,3:1).
     * Úsalo cuando el protagonista de la pantalla es una relación de ese modo (F2, F8).
     */
    fun modeColors(mode: ModeColors): HeroBlockColors = HeroBlockColors(
        container = mode.solid,
        content = mode.onSolid,
        quiet = mode.onSolid.copy(alpha = 0.28f)
    )

    /** Relleno del héroe: 16 a los lados, 14 arriba y abajo (dirección A). */
    val ContentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
}

/**
 * Tamaño del titular del héroe, todos Sora 800: número gigante de 56 ([Number]), número compacto de 46
 * ([NumberCompact], el del mockup A · Inicio cuando hay más bloques arriba del pliegue) o titular de 28
 * ([Headline]).
 */
enum class HeroTitleSize { Number, NumberCompact, Headline }

/**
 * Bloque héroe de la dirección A "Parche Vivo": el protagonista de una pantalla. Relleno de color pleno
 * (violeta por defecto) con radio 24 y, de arriba abajo:
 *
 * 1. [overline] opcional (Inter 12/600 en mayúsculas) y [badge] opcional arriba a la derecha (p. ej.
 *    `ParcheUPlusBadge`).
 * 2. [title] en Sora 800: con [HeroTitleSize.Number] es un número gigante de 56 y [titleSuffix] va
 *    pegado en Sora 600 de 15 ("2 libres de 5"); con [HeroTitleSize.Headline] es un titular de 28.
 *    Es un solo nodo de texto con rol de encabezado para TalkBack.
 * 3. [content] opcional: lo que ilustra el número (p. ej. `QuotaMascots`).
 * 4. [subtitle] opcional (Inter 13, color de contenido pleno: nunca con alpha para no perder AA).
 * 5. [action] opcional: un botón (el contenido hereda `LocalContentColor` = [HeroBlockColors.content]).
 *
 * Con [onClick] todo el bloque es un botón. No lleva sombra: la profundidad sale del color.
 */
@Composable
fun HeroBlock(
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    titleSuffix: String? = null,
    subtitle: String? = null,
    titleSize: HeroTitleSize = HeroTitleSize.Number,
    colors: HeroBlockColors = HeroBlockDefaults.colors(),
    badge: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    val shape = ParcheUTheme.shapes.hero
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.container, shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(HeroBlockDefaults.ContentPadding)
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.content) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (overline != null || badge != null) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = overline?.uppercase().orEmpty(),
                            style = ParcheUTheme.textStyles.overline.copy(letterSpacing = 0.9.sp),
                            color = colors.content,
                            modifier = Modifier.weight(1f).padding(top = 2.dp)
                        )
                        badge?.invoke()
                    }
                }
                HeroTitle(title, titleSuffix, titleSize, colors.content)
                content?.invoke(this)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.content
                    )
                }
                if (action != null) {
                    Box(Modifier.fillMaxWidth().padding(top = 4.dp)) { action() }
                }
            }
        }
    }
}

@Composable
private fun HeroTitle(title: String, suffix: String?, size: HeroTitleSize, color: Color) {
    val style: TextStyle =
        when (size) {
            HeroTitleSize.Number -> ParcheUTheme.textStyles.hero56
            HeroTitleSize.NumberCompact -> ParcheUTheme.textStyles.hero46
            HeroTitleSize.Headline -> MaterialTheme.typography.headlineLarge
        }
    val text =
        buildAnnotatedString {
            append(title)
            if (suffix != null) {
                withStyle(
                    SpanStyle(
                        fontFamily = SoraFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        letterSpacing = 0.sp
                    )
                ) {
                    append(" ")
                    append(suffix)
                }
            }
        }
    Text(text = text, style = style, color = color, modifier = Modifier.semantics { heading() })
}
