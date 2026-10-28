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

    /** Un texto recortado es el que desborda su caja (ancho o alto) sin haber pedido elipsis. */
    fun assertNoClippedText(rule: ComposeContentTestRule, screen: String) {
        val offenders =
            nodes(rule).filter { node ->
                val getter =
                    node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
                        ?: return@filter false
                val results = mutableListOf<TextLayoutResult>()
                if (!getter(results)) return@filter false
                results.any { it.isClipped() }
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
     * el borde derecho real de las líneas. El alto sí usa `didOverflowHeight` (texto que no cabe en su caja).
     */
    private fun TextLayoutResult.isClipped(): Boolean {
        val widest = (0 until lineCount).maxOfOrNull { getLineRight(it) } ?: 0f
        val widthClipped = widest > size.width + 1f
        val heightClipped = didOverflowHeight && !hasEllipsis()
        return widthClipped || heightClipped
    }

    /** Todo nodo con acción de clic debe ofrecer un área táctil de al menos 48x48dp. */
    fun assertTouchTargets(rule: ComposeContentTestRule, screen: String) {
        val density = Density(rule.density.density, 1f)
        val minPx = with(density) { MIN_TOUCH_DP.dp.toPx() }
        val offenders =
            nodes(rule).filter { node ->
                node.config.getOrNull(SemanticsActions.OnClick) != null &&
                    node.config.getOrNull(SemanticsProperties.Disabled) == null &&
                    node.layoutInfo.isPlaced
            }.filter { node ->
                val b = node.touchBoundsInRoot
                b.width < minPx - 1f || b.height < minPx - 1f
            }
        assertTrue(
            "$screen: áreas táctiles < 48dp:\n" + offenders.joinToString("\n") { describe(it) },
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
