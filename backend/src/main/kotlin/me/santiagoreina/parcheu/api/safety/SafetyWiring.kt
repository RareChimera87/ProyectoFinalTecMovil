package me.santiagoreina.parcheu.api.safety

import me.santiagoreina.parcheu.api.chat.ChatModule
import me.santiagoreina.parcheu.api.chat.InMemoryChatRepository
import me.santiagoreina.parcheu.api.graph.GraphModule
import me.santiagoreina.parcheu.api.graph.InMemoryGraphRepository
import me.santiagoreina.parcheu.api.matching.InMemoryMatchingRepository
import me.santiagoreina.parcheu.api.matching.MatchingModule
import org.jetbrains.exposed.v1.jdbc.Database
import java.time.Clock

/** Piezas del dominio safety (E8.5 y E8.7 reutilizan [service] y [repository]). */
class SafetyModule(
    val repository: SafetyRepository,
    val service: SafetyService,
)

private fun assemble(repo: SafetyRepository, chat: ChatModule, graph: GraphModule, matching: MatchingModule?, clock: Clock) = SafetyModule(
    repository = repo,
    service = SafetyService(repo, chat.service, chat.repository, graph.repository, graph.policy, graph.anonIds, matching?.repository, clock),
)

/** Cableado con Postgres: todos los dominios leen la misma tabla `blocks`. */
fun safetyModule(db: Database, chat: ChatModule, graph: GraphModule, matching: MatchingModule, clock: Clock): SafetyModule =
    assemble(ExposedSafetyRepository(db), chat, graph, matching, clock)

/**
 * Todo en memoria (pruebas y valor por defecto del contenedor). Como cada repositorio en memoria
 * guarda su propia copia de los bloqueos, aquí se propagan a los del chat, el grafo y la fila de
 * match; las personas y los mensajes se leen de esos mismos repositorios.
 */
fun inMemorySafetyModule(
    clock: Clock,
    chat: ChatModule,
    graph: GraphModule,
    matching: MatchingModule? = null,
    repo: InMemorySafetyRepository? = null,
): SafetyModule {
    val chatRepo = chat.repository as? InMemoryChatRepository
    val graphRepo = graph.repository as? InMemoryGraphRepository
    val matchRepo = matching?.repository as? InMemoryMatchingRepository
    val safetyRepo = repo ?: InMemorySafetyRepository(
        people = { id -> graph.repository.person(id) ?: chat.repository.person(id) },
        messages = { chatId ->
            chatRepo?.allMessages.orEmpty().filter { it.chatId == chatId }.map {
                SnapshotMessage(it.id, it.senderId, it.text, it.createdAt, it.hidden)
            }
        },
    )
    if (safetyRepo is InMemorySafetyRepository) {
        safetyRepo.onBlock { blocker, blocked ->
            chatRepo?.block(blocker, blocked)
            graphRepo?.block(blocker, blocked)
            matchRepo?.block(blocker, blocked)
        }
        safetyRepo.onUnblock { blocker, blocked ->
            chatRepo?.unblock(blocker, blocked)
            graphRepo?.unblock(blocker, blocked)
            matchRepo?.unblock(blocker, blocked)
        }
    }
    return assemble(safetyRepo, chat, graph, matching, clock)
}
