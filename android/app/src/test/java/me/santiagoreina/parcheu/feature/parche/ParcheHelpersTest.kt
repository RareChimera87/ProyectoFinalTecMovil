package me.santiagoreina.parcheu.feature.parche

import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.designsystem.graph.GraphMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ParcheHelpersTest {
    private val base = Instant.parse("2026-11-01T10:00:00Z")

    @Test
    fun relativeDaysUsesTodayYesterdayAndCount() {
        assertEquals("hoy", relativeDays(base, Instant.parse("2026-11-01T22:00:00Z")))
        assertEquals("ayer", relativeDays(base, Instant.parse("2026-11-02T11:00:00Z")))
        assertEquals("hace 3 días", relativeDays(base, Instant.parse("2026-11-04T11:00:00Z")))
        assertEquals("hoy", relativeDays(base, Instant.parse("2026-10-30T00:00:00Z")))
    }

    @Test
    fun nodesMapToGraphNodesKeepingModeAndState() {
        val node = ParcheSamples.nodes.first { it.id == "dan" }.toGraphNode()
        assertEquals(GraphMode.ESTUDIO, node.mode)
        assertEquals("simularon", node.state.label)
        assertEquals(
            "Daniela, 54 % afín, Estudio, simularon",
            me.santiagoreina.parcheu.designsystem.graph.graphNodeDescription(node)
        )
        assertEquals("Amistad", Mode.AMISTAD.label())
    }
}
