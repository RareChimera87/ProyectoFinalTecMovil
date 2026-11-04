package me.santiagoreina.parcheu.api.quota

import me.santiagoreina.parcheu.api.platform.BogotaZone
import me.santiagoreina.parcheu.api.platform.errors.QuotaExceeded
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/**
 * Topes diarios internos por persona (B-M1, ADR-0010): cortan el abuso de acciones que disparan IA y que no
 * son cupos de producto. A diferencia de [QuotaService] no tienen `QuotaKind` en el contrato ni salen en
 * `GET /v1/quota` (la app no los promete), así que agregarlos no exige cambiar el contrato ni Android.
 */
enum class DailyKind {
    /** Envíos de `POST /v1/twin/answers`: cada uno sube la versión y encola `build-persona` (LLM lite). */
    TWIN_ANSWERS,

    /** Mensajes de chat moderados con IA (E8.6); pasado el tope se muestrea (ADR-0026). */
    MODERATION,

    /**
     * Moderaciones muestreadas (1 de cada `MODERATION_SAMPLE_EVERY`) después del tope o del sub-techo: corte duro de
     * la cola muestreada, sin él el muestreo de una sola cuenta no tendría límite por persona (ADR-0026).
     */
    MODERATION_SAMPLED,
}

/** Contadores de `daily_counters`, uno por persona, día de Bogotá y [DailyKind]. */
interface DailyCounterRepository {
    /**
     * Suma 1 al contador de hoy si está por debajo de [limit] y devuelve true; si ya llegó, no cambia nada y
     * devuelve false. Atómico: dos llamadas simultáneas nunca pasan el límite.
     */
    fun increment(userId: UUID, kind: DailyKind, day: LocalDate, limit: Int): Boolean
}

/** `daily_counters` en Postgres: un solo `INSERT ... ON CONFLICT DO UPDATE ... WHERE count < limit`. */
class ExposedDailyCounterRepository(private val db: Database) : DailyCounterRepository {
    override fun increment(userId: UUID, kind: DailyKind, day: LocalDate, limit: Int): Boolean {
        if (limit <= 0) return false
        return transaction(db) {
            val sql = connection.connection as Connection
            sql.prepareStatement(
                "insert into daily_counters (user_id, day, kind, count) values (?, ?, ?, 1) " +
                    "on conflict (user_id, day, kind) do update set count = daily_counters.count + 1 " +
                    "where daily_counters.count < ? returning count",
            ).use { st ->
                st.setObject(1, userId)
                st.setObject(2, day)
                st.setString(3, kind.name)
                st.setInt(4, limit)
                st.executeQuery().use { it.next() }
            }
        }
    }
}

/** `daily_counters` en memoria: pruebas y `ENV=local` sin base. */
class InMemoryDailyCounterRepository : DailyCounterRepository {
    private val counts = mutableMapOf<Triple<UUID, DailyKind, LocalDate>, Int>()

    @Synchronized
    override fun increment(userId: UUID, kind: DailyKind, day: LocalDate, limit: Int): Boolean {
        val key = Triple(userId, kind, day)
        val current = counts[key] ?: 0
        if (current >= limit) return false
        counts[key] = current + 1
        return true
    }
}

/** Aplica los topes de [QuotaLimits] a [DailyKind]; el día se corta a las 00:00 de Bogotá, como los cupos. */
class DailyLimiter(
    private val counters: DailyCounterRepository,
    private val entitlements: EntitlementService,
    private val limits: QuotaLimits,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(DailyLimiter::class.java)

    /** Cuenta un uso de [kind] o lanza [QuotaExceeded] (402, sin `kind`, `resetsAt` = medianoche de Bogotá). */
    fun consume(userId: UUID, kind: DailyKind) {
        if (tryConsume(userId, kind)) return
        log.info("daily_limit_rejected kind={}", kind)
        throw QuotaExceeded(windowFor(QuotaPeriod.DAY, clock.instant()).end.toKotlin())
    }

    /** Cuenta un uso de [kind] si queda tope hoy; false si ya se agotó (no lanza). */
    fun tryConsume(userId: UUID, kind: DailyKind): Boolean {
        val now = clock.instant()
        val limit = when (kind) {
            DailyKind.TWIN_ANSWERS ->
                if (entitlements.isPlus(userId, now)) limits.plusDailyTwinAnswers else limits.freeDailyTwinAnswers
            // Igual para ambos planes: no hace falta consultar `entitlements` en cada mensaje.
            DailyKind.MODERATION -> limits.dailyModerations
            DailyKind.MODERATION_SAMPLED -> limits.dailySampledModerations
        }
        return counters.increment(userId, kind, now.atZone(BogotaZone).toLocalDate(), limit)
    }
}
