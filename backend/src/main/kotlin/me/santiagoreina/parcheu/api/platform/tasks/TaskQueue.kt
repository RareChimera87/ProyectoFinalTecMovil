package me.santiagoreina.parcheu.api.platform.tasks

import java.util.concurrent.CopyOnWriteArrayList

/** Colas de Cloud Tasks (ADR-0019). Cada una se configura con su variable `TASKS_QUEUE_*`. */
enum class QueueKind(val envVar: String) {
    SIMULATIONS("TASKS_QUEUE_SIM"),
    AI_LIGHT("TASKS_QUEUE_AI_LIGHT"),
    MAINTENANCE("TASKS_QUEUE_MAINT"),
}

/**
 * Una tarea para `POST /internal/tasks/<path>`. [name] es el nombre de deduplicación de Cloud
 * Tasks (solo letras, dígitos, `-` y `_`); encolar dos veces el mismo nombre no duplica el trabajo.
 */
data class TaskRequest(
    val queue: QueueKind,
    /** Ruta bajo `/internal`, p. ej. `/tasks/build-persona`. */
    val path: String,
    val name: String,
    /** Cuerpo JSON, p. ej. `{"id":"..."}`. */
    val body: String,
)

/** Encola trabajo asíncrono. Los handlers de las tareas deben ser idempotentes (ADR-0019). */
interface TaskQueue {
    suspend fun enqueue(task: TaskRequest)
}

/**
 * Cola Fake para tests y `ENV=local`: registra las tareas (deduplicadas por nombre) y, si se
 * define [inline], las ejecuta en línea al encolarlas.
 */
class FakeTaskQueue(var inline: (suspend (TaskRequest) -> Unit)? = null) : TaskQueue {
    private val recorded = CopyOnWriteArrayList<TaskRequest>()
    val tasks: List<TaskRequest> get() = recorded.toList()

    /** Si no es null, [enqueue] lanza este error sin registrar la tarea (simula un fallo de Cloud Tasks). */
    @Volatile var failWith: Throwable? = null

    override suspend fun enqueue(task: TaskRequest) {
        failWith?.let { throw it }
        if (recorded.any { it.name == task.name && it.queue == task.queue }) return
        recorded += task
        inline?.invoke(task)
    }
}

/** Cola sin configurar (dev/prod sin `TASKS_QUEUE_*` ni `SERVICE_URL`): falla al encolar, no al arrancar. */
class UnconfiguredTaskQueue(private val reason: String) : TaskQueue {
    override suspend fun enqueue(task: TaskRequest) {
        error("Cloud Tasks no está configurado: $reason")
    }
}
