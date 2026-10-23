package me.santiagoreina.parcheu.api.simulation

import me.santiagoreina.parcheu.api.ai.AiGateway
import me.santiagoreina.parcheu.api.chat.ChatModule
import me.santiagoreina.parcheu.api.chat.inMemoryChatModule
import me.santiagoreina.parcheu.api.demo.DemoConnectPolicy
import me.santiagoreina.parcheu.api.platform.notifier.NoopNotifier
import me.santiagoreina.parcheu.api.platform.notifier.Notifier
import me.santiagoreina.parcheu.api.simulation.report.ExposedReportRepository
import me.santiagoreina.parcheu.api.simulation.report.InMemoryReportRepository
import me.santiagoreina.parcheu.api.simulation.report.ReportRepository
import me.santiagoreina.parcheu.api.simulation.report.ReportService
import me.santiagoreina.parcheu.api.graph.GraphModule
import me.santiagoreina.parcheu.api.graph.ViewContext
import me.santiagoreina.parcheu.api.graph.GraphVisibilityPolicy
import me.santiagoreina.parcheu.api.platform.config.Env
import me.santiagoreina.parcheu.api.platform.tasks.FakeTaskQueue
import me.santiagoreina.parcheu.api.platform.tasks.TaskQueue
import me.santiagoreina.parcheu.api.quota.QuotaModule
import me.santiagoreina.parcheu.api.twin.TaskPayload
import me.santiagoreina.parcheu.contract.ContractJson
import me.santiagoreina.parcheu.contract.PersonRef
import org.jetbrains.exposed.v1.jdbc.Database
import java.time.Clock

/** Lo que el dominio de simulación expone al resto del backend (E4.2, E4.6, E6.4 usan [service]). */
class SimulationModule(
    val service: SimulationService,
    val engine: SimulationEngine,
    val repository: SimulationRepository,
    /** Informe, desbloqueo y decisión mutua (E4.6). */
    val report: ReportService,
)

/**
 * Cómo ve el usuario a la otra persona: la decide la policy del grafo (ADR-0012). Sin fila en
 * `users` (o borrada) sale como "Alguien".
 */
fun graphPersonPresenter(graph: GraphModule): PersonPresenter = PersonPresenter { viewer, subject ->
    val person = graph.repository.person(subject)?.takeIf { it.deletedAt == null }
    if (person == null) {
        PersonRef(label = GraphVisibilityPolicy.ANON_LABEL, initials = GraphVisibilityPolicy.ANON_INITIALS)
    } else {
        val connected = subject in graph.repository.connectedWith(viewer)
        val view = graph.policy.present(viewer, person, connected, ViewContext.EGO)
        PersonRef(
            id = if (view.anonymous) null else view.id,
            anonId = if (view.anonymous) view.id else null,
            label = view.label,
            initials = view.initials,
            photoUrl = view.photoUrl,
            isDemo = view.isDemo,
        )
    }
}

/**
 * Cableado con Postgres, la cola real y Firestore (si hay proyecto de Firebase). [chat] y [push] los
 * usa la decisión mutua (E4.6): chat DIRECT y aviso de conexión.
 */
fun simulationModule(
    env: Env,
    db: Database,
    clock: Clock,
    ai: AiGateway,
    quota: QuotaModule,
    graph: GraphModule,
    tasks: TaskQueue,
    firebaseProjectId: String?,
    notifier: SimulationNotifier,
    chat: ChatModule,
    push: Notifier,
    demo: DemoConnectPolicy = DemoConnectPolicy.DEFAULT,
): SimulationModule {
    val mirror = if (env == Env.LOCAL || firebaseProjectId == null) NoopSimulationMirror else FirestoreSimulationMirror(firebaseProjectId)
    return assemble(
        ExposedSimulationRepository(db), clock, ai, quota, graph, tasks, mirror, notifier,
        ExposedReportRepository(db), chat, push, demo,
    )
}

/**
 * Todo en memoria (pruebas y valor por defecto del contenedor). Con [runInline] la tarea
 * `simulate` se ejecuta al encolarla, como haría Cloud Tasks.
 */
fun inMemorySimulationModule(
    clock: Clock,
    ai: AiGateway,
    quota: QuotaModule,
    graph: GraphModule,
    repo: SimulationRepository = InMemorySimulationRepository(),
    queue: FakeTaskQueue = FakeTaskQueue(),
    mirror: SimulationMirror = NoopSimulationMirror,
    notifier: SimulationNotifier = NoopSimulationNotifier,
    runInline: Boolean = true,
    reportRepo: ReportRepository = InMemoryReportRepository(),
    push: Notifier = NoopNotifier,
    chat: ChatModule = inMemoryChatModule(clock, graph.policy, push),
    demo: DemoConnectPolicy = DemoConnectPolicy.DEFAULT,
): SimulationModule {
    val module = assemble(repo, clock, ai, quota, graph, queue, mirror, notifier, reportRepo, chat, push, demo)
    if (runInline) queue.inline = { task -> module.engine.run(ContractJson.decodeFromString<TaskPayload>(task.body).uuid()) }
    return module
}

private fun assemble(
    repo: SimulationRepository,
    clock: Clock,
    ai: AiGateway,
    quota: QuotaModule,
    graph: GraphModule,
    tasks: TaskQueue,
    mirror: SimulationMirror,
    notifier: SimulationNotifier,
    reportRepo: ReportRepository,
    chat: ChatModule,
    push: Notifier,
    demo: DemoConnectPolicy,
): SimulationModule {
    val people = graphPersonPresenter(graph)
    val engine = SimulationEngine(repo, ai, quota.service, graph.edges, mirror, notifier, clock)
    val report = ReportService(repo, reportRepo, quota.service, graph.edges, chat.service, push, people, clock, demo)
    val service = SimulationService(repo, quota.service, ai, tasks, people, mirror, clock, report)
    return SimulationModule(service, engine, repo, report)
}
