package me.santiagoreina.parcheu.api.simulation.report

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.ai.testGateway
import me.santiagoreina.parcheu.api.chat.ChatModule
import me.santiagoreina.parcheu.api.chat.ExposedChatRepository
import me.santiagoreina.parcheu.api.chat.InMemoryChatMirror
import me.santiagoreina.parcheu.api.graph.AnonIdSigner
import me.santiagoreina.parcheu.api.graph.ExposedGraphRepository
import me.santiagoreina.parcheu.api.graph.GraphModule
import me.santiagoreina.parcheu.api.platform.config.Env
import me.santiagoreina.parcheu.api.platform.db.TestDatabase
import me.santiagoreina.parcheu.api.platform.db.connectExposed
import me.santiagoreina.parcheu.api.platform.db.runMigrations
import me.santiagoreina.parcheu.api.platform.db.tables.ChatMembers
import me.santiagoreina.parcheu.api.platform.db.tables.Chats
import me.santiagoreina.parcheu.api.platform.db.tables.ConnectionDecisions
import me.santiagoreina.parcheu.api.platform.db.tables.Edges
import me.santiagoreina.parcheu.api.platform.db.tables.ReportUnlocks
import me.santiagoreina.parcheu.api.platform.db.tables.Twins
import me.santiagoreina.parcheu.api.platform.db.tables.UsageEvents
import me.santiagoreina.parcheu.api.platform.db.tables.UserModes
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.api.platform.errors.QuotaExceeded
import me.santiagoreina.parcheu.api.platform.notifier.FakeNotifier
import me.santiagoreina.parcheu.api.platform.tasks.FakeTaskQueue
import me.santiagoreina.parcheu.api.quota.quotaModule
import me.santiagoreina.parcheu.api.simulation.NoopSimulationNotifier
import me.santiagoreina.parcheu.api.simulation.judgeJson
import me.santiagoreina.parcheu.api.simulation.simulationModule
import me.santiagoreina.parcheu.api.ai.FakeLlmClient
import me.santiagoreina.parcheu.api.twin.TaskPayload
import me.santiagoreina.parcheu.api.simulation.createRandom
import me.santiagoreina.parcheu.contract.ChatType
import me.santiagoreina.parcheu.contract.ContractJson
import me.santiagoreina.parcheu.contract.Decision
import me.santiagoreina.parcheu.contract.DecisionStatus
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.QuotaKind
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Informe y decisión mutua contra Postgres 16 real. Necesita Docker o `PARCHEU_TEST_DB_URL`: con
 * `-PsinContenedores` se omite (ver [TestDatabase]).
 */
class ReportDbTest : FunSpec({
    val now = OffsetDateTime.parse("2026-10-20T10:00:00-05:00")

    fun Database.user(name: String): UUID = transaction(this) {
        val id = Users.insert {
            it[firebaseUid] = "uid-$name"
            it[displayName] = name
            it[ageVerifiedAt] = now
            it[parejaConfirmedAt] = now
            it[createdAt] = now
            it[updatedAt] = now
        }[Users.id]
        Mode.entries.forEach { m ->
            UserModes.insert {
                it[userId] = id
                it[mode] = m
                it[enabled] = true
                it[enabledAt] = now
            }
        }
        Twins.insert {
            it[userId] = id
            it[traits] = listOf(0.2f, 0.4f, 0.6f, 0.8f, 0.5f, 0.5f)
            it[personaSummary] = "Persona de $name"
            it[personaReady] = true
            it[version] = 1
            it[updatedAt] = now
        }
        id
    }

    class Db(val db: Database) {
        val ai = testGateway(FakeLlmClient(textResponder = { "Dale, de una." }, jsonResponder = { _, _ -> judgeJson(70) }))
        val quota = quotaModule(Env.DEV, db, FIXED_CLOCK)
        val graph = GraphModule(ExposedGraphRepository(db), AnonIdSigner.forTests(), FIXED_CLOCK)
        val push = FakeNotifier()
        val chat = ChatModule(ExposedChatRepository(db), InMemoryChatMirror(), push, graph.policy, FIXED_CLOCK)
        val queue = FakeTaskQueue()
        val module = simulationModule(Env.DEV, db, FIXED_CLOCK, ai, quota, graph, queue, firebaseProjectId = null,
            notifier = NoopSimulationNotifier, chat = chat, push = push,
        )
            .also { m -> queue.inline = { task -> m.engine.run(ContractJson.decodeFromString<TaskPayload>(task.body).uuid()) } }

        fun done(a: UUID, b: UUID): String = runBlocking { module.service.createRandom(a, b).id.toString() }
    }

    fun withDb(block: (Database) -> Unit) = TestDatabase.withCleanDatabase { ds ->
        runMigrations(ds)
        block(connectExposed(ds))
    }

    test("Postgres: dos decisiones simultáneas connect=true crean exactamente un chat y una arista CONNECTED").config(enabled = TestDatabase.enabled) {
        withDb { db ->
            val w = Db(db)
            repeat(5) { round ->
                val a = db.user("a$round")
                val b = db.user("b$round")
                val id = w.done(a, b)
                val results = runBlocking(Dispatchers.IO) {
                    listOf(a, b).map { user -> async { w.module.report.decide(user, id, Decision(connect = true)) } }.awaitAll()
                }
                results.map { it.status } shouldContainExactlyInAnyOrder listOf(DecisionStatus.WAITING, DecisionStatus.CONNECTED)
                transaction(db) {
                    val chats = Chats.innerJoin(ChatMembers).selectAll()
                        .where { ChatMembers.userId eq a }
                        .map { it[Chats.id] to it[Chats.type] }
                    chats shouldBe listOf(UUID.fromString(results.single { it.chatId != null }.chatId) to ChatType.DIRECT)
                    val edges = Edges.selectAll().where { (Edges.userLow inList listOf(a, b)) and (Edges.userHigh inList listOf(a, b)) }.map { it[Edges.state] }
                    edges shouldBe listOf(EdgeState.CONNECTED)
                    ConnectionDecisions.selectAll().where { ConnectionDecisions.simulationId eq UUID.fromString(id) }.count() shouldBe 2L
                }
            }
            w.push.sent.size shouldBe 10
        }
    }

    test("Postgres: el desbloqueo queda en report_unlocks con un solo FULL_REPORT; el segundo del mes → QUOTA_EXCEEDED").config(enabled = TestDatabase.enabled) {
        withDb { db ->
            val w = Db(db)
            val ana = db.user("ana")
            val first = w.done(ana, db.user("beto"))
            val second = w.done(ana, db.user("caro"))
            runBlocking {
                w.module.report.unlock(ana, first).locked shouldBe false
                w.module.report.unlock(ana, first).locked shouldBe false
                w.module.report.report(ana, first).reasons!!.size shouldBe 3
                shouldThrow<QuotaExceeded> { w.module.report.unlock(ana, second) }
                w.module.report.report(ana, second).locked shouldBe true
            }
            transaction(db) {
                ReportUnlocks.selectAll().count() shouldBe 1L
                UsageEvents.selectAll().where { UsageEvents.kind eq QuotaKind.FULL_REPORT }.count() shouldBe 1L
            }
        }
    }
})
