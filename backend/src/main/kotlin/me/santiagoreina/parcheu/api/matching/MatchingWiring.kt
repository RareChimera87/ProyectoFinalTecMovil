package me.santiagoreina.parcheu.api.matching

import me.santiagoreina.parcheu.api.graph.GraphModule
import me.santiagoreina.parcheu.api.platform.config.Env
import me.santiagoreina.parcheu.api.platform.notifier.Notifier
import me.santiagoreina.parcheu.api.quota.QuotaModule
import me.santiagoreina.parcheu.api.simulation.SimulationModule
import org.jetbrains.exposed.v1.jdbc.Database
import java.time.Clock

/** Lo que el dominio de matching expone al resto del backend (E4.8 y E4.9 usan [service] y [repository]). */
class MatchingModule(
    val service: MatchingService,
    val repository: MatchingRepository,
    /** Banderas de la fila; Inicio (E4.7) lee `demoUsersEnabled` para no sugerir demos con la bandera apagada. */
    val flags: MatchingFlags,
)

/** Cableado con Postgres y Firestore (si hay proyecto de Firebase); sin proyecto, solo en `ENV=local`. */
fun matchingModule(
    env: Env,
    db: Database,
    clock: Clock,
    quota: QuotaModule,
    simulation: SimulationModule,
    graph: GraphModule,
    notifier: Notifier,
    firebaseProjectId: String?,
    flags: MatchingFlags,
): MatchingModule {
    val mirror = if (env == Env.LOCAL || firebaseProjectId == null) NoopMatchTicketMirror else FirestoreMatchTicketMirror.forProject(firebaseProjectId)
    return assemble(ExposedMatchingRepository(db), quota, simulation, graph, notifier, mirror, flags, clock)
}

/** Todo en memoria (pruebas y valor por defecto del contenedor). */
fun inMemoryMatchingModule(
    clock: Clock,
    quota: QuotaModule,
    simulation: SimulationModule,
    graph: GraphModule,
    notifier: Notifier,
    repo: MatchingRepository = InMemoryMatchingRepository(),
    mirror: MatchTicketMirror = NoopMatchTicketMirror,
    flags: MatchingFlags = MatchingFlags(demoUsersEnabled = true, scenarioModeEnabled = false),
): MatchingModule = assemble(repo, quota, simulation, graph, notifier, mirror, flags, clock)

private fun assemble(
    repo: MatchingRepository,
    quota: QuotaModule,
    simulation: SimulationModule,
    graph: GraphModule,
    notifier: Notifier,
    mirror: MatchTicketMirror,
    flags: MatchingFlags,
    clock: Clock,
) = MatchingModule(
    service = MatchingService(repo, quota.service, simulation.service, graph.anonIds, notifier, mirror, flags, clock),
    repository = repo,
    flags = flags,
)
