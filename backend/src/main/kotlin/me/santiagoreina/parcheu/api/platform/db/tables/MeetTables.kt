package me.santiagoreina.parcheu.api.platform.db.tables

import me.santiagoreina.parcheu.contract.MatchChoice
import me.santiagoreina.parcheu.contract.MatchStatus
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.SimulationOrigin
import me.santiagoreina.parcheu.contract.SimulationStatus
import me.santiagoreina.parcheu.contract.TicketStatus
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

/* Conocer: escenarios, fila, matches, simulaciones y decisiones de conexión (§4, §7.2-7.6). */

object Scenarios : Table("scenarios") {
    val id = short("id")
    val mode = enumText<Mode>("mode")
    val slug = text("slug")
    val title = text("title")
    val prompt = text("prompt")
    val isBasic = bool("is_basic")
    val timerSec = integer("timer_sec")
    val budgetCop = integer("budget_cop")
    /** `[{name, weight, description}]`: dimensiones del juez. */
    val rubric = jsonbColumn("rubric")
    override val primaryKey = PrimaryKey(id)
}

object MatchQueue : Table("match_queue") {
    val userId = userRef("user_id", ReferenceOption.CASCADE)
    val mode = enumText<Mode>("mode")
    val priority = short("priority").default(0)
    val enqueuedAt = timestampWithTimeZone("enqueued_at")
    val expiresAt = timestampWithTimeZone("expires_at")
    /** Último ticket de la persona (E4.2): WAITING mientras espera, MATCHED con [matchId] o EXPIRED. */
    val status = enumText<TicketStatus>("status").default(TicketStatus.WAITING)
    val matchId = javaUUID("match_id").nullable()
    /** Lo asigna un trigger de Postgres en cada insert o update (E4.2): ordena las publicaciones al espejo. */
    val version = long("version").default(0)
    override val primaryKey = PrimaryKey(userId)
}

object Simulations : Table("simulations") {
    val id = uuidPk()
    val mode = enumText<Mode>("mode")
    val userA = userRef("user_a")
    val userB = userRef("user_b")
    val requestedBy = userRef("requested_by").nullable()
    val origin = enumText<SimulationOrigin>("origin")
    val status = enumText<SimulationStatus>("status").default(SimulationStatus.QUEUED)
    val progress = jsonbColumn("progress").nullable()
    val score = short("score").nullable()
    val report = jsonbColumn("report").nullable()
    val transcript = jsonbColumn("transcript").nullable()
    val twinVersionA = integer("twin_version_a").nullable()
    val twinVersionB = integer("twin_version_b").nullable()
    val cachedFrom = javaUUID("cached_from").references(id).nullable()
    val provider = text("provider").nullable()
    val model = text("model").nullable()
    val tokensIn = integer("tokens_in").nullable()
    val tokensOut = integer("tokens_out").nullable()
    val costMicros = long("cost_micros").nullable()
    val error = text("error").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val finishedAt = timestampWithTimeZone("finished_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object Matches : Table("matches") {
    val id = uuidPk()
    val mode = enumText<Mode>("mode")
    val userA = userRef("user_a", ReferenceOption.CASCADE)
    val userB = userRef("user_b", ReferenceOption.CASCADE)
    val choiceA = enumText<MatchChoice>("choice_a").nullable()
    val choiceB = enumText<MatchChoice>("choice_b").nullable()
    val status = enumText<MatchStatus>("status").default(MatchStatus.PENDING_CHOICE)
    val simulationId = javaUUID("simulation_id").references(Simulations.id).nullable()
    val chatId = javaUUID("chat_id").references(Chats.id).nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val decideBy = timestampWithTimeZone("decide_by")
    override val primaryKey = PrimaryKey(id)
}

object ReportUnlocks : Table("report_unlocks") {
    val userId = userRef("user_id", ReferenceOption.CASCADE)
    val simulationId = javaUUID("simulation_id").references(Simulations.id)
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(userId, simulationId)
}

object ConnectionDecisions : Table("connection_decisions") {
    val simulationId = javaUUID("simulation_id").references(Simulations.id)
    val userId = userRef("user_id", ReferenceOption.CASCADE)
    val connect = bool("connect")
    val decidedAt = timestampWithTimeZone("decided_at")
    override val primaryKey = PrimaryKey(simulationId, userId)
}
