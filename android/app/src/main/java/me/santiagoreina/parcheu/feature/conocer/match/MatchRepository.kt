package me.santiagoreina.parcheu.feature.conocer.match

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.time.Instant
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import me.santiagoreina.parcheu.contract.Choice
import me.santiagoreina.parcheu.contract.Enqueue
import me.santiagoreina.parcheu.contract.MatchChoice
import me.santiagoreina.parcheu.contract.MatchDto
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.TicketDto
import me.santiagoreina.parcheu.contract.TicketStatus
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.core.network.apiCall

/** Acceso a la fila de match y al match encontrado (F1, F2). */
interface MatchRepository {
    suspend fun enqueue(mode: Mode): ApiResult<TicketDto>

    /** `GET /v1/match/queue`: ticket vigente o `null` si el usuario no está en la fila (404). */
    suspend fun currentTicket(): ApiResult<TicketDto?>

    /** Último modo con el que el usuario entró a la fila en esta sesión, para preseleccionarlo. */
    var lastMode: Mode?

    /** `DELETE /v1/match/queue`. */
    suspend fun cancel(): ApiResult<Unit>

    /**
     * Escucha `matchTickets/{uid}` (solo lectura, ADR-0004). Emite `null` si el documento no existe
     * (el ticket se borró al cancelar). Ignora las lecturas de la caché local y termina con
     * error si el listener falla (permisos, token vencido).
     */
    fun observeTicket(): Flow<TicketDto?>

    suspend fun match(id: String): ApiResult<MatchDto>

    suspend fun choose(id: String, choice: MatchChoice): ApiResult<MatchDto>
}

class MatchRepositoryImpl(
    private val api: MatchApi,
    private val mapper: ApiErrorMapper,
    private val tickets: MatchTicketSource
) : MatchRepository {
    override var lastMode: Mode? = null

    override suspend fun enqueue(mode: Mode): ApiResult<TicketDto> {
        lastMode = mode
        return apiCall(mapper) { api.enqueue(Enqueue(mode)) }
    }

    override suspend fun currentTicket(): ApiResult<TicketDto?> =
        when (val result = apiCall(mapper) { api.ticket() }) {
            is ApiResult.Success -> ApiResult.Success(result.value)
            is ApiResult.Failure ->
                if (result.error == AppError.NotFound) ApiResult.Success(null) else result
        }

    override suspend fun cancel() = apiCall(mapper) { api.cancel() }

    override fun observeTicket(): Flow<TicketDto?> = tickets.observe()

    override suspend fun match(id: String) = apiCall(mapper) { api.match(id) }

    override suspend fun choose(id: String, choice: MatchChoice) =
        apiCall(mapper) { api.choose(id, Choice(choice)) }
}

/** Origen de los cambios del ticket; en producción es Firestore y en pruebas un fake. */
fun interface MatchTicketSource {
    fun observe(): Flow<TicketDto?>
}

/** Campos de `matchTickets/{uid}` (arquitectura.md §9) a un [TicketDto]; `null` si falta alguno obligatorio. */
fun ticketFromFields(fields: Map<String, Any?>): TicketDto? {
    val status = (fields["status"] as? String)?.let { name ->
        TicketStatus.entries.firstOrNull { it.name == name }
    } ?: return null
    val mode = (fields["mode"] as? String)?.let { name ->
        Mode.entries.firstOrNull { it.name == name }
    } ?: return null
    val expiresAt = when (val raw = fields["expiresAt"]) {
        is Timestamp -> Instant.fromEpochSeconds(raw.seconds, raw.nanoseconds)
        is String -> runCatching { Instant.parse(raw) }.getOrNull()
        else -> null
    } ?: return null
    return TicketDto(status, mode, expiresAt, fields["matchId"] as? String)
}

/**
 * Las instancias se piden en cada escucha (no se guardan): al cerrar sesión (E8.2) Firestore se termina y
 * su instancia queda inservible; la siguiente sesión necesita la nueva.
 */
class FirestoreMatchTicketSource(
    private val firestore: () -> FirebaseFirestore,
    private val auth: () -> FirebaseAuth
) : MatchTicketSource {
    override fun observe(): Flow<TicketDto?> = callbackFlow {
        val uid = auth().currentUser?.uid
        if (uid == null) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val registration = firestore().collection("matchTickets").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Tras un error Firestore no entrega más eventos: se cierra el flujo para que
                    // quien escucha lo sepa y recurra a GET /v1/match/queue.
                    close(error)
                } else if (snapshot != null && !snapshot.metadata.isFromCache) {
                    // Una lectura de la caché puede traer un ticket MATCHED de un match anterior.
                    trySend(snapshot.data?.let(::ticketFromFields))
                }
            }
        awaitClose { registration.remove() }
    }
}
