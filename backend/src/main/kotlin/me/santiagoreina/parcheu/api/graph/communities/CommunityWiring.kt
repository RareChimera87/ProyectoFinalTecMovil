package me.santiagoreina.parcheu.api.graph.communities

import me.santiagoreina.parcheu.api.ai.AiGateway
import me.santiagoreina.parcheu.api.graph.GraphModule
import me.santiagoreina.parcheu.api.graph.InMemoryGraphRepository
import me.santiagoreina.parcheu.api.platform.tasks.FakeTaskQueue
import me.santiagoreina.parcheu.api.platform.tasks.TaskQueue
import me.santiagoreina.parcheu.api.quota.QuotaModule
import java.time.Clock
import java.util.UUID

/** Lo que el dominio de comunidades expone al contenedor (E6.4). */
class CommunityModule(
    val repository: CommunityRepository,
    val service: CommunityService,
    val view: CommunitiesView,
)

/** Arma el módulo sobre [repository] (Postgres en `AppContainer.fromConfig`). La cola es la misma de las demás tareas. */
fun communityModule(
    repository: CommunityRepository,
    graph: GraphModule,
    ai: AiGateway,
    quota: QuotaModule,
    tasks: TaskQueue,
    clock: Clock,
    demoUsersEnabled: Boolean = true,
): CommunityModule = CommunityModule(
    repository,
    CommunityService(repository, tasks, CommunityNamer(ai), clock),
    CommunitiesView(repository, graph.repository, graph.policy, { quota.service.isPlus(it) }, demoUsersEnabled),
)

/**
 * Comunidades en memoria sobre el grafo en memoria (pruebas de rutas y `ENV=local` sin base): los rasgos
 * salen de [traitsOf] y las tareas se registran en una cola Fake.
 */
fun inMemoryCommunityModule(
    graph: GraphModule,
    ai: AiGateway,
    quota: QuotaModule,
    clock: Clock,
    tasks: TaskQueue = FakeTaskQueue(),
    traitsOf: (UUID) -> List<Double>? = { null },
    demoUsersEnabled: Boolean = true,
): CommunityModule {
    val memory = graph.repository as? InMemoryGraphRepository ?: InMemoryGraphRepository()
    return communityModule(InMemoryCommunityRepository(memory, traitsOf), graph, ai, quota, tasks, clock, demoUsersEnabled)
}
