package me.santiagoreina.parcheu.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.feature.safety.SafetyTarget

/** Estado de F9. */
data class ChatUiState(
    val loading: Boolean = true,
    val header: ChatHeader? = null,
    val messages: List<ChatMessageUi> = emptyList(),
    val draft: String = "",
    val consentBannerVisible: Boolean = true,
    /** No se pudo cargar la cabecera (`GET /v1/chats`); los mensajes pueden seguir llegando. */
    val headerError: AppError? = null,
    /** El listener de Firestore falló (sin permiso o sin caché ni red). */
    val liveError: AppError? = null,
    /** Hay mensajes anteriores sin cargar: la pantalla pide más al llegar al primero de la lista. */
    val canLoadOlder: Boolean = false,
    val loadingOlder: Boolean = false
) {
    val canSend: Boolean get() = draft.isNotBlank() && header?.closed != true
}

sealed interface ChatEffect {
    /** Abre H1 (reportar o bloquear) para [target] desde este chat (E8.1). */
    data class OpenReport(val target: SafetyTarget) : ChatEffect

    /** No se pudo silenciar o quitar el silencio. */
    data class MuteFailed(val error: AppError) : ChatEffect
}

/** Mensaje propio que la API aún no confirmó (o que falló). [sentId] es el id de la API tras enviarse. */
private data class LocalMessage(
    val clientId: String,
    val text: String,
    val status: MessageStatus,
    val sentId: String? = null
)

private data class Meta(
    val loading: Boolean = true,
    val header: ChatHeader? = null,
    val draft: String = "",
    val consentBannerVisible: Boolean = true,
    val headerError: AppError? = null,
    val liveError: AppError? = null,
    val olderExhausted: Boolean = false,
    val loadingOlder: Boolean = false
)

/**
 * F9 · Chat en vivo (E5.2). Los mensajes llegan del listener de Firestore; el envío va por la API y se
 * muestra como pendiente hasta que responde. Un mensaje enviado queda visible desde la respuesta de la API
 * y se reemplaza por el del snapshot cuando este llega (mismo id). El listener trae solo los últimos
 * [CHAT_PAGE_SIZE] mensajes; los anteriores se piden por páginas con [loadOlder] (A-M3). Un fallo deja el mensaje con "Reintentar",
 * que repite el envío con el mismo `clientId` (la API es idempotente).
 */
