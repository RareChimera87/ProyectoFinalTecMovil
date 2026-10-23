package me.santiagoreina.parcheu.api.social.introductions

import me.santiagoreina.parcheu.api.chat.ChatModule
import me.santiagoreina.parcheu.api.graph.GraphModule
import me.santiagoreina.parcheu.api.platform.notifier.Notifier
import me.santiagoreina.parcheu.api.quota.QuotaModule
import me.santiagoreina.parcheu.api.simulation.SimulationModule
import me.santiagoreina.parcheu.api.simulation.graphPersonPresenter
import java.time.Clock

/** Lo que el dominio de presentaciones expone al contenedor (E6.2). */
class IntroductionModule(
    val repository: IntroductionRepository,
    val people: IntroductionPeople,
    val service: IntroductionService,
)

/**
 * Arma el módulo sobre [repository] (Postgres en `AppContainer.fromConfig`, en memoria por defecto). El
 * mismo repositorio va en el `IntroductionSimulationNotifier` de [simulation] para que el fin de la
 * simulación avise solo a quien presenta.
 */
fun introductionModule(
    repository: IntroductionRepository,
    simulation: SimulationModule,
    graph: GraphModule,
    chat: ChatModule,
    quota: QuotaModule,
    notifier: Notifier,
    clock: Clock,
): IntroductionModule {
    val people = IntroductionPeople(graph.repository, graph.policy, graph.anonIds)
    val service = IntroductionService(
        repo = repository,
        simulations = simulation.service,
        simulationRepo = simulation.repository,
        reports = simulation.report,
        graph = graph.repository,
        people = people,
        presenter = graphPersonPresenter(graph),
        quota = quota.service,
        edges = graph.edges,
        chat = chat.service,
        notifier = notifier,
        clock = clock,
    )
    return IntroductionModule(repository, people, service)
}
