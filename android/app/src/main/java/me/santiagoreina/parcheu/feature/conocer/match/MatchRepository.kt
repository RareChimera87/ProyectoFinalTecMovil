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
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.core.network.apiCall

/** Acceso a la fila de match y al match encontrado (F1, F2). */
interface MatchRepository {
    suspend fun enqueue(mode: Mode): ApiResult<TicketDto>

    /** `DELETE /v1/match/queue`. */
    suspend fun cancel(): ApiResult<Unit>

    /**
     * Escucha `matchTickets/{uid}` (solo lectura, ADR-0004). Emite `null` si el documento no existe
     * (el ticket se borró al cancelar).
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
    override suspend fun enqueue(mode: Mode) = apiCall(mapper) { api.enqueue(Enqueue(mode)) }

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

class FirestoreMatchTicketSource(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : MatchTicketSource {
    override fun observe(): Flow<TicketDto?> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val registration = firestore.collection("matchTickets").document(uid)
            .addSnapshotListener { snapshot, error ->
                // Un error de permisos o de red no cierra el flujo: la UI sigue con el último estado.
                if (error == null && snapshot != null) {
                    trySend(snapshot.data?.let(::ticketFromFields))
                }
            }
        awaitClose { registration.remove() }
    }
}
