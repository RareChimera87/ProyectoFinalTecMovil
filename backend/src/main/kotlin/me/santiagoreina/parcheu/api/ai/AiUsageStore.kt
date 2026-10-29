package me.santiagoreina.parcheu.api.ai

import me.santiagoreina.parcheu.api.platform.db.tables.AiUsage
import me.santiagoreina.parcheu.api.platform.db.tables.AiUsageDaily
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.upsert
import java.sql.Connection
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

/** Una llamada facturable a un modelo (fila de `ai_usage`). */
data class AiUsageEntry(
    /** Día en America/Bogota. */
    val day: LocalDate,
    val purpose: AiPurpose,
    val provider: AiProvider,
    val model: String,
    val userId: UUID?,
    val tokensIn: Int,
    val tokensOut: Int,
    val costMicros: Long,
    val createdAt: OffsetDateTime,
)

/**
 * Lo que ve [AiUsageStore.hold] con las filas del día bloqueadas: gasto registrado más reservas de llamadas
 * en curso, del total ([globalMicros]) y de la persona ([userMicros], null si la llamada no es de nadie).
 */
data class BudgetSnapshot(val globalMicros: Long, val userMicros: Long?)

/** Reserva hecha antes de llamar al modelo; [AiUsageStore.settle] la cambia por el costo real. */
data class BudgetHold(val day: LocalDate, val userId: UUID?, val micros: Long)

/** Registro de uso de IA y gasto del día (ADR-0010). */
interface AiUsageStore {
    /** Guarda la llamada en `ai_usage` y la suma en `ai_usage_daily` en la misma transacción. */
    suspend fun record(entry: AiUsageEntry)

    /** Gasto total registrado del día (todas las combinaciones proveedor-modelo), en micro-USD. */
    suspend fun spentMicros(day: LocalDate): Long

    /**
     * Reserva atómica (B-M1): bloquea el gasto del día total y el de [userId], llama a [decide] con ese estado
     * y suma lo que devuelve a ambos antes de soltar el bloqueo. Si [decide] lanza, no se reserva nada. Dos
     * reservas simultáneas se serializan, así que ninguna ve el gasto sin las reservas de las demás.
     */
    suspend fun hold(day: LocalDate, userId: UUID?, decide: (BudgetSnapshot) -> Long): BudgetHold

    /** Cambia [hold] por el costo real: registra [entry] (si hubo llamada facturable) y descuenta la reserva. */
    suspend fun settle(hold: BudgetHold, entry: AiUsageEntry?)

    /** Gasto registrado de [userId] en [day] con [purpose] (sub-techo de moderación). */
    suspend fun userSpentMicros(day: LocalDate, userId: UUID, purpose: AiPurpose): Long
}

/**
 * Implementación sobre Postgres (tablas de E0.4). [database] se resuelve en la primera llamada
 * para que el arranque del servicio no dependa de que Cloud SQL responda.
 *
 * La reserva vive en `ai_budget_global` y `ai_budget_user` (B-M1): siempre se bloquea primero la fila total
 * y luego la de la persona, en ese orden, para que dos transacciones nunca se esperen en círculo.
 */
class ExposedAiUsageStore(database: () -> Database) : AiUsageStore {
    constructor(db: Database) : this({ db })

    private val db by lazy(database)

    override suspend fun record(entry: AiUsageEntry) {
        withContext(Dispatchers.IO) {
            suspendTransaction(db = db) {
                insertRows(entry)
                adjust(sql(), entry.day, entry.userId?.takeIf { entry.purpose.chargesUserBudget }, entry.costMicros)
            }
        }
    }

