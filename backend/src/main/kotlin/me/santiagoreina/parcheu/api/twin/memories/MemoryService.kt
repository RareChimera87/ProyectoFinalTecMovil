package me.santiagoreina.parcheu.api.twin.memories

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import me.santiagoreina.parcheu.api.platform.BogotaZone
import me.santiagoreina.parcheu.api.platform.errors.Conflict
import me.santiagoreina.parcheu.api.platform.errors.NotFound
import me.santiagoreina.parcheu.api.platform.errors.ValidationFailed
import me.santiagoreina.parcheu.api.platform.tasks.QueueKind
import me.santiagoreina.parcheu.api.platform.tasks.TaskQueue
import me.santiagoreina.parcheu.api.platform.tasks.TaskRequest
import me.santiagoreina.parcheu.contract.MemoryDto
import me.santiagoreina.parcheu.contract.MemoryStatus
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Page
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Resultado de una ejecución de `extract-memories` (para logs y pruebas). */
enum class ExtractionOutcome {
    /** Se procesaron mensajes nuevos (puede que sin memorias: consentimiento revocado o nada que aprender). */
    PROCESSED,

    /** No había mensajes propios aprendibles desde el cursor. */
    NOTHING_TO_DO,

    /** Otra ejecución movió el cursor mientras esta llamaba al modelo: se descartó el resultado. */
    STALE,
}

/**
 * Entrenamiento continuo del gemelo (E9.2, arquitectura.md §7.1 punto 4, ADR-0032).
 *
 * - `POST /internal/jobs/memories` (Scheduler, 02:00 Bogotá) encola en `ai-light` una tarea
 *   `extract-memories` por usuario con mensajes propios aprendibles nuevos.
 * - La tarea toma esos mensajes desde `twin_learning_cursor`, descarta los de modos sin
 *   consentimiento vigente, llama al modelo **una vez por modo** y guarda hasta 5 memorias PENDING
 *   por modo, cada una con el modo de su chat. El cursor avanza aunque no haya consentimiento: lo
 *   escrito sin consentimiento vigente no se aprende después.
 * - `GET /v1/twin/memories` y `POST /v1/twin/memories/{id}/accept|reject`; aceptar sube la versión
 *   del gemelo (invalida la caché de simulaciones).
 */
