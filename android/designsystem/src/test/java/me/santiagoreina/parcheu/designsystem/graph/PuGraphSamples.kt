package me.santiagoreina.parcheu.designsystem.graph

import me.santiagoreina.parcheu.designsystem.graph.GraphEdgeState.CONNECTED
import me.santiagoreina.parcheu.designsystem.graph.GraphEdgeState.PENDING
import me.santiagoreina.parcheu.designsystem.graph.GraphEdgeState.SIMULATED
import me.santiagoreina.parcheu.designsystem.graph.GraphMode.AMISTAD
import me.santiagoreina.parcheu.designsystem.graph.GraphMode.ESTUDIO
import me.santiagoreina.parcheu.designsystem.graph.GraphMode.PAREJA

/** Datos de PU Grafo (Claude Design): los 9 nodos y los 5 amigos de amigos del mockup. */
object PuGraphSamples {
    val nodes = listOf(
        GraphNode("val", "Valentina", "V", 92, AMISTAD, CONNECTED),
        GraphNode("and", "Andrés", "A", 88, ESTUDIO, CONNECTED),
        GraphNode("seb", "Sebas", "S", 76, AMISTAD, CONNECTED),
        GraphNode("cam", "Camila", "C", 85, PAREJA, CONNECTED),
        GraphNode("lau", "Laura P.", "L", 87, ESTUDIO, CONNECTED),
        GraphNode("juli", "Juli", "J", 62, AMISTAD, CONNECTED),
        GraphNode("mat", "Mateo", "M", 58, AMISTAD, PENDING),
        GraphNode("dan", "Daniela", "D", 54, ESTUDIO, SIMULATED),
        GraphNode("nico", "Nico", "N", 50, AMISTAD, PENDING, anonymous = true),
    )

    val fof = listOf(
        GraphFof("f1", "val", "", "", 87),
        GraphFof("f2", "val", "", "", 82),
        GraphFof("f3", "and", "Felipe R.", "F", 84, anonymous = false),
        GraphFof("f4", "seb", "", "", 79),
        GraphFof("f5", "lau", "Sara M.", "S", 76, anonymous = false),
    )
}
