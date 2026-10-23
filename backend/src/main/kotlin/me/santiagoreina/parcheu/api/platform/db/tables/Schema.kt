package me.santiagoreina.parcheu.api.platform.db.tables

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.json.JsonBColumnType

/** Registro de todas las tablas de `V1__esquema.sql` y de las migraciones fechadas, por dominio. Lo usan los tests de esquema. */
object Schema {
    val identity: List<Table> = listOf(Universities, Interests, Users, UserModes, Consents, FcmTokens)
    val twin: List<Table> = listOf(Questions, TwinAnswers, Twins, TwinVersions, TwinAccuracy, TwinMemories, TwinFeedback, TwinLearningCursor)
    val meet: List<Table> = listOf(Scenarios, MatchQueue, Simulations, Matches, ReportUnlocks, ConnectionDecisions)
    val graph: List<Table> = listOf(Edges, Communities, CommunityMembers)
    val chat: List<Table> = listOf(Chats, ChatMembers, ChatMessages)
    val social: List<Table> = listOf(Invites, InviteRedemptions, Introductions, IntroRequests)
    val notifications: List<Table> = listOf(Notifications, UsageEvents)
    val safety: List<Table> = listOf(Blocks, Reports)
    val billing: List<Table> = listOf(Entitlements, PurchaseEvents)
    val ai: List<Table> = listOf(AiUsage, AiUsageDaily)

    /** Tablas base (sin vistas). */
    val tables: List<Table> = identity + twin + meet + graph + chat + social + notifications + safety + billing + ai

    /** Vistas de solo lectura. */
    val views: List<Table> = listOf(EdgesView)

    /** Todas las columnas jsonb de las tablas base. */
    fun jsonbColumns(): List<Column<*>> = tables.flatMap { it.columns }.filter { it.columnType is JsonBColumnType<*> }
}
