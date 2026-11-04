package me.santiagoreina.parcheu.api.platform.db.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

/* Uso de IA por llamada y agregado diario para `DailyBudgetGuard` (ADR-0010, §7.3). */

object AiUsage : Table("ai_usage") {
    val id = long("id").autoIncrement()
    /** Día en America/Bogota. */
    val day = date("day")
    val purpose = text("purpose")
    val provider = text("provider")
    val model = text("model")
    val userId = userRef("user_id", ReferenceOption.SET_NULL).nullable()
    val tokensIn = integer("tokens_in").default(0)
    val tokensOut = integer("tokens_out").default(0)
    val costMicros = long("cost_micros").default(0)
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object AiUsageDaily : Table("ai_usage_daily") {
    val day = date("day")
    val provider = text("provider")
    val model = text("model")
    val tokensIn = long("tokens_in").default(0)
    val tokensOut = long("tokens_out").default(0)
    val costMicros = long("cost_micros").default(0)
    override val primaryKey = PrimaryKey(day, provider, model)
}

/**
 * Gasto del día (registrado + reservas en curso) que `DailyBudgetGuard` bloquea con `FOR UPDATE` antes de cada
 * llamada (B-M1). La fila nace con el total de `ai_usage_daily`.
 */
object AiBudgetGlobal : Table("ai_budget_global") {
    val day = date("day")
    val micros = long("micros").default(0)
    override val primaryKey = PrimaryKey(day)
}

/** Lo mismo por persona: techo diario `AI_USER_DAILY_BUDGET_USD` (B-M1). La fila nace con su gasto de `ai_usage`. */
object AiBudgetUser : Table("ai_budget_user") {
    val day = date("day")
    val userId = userRef("user_id", ReferenceOption.CASCADE)
    val micros = long("micros").default(0)
    override val primaryKey = PrimaryKey(day, userId)
}
