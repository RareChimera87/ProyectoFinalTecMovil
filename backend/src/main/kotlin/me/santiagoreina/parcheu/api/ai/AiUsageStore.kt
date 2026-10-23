package me.santiagoreina.parcheu.api.ai

import me.santiagoreina.parcheu.api.platform.db.tables.AiUsage
import me.santiagoreina.parcheu.api.platform.db.tables.AiUsageDaily
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.upsert
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

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

/** Registro de uso de IA y gasto del día (ADR-0010). */
interface AiUsageStore {
    /** Guarda la llamada en `ai_usage` y la suma en `ai_usage_daily` en la misma transacción. */
    suspend fun record(entry: AiUsageEntry)

    /** Gasto total del día (todas las combinaciones proveedor-modelo), en micro-USD. */
    suspend fun spentMicros(day: LocalDate): Long
}

/**
 * Implementación sobre Postgres (tablas de E0.4). [database] se resuelve en la primera llamada
 * para que el arranque del servicio no dependa de que Cloud SQL responda.
 */
class ExposedAiUsageStore(database: () -> Database) : AiUsageStore {
    constructor(db: Database) : this({ db })

    private val db by lazy(database)

    override suspend fun record(entry: AiUsageEntry) {
        withContext(Dispatchers.IO) {
            suspendTransaction(db = db) { insertRows(entry) }
        }
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
}

/** Implementación en memoria para `ENV=local` sin base de datos y para los tests. */
class InMemoryAiUsageStore : AiUsageStore {
    private val rows = java.util.Collections.synchronizedList(mutableListOf<AiUsageEntry>())
    private val extraSpent = ConcurrentHashMap<LocalDate, Long>()

    val entries: List<AiUsageEntry> get() = synchronized(rows) { rows.toList() }

    override suspend fun record(entry: AiUsageEntry) {
        rows += entry
    }

    override suspend fun spentMicros(day: LocalDate): Long =
        entries.filter { it.day == day }.sumOf { it.costMicros } + (extraSpent[day] ?: 0L)

    /** Simula gasto previo del día (tests del guard). */
    fun seedSpent(day: LocalDate, micros: Long) {
        extraSpent[day] = micros
    }
}
