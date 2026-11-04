package me.santiagoreina.parcheu.api.twin.memories

import me.santiagoreina.parcheu.contract.MemoryStatus
import me.santiagoreina.parcheu.contract.Mode
import java.time.Instant
import java.util.UUID

/** Un mensaje **propio** con `learnable = true`, con el modo del chat donde se escribió. */
data class LearnableMessage(
    val id: UUID,
    val chatId: UUID,
    val mode: Mode,
    val text: String,
    val createdAt: Instant,
)

/** Una fila de `twin_memories`. */
data class MemoryRecord(
    val id: UUID,
    val userId: UUID,
    val mode: Mode,
    val text: String,
    val sourceChatId: UUID?,
    val status: MemoryStatus,
    val createdAt: Instant,
    val decidedAt: Instant?,
)

/** Memoria candidata lista para guardarse en PENDING; [mode] es siempre el del chat de origen. */
data class NewMemory(val mode: Mode, val text: String, val sourceChatId: UUID?)

/** Cursor de `GET /v1/twin/memories`: `<epochMicros>_<uuid>` de la última memoria de la página anterior. */
data class MemoryCursor(val createdAt: Instant, val id: UUID) {
    fun encode(): String = "${createdAt.epochSecond * 1_000_000 + createdAt.nano / 1_000}_$id"

    companion object {
        fun parse(raw: String): MemoryCursor? {
            val parts = raw.split('_')
            if (parts.size != 2) return null
            val micros = parts[0].toLongOrNull() ?: return null
            val id = runCatching { UUID.fromString(parts[1]) }.getOrNull() ?: return null
            return MemoryCursor(Instant.ofEpochSecond(Math.floorDiv(micros, 1_000_000L), Math.floorMod(micros, 1_000_000L) * 1_000L), id)
        }
    }
}

/**
 * Persistencia de la extracción de memorias (E9.2): mensajes aprendibles, consentimientos vigentes,
 * `twin_learning_cursor` y `twin_memories`.
 */
interface MemoryRepository {
    /**
     * Usuarios vivos (sin `deleted_at`) con al menos un mensaje propio `learnable = true` y no oculto
     * posterior a su cursor, ordenados por id y con id mayor que [after]. Para paginar el job nocturno.
     */
    fun usersToLearn(after: UUID?, limit: Int): List<UUID>

    /** Modos cuyo consentimiento de aprendizaje está vigente (la última fila de `consents` concede). */
    fun learningModes(userId: UUID): Set<Mode>

    /** `twin_learning_cursor.last_message_at`, o null si nunca se extrajo nada. */
    fun cursor(userId: UUID): Instant?

    /**
     * Mensajes propios (`sender_id = userId`), `learnable = true` y no ocultos, con `created_at` en
     * (`since`, `until`], del más antiguo al más reciente (y por id), como máximo [limit]. Nunca
     * devuelve mensajes de otras personas.
     */
    fun learnableMessages(userId: UUID, since: Instant?, until: Instant, limit: Int): List<LearnableMessage>

    /**
     * En una transacción: si el cursor sigue en [expectedCursor] guarda [memories] en PENDING y lo
     * mueve a [newCursor]; si otra ejecución ya lo movió no guarda nada y devuelve false. Bajo el
     * bloqueo relee el consentimiento: descarta las memorias de modos ya no concedidos y borra las
     * PENDING de esos modos (el consentimiento pudo revocarse durante la llamada al modelo).
     */
    fun saveExtraction(userId: UUID, expectedCursor: Instant?, newCursor: Instant, memories: List<NewMemory>, now: Instant): Boolean

    /** Borra las memorias PENDING de [modes] (consentimiento revocado). Devuelve cuántas borró. */
    fun deletePending(userId: UUID, modes: Set<Mode>): Int

    /** Memorias del usuario, de la más reciente a la más antigua (y por id), anteriores a [before]. */
    fun list(userId: UUID, status: MemoryStatus?, before: MemoryCursor?, limit: Int): List<MemoryRecord>

    /**
     * Decide una memoria PENDING del usuario ([status] es ACCEPTED o REJECTED) en una transacción.
     * Aceptar sube `twins.version` en 1 (invalida la caché de simulaciones, ADR-0032). Repetir la
     * misma decisión no cambia nada; null si la memoria no existe o es de otra persona.
     */
    fun decide(userId: UUID, memoryId: UUID, status: MemoryStatus, now: Instant): Decision?
}

/** Resultado de [MemoryRepository.decide]. [changed] = false si ya tenía esa u otra decisión. */
data class Decision(val record: MemoryRecord, val changed: Boolean)
