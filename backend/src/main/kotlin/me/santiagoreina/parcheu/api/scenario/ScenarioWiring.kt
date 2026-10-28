package me.santiagoreina.parcheu.api.scenario

import me.santiagoreina.parcheu.api.ai.AiGateway
import me.santiagoreina.parcheu.api.chat.ChatModule
import me.santiagoreina.parcheu.api.graph.GraphModule
import me.santiagoreina.parcheu.api.platform.notifier.Notifier
import me.santiagoreina.parcheu.api.platform.tasks.FakeTaskQueue
import me.santiagoreina.parcheu.api.platform.tasks.TaskQueue
import me.santiagoreina.parcheu.api.quota.QuotaModule
import me.santiagoreina.parcheu.api.simulation.SimulationModule
import me.santiagoreina.parcheu.api.twin.TaskPayload
import me.santiagoreina.parcheu.contract.ContractJson
import java.time.Clock

/** Lo que el modo escenario expone al resto del backend (el contenedor registra su tarea y su job con [service]). */
class ScenarioModule(val service: ScenarioService)

/**
 * Cableado de producción sobre el chat, la simulación (repositorio y juez), el grafo y la cola reales. Conecta
 * además el 409 de `POST /v1/chats/{id}/messages` tras `ends_at` con el cierre del escenario.
 */
fun scenarioModule(
    clock: Clock,
    ai: AiGateway,
    quota: QuotaModule,
    graph: GraphModule,
    chat: ChatModule,
    simulation: SimulationModule,
    tasks: TaskQueue,
    notifier: Notifier,
): ScenarioModule {
    val service = ScenarioService(
        chat.repository, chat.service, simulation.repository, ai, quota.service, graph.edges, tasks, notifier, clock,
    )
    chat.service.scenarioEnded = service.endedHook
    return ScenarioModule(service)
}

/**
 * Todo en memoria (pruebas y valor por defecto del contenedor). Con [runInline] la tarea `judge-scenario` se
 * ejecuta al encolarla, como haría Cloud Tasks.
 */
fun inMemoryScenarioModule(
    clock: Clock,
    ai: AiGateway,
    quota: QuotaModule,
    graph: GraphModule,
    chat: ChatModule,
    simulation: SimulationModule,
    notifier: Notifier,
    queue: FakeTaskQueue = FakeTaskQueue(),
    runInline: Boolean = true,
): ScenarioModule {
    val module = scenarioModule(clock, ai, quota, graph, chat, simulation, queue, notifier)
    if (runInline) {
        queue.inline = { task ->
            if (task.path == ScenarioService.JUDGE_PATH) module.service.judge(ContractJson.decodeFromString<TaskPayload>(task.body).uuid())
        }
    }
    return module
}