class ChatViewModel(
    private val chatId: String,
    private val repository: ChatRepository,
    private val newClientId: () -> String = { UUID.randomUUID().toString() }
) : ViewModel() {
    private val remote = MutableStateFlow<List<RemoteMessage>>(emptyList())

    /** Páginas anteriores a la ventana del listener (más viejo primero) y lo que esta fue soltando al avanzar. */
    private val older = MutableStateFlow<List<RemoteMessage>>(emptyList())

    /** Mensajes ocultados por la moderación, por id: pisan la copia de [older] o de [remote]. */
    private val hidden = MutableStateFlow<Map<String, RemoteMessage>>(emptyMap())
    private val local = MutableStateFlow<List<LocalMessage>>(emptyList())
    private val meta = MutableStateFlow(Meta())

    private val _effects = Channel<ChatEffect>(Channel.BUFFERED)
    val effects: Flow<ChatEffect> = _effects.receiveAsFlow()

    val state: StateFlow<ChatUiState> =
        combine(remote, older, hidden, local, meta) {
                remoteMessages,
                olderMessages,
                hiddenById,
                localMessages,
                m
            ->
            ChatUiState(
                loading = m.loading,
                header = m.header,
                messages = merge(
                    stitch(olderMessages, remoteMessages, hiddenById),
                    localMessages
                ),
                draft = m.draft,
                consentBannerVisible = m.consentBannerVisible,
                headerError = m.headerError,
                liveError = m.liveError,
                canLoadOlder = !m.olderExhausted &&
                    (olderMessages.isNotEmpty() || remoteMessages.size >= CHAT_PAGE_SIZE),
                loadingOlder = m.loadingOlder
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ChatUiState())

    init {
        loadHeader()
        observeMessages()
        observeHidden()
        viewModelScope.launch { repository.markRead(chatId) }
    }

    /** Escucha los mensajes ocultados para que los de [older] no conserven su texto (E8.5). */
    private fun observeHidden() {
        viewModelScope.launch {
            repository
                .observeHidden(chatId)
                .catch { e -> if (e is CancellationException) throw e }
                .collect { list -> hidden.value = list.associateBy { it.id } }
        }
    }

    /**
     * Une el historial con la ventana en vivo: gana la copia del snapshot, luego la de moderación, y el
     * resultado va ordenado por `createdAt` (estable: los empates conservan el orden recibido).
     */
    private fun stitch(
        olderMessages: List<RemoteMessage>,
        remoteMessages: List<RemoteMessage>,
        hiddenById: Map<String, RemoteMessage>
    ): List<RemoteMessage> {
        val liveIds = remoteMessages.mapTo(HashSet()) { it.id }
        return (olderMessages.filter { it.id !in liveIds } + remoteMessages)
            .map { hiddenById[it.id] ?: it }
            .sortedWith(compareBy(nullsLast<Instant>()) { it.createdAt })
    }

    private var liveJob: Job? = null

    private fun observeMessages() {
        liveJob?.cancel()
        liveJob = viewModelScope.launch {
            repository
                .observeMessages(chatId)
                .catch { e ->
                    if (e is CancellationException) throw e
                    meta.update {
                        it.copy(
                            loading = false,
                            liveError =
                            (e as? ChatLiveException)?.error ?: AppError.Network
                        )
                    }
                }.collect { messages ->
                    keepWhatSlidedOut(messages)
                    remote.value = messages
                    meta.update { it.copy(loading = false, liveError = null) }
                }
        }
    }

    /** Al avanzar la ventana en vivo, los mensajes que salen por arriba pasan a [older] en vez de desaparecer. */
    private fun keepWhatSlidedOut(incoming: List<RemoteMessage>) {
        val first = incoming.firstOrNull()?.createdAt ?: return
        val ids = incoming.mapTo(HashSet()) { it.id }
        val slided = remote.value.filter { it.id !in ids && (it.createdAt ?: first) < first }
        if (slided.isNotEmpty()) older.update { (it + slided).distinctBy { m -> m.id } }
    }

    private var olderJob: Job? = null

    /**
     * La pantalla llegó al primer mensaje: trae la página anterior (`startAfter` del más viejo que se
     * conoce). Una página corta marca el historial como agotado; un fallo deja reintentar al volver al tope.
     */
    fun loadOlder() {
        if (!state.value.canLoadOlder || olderJob?.isActive == true) return
        val oldest = (older.value + remote.value).firstNotNullOfOrNull { it.createdAt } ?: return
        meta.update { it.copy(loadingOlder = true) }
        olderJob = viewModelScope.launch {
            try {
                val page = repository.loadOlder(
                    chatId,
                    Timestamp(oldest.epochSeconds, oldest.nanosecondsOfSecond)
                )
                older.update { (page + it).distinctBy { m -> m.id } }
                meta.update {
                    it.copy(
                        loadingOlder = false,
                        olderExhausted =
                        page.size < CHAT_PAGE_SIZE
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                meta.update { it.copy(loadingOlder = false) }
            }
        }
    }

    private fun loadHeader() {
        viewModelScope.launch {
            when (val result = repository.chats()) {
                is ApiResult.Success -> {
                    val summary = result.value.firstOrNull { it.id == chatId }
                    meta.update {
                        it.copy(
                            header = summary?.toHeader(),
                            headerError = if (summary == null) AppError.NotFound else null
                        )
                    }
                }
                is ApiResult.Failure -> meta.update { it.copy(headerError = result.error) }
            }
        }
    }

    fun retryLoad() {
        meta.update { it.copy(headerError = null) }
        loadHeader()
    }

    /** "Reintentar" del error del listener: vuelve a suscribirse a Firestore. */
    fun retryLive() {
        meta.update { it.copy(loading = true, liveError = null) }
        observeMessages()
    }

    fun onDraftChange(value: String) {
        meta.update { it.copy(draft = value.take(MAX_MESSAGE_LENGTH)) }
    }

    fun dismissConsentBanner() {
        meta.update { it.copy(consentBannerVisible = false) }
    }

    fun send() {
        val current = state.value
        if (!current.canSend) return
        val message = LocalMessage(newClientId(), current.draft.trim(), MessageStatus.Pending)
        meta.update { it.copy(draft = "") }
        local.update { it + message }
        deliver(message)
    }

    /** "Reintentar" en un mensaje fallido: mismo `clientId`, así no se duplica si el primero sí llegó. */
    fun retry(key: String) {
        val message =
            local.value.firstOrNull { it.clientId == key && it.status is MessageStatus.Failed }
                ?: return
        replace(key) { it.copy(status = MessageStatus.Pending) }
        deliver(message)
    }

    private fun deliver(message: LocalMessage) {
        viewModelScope.launch {
            when (val result = repository.send(chatId, message.clientId, message.text)) {
                is ApiResult.Success ->
                    replace(message.clientId) {
                        it.copy(status = MessageStatus.Sent, sentId = result.value.id)
                    }
                is ApiResult.Failure ->
                    replace(message.clientId) {
                        it.copy(status = MessageStatus.Failed(result.error))
                    }
            }
        }
    }

    private fun replace(clientId: String, transform: (LocalMessage) -> LocalMessage) {
        local.update { list -> list.map { if (it.clientId == clientId) transform(it) else it } }
    }

    /** Silenciar / quitar el silencio (también desde el menú de F9). */
    fun toggleMute() {
        val header = state.value.header ?: return
        val target = !header.muted
        meta.update { it.copy(header = header.copy(muted = target)) }
        viewModelScope.launch {
            val result = repository.setMuted(chatId, target)
            if (result is ApiResult.Failure) {
                meta.update { m -> m.copy(header = m.header?.copy(muted = !target)) }
                _effects.send(ChatEffect.MuteFailed(result.error))
            }
        }
    }

    /** Menú → "Reportar o bloquear" (H1). Con una persona anónima el objetivo es su `anonId`. */
    fun report() {
        val header = state.value.header ?: return
        val anonymous = header.otherUserId == null
        val id = header.otherUserId ?: header.otherAnonId ?: return
        val target =
            SafetyTarget(
                id = id,
                name = header.name,
                anonymous = anonymous,
                chatId = chatId,
                muted = header.muted
            )
        viewModelScope.launch { _effects.send(ChatEffect.OpenReport(target)) }
    }

    /** H1 cambió el silencio del chat: la barra lo refleja sin recargar. */
    fun onMutedChanged(muted: Boolean) {
        meta.update { m -> m.copy(header = m.header?.copy(muted = muted)) }
    }

    private fun merge(
        remoteMessages: List<RemoteMessage>,
        localMessages: List<LocalMessage>
    ): List<ChatMessageUi> {
        val myUid = repository.currentUid
        val remoteIds = remoteMessages.mapTo(HashSet()) { it.id }
        val fromRemote =
            remoteMessages.distinctBy { it.id }.map {
                ChatMessageUi(
                    key = it.id,
                    text = it.text,
                    mine = !it.system && myUid != null && it.senderUid == myUid,
                    system = it.system,
                    hidden = it.hidden,
                    createdAt = it.createdAt
                )
            }
        val fromLocal =
            localMessages
                .filter { it.sentId == null || it.sentId !in remoteIds }
                .map {
                    ChatMessageUi(
                        key = it.clientId,
                        text = it.text,
                        mine = true,
                        status = it.status
                    )
                }
        return fromRemote + fromLocal
    }
}
