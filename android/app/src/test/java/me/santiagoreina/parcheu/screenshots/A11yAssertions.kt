package me.santiagoreina.parcheu.screenshots

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue

/**
 * Verificaciones de accesibilidad por semántica (E10.4): sin texto recortado, áreas táctiles de al menos
 * 48dp y etiqueta para TalkBack en todo lo accionable. Se aplican sobre el árbol sin fusionar para ver
 * cada `Text` por separado.
 */
object A11yAssertions {
    private const val MIN_TOUCH_DP = 48f
    private const val MIN_VISIBLE_DP = 16f

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.fetchRoot() = fetchSemanticsNode()

    private fun nodes(rule: ComposeContentTestRule): List<SemanticsNode> {
        val out = mutableListOf<SemanticsNode>()
        fun walk(n: SemanticsNode) {
            out += n
            n.children.forEach(::walk)
        }
        walk(rule.onRoot(useUnmergedTree = true).fetchRoot())
        return out
    }

    private fun describe(n: SemanticsNode): String {
        val text = n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
        return "id=${n.id} texto=${text ?: "-"} bounds=${n.boundsInRoot}"
    }

    private fun textOf(n: SemanticsNode): String? =
        n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }

    private fun layoutResults(n: SemanticsNode): List<TextLayoutResult> {
        val getter = n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
            ?: return emptyList()
        val results = mutableListOf<TextLayoutResult>()
        return if (getter(results)) results else emptyList()
    }

    /** Textos que el layout trunca con elipsis (sirve para fijar la línea base con escala 1,0). */
    fun ellipsizedTexts(rule: ComposeContentTestRule): Set<String> =
        nodes(rule).filter { n -> layoutResults(n).any { it.hasEllipsis() } }
            .mapNotNull { textOf(it) }
            .toSet()

    private fun isScrollable(n: SemanticsNode): Boolean =
        n.config.contains(SemanticsProperties.VerticalScrollAxisRange) ||
            n.config.contains(SemanticsProperties.HorizontalScrollAxisRange)

    /** Un nodo está bajo un contenedor desplazable si algún ancestro tiene eje de scroll. */
    private fun insideScrollable(node: SemanticsNode): Boolean {
        var p = node.parent
        while (p != null) {
            if (isScrollable(p)) return true
            p = p.parent
        }
        return false
    }

    /**
     * Recorte por contenedor. Fuera de un scroll, el área visible (`boundsInRoot`, recortada por los
     * ancestros) es menor que el tamaño medido del nodo. Dentro de un scroll, ver solo una parte es lo
     * normal, así que se comparan las cajas sin recortar: se recorren los ancestros hasta el contenedor
     * desplazable más cercano (sin incluirlo) y se marca el nodo si su caja (`positionInRoot` + `size`)
     * se sale de la de un ancestro intermedio (clip o alto fijo del padre).
     */
    private fun isClippedByAncestor(node: SemanticsNode): Boolean {
        if (!node.layoutInfo.isPlaced) return false
        if (!insideScrollable(node)) {
            val visible = node.boundsInRoot
            if (visible.width < node.size.width - 1f || visible.height < node.size.height - 1f) {
                return true
            }
        }
        val pos = node.positionInRoot
        val right = pos.x + node.size.width
        val bottom = pos.y + node.size.height
        var p = node.parent
        while (p != null && !isScrollable(p)) {
            val pp = p.positionInRoot
            if (pos.x < pp.x - 1f ||
                pos.y < pp.y - 1f ||
                right > pp.x + p.size.width + 1f ||
                bottom > pp.y + p.size.height + 1f
            ) {
                return true
            }
            p = p.parent
        }
        return false
    }

    /**
     * Texto recortado: (a) desborda su propia caja (ancho o alto), (b) se trunca con elipsis que no
     * existía con escala 1,0 (`allowedEllipsis` son los textos ya truncados en la línea base), o (c) lo
     * recorta un contenedor (clip o alto fijo del padre) en un nodo de texto o accionable.
     */
    fun assertNoClippedText(
        rule: ComposeContentTestRule,
        screen: String,
        allowedEllipsis: Set<String> = emptySet()
    ) {
        val offenders =
            nodes(rule).filter { node ->
                val results = layoutResults(node)
                val own = results.any { it.isClipped() }
                val newEllipsis =
                    results.any { it.hasEllipsis() } && textOf(node) !in allowedEllipsis
                val relevant =
                    results.isNotEmpty() || node.config.getOrNull(SemanticsActions.OnClick) != null
                own || newEllipsis || (relevant && isClippedByAncestor(node))
            }
        assertTrue(
            "$screen: texto recortado con fontScale 1,3:\n" +
                offenders.joinToString("\n") { describe(it) },
            offenders.isEmpty()
        )
    }

    private fun TextLayoutResult.hasEllipsis(): Boolean =
        (0 until lineCount).any { isLineEllipsized(it) }

    /**
     * `didOverflowWidth` marca como desborde el ancho de párrafo no usado, así que el ancho se mide con
     * el borde derecho real de las líneas. El alto usa `didOverflowHeight` (texto que no cabe en su caja).
     */
    private fun TextLayoutResult.isClipped(): Boolean {
        val widest = (0 until lineCount).maxOfOrNull { getLineRight(it) } ?: 0f
        // Con elipsis, `didOverflowHeight` solo refleja `maxLines`; la elipsis se juzga aparte.
        return widest > size.width + 1f || (didOverflowHeight && !hasEllipsis())
    }

    /**
     * Todo nodo con acción de clic debe ofrecer un área táctil de al menos 48x48dp. La ampliación
     * automática de Compose (`touchBoundsInRoot`) cubre los componentes M3 con
     * `minimumInteractiveComponentSize`, pero no basta: además el nodo debe verse completo y tener un
     * tamaño visible mínimo (16dp: las insignias de diseño miden 22dp y heredan los 48dp de área táctil; un botón aplastado mide ~7dp), de modo que un botón aplastado o recortado falle.
     */
    fun assertTouchTargets(rule: ComposeContentTestRule, screen: String) {
        val density = Density(rule.density.density, 1f)
        val minPx = with(density) { MIN_TOUCH_DP.dp.toPx() }
        val minVisiblePx = with(density) { MIN_VISIBLE_DP.dp.toPx() }
        val offenders =
            nodes(rule).filter { node ->
                node.config.getOrNull(SemanticsActions.OnClick) != null &&
                    node.config.getOrNull(SemanticsProperties.Disabled) == null &&
                    node.layoutInfo.isPlaced
            }.filter { node ->
                val touch = node.touchBoundsInRoot
                if (insideScrollable(node)) {
                    // Parcialmente fuera de pantalla por el scroll: se mide el tamaño real del nodo.
                    // Y el nodo debe tener un tamaño visible mínimo (un botón aplastado falla aunque
                    // la ampliación automática de Compose le dé 48dp de área táctil) y no estar recortado.
                    maxOf(touch.width, node.size.width.toFloat()) < minPx - 1f ||
                        maxOf(touch.height, node.size.height.toFloat()) < minPx - 1f ||
                        node.size.width < minVisiblePx - 1f ||
                        node.size.height < minVisiblePx - 1f ||
                        isClippedByAncestor(node)
                } else {
                    val visible = node.boundsInRoot
                    touch.width < minPx - 1f ||
                        touch.height < minPx - 1f ||
                        visible.width < minVisiblePx - 1f ||
                        visible.height < minVisiblePx - 1f ||
                        isClippedByAncestor(node)
                }
            }
        assertTrue(
            "$screen: áreas táctiles < 48dp o recortadas:\n" +
                offenders.joinToString("\n") { describe(it) },
            offenders.isEmpty()
        )
    }

    /** Todo elemento accionable debe tener texto o `contentDescription` (se evalúa en el árbol fusionado). */
    fun assertActionsLabeled(rule: ComposeContentTestRule, screen: String) {
        val merged = mutableListOf<SemanticsNode>()
        fun walk(n: SemanticsNode) {
            merged += n
            n.children.forEach(::walk)
        }
        walk(rule.onRoot().fetchRoot())
        val offenders =
            merged.filter { node ->
                node.config.getOrNull(SemanticsActions.OnClick) != null &&
                    node.layoutInfo.isPlaced &&
                    node.config.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty() &&
                    node.config.getOrNull(SemanticsProperties.Text).isNullOrEmpty() &&
                    node.config.getOrNull(SemanticsProperties.StateDescription) == null
            }
        assertTrue(
            "$screen: accionables sin etiqueta de TalkBack:\n" +
                offenders.joinToString("\n") { describe(it) },
            offenders.isEmpty()
        )
    }
}
