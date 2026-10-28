package me.santiagoreina.parcheu.api.safety.moderation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.ai.FakeLlmClient
import me.santiagoreina.parcheu.api.ai.testGateway
import me.santiagoreina.parcheu.api.chat.ChatModule
import me.santiagoreina.parcheu.api.chat.ExposedChatRepository
import me.santiagoreina.parcheu.api.chat.InMemoryChatMirror
import me.santiagoreina.parcheu.api.graph.AnonIdSigner
import me.santiagoreina.parcheu.api.graph.ExposedGraphRepository
import me.santiagoreina.parcheu.api.graph.GraphModule
import me.santiagoreina.parcheu.api.identity.ExposedIdentityRepository
import me.santiagoreina.parcheu.api.platform.db.TestDatabase
import me.santiagoreina.parcheu.api.platform.db.connectExposed
import me.santiagoreina.parcheu.api.platform.db.runMigrations
import me.santiagoreina.parcheu.api.platform.db.tables.ChatMessages
import me.santiagoreina.parcheu.api.platform.db.tables.Reports
import me.santiagoreina.parcheu.api.platform.notifier.FakeNotifier
import me.santiagoreina.parcheu.api.platform.notifier.NoopNotifier
import me.santiagoreina.parcheu.api.platform.tasks.FakeTaskQueue
import me.santiagoreina.parcheu.api.safety.ExposedSafetyRepository
import me.santiagoreina.parcheu.api.safety.SafetyModule
import me.santiagoreina.parcheu.api.safety.SafetyService
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.ReportSource
import me.santiagoreina.parcheu.contract.SendMessage
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/** E8.6 contra Postgres 16 real: ocultar, reporte AUTO sin `reporter_id` y ayuda de autolesión solo al autor. */
class ModerationDbTest : FunSpec({
    fun json(category: String, severity: String) = buildJsonObject {
        put("findings", buildJsonArray { add(buildJsonObject { put("category", category); put("severity", severity) }) })
    }

    fun withDb(block: suspend (ModerationService, ChatModule, org.jetbrains.exposed.v1.jdbc.Database, FakeLlmClient, FakeNotifier) -> Unit) =
        TestDatabase.withCleanDatabase { ds ->
            runMigrations(ds)
            val db = connectExposed(ds)
            val graph = GraphModule(ExposedGraphRepository(db), AnonIdSigner.forTests(), FIXED_CLOCK)
            val chat = ChatModule(ExposedChatRepository(db), InMemoryChatMirror(), NoopNotifier, graph.policy, FIXED_CLOCK)
            val safetyRepo = ExposedSafetyRepository(db)
            val safety = SafetyModule(
                safetyRepo,
                SafetyService(safetyRepo, chat.service, chat.repository, graph.repository, graph.policy, graph.anonIds, null, FIXED_CLOCK),
            )
            val llm = FakeLlmClient()
            val notifier = FakeNotifier()
            val moderation = moderationService(testGateway(llm), chat, safety, FakeTaskQueue(), FIXED_CLOCK, notifier)
            runBlocking { block(moderation, chat, db, llm, notifier) }
        }

    suspend fun twoUsers(db: org.jetbrains.exposed.v1.jdbc.Database) = ExposedIdentityRepository(db).let { identity ->
        identity.createIfAbsent("uid-a-${UUID.randomUUID()}", null, "Ana", FIXED_CLOCK.instant()) to
            identity.createIfAbsent("uid-b-${UUID.randomUUID()}", null, "Beto", FIXED_CLOCK.instant())
    }

    test("severidad alta oculta el mensaje y guarda un reporte AUTO sin reporter_id").config(enabled = TestDatabase.enabled) {
        withDb { moderation, chat, db, llm, notifier ->
            val (ana, beto) = twoUsers(db)
            val room = chat.service.createDirect(ana.id, beto.id, Mode.AMISTAD)
            chat.service.send(beto, room.id, SendMessage("b-1", "mensaje agresivo"))
            val id = transaction(db) { ChatMessages.selectAll().single()[ChatMessages.id] }
            llm.jsonResponder = { _, _ -> json("THREAT", "HIGH") }

            moderation.moderate(id) shouldBe ModerationOutcome.ACTED
            moderation.moderate(id) shouldBe ModerationOutcome.SKIPPED

            transaction(db) { ChatMessages.selectAll().single()[ChatMessages.hidden] } shouldBe true
            val reports = transaction(db) { Reports.selectAll().toList() }
            reports shouldHaveSize 1
            reports.single().apply {
                get(Reports.reportSource) shouldBe ReportSource.AUTO
                get(Reports.reporterId) shouldBe null
                get(Reports.targetId) shouldBe beto.id
                get(Reports.messageId) shouldBe id
            }
        }
    }

    test("autolesión avisa solo al autor y no agrega mensajes al chat").config(enabled = TestDatabase.enabled) {
        withDb { moderation, chat, db, llm, notifier ->
            val (ana, beto) = twoUsers(db)
            val room = chat.service.createDirect(ana.id, beto.id, Mode.AMISTAD)
            chat.service.send(beto, room.id, SendMessage("b-1", "ya no quiero seguir"))
            val id = transaction(db) { ChatMessages.selectAll().single()[ChatMessages.id] }
            llm.jsonResponder = { _, _ -> json("SELF_HARM", "HIGH") }

            moderation.moderate(id)

            notifier.sent.single().userId shouldBe beto.id
            transaction(db) { ChatMessages.selectAll().count() } shouldBe 1L
            transaction(db) { Reports.selectAll().count() } shouldBe 0L
        }
    }
})
