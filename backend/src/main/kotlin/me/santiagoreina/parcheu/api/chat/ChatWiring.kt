package me.santiagoreina.parcheu.api.chat

import me.santiagoreina.parcheu.api.graph.AnonIdSigner
import me.santiagoreina.parcheu.api.graph.GraphVisibilityPolicy
import me.santiagoreina.parcheu.api.platform.notifier.NoopNotifier
import me.santiagoreina.parcheu.api.platform.notifier.Notifier
import java.time.Clock

/** Piezas del dominio chat que comparten las rutas y otras features (E4.6, E4.8, E6.2 y E8.1 usan [service]). */
class ChatModule(
    val repository: ChatRepository,
    val mirror: ChatMirror,
    notifier: Notifier,
    policy: GraphVisibilityPolicy,
    clock: Clock,
) {
    val service = ChatService(repository, mirror, notifier, policy, clock)
}

/** Chat en memoria (repositorio y espejo): pruebas de rutas y `ENV=local` sin base de datos. */
fun inMemoryChatModule(
    clock: Clock,
    policy: GraphVisibilityPolicy = GraphVisibilityPolicy(AnonIdSigner.forTests()),
    notifier: Notifier = NoopNotifier,
    repository: ChatRepository = InMemoryChatRepository(),
    mirror: ChatMirror = InMemoryChatMirror(),
): ChatModule = ChatModule(repository, mirror, notifier, policy, clock)
