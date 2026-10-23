package me.santiagoreina.parcheu.api.social.invites

import me.santiagoreina.parcheu.api.graph.GraphModule
import me.santiagoreina.parcheu.api.platform.notifier.Notifier
import me.santiagoreina.parcheu.api.simulation.SimulationModule
import org.jetbrains.exposed.v1.jdbc.Database
import java.time.Clock

/** Piezas de las invitaciones (E6.1). */
class InviteModule(
    val repository: InviteRepository,
    val service: InviteService,
)

/**
 * Arma el servicio sobre el grafo (aristas, bloqueos, personas y `anonId`) y los modos activos que lee
 * la simulación de `user_modes` (la misma fuente que `SimulationService.create`).
 */
fun inviteModule(
    repo: InviteRepository,
    graph: GraphModule,
    simulation: SimulationModule,
    notifier: Notifier,
    links: InviteLinks,
    clock: Clock,
): InviteModule = InviteModule(
    repository = repo,
    service = InviteService(
        repo = repo,
        graph = graph.repository,
        edges = graph.edges,
        anonIds = graph.anonIds,
        participants = { id ->
            simulation.repository.participant(id)?.let { InviteParticipant(it.id, it.activeModes, it.deleted) }
        },
        notifier = notifier,
        links = links,
        clock = clock,
    ),
)

/** Cableado con Postgres. */
fun exposedInviteModule(db: Database, graph: GraphModule, simulation: SimulationModule, notifier: Notifier, links: InviteLinks, clock: Clock) =
    inviteModule(ExposedInviteRepository(db), graph, simulation, notifier, links, clock)

/** Todo en memoria: pruebas y valor por defecto del contenedor. */
fun inMemoryInviteModule(
    clock: Clock,
    graph: GraphModule,
    simulation: SimulationModule,
    notifier: Notifier,
    repo: InviteRepository = InMemoryInviteRepository(),
    links: InviteLinks = InviteLinks(InviteLinks.LOCAL_BASE),
): InviteModule = inviteModule(repo, graph, simulation, notifier, links, clock)
