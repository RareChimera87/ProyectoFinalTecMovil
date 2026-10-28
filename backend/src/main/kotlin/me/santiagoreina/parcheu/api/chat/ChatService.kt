package me.santiagoreina.parcheu.api.chat

import me.santiagoreina.parcheu.api.graph.GraphVisibilityPolicy
import me.santiagoreina.parcheu.api.graph.PersonRow
import me.santiagoreina.parcheu.api.graph.ViewContext
import me.santiagoreina.parcheu.api.identity.UserRecord
import me.santiagoreina.parcheu.api.identity.toKotlin
import me.santiagoreina.parcheu.api.platform.errors.Blocked
import me.santiagoreina.parcheu.api.platform.errors.Conflict
import me.santiagoreina.parcheu.api.platform.errors.Forbidden
import me.santiagoreina.parcheu.api.platform.errors.NotFound
import me.santiagoreina.parcheu.api.platform.errors.ScenarioEnded
import me.santiagoreina.parcheu.api.platform.errors.Suspended
import me.santiagoreina.parcheu.api.platform.errors.ValidationFailed
import me.santiagoreina.parcheu.api.platform.notifier.Notification
import me.santiagoreina.parcheu.api.platform.notifier.Notifier
import me.santiagoreina.parcheu.contract.ChatStatus
import me.santiagoreina.parcheu.contract.ChatSummary
import me.santiagoreina.parcheu.contract.ChatType
import me.santiagoreina.parcheu.contract.ConsentKind
import me.santiagoreina.parcheu.contract.MessageDto
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.NotificationKinds
import me.santiagoreina.parcheu.contract.PersonRef
import me.santiagoreina.parcheu.contract.SendMessage
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Chats y mensajes (E5.1, arquitectura.md §8.3). Postgres es la fuente de verdad; cada cambio se
 * replica en Firestore con [mirror] y los mensajes nuevos se avisan con [notifier].
 *
 * Otras features crean los chats con [createDirect] (decisión mutua E4.6, presentaciones E6.2) y
 * [createScenario] (modo escenario E4.8); el bloqueo (E8.1) los cierra con [close].
 */
