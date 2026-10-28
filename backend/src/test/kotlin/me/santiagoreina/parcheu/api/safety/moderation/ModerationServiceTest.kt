package me.santiagoreina.parcheu.api.safety.moderation

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.TEST_LEGAL_VERSION
import me.santiagoreina.parcheu.api.ai.FakeLlmClient
import me.santiagoreina.parcheu.api.ai.LlmInvalidOutput
import me.santiagoreina.parcheu.api.ai.testGateway
import me.santiagoreina.parcheu.api.chat.InMemoryChatMirror
import me.santiagoreina.parcheu.api.chat.InMemoryChatRepository
import me.santiagoreina.parcheu.api.chat.inMemoryChatModule
import me.santiagoreina.parcheu.api.graph.PersonRow
import me.santiagoreina.parcheu.api.graph.inMemoryGraphModule
import me.santiagoreina.parcheu.api.identity.InMemoryIdentityRepository
import me.santiagoreina.parcheu.api.platform.auth.FakeOidcVerifier
import me.santiagoreina.parcheu.api.platform.tasks.FakeTaskQueue
import me.santiagoreina.parcheu.api.safety.InMemorySafetyRepository
import me.santiagoreina.parcheu.api.safety.inMemorySafetyModule
import me.santiagoreina.parcheu.api.startApp
import me.santiagoreina.parcheu.api.testContainer
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.ReportReason
import me.santiagoreina.parcheu.contract.ReportSource
import me.santiagoreina.parcheu.contract.SendMessage
import me.santiagoreina.parcheu.contract.Visibility
import org.slf4j.LoggerFactory
import java.util.UUID

