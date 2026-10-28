package me.santiagoreina.parcheu.api.platform.jobs

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.santiagoreina.parcheu.api.platform.BogotaZone
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import kotlin.coroutines.CoroutineContext

/**
 * Sustituto de Cloud Scheduler en el homeserver (`TASKS_MODE=inline`, ADR-0033): un bucle por job.
 * Un fallo se registra y el siguiente ciclo reintenta; nunca tumba el proceso.
 */
open class InProcessScheduler(context: CoroutineContext = Dispatchers.IO) : AutoCloseable {
    private val log = LoggerFactory.getLogger(InProcessScheduler::class.java)
    private val scope = CoroutineScope(SupervisorJob() + context)

    open fun every(name: String, period: Duration, initialDelay: Duration = Duration.ZERO, block: suspend () -> Unit) {
        scope.launch {
            delay(initialDelay.toMillis())
            while (true) {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.error("El job {} falló: se reintenta en el siguiente ciclo", name, e)
                }
                delay(period.toMillis())
            }
        }
    }

    override fun close() {
        scope.cancel()
    }

    companion object {
        /**
         * Retraso desde ahora hasta la próxima [at] en [zone] (hoy si aún no pasa, si no mañana). Con un
         * periodo de 24 h el job queda anclado a esa hora local, como un cron diario de Cloud Scheduler.
         */
        fun delayUntil(at: LocalTime, clock: Clock, zone: ZoneId = BogotaZone): Duration {
            val now = ZonedDateTime.ofInstant(clock.instant(), zone)
            var next = now.with(at)
            if (!next.isAfter(now)) next = next.plusDays(1)
            return Duration.between(now, next)
        }

        /**
         * Retraso hasta el próximo [day] a las [at] en [zone]. Con un periodo de 7 días el job queda anclado
         * a ese momento de la semana, como un cron semanal de Cloud Scheduler.
         */
        fun delayUntilWeekly(day: DayOfWeek, at: LocalTime, clock: Clock, zone: ZoneId = BogotaZone): Duration {
            val now = ZonedDateTime.ofInstant(clock.instant(), zone)
            var next = now.with(TemporalAdjusters.nextOrSame(day)).with(at)
            if (!next.isAfter(now)) next = next.plusWeeks(1)
            return Duration.between(now, next)
        }
    }
}