class ChatService(
    private val repo: ChatRepository,
    private val mirror: ChatMirror,
    private val notifier: Notifier,
    private val policy: GraphVisibilityPolicy,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Se invoca después de guardar cada mensaje nuevo (no en reintentos por `clientId`). La moderación
     * con IA (E8.6) se engancha aquí; su fallo nunca rompe el envío (fail-open, ADR-0026).
     */
    @Volatile var onMessageSaved: (suspend (MessageRecord) -> Unit)? = null

    /**
     * Qué hacer cuando alguien intenta escribir en un chat SCENARIO que ya terminó (E4.8): el módulo de
     * escenario lo conecta para cerrar el chat y disparar al juez, una sola vez. Sin él (pruebas de chat
     * solo) el envío responde 409 sin más.
     */
    @Volatile
    var scenarioEnded: ScenarioEndedHook = ScenarioEndedHook.NONE

    // --- creación (la usan otros dominios) ---------------------------------------------------

    /** Chat DIRECT de [a] y [b] en [mode]; si ya existe lo devuelve (idempotente y seguro ante concurrencia). */
    suspend fun createDirect(a: UUID, b: UUID, mode: Mode): ChatRecord {
        if (a == b) throw ValidationFailed("Un chat necesita dos personas distintas")
        if (repo.blockedBetween(a, b)) throw Blocked()
        val result = repo.getOrCreateDirect(a, b, mode, clock.instant())
        // Se escribe también si ya existía: repara el espejo si una escritura anterior falló.
        mirror.upsertChat(result.chat.toDoc())
        return result.chat
    }

    /** Chat DIRECT existente de [a] y [b] en [mode], solo lectura: no lo crea, no lo reabre y no toca Firestore. */
    suspend fun findDirect(a: UUID, b: UUID, mode: Mode): ChatRecord? = repo.findDirect(a, b, mode)

    /**
     * Como [createDirect], pero un fallo del espejo de Firestore no se propaga: el chat ya existe en Postgres
     * (fuente de verdad) y aparece en `GET /v1/chats`. Lo usa la decisión mutua (E4.6) para no perder los avisos;
     * el primer `send` del chat repara el documento que falte.
     */
    suspend fun createDirectTolerant(a: UUID, b: UUID, mode: Mode): ChatRecord {
        if (a == b) throw ValidationFailed("Un chat necesita dos personas distintas")
        if (repo.blockedBetween(a, b)) throw Blocked()
        val result = repo.getOrCreateDirect(a, b, mode, clock.instant())
        try {
            mirror.upsertChat(result.chat.toDoc())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("No se pudo replicar el chat {} en Firestore: {}", result.chat.id, e.message)
        }
        return result.chat
    }

    /** Chat SCENARIO (F3) del escenario [scenarioId] que termina en [endsAt]. */
    suspend fun createScenario(a: UUID, b: UUID, mode: Mode, scenarioId: Int, endsAt: Instant): ChatRecord {
        if (a == b) throw ValidationFailed("Un chat necesita dos personas distintas")
        if (repo.blockedBetween(a, b)) throw Blocked()
        val now = clock.instant()
        if (!endsAt.isAfter(now)) throw ValidationFailed("endsAt debe ser futuro")
        val chat = repo.create(NewChat(ChatType.SCENARIO, mode, listOf(a, b), now, scenarioId, endsAt))
        mirror.upsertChat(chat.toDoc())
        return chat
    }

    /**
     * Vuelve a escribir `chats/{id}` en Firestore con el estado actual de Postgres. Lo usa el match al crear el
     * chat SCENARIO (E4.2 no escribe el espejo; pendientes-humanos E4.2 paso 4). Idempotente; null si no existe.
     */
    suspend fun mirrorChat(chatId: UUID): ChatRecord? {
        val chat = repo.chat(chatId) ?: return null
        mirror.upsertChat(chat.toDoc())
        return chat
    }

    /** Cierra el chat (bloqueo, fin del escenario). Devuelve null si no existe. */
    suspend fun close(chatId: UUID): ChatRecord? {
        val chat = repo.setStatus(chatId, ChatStatus.CLOSED) ?: return null
        mirror.upsertChat(chat.toDoc())
        return chat
    }

    // --- rutas -------------------------------------------------------------------------------

    /** `GET /v1/chats`: chats del usuario sin los de personas bloqueadas (en cualquier sentido). */
    suspend fun list(viewer: UserRecord): List<ChatSummary> {
        val blocked = repo.blockedEitherWay(viewer.id)
        return repo.chatsOf(viewer.id)
            .filter { row -> row.chat.members.none { it.userId in blocked } }
            .map { row ->
                val other = personRef(viewer.id, row.other, row.chat)
                ChatSummary(
                    id = row.chat.id.toString(),
                    type = row.chat.type,
                    mode = row.chat.mode,
                    other = other,
                    lastMessage = row.lastMessage?.let { toDto(it, viewer.id, other) },
                    unread = row.unread,
                    muted = row.chat.member(viewer.id)?.muted ?: false,
                    endsAt = row.chat.endsAt?.toKotlin(),
                    status = row.chat.status,
                )
            }
    }

    /**
     * `POST /v1/chats/{id}/messages`. Valida membresía, suspensión, bloqueo, estado y longitud;
     * guarda con `learnable` según el consentimiento vigente del autor para el modo del chat; escribe
     * en Firestore y avisa a los demás miembros que no lo silenciaron. Repetir el `clientId` devuelve
     * el mismo mensaje sin duplicarlo (y vuelve a escribir el chat y el mensaje, por si el espejo falló).
     */
    suspend fun send(sender: UserRecord, chatId: UUID, request: SendMessage): MessageDto {
        val clientId = request.clientId.trim()
        if (clientId.isEmpty() || clientId.length > MAX_CLIENT_ID) throw ValidationFailed("clientId debe tener entre 1 y $MAX_CLIENT_ID caracteres")
        val text = request.text.trim()
        if (text.isEmpty()) throw ValidationFailed("El mensaje no puede estar vacío")
        if (text.codePointCount(0, text.length) > MAX_TEXT) throw ValidationFailed("El mensaje admite máximo $MAX_TEXT caracteres")

        val now = clock.instant()
        sender.suspendedUntil?.let { if (it.isAfter(now)) throw Suspended() }
        if (sender.isDemo) throw Forbidden("Los perfiles de demostración no escriben mensajes")

        val chat = repo.chat(chatId) ?: throw NotFound("El chat no existe")
        if (chat.member(sender.id) == null) throw Forbidden("No eres miembro de este chat")

        repo.findByClientId(sender.id, clientId)?.let { existing ->
            if (existing.chatId != chatId) throw Conflict("clientId ya se usó en otro chat")
            repairChatMirror(chatId)
            mirror.upsertMessage(chatId, existing.toDoc(sender.firebaseUid))
            return toOwnDto(existing)
        }

        val others = chat.members.filter { it.userId != sender.id }
        if (others.any { repo.blockedBetween(sender.id, it.userId) }) throw Blocked()
        when {
            chat.type == ChatType.SCENARIO && (chat.status == ChatStatus.CLOSED || chat.endsAt?.isAfter(now) == false) -> {
                // Se acabó el tiempo: cierra el chat y dispara al juez (una sola vez; E4.8). Nunca rompe el 409.
                runCatching { scenarioEnded.ended(chatId) }.onFailure { log.warn("No se pudo cerrar el escenario {}", chatId, it) }
                throw ScenarioEnded()
            }
            chat.status == ChatStatus.CLOSED -> throw Forbidden("El chat está cerrado")
        }

        val learnable = repo.learningConsent(sender.id, chat.mode)
        val saved = repo.saveMessage(NewMessage(chatId, sender.id, clientId, text, learnable, now))
        if (saved.message.chatId != chatId) throw Conflict("clientId ya se usó en otro chat")
        repairChatMirror(chatId)
        mirror.upsertMessage(chatId, saved.message.toDoc(sender.firebaseUid))
        if (saved.inserted) {
            notifyOthers(chat, sender, others, saved.message)
            notifySaved(saved.message)
        }
        return toOwnDto(saved.message)
    }

    /** `POST /v1/chats/{id}/read`. */
    suspend fun markRead(user: UserRecord, chatId: UUID) {
        requireMember(user, chatId)
        repo.markRead(chatId, user.id, clock.instant())
    }

    /** `POST /v1/chats/{id}/mute`. Silenciar nunca exige más que ser miembro (tampoco con bloqueo). */
    suspend fun mute(user: UserRecord, chatId: UUID, muted: Boolean) {
        requireMember(user, chatId)
        repo.setMuted(chatId, user.id, muted)
    }

    /**
     * Oculta un mensaje por decisión de un admin (E8.5): `hidden = true` en Postgres y en el espejo de
     * Firestore (la app deja de ver el texto). Devuelve false si el mensaje no existe. Idempotente.
     */
    suspend fun hideMessage(messageId: UUID): Boolean {
        val message = repo.hideMessage(messageId) ?: return false
        val senderUid = message.senderId?.let { sender -> repo.chat(message.chatId)?.members?.firstOrNull { it.userId == sender }?.firebaseUid }
        mirror.upsertMessage(
            message.chatId,
            MessageDoc(message.id, senderUid, system = message.senderId == null, text = "", createdAt = message.createdAt, hidden = true),
        )
        return true
    }

    // --- internos ----------------------------------------------------------------------------

    private suspend fun notifySaved(message: MessageRecord) {
        val hook = onMessageSaved ?: return
        try {
            hook(message)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("El gancho posterior al mensaje {} falló: {}", message.id, e.message)
        }
    }

    /**
     * Repara el documento `chats/{id}` si la conexión lo dejó sin escribir (las reglas de `messages`
     * leen el chat padre). Relee el chat justo antes: con la lectura del inicio de `send`, un `close`
     * concurrente podría quedar pisado y el espejo volvería a OPEN.
     */
    private suspend fun repairChatMirror(chatId: UUID) {
        repo.chat(chatId)?.let { mirror.upsertChat(it.toDoc()) }
    }

    private suspend fun requireMember(user: UserRecord, chatId: UUID): ChatRecord {
        val chat = repo.chat(chatId) ?: throw NotFound("El chat no existe")
        if (chat.member(user.id) == null) throw Forbidden("No eres miembro de este chat")
        return chat
    }

    private suspend fun notifyOthers(chat: ChatRecord, sender: UserRecord, others: List<ChatMemberRecord>, message: MessageRecord) {
        val senderPerson = repo.person(sender.id)
        for (member in others.filterNot { it.muted }) {
            val label = senderPerson?.let { personRef(member.userId, it, chat).label } ?: GraphVisibilityPolicy.ANON_LABEL
            val notification = Notification(
                kind = NotificationKinds.CHAT_MESSAGE,
                title = label,
                body = preview(message.text),
                route = "parcheu://chat/${chat.id}",
                actorId = if (chat.type == ChatType.DIRECT) sender.id else null,
            )
            // El aviso nunca rompe el envío: el mensaje ya está en Postgres y en Firestore.
            runCatching { notifier.notify(member.userId, notification) }
                .onFailure { log.warn("No se pudo avisar del mensaje {} del chat {}", message.id, chat.id, it) }
        }
    }

    /**
     * Cómo ve [viewer] al otro miembro. Un chat DIRECT solo existe tras la conexión mutua (nombre);
     * en SCENARIO sigue anónimo ("Alguien de la UIS" con su `anonId`, sin uuid interno).
     */
    private fun personRef(viewer: UUID, other: PersonRow?, chat: ChatRecord): PersonRef {
        if (other == null || other.deletedAt != null) return PersonRef(label = DELETED_LABEL, initials = DELETED_INITIALS)
        val view = policy.present(viewer, other, connected = chat.type == ChatType.DIRECT, context = ViewContext.EGO)
        return PersonRef(
            id = view.id.takeUnless { view.anonymous },
            anonId = view.id.takeIf { view.anonymous },
            label = view.label,
            initials = view.initials,
            photoUrl = view.photoUrl,
            isDemo = view.isDemo,
        )
    }

    /** Mensaje visto por [viewer]: su propio id, o el id (o `anonId`) con el que ve al otro. */
    private fun toDto(message: MessageRecord, viewer: UUID, other: PersonRef): MessageDto = MessageDto(
        id = message.id.toString(),
        senderId = when (message.senderId) {
            null -> null
            viewer -> viewer.toString()
            else -> other.id ?: other.anonId
        },
        text = if (message.hidden) "" else message.text,
        createdAt = message.createdAt.toKotlin(),
        hidden = message.hidden,
    )

    private fun toOwnDto(message: MessageRecord) = MessageDto(
        id = message.id.toString(),
        senderId = message.senderId?.toString(),
        text = if (message.hidden) "" else message.text,
        createdAt = message.createdAt.toKotlin(),
        hidden = message.hidden,
    )

    private fun ChatRecord.toDoc() = ChatDoc(id, members.map { it.firebaseUid }, type, mode, status, endsAt)

    private fun MessageRecord.toDoc(senderUid: String) = MessageDoc(
        id = id,
        senderUid = senderUid,
        system = false,
        text = if (hidden) "" else text,
        createdAt = createdAt,
        hidden = hidden,
    )

    private fun preview(text: String): String =
        if (text.codePointCount(0, text.length) <= MAX_PREVIEW) text else text.substring(0, text.offsetByCodePoints(0, MAX_PREVIEW)) + "…"

    companion object {
        const val MAX_TEXT = 1000
        const val MAX_CLIENT_ID = 64
        const val MAX_PREVIEW = 100
        const val DELETED_LABEL = "Usuario eliminado"
        const val DELETED_INITIALS = "UE"
    }
}

/** Gancho de fin de escenario (E4.8): lo conecta el módulo `scenario`. Debe ser idempotente. */
fun interface ScenarioEndedHook {
    suspend fun ended(chatId: UUID)

    companion object {
        val NONE = ScenarioEndedHook { }
    }
}

/** Tipo de consentimiento que permite aprender de los chats de [mode] (arquitectura.md §4). */
internal fun learningKind(mode: Mode): ConsentKind = when (mode) {
    Mode.AMISTAD -> ConsentKind.CHAT_LEARNING_AMISTAD
    Mode.PAREJA -> ConsentKind.CHAT_LEARNING_PAREJA
    Mode.ESTUDIO -> ConsentKind.CHAT_LEARNING_ESTUDIO
}