    override suspend fun hold(day: LocalDate, userId: UUID?, decide: (BudgetSnapshot) -> Long): BudgetHold =
        withContext(Dispatchers.IO) {
            suspendTransaction(db = db) {
                val sql = sql()
                sql.run(
                    "insert into ai_budget_global (day, micros) " +
                        "select ?, coalesce(sum(cost_micros), 0) from ai_usage_daily where day = ? on conflict (day) do nothing",
                    day, day,
                )
                val global = sql.long("select micros from ai_budget_global where day = ? for update", day)
                    ?: error("ai_budget_global sin fila para $day")
                // Sin fila en `users` (cuenta purgada) no hay techo personal que aplicar.
                val user = userId?.let {
                    sql.run(
                        "insert into ai_budget_user (day, user_id, micros) select ?, u.id, " +
                            "coalesce((select sum(a.cost_micros) from ai_usage a where a.user_id = u.id and a.day = ? " +
                            "and a.purpose not in ($EXEMPT_PURPOSES)), 0) " +
                            "from users u where u.id = ? on conflict (day, user_id) do nothing",
                        day, day, it,
                    )
                    sql.long("select micros from ai_budget_user where day = ? and user_id = ? for update", day, it)
                }
                val micros = decide(BudgetSnapshot(global, user))
                val holder = userId.takeIf { user != null }
                adjust(sql, day, holder, micros)
                BudgetHold(day, holder, micros)
            }
        }

    override suspend fun settle(hold: BudgetHold, entry: AiUsageEntry?) {
        withContext(Dispatchers.IO) {
            suspendTransaction(db = db) {
                entry?.let { insertRows(it) }
                adjust(sql(), hold.day, hold.userId, (entry?.costMicros ?: 0L) - hold.micros)
            }
        }
    }

    override suspend fun userSpentMicros(day: LocalDate, userId: UUID, purpose: AiPurpose): Long = withContext(Dispatchers.IO) {
        suspendTransaction(db = db) {
            sql().long(
                "select coalesce(sum(cost_micros), 0) from ai_usage where user_id = ? and day = ? and purpose = ?",
                userId, day, purpose.id,
            ) ?: 0L
        }
    }

    /** Suma [delta] al gasto del día (total y de la persona) si esas filas ya existen; si no, nacerán con la semilla. */
    private fun adjust(sql: Connection, day: LocalDate, userId: UUID?, delta: Long) {
        if (delta == 0L) return
        sql.run("update ai_budget_global set micros = micros + ? where day = ?", delta, day)
        if (userId != null) sql.run("update ai_budget_user set micros = micros + ? where day = ? and user_id = ?", delta, day, userId)
    }

    private fun insertRows(entry: AiUsageEntry) {
        AiUsage.insert {
            it[day] = entry.day
            it[purpose] = entry.purpose.id
            it[provider] = entry.provider.id
            it[model] = entry.model
            it[userId] = entry.userId
            it[tokensIn] = entry.tokensIn
            it[tokensOut] = entry.tokensOut
            it[costMicros] = entry.costMicros
            it[createdAt] = entry.createdAt
        }
        AiUsageDaily.upsert(
            onUpdate = {
                it[AiUsageDaily.tokensIn] = AiUsageDaily.tokensIn + entry.tokensIn.toLong()
                it[AiUsageDaily.tokensOut] = AiUsageDaily.tokensOut + entry.tokensOut.toLong()
                it[AiUsageDaily.costMicros] = AiUsageDaily.costMicros + entry.costMicros
            },
        ) {
            it[day] = entry.day
            it[provider] = entry.provider.id
            it[model] = entry.model
            it[tokensIn] = entry.tokensIn.toLong()
            it[tokensOut] = entry.tokensOut.toLong()
            it[costMicros] = entry.costMicros
        }
    }

    override suspend fun spentMicros(day: LocalDate): Long = withContext(Dispatchers.IO) {
        suspendTransaction(db = db) {
            val total = AiUsageDaily.costMicros.sum()
            AiUsageDaily.select(total).where { AiUsageDaily.day eq day }.firstOrNull()?.get(total) ?: 0L
        }
    }

    private fun JdbcTransaction.sql(): Connection = connection.connection as Connection

    private companion object {
        /** Propósitos que no cuentan para el techo personal (semilla de `ai_budget_user`). Ids fijos del enum, sin entrada externa. */
        val EXEMPT_PURPOSES = AiPurpose.entries.filterNot { it.chargesUserBudget }.joinToString { "'${it.id}'" }.ifEmpty { "''" }
    }

