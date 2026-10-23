package me.santiagoreina.parcheu.api.graph

import me.santiagoreina.parcheu.api.graph.fof.FofRepository
import java.time.Clock

/**
 * Piezas del dominio graph que comparten rutas y otras features (E4.3, E4.5, E6.x usan [edges]).
 * [fofRepository] es la consulta de amigos de amigos (E6.3) en Postgres; nulo en memoria, donde
 * `allDomains` la arma sobre [repository] y los rasgos del gemelo en memoria.
 */
class GraphModule(
    val repository: GraphRepository,
    val anonIds: AnonIdSigner,
    clock: Clock,
    val fofRepository: FofRepository? = null,
) {
    val policy = GraphVisibilityPolicy(anonIds)
    val service = GraphService(repository, policy)
    val edges = EdgeService(repository, clock)
}

/** Grafo en memoria con la clave local: pruebas de rutas y `ENV=local` sin base de datos. */
fun inMemoryGraphModule(clock: Clock, repository: GraphRepository = InMemoryGraphRepository()): GraphModule =
    GraphModule(repository, AnonIdSigner.forTests(), clock)