class MemoryService(
    private val repo: MemoryRepository,
    private val extractor: MemoryExtractor,
    private val tasks: TaskQueue,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(MemoryService::class.java)

    /**
     * Handler del job nocturno: encola `extract-memories` para cada usuario con mensajes nuevos,
     * paginando por id. El nombre lleva el día de Bogotá: repetir el job el mismo día no duplica
     * tareas y el de la noche siguiente sí encola de nuevo. Devuelve cuántas encoló.
     */
    suspend fun enqueueNightly(): Int {
        val day = LocalDate.ofInstant(clock.instant(), BogotaZone).format(DateTimeFormatter.BASIC_ISO_DATE)
        var after: UUID? = null
        var count = 0
        while (true) {
            val page = repo.usersToLearn(after, JOB_PAGE)
            page.forEach { tasks.enqueue(extractTask(it, day)); count++ }
            if (page.size < JOB_PAGE) break
            after = page.last()
        }
        log.info("Job de memorias: {} tareas extract-memories encoladas", count)
        return count
    }

    /**
     * Handler de `POST /internal/tasks/extract-memories`. Idempotente: el cursor solo avanza junto
     * con las memorias guardadas (misma transacción), así que un reintento después de un éxito no
     * encuentra mensajes nuevos, y uno después de un fallo vuelve a empezar desde el mismo punto. Si el
     * modelo falla, el error se propaga para que Cloud Tasks reintente.
     */
    suspend fun extract(userId: UUID): ExtractionOutcome {
        val now = clock.instant()
        val cursor = repo.cursor(userId)
        val messages = window(repo.learnableMessages(userId, cursor, now, MAX_MESSAGES))
        if (messages.isEmpty()) return ExtractionOutcome.NOTHING_TO_DO

        val modes = repo.learningModes(userId)
        // Revocar el consentimiento de un modo borra sus memorias PENDING (arquitectura.md §10.1); aquí se repasa por si acaso.
        val revoked = Mode.entries.toSet() - modes
        repo.deletePending(userId, revoked).takeIf { it > 0 }?.let { log.info("{} memorias PENDING de modos revocados borradas para {}", it, userId) }

        val memories = messages
            .filter { it.mode in modes }
            .groupBy { it.mode }
            .toSortedMap()
            .flatMap { (mode, batch) -> extractor.extract(userId, mode, batch) }

        if (!repo.saveExtraction(userId, cursor, messages.last().createdAt, memories, now)) {
            log.info("extract-memories de {}: otra ejecución ya avanzó el cursor; se descarta el resultado", userId)
            return ExtractionOutcome.STALE
        }
        log.info("extract-memories de {}: {} mensajes, {} memorias candidatas", userId, messages.size, memories.size)
        return ExtractionOutcome.PROCESSED
    }

    /** `GET /v1/twin/memories?status=&cursor=&limit=`: del más reciente al más antiguo. */
    fun list(userId: UUID, status: String?, cursor: String?, limit: Int?): Page<MemoryDto> {
        val size = (limit ?: DEFAULT_PAGE).also { if (it !in 1..MAX_PAGE) throw ValidationFailed("limit va de 1 a $MAX_PAGE") }
        val filter = status?.takeIf { it.isNotBlank() }?.let { raw ->
            MemoryStatus.entries.firstOrNull { it.name == raw.uppercase() } ?: throw ValidationFailed("status inválido: $raw")
        }
        val before = cursor?.takeIf { it.isNotBlank() }?.let { MemoryCursor.parse(it) ?: throw ValidationFailed("cursor inválido") }
        val rows = repo.list(userId, filter, before, size + 1)
        val page = rows.take(size)
        val next = if (rows.size > size) page.last().let { MemoryCursor(it.createdAt, it.id).encode() } else null
        return Page(page.map { it.toDto() }, next)
    }

    fun accept(userId: UUID, id: String): MemoryDto = decide(userId, id, MemoryStatus.ACCEPTED)

    fun reject(userId: UUID, id: String): MemoryDto = decide(userId, id, MemoryStatus.REJECTED)

    /** Repetir la misma decisión devuelve la memoria sin cambios; cambiar una ya decidida es CONFLICT. */
    private fun decide(userId: UUID, id: String, status: MemoryStatus): MemoryDto {
        val memoryId = runCatching { UUID.fromString(id) }.getOrNull() ?: throw NotFound("Memoria no encontrada")
        val decision = repo.decide(userId, memoryId, status, clock.instant()) ?: throw NotFound("Memoria no encontrada")
        if (!decision.changed && decision.record.status != status) {
            throw Conflict("La memoria ya fue ${if (decision.record.status == MemoryStatus.ACCEPTED) "aceptada" else "rechazada"}")
        }
        return decision.record.toDto()
    }

    private fun extractTask(userId: UUID, day: String) = TaskRequest(
        queue = QueueKind.AI_LIGHT,
        path = TASK_PATH,
        name = "extract-memories-$userId-$day",
        body = buildJsonObject { put("id", JsonPrimitive(userId.toString())) }.toString(),
    )

    companion object {
        const val TASK_PATH = "/tasks/extract-memories"
        const val JOB_PATH = "/jobs/memories"

        /** Usuarios por página del job nocturno. */
        const val JOB_PAGE = 500

        /** Mensajes por ejecución; lo que sobre queda para la noche siguiente. */
        const val MAX_MESSAGES = 200

        const val DEFAULT_PAGE = 20
        const val MAX_PAGE = 50

        /**
         * Si la consulta llenó [MAX_MESSAGES], quita los últimos mensajes que comparten el instante
         * del último: el cursor es un instante y `created_at > cursor` los saltaría la próxima vez.
         * Si todos comparten instante se procesan todos.
         */
        internal fun window(messages: List<LearnableMessage>): List<LearnableMessage> {
            if (messages.size < MAX_MESSAGES) return messages
            val last = messages.last().createdAt
            return messages.filter { it.createdAt < last }.ifEmpty { messages }
        }
    }
}

private fun MemoryRecord.toDto() = MemoryDto(
    id = id.toString(),
    text = text,
    mode = mode,
    status = status,
    createdAt = createdAt.toKotlinInstant(),
)

private fun Instant.toKotlinInstant(): kotlin.time.Instant = kotlin.time.Instant.fromEpochSeconds(epochSecond, nano)