    private fun Connection.run(statement: String, vararg args: Any) {
        prepareStatement(statement).use { st ->
            args.forEachIndexed { i, arg -> st.setObject(i + 1, arg) }
            st.executeUpdate()
        }
    }

    private fun Connection.long(query: String, vararg args: Any): Long? = prepareStatement(query).use { st ->
        args.forEachIndexed { i, arg -> st.setObject(i + 1, arg) }
        st.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else null }
    }
}

/** Implementación en memoria para `ENV=local` sin base de datos y para los tests. Las reservas se serializan con un bloqueo. */
class InMemoryAiUsageStore : AiUsageStore {
    private val lock = Any()
    private val rows = mutableListOf<AiUsageEntry>()
    private val extraSpent = mutableMapOf<LocalDate, Long>()
    private val extraUserSpent = mutableMapOf<Pair<LocalDate, UUID>, Long>()
    private val heldGlobal = mutableMapOf<LocalDate, Long>()
    private val heldUser = mutableMapOf<Pair<LocalDate, UUID>, Long>()

    val entries: List<AiUsageEntry> get() = synchronized(lock) { rows.toList() }

    override suspend fun record(entry: AiUsageEntry) {
        synchronized(lock) { rows += entry }
    }

    override suspend fun spentMicros(day: LocalDate): Long = synchronized(lock) { spent(day) }

    override suspend fun hold(day: LocalDate, userId: UUID?, decide: (BudgetSnapshot) -> Long): BudgetHold = synchronized(lock) {
        val global = spent(day) + (heldGlobal[day] ?: 0L)
        val user = userId?.let { userSpent(day, it) + (heldUser[day to it] ?: 0L) }
        val micros = decide(BudgetSnapshot(global, user))
        heldGlobal.merge(day, micros, Long::plus)
        userId?.let { heldUser.merge(day to it, micros, Long::plus) }
        BudgetHold(day, userId, micros)
    }

    override suspend fun settle(hold: BudgetHold, entry: AiUsageEntry?) {
        synchronized(lock) {
            entry?.let { rows += it }
            heldGlobal.merge(hold.day, -hold.micros, Long::plus)
            hold.userId?.let { heldUser.merge(hold.day to it, -hold.micros, Long::plus) }
        }
    }

    override suspend fun userSpentMicros(day: LocalDate, userId: UUID, purpose: AiPurpose): Long = synchronized(lock) {
        rows.filter { it.day == day && it.userId == userId && it.purpose == purpose }.sumOf { it.costMicros }
    }

    /** Micro-USD reservados ahora mismo por llamadas en curso (tests de la reserva). */
    fun heldMicros(day: LocalDate): Long = synchronized(lock) { heldGlobal[day] ?: 0L }

    /** Gasto registrado de [userId] en [day] que cuenta para su techo, sin reservas. */
    fun userSpentMicros(day: LocalDate, userId: UUID): Long = synchronized(lock) { userSpent(day, userId) }

    /** Simula gasto previo del día (tests del guard). */
    fun seedSpent(day: LocalDate, micros: Long) {
        synchronized(lock) { extraSpent[day] = micros }
    }

    /** Simula gasto previo de una persona en el día (tests del techo por persona); cuenta también en el total. */
    fun seedUserSpent(day: LocalDate, userId: UUID, micros: Long) {
        synchronized(lock) {
            extraUserSpent[day to userId] = micros
            extraSpent.merge(day, micros, Long::plus)
        }
    }

    private fun spent(day: LocalDate): Long = rows.filter { it.day == day }.sumOf { it.costMicros } + (extraSpent[day] ?: 0L)

    private fun userSpent(day: LocalDate, userId: UUID): Long =
        rows.filter { it.day == day && it.userId == userId && it.purpose.chargesUserBudget }.sumOf { it.costMicros } +
            (extraUserSpent[day to userId] ?: 0L)
}
