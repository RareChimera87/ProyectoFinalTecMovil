package me.santiagoreina.parcheu.feature.parche

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUPlusBadge
import me.santiagoreina.parcheu.designsystem.components.compound.HeroBlock
import me.santiagoreina.parcheu.designsystem.components.compound.HeroTitleSize
import me.santiagoreina.parcheu.designsystem.mascot.TwinMascotGlyph
import me.santiagoreina.parcheu.designsystem.theme.ModeColors
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/*
 * Piezas de la dirección A "Parche Vivo" que comparten D1-D9 (Tu parche y lo social). Viven en la
 * feature para no tocar los componentes compartidos de :designsystem.
 */

/**
 * Lomo violeta de las listas que no son de un modo (amigos de amigos, comunidades): personas y grupos
 * "por conocer", del color de la marca. Con él, el modo no se calla: simplemente no hay uno.
 */
@Composable
fun parcheSpine(): ModeColors {
    val c = ParcheUTheme.colors
    return ModeColors(
        solid = c.hero,
        text = c.prt,
        onSolid = c.onHero,
        container = c.prc,
        onContainer = c.onprc
    )
}

/** Número de estadística en Sora 800 con su rótulo debajo; TalkBack lo lee como "9 conexiones". */
@Composable
fun ParcheStatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = ParcheUTheme.colors.tx
) {
    val colors = ParcheUTheme.colors
    Column(
        modifier = modifier
            .clip(ParcheUTheme.shapes.card)
            .background(colors.s1)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.displaySmall,
            color = valueColor,
            maxLines = 1
        )
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = colors.mu)
    }
}

/**
 * Ilustración de ParcheU+ en el héroe: tu P (blanca) y, a dos pasos por una línea punteada, las P de
 * quienes aún no conoces (apagadas y mirándote). Decorativa.
 */
@Composable
fun PlusPeopleIllustration(
    modifier: Modifier = Modifier,
    others: Int = 3,
    mascot: Dp = 34.dp,
    linked: Boolean = true
) {
    val colors = ParcheUTheme.colors
    Row(
        modifier = modifier.clearAndSetSemantics { },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TwinMascotGlyph(width = mascot, color = colors.onHero, eyeColor = colors.hero)
        if (linked) PlusLink()
        repeat(others) {
            TwinMascotGlyph(
                width = mascot * 0.8f,
                color = colors.heroQuiet,
                eyeColor = colors.hero,
                mirrored = true
            )
        }
    }
}

/** Línea punteada "a dos pasos" entre tu P y las demás. */
@Composable
private fun PlusLink() {
    val colors = ParcheUTheme.colors
    Box(Modifier.height(30.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.width(44.dp).height(4.dp)) {
            drawLine(
                color = colors.onHero,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 6.dp.toPx()))
            )
        }
    }
}

/**
 * Botón sobre un héroe violeta: píldora blanca con texto violeta (5,15:1) y 48 dp de alto. Se usa para
 * "Ver ParcheU+" dentro de [ParchePlusHero].
 */
@Composable
fun HeroActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null
) {
    val colors = ParcheUTheme.colors
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(ParcheUTheme.shapes.pill)
            .background(colors.onHero)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = colors.hero,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = colors.hero,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Bloque héroe de una función de ParcheU+ bloqueada en gratis (D7, D8, D9): overline "ParcheU+", la
 * insignia, el titular de la pantalla en Sora 800, la ilustración de las P, lo que desbloquea y el
 * botón "Ver ParcheU+". Sin ningún dato real (el servidor solo manda conteos).
 */
@Composable
fun ParchePlusHero(
    title: String,
    body: String,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
    overline: String = "Con ParcheU+",
    actionLabel: String = "Ver ParcheU+",
    illustration: @Composable () -> Unit = { PlusPeopleIllustration() }
) {
    HeroBlock(
        title = title,
        overline = overline,
        titleSize = HeroTitleSize.Headline,
        subtitle = body,
        badge = { ParcheUPlusBadge(onHero = true) },
        modifier = modifier,
        action = {
            HeroActionButton(
                text = actionLabel,
                onClick = onUnlock,
                icon = parcheIconPainter(ParcheIcon.Lock),
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Box(Modifier.padding(vertical = 10.dp)) { illustration() }
    }
}

/** Fila con "✓" para "Gratis tienes" de los bloqueos. */
@Composable
fun IncludedFeatureRow(text: String, icon: Painter, modifier: Modifier = Modifier) {
    val colors = ParcheUTheme.colors
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = colors.prt,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = colors.tx,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}

/**
 * Entrada a una función de ParcheU+ dentro de D1: tarjeta de acceso tonal (prc) con lomo violeta, el
 * texto y la insignia. Con [locked] lleva candado (gratis); sin él, la flecha de entrar (ParcheU+).
 */
@Composable
fun ParchePlusEntry(
    title: String,
    subtitle: String?,
    locked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String
) {
    val colors = ParcheUTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(tag)
            .clip(ParcheUTheme.shapes.card)
            .background(colors.prc)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(40.dp).clip(ParcheUTheme.shapes.pill).background(colors.hero),
            contentAlignment = Alignment.Center
        ) {
            if (locked) {
                Icon(
                    painter = parcheIconPainter(ParcheIcon.Lock),
                    contentDescription = null,
                    tint = colors.onHero,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                TwinMascotGlyph(width = 24.dp, color = colors.onHero, eyeColor = colors.hero)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ParcheUPlusBadge()
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onprc
            )
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onprc)
            }
        }
        Icon(
            painter = parcheIconPainter(ParcheIcon.ChevronRight),
            contentDescription = null,
            tint = colors.onprc,
            modifier = Modifier.size(20.dp)
        )
    }
}