/** Criterios de E8.6 con FakeLlm: severidad alta, autolesión, severidad baja y fail-open. */
class ModerationServiceTest : FunSpec({
    fun finding(category: String, severity: String): JsonObject = buildJsonObject {
        put("findings", buildJsonArray { add(buildJsonObject { put("category", category); put("severity", severity) }) })
    }

    class Fixture {
        val identityRepo = InMemoryIdentityRepository().apply { listOf("ana", "beto").forEach { seedAcceptedUser(it, TEST_LEGAL_VERSION) } }
        val ana = identityRepo.user("ana")
        val beto = identityRepo.user("beto")
        val chatRepo = InMemoryChatRepository().apply {
            addUser(row(ana.id, "Ana"), "ana")
            addUser(row(beto.id, "Beto"), "beto")
        }
        val mirror = InMemoryChatMirror()
        val graph = inMemoryGraphModule(FIXED_CLOCK)
        val chat = inMemoryChatModule(FIXED_CLOCK, graph.policy, repository = chatRepo, mirror = mirror)
        val safetyRepo = InMemorySafetyRepository(
            people = { chatRepo.person(it) },
            messages = { id -> chatRepo.allMessages.filter { it.chatId == id }.map { me.santiagoreina.parcheu.api.safety.SnapshotMessage(it.id, it.senderId, it.text, it.createdAt, it.hidden) } },
        )
        val safety = inMemorySafetyModule(FIXED_CLOCK, chat, graph, repo = safetyRepo)
        val llm = FakeLlmClient()
        val queue = FakeTaskQueue()
        val moderation = moderationService(testGateway(llm), chat, safety, queue, FIXED_CLOCK).also { it.attachTo(chat) }
        val room = runBlocking { chat.service.createDirect(ana.id, beto.id, Mode.AMISTAD) }

        /** Beto escribe [text]; devuelve el id del mensaje guardado. */
        fun send(text: String): UUID = runBlocking {
            chat.service.send(beto, room.id, SendMessage(UUID.randomUUID().toString(), text))
            chatRepo.allMessages.last { it.senderId == beto.id }.id
        }

        private fun row(id: UUID, name: String) = PersonRow(id, name, null, "UIS", Visibility.ANONYMOUS, isDemo = false, deletedAt = null)
    }

    test("enviar un mensaje encola moderate en ai-light sin llamar al LLM") {
        val f = Fixture()
        val id = f.send("hola Ana")
        f.queue.tasks shouldHaveSize 1
        f.queue.tasks.single().apply {
            queue.name shouldBe "AI_LIGHT"
            path shouldBe "/tasks/moderate"
            body shouldContain id.toString()
        }
        f.llm.calls shouldBe 0
    }

    test("severidad alta oculta el mensaje en Postgres y Firestore y crea un reporte AUTO") {
        val f = Fixture()
        f.llm.jsonResponder = { _, _ -> finding("HARASSMENT", "HIGH") }
        val id = f.send("mensaje agresivo")
        runBlocking { f.moderation.moderate(id) } shouldBe ModerationOutcome.ACTED
        f.chatRepo.allMessages.single { it.id == id }.hidden shouldBe true
        f.mirror.messages(f.room.id).single { it.id == id }.apply { hidden shouldBe true; text shouldBe "" }
        val report = f.safetyRepo.allReports.single()
        report.source shouldBe ReportSource.AUTO
        report.reporterId shouldBe null
        report.targetId shouldBe f.beto.id
        report.messageId shouldBe id
        report.reason shouldBe ReportReason.HARASSMENT
        report.snapshot!!.single { it.id == id }.hidden shouldBe true
    }

    test("reintentar la tarea no duplica el reporte") {
        val f = Fixture()
        f.llm.jsonResponder = { _, _ -> finding("SEXUAL", "HIGH") }
        val id = f.send("algo explícito")
        runBlocking { f.moderation.moderate(id) } shouldBe ModerationOutcome.ACTED
        runBlocking { f.moderation.moderate(id) } shouldBe ModerationOutcome.SKIPPED
        f.safetyRepo.allReports shouldHaveSize 1
    }

    test("autolesión inserta el mensaje de recursos de ayuda, sin ocultar ni reportar, y es idempotente") {
        val f = Fixture()
        f.llm.jsonResponder = { _, _ -> finding("SELF_HARM", "HIGH") }
        val id = f.send("ya no quiero seguir")
        runBlocking { f.moderation.moderate(id) } shouldBe ModerationOutcome.ACTED
        runBlocking { f.moderation.moderate(id) } shouldBe ModerationOutcome.ACTED
        val system = f.chatRepo.allMessages.filter { it.senderId == null }
        system shouldHaveSize 1
        system.single().text.apply { shouldContain("192"); shouldContain("106"); shouldContain("123") }
        f.chatRepo.allMessages.single { it.id == id }.hidden shouldBe false
        f.mirror.messages(f.room.id).single { it.system }.hidden shouldBe false
        f.safetyRepo.allReports.shouldBeEmpty()
    }

    test("posible menor crea un reporte AUTO de prioridad alta y el mensaje sigue visible") {
        val f = Fixture()
        f.llm.jsonResponder = { _, _ -> finding("POSSIBLE_MINOR", "HIGH") }
        val id = f.send("tengo 15 años")
        runBlocking { f.moderation.moderate(id) } shouldBe ModerationOutcome.ACTED
        f.chatRepo.allMessages.single { it.id == id }.hidden shouldBe false
        f.safetyRepo.allReports.single().apply {
            reason shouldBe ReportReason.UNDERAGE
            priority shouldBe 2
            source shouldBe ReportSource.AUTO
        }
    }

    test("severidad baja o sin hallazgos no hace nada") {
        val f = Fixture()
        f.llm.jsonResponder = { _, _ -> finding("HARASSMENT", "LOW") }
        val id = f.send("qué pesado eres")
        runBlocking { f.moderation.moderate(id) } shouldBe ModerationOutcome.CLEAN
        f.llm.jsonResponder = { _, _ -> buildJsonObject { put("findings", JsonArray(emptyList())) } }
        val other = f.send("buenas")
        runBlocking { f.moderation.moderate(other) } shouldBe ModerationOutcome.CLEAN
        f.chatRepo.allMessages.none { it.hidden || it.senderId == null } shouldBe true
        f.safetyRepo.allReports.shouldBeEmpty()
    }

    test("error del LLM deja el mensaje visible y registra moderation_failed") {
        val f = Fixture()
        f.llm.jsonResponder = { _, _ -> throw LlmInvalidOutput("el modelo respondió basura") }
        val id = f.send("texto privado del chat")
        val logger = LoggerFactory.getLogger(ModerationService::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            runBlocking { f.moderation.moderate(id) } shouldBe ModerationOutcome.FAILED
        } finally {
            logger.detachAppender(appender)
        }
        f.chatRepo.allMessages.single { it.id == id }.hidden shouldBe false
        f.safetyRepo.allReports.shouldBeEmpty()
        val event = appender.list.single { it.formattedMessage == "moderation_failed" }
        event.keyValuePairs.any { it.key == "event" && it.value == "moderation_failed" } shouldBe true
        event.formattedMessage shouldNotContain "texto privado"
        event.keyValuePairs.none { it.value.toString().contains("texto privado") } shouldBe true
    }

    test("un fallo al encolar no rompe el envío del mensaje") {
        val f = Fixture()
        f.queue.failWith = IllegalStateException("cola caída")
        val id = f.send("hola")
        f.chatRepo.allMessages.single { it.id == id }.hidden shouldBe false
    }

    test("POST /internal/tasks/moderate exige OIDC y responde 204 aunque el LLM falle") {
        val f = Fixture()
        f.llm.jsonResponder = { _, _ -> throw LlmInvalidOutput("roto") }
        val id = f.send("hola")
        testApplication {
            val client = startApp(testContainer(chat = f.chat, graph = f.graph, safety = f.safety, moderation = f.moderation, ai = testGateway(f.llm)))
            val body = """{"id":"$id"}"""
            client.post("/internal/tasks/moderate") { contentType(ContentType.Application.Json); setBody(body) }.status shouldBe HttpStatusCode.Forbidden
            client.post("/internal/tasks/moderate") {
                header(HttpHeaders.Authorization, "Bearer ${FakeOidcVerifier.VALID_TOKEN}")
                contentType(ContentType.Application.Json)
                setBody(body)
            }.status shouldBe HttpStatusCode.NoContent
        }
    }
})
