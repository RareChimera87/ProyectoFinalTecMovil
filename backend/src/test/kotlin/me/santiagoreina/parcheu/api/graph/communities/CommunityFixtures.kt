package me.santiagoreina.parcheu.api.graph.communities

import me.santiagoreina.parcheu.api.graph.InMemoryGraphRepository
import me.santiagoreina.parcheu.api.graph.PersonRow
import me.santiagoreina.parcheu.api.graph.person
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Visibility

/** Une todos los pares de [people] con una arista CONNECTED de [mode] y [affinity]. */
fun InMemoryGraphRepository.clique(people: List<PersonRow>, affinity: Int = 80, mode: Mode = Mode.AMISTAD) {
    for (i in people.indices) for (j in i + 1 until people.size) {
        putEdge(people[i].id, people[j].id, mode, EdgeState.CONNECTED, affinity)
    }
}

/** Personas `from until from+count` con visibilidad [visibility], ya agregadas al grafo. */
fun InMemoryGraphRepository.group(from: Int, count: Int, visibility: Visibility = Visibility.NAME): List<PersonRow> =
    List(count) { person(from + it, name = "Persona ${from + it} Apellido${from + it}", visibility = visibility) }
        .onEach(::addPerson)
