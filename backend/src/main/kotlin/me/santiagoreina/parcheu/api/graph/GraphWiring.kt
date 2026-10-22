package me.santiagoreina.parcheu.api.graph

import java.time.Clock

/** Piezas del dominio graph que comparten rutas y otras features (E4.3, E4.5, E6.x usan [edges]). */
class GraphModule(
    val repository: GraphRepository,
    val anonIds: AnonIdSigner,
    clock: Clock,
) {
    val policy = GraphVisibilityPolicy(anonIds)
    val service = GraphService(repository, policy)
    val edges = EdgeService(repository, clock)
}

/** Grafo en memoria con la clave local: pruebas de rutas y `ENV=local` sin base de datos. */
fun inMemoryGraphModule(clock: Clock, repository: GraphRepository = InMemoryGraphRepository()): GraphModule =
    GraphModule(repository, AnonIdSigner.forTests(), clock)
