package me.santiagoreina.parcheu.api.platform

import me.santiagoreina.parcheu.api.platform.jobs.InProcessScheduler
import me.santiagoreina.parcheu.api.platform.tasks.InProcessTaskQueue

/**
 * Trabajo en segundo plano del modo `TASKS_MODE=inline`: la cola en proceso y el programador de jobs.
 * Nada corre hasta [start]; Main lo arranca solo en modo `serve`, así los tests con `fromConfig` no
 * lanzan bucles.
 */
class BackgroundWork(
    val queue: InProcessTaskQueue,
    val scheduler: InProcessScheduler,
    private val schedule: (InProcessScheduler) -> Unit,
) : AutoCloseable {
    @Volatile private var started = false

    @Synchronized
    fun start() {
        if (started) return
        started = true
        schedule(scheduler)
    }

    /** Programa los jobs en [other] en vez de en [scheduler]; permite a los tests comprobar qué jobs existen sin lanzar bucles. */
    internal fun scheduleOn(other: InProcessScheduler) = schedule(other)

    override fun close() {
        scheduler.close()
        queue.close()
    }
}
