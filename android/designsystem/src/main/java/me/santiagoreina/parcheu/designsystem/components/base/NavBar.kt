package me.santiagoreina.parcheu.designsystem.components.base

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.designsystem.theme.ParcheURadius
import me.santiagoreina.parcheu.designsystem.theme.ParcheUSizes
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/**
 * Ítem de la barra de navegación. [activeIcon] es la variante rellena (FILL 1) del ícono activo.
 * [unread] > 0 muestra el badge de no leídos; con 0 no se dibuja nada.
 */
data class NavBarItem(
    val key: String,
    val label: String,
    val icon: Painter,
    val activeIcon: Painter = icon,
    val unread: Int = 0,
    /** Texto de TalkBack para el estado del badge, p. ej. "3 sin leer"; si es nulo se usa el número. */
    val unreadDescription: String? = null,
)

/** Etiqueta de prueba del badge de un ítem de la NavBar. */
fun navBarBadgeTag(key: String) = "navbar_badge_$key"

/**
 * Barra de navegación inferior: 76dp más el inset de navegación, fondo s1 con borde superior de 1dp en ln.
 * El activo lleva píldora de 56 x 30 (radio 15) en prc con ícono relleno en onprc y etiqueta 11/600; los
 * inactivos van en mu con etiqueta 11/500.
 */
@Composable
fun ParcheUNavBar(
    items: List<NavBarItem>,
    activeKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ParcheUTheme.colors
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .background(colors.s1)
            .drawBehind {
                drawLine(
                    colors.ln,
                    Offset(0f, 0f),
                    Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(ParcheUSizes.NavBar).selectableGroup(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                NavBarEntry(
                    item = item,
                    selected = item.key == activeKey,
                    onClick = { onSelect(item.key) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NavBarEntry(item: NavBarItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = ParcheUTheme.colors
    val content = if (selected) colors.onprc else colors.mu
    val badgeText = item.unreadDescription ?: item.unread.toString()
    Column(
        modifier =
        modifier
            .touchTarget()
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .semantics(mergeDescendants = true) {
                if (item.unread > 0) stateDescription = badgeText
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier =
                Modifier
                    .size(56.dp, 30.dp)
                    .clip(RoundedCornerShape(ParcheURadius.NavPill))
                    .background(
                        if (selected) colors.prc else androidx.compose.ui.graphics.Color.Transparent,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = if (selected) item.activeIcon else item.icon,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(24.dp),
                )
            }
            if (item.unread > 0) {
                UnreadBadge(
                    count = item.unread,
                    modifier = Modifier.align(
                        Alignment.TopEnd,
                    ).offset(x = (-2).dp, y = (-4).dp).testTag(navBarBadgeTag(item.key)),
                )
            }
        }
        Text(
            text = item.label,
            style =
            MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = content,
            maxLines = 1,
            modifier = Modifier.sizeIn(minHeight = 14.dp),
        )
    }
}

@Composable
private fun UnreadBadge(count: Int, modifier: Modifier = Modifier) {
    val colors = ParcheUTheme.colors
    Box(
        modifier =
        modifier
            .sizeIn(minWidth = 16.dp, minHeight = 16.dp)
            .clip(ParcheUTheme.shapes.pill)
            .background(colors.error),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = colors.onError,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
