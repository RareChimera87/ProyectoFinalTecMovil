package me.santiagoreina.parcheu.api.social.requests

import me.santiagoreina.parcheu.api.graph.GraphModule
import me.santiagoreina.parcheu.api.quota.QuotaModule
import me.santiagoreina.parcheu.api.simulation.SimulationModule
import org.jetbrains.exposed.v1.jdbc.Database

/** Lo que el dominio de solicitudes de conexión expone al contenedor (E6.6). */
class ConnectRequestModule(
    val repository: ConnectRequestRepository,
    val service: ConnectRequestService,
)

fun connectRequestModule(
    repository: ConnectRequestRepository,
    graph: GraphModule,
    quota: QuotaModule,
    demoUsersEnabled: Boolean = true,
): ConnectRequestModule = ConnectRequestModule(
    repository,
    ConnectRequestService(repository, graph.repository, graph.policy, { quota.service.isPlus(it) }, demoUsersEnabled),
)

/** Cableado con Postgres. */
fun exposedConnectRequestModule(db: Database, graph: GraphModule, quota: QuotaModule, demoUsersEnabled: Boolean = true) =
    connectRequestModule(ExposedConnectRequestRepository(db), graph, quota, demoUsersEnabled)

/** En memoria: lee las decisiones y simulaciones de [simulation] (pruebas y valor por defecto del contenedor). */
fun inMemoryConnectRequestModule(
    simulation: SimulationModule,
    graph: GraphModule,
    quota: QuotaModule,
    demoUsersEnabled: Boolean = true,
) = connectRequestModule(
    SimulationBackedConnectRequestRepository(simulation.repository, simulation.reportRepository),
    graph, quota, demoUsersEnabled,
)
