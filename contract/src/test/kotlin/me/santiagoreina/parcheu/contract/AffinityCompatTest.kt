package me.santiagoreina.parcheu.contract

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Afinidad sin medir (conexión por invitación sin simulación): compatibilidad en los dos sentidos.
 * - App nueva contra backend anterior: el JSON no trae `affinityMeasured` y la afinidad se toma tal cual.
 * - App anterior contra backend nuevo: el JSON trae `affinity: 0` y `affinityMeasured: false`; la app vieja
 *   (copia de su `NodeDto`/`EdgeDto` abajo) ignora la clave nueva y sigue deserializando.
 */
class AffinityCompatTest {

    /** `NodeDto` de las apps 0.1/0.2: `affinity` obligatorio y sin `affinityMeasured`. */
    @Serializable
    private data class LegacyNodeDto(
        val id: String,
        val label: String,
        val initials: String,
        val photoUrl: String? = null,
        val affinity: Int,
        val mode: Mode,
        val state: EdgeState,
        val anonymous: Boolean = false,
        val isDemo: Boolean = false,
    )

    @Serializable
    private data class LegacyEdgeDto(val otherId: String, val mode: Mode, val state: EdgeState, val affinity: Int)

    @Serializable
    private data class LegacyGraphDto(
        val me: LegacyNodeDto,
        val nodes: List<LegacyNodeDto> = emptyList(),
        val edges: List<LegacyEdgeDto> = emptyList(),
        val stats: GraphStats,
    )

    private val sinMedir = NodeDto(
        id = "u1", label = "Camilo", initials = "CA", affinity = 0, mode = Mode.ESTUDIO,
        state = EdgeState.CONNECTED, affinityMeasured = false,
    )

    @Test
    fun `una afinidad sin medir viaja como 0 con affinityMeasured false y se lee como null`() {
        val json = ContractJson.encodeToJsonElement(NodeDto.serializer(), sinMedir).jsonObject
        assertEquals(0, json.getValue("affinity").jsonPrimitive.int)
        assertEquals("false", json.getValue("affinityMeasured").jsonPrimitive.content)
        val back = ContractJson.decodeFromJsonElement(NodeDto.serializer(), json)
        assertEquals(sinMedir, back)
        assertNull(back.measuredAffinity)
    }

    @Test
    fun `app nueva contra backend anterior conserva la afinidad que llega`() {
        val anterior = """{"id":"u1","label":"Laura","initials":"LP","affinity":87,"mode":"AMISTAD","state":"CONNECTED"}"""
        val node = ContractJson.decodeFromString(NodeDto.serializer(), anterior)
        assertEquals(87, node.measuredAffinity)
        // El backend anterior inventaba 0 para las invitaciones: sin la clave nueva no se puede distinguir.
        val cero = ContractJson.decodeFromString(NodeDto.serializer(), anterior.replace("87", "0"))
        assertEquals(0, cero.measuredAffinity)
        val edge = ContractJson.decodeFromString(
            EdgeDto.serializer(),
            """{"otherId":"u1","mode":"AMISTAD","state":"CONNECTED","affinity":64}""",
        )
        assertEquals(64, edge.measuredAffinity)
    }

    @Test
    fun `app anterior contra backend nuevo deserializa el grafo con nodos sin medir`() {
        val nuevo = GraphDto(
            me = sinMedir.copy(id = "me", affinity = 100, affinityMeasured = true),
            nodes = listOf(sinMedir),
            edges = listOf(EdgeDto("u1", Mode.ESTUDIO, EdgeState.CONNECTED, affinity = 0, affinityMeasured = false)),
            stats = GraphStats(connections = 1, simulated = 0, pending = 0, avgAffinity = 0),
        )
        val wire = ContractJson.encodeToString(GraphDto.serializer(), nuevo)
        val vieja = ContractJson.decodeFromString(LegacyGraphDto.serializer(), wire)
        assertEquals(0, vieja.nodes.single().affinity)
        assertEquals(0, vieja.edges.single().affinity)
    }

    @Test
    fun `un grafo guardado con el formato anterior se sigue leyendo`() {
        val guardado = ContractJson.encodeToString(
            LegacyGraphDto.serializer(),
            LegacyGraphDto(
                me = LegacyNodeDto("me", "Tú", "T", affinity = 100, mode = Mode.AMISTAD, state = EdgeState.CONNECTED),
                nodes = listOf(LegacyNodeDto("u1", "Laura", "LP", affinity = 72, mode = Mode.AMISTAD, state = EdgeState.SIMULATED)),
                edges = listOf(LegacyEdgeDto("u1", Mode.AMISTAD, EdgeState.SIMULATED, 72)),
                stats = GraphStats(0, 1, 0, 72),
            ),
        )
        val leido = ContractJson.decodeFromString(GraphDto.serializer(), guardado)
        assertEquals(72, leido.nodes.single().measuredAffinity)
        assertEquals(72, leido.edges.single().measuredAffinity)
    }
}
