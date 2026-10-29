package me.santiagoreina.parcheu.api.safety.moderation

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.assertions.throwables.shouldThrow
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
import me.santiagoreina.parcheu.api.chat.ChatMirror
import me.santiagoreina.parcheu.api.chat.InMemoryChatMirror
import me.santiagoreina.parcheu.api.chat.MessageDoc
import me.santiagoreina.parcheu.api.platform.notifier.FakeNotifier
import me.santiagoreina.parcheu.api.platform.notifier.Notification
import me.santiagoreina.parcheu.api.platform.notifier.Notifier
import me.santiagoreina.parcheu.api.safety.NewReport
import me.santiagoreina.parcheu.api.safety.SafetyRepository
import me.santiagoreina.parcheu.api.chat.ChatRepository
import me.santiagoreina.parcheu.api.chat.MessageRecord
import me.santiagoreina.parcheu.api.chat.InMemoryChatRepository
import me.santiagoreina.parcheu.api.chat.inMemoryChatModule
import me.santiagoreina.parcheu.api.graph.PersonRow
import me.santiagoreina.parcheu.api.graph.inMemoryGraphModule
import me.santiagoreina.parcheu.api.identity.InMemoryIdentityRepository
import me.santiagoreina.parcheu.api.platform.auth.FakeOidcVerifier
import me.santiagoreina.parcheu.api.platform.tasks.FakeTaskQueue
import me.santiagoreina.parcheu.api.quota.DailyKind
import me.santiagoreina.parcheu.api.quota.DailyLimiter
import me.santiagoreina.parcheu.api.quota.InMemoryDailyCounterRepository
import me.santiagoreina.parcheu.api.quota.InMemoryDevEntitlementService
import me.santiagoreina.parcheu.api.quota.QuotaLimits
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

    /** Espejo que falla una vez al ocultar un mensaje si [failOnHide] está activo. */
    class FlakyMirror(private val delegate: InMemoryChatMirror) : ChatMirror by delegate {
        var failOnHide = false

        override suspend fun upsertMessage(chatId: UUID, doc: MessageDoc) {
            if (failOnHide && doc.hidden) {
                failOnHide = false
                throw IllegalStateException("Firestore caído")
            }
            delegate.upsertMessage(chatId, doc)
        }
    }

    /** Repositorio de seguridad cuyo primer insertReport falla si [failInsert] está activo. */
    class FlakySafety(private val delegate: SafetyRepository) : SafetyRepository by delegate {
        var failInsert = false

        override suspend fun insertReport(report: NewReport) {
            if (failInsert) {
                failInsert = false
                throw IllegalStateException("base caída")
            }
            delegate.insertReport(report)
        }
    }

    /** Notifier que falla en el primer aviso si [failOnce] está activo y después registra como FakeNotifier. */
    class FlakyNotifier(private val delegate: FakeNotifier) : Notifier by delegate {
        var failOnce = false

        override suspend fun notify(userId: UUID, notification: Notification) {
            if (failOnce) {
                failOnce = false
                throw IllegalStateException("Postgres caído")
            }
            delegate.notify(userId, notification)
        }
    }

    /** Repositorio de chat cuyo primer `hideMessage` falla (antes de tocar Postgres) si [failHide] está activo. */
    class FlakyChatRepo(private val delegate: InMemoryChatRepository) : ChatRepository by delegate {
        var failHide = false

        override suspend fun hideMessage(messageId: UUID): MessageRecord? {
            if (failHide) {
                failHide = false
                throw IllegalStateException("Postgres caído")
            }
            return delegate.hideMessage(messageId)
        }
    }

    class Fixture(
        withinDailyCap: (UUID) -> Boolean = { true },
        sampleEvery: Int = ModerationService.DEFAULT_SAMPLE_EVERY,
    ) {
        val identityRepo = InMemoryIdentityRepository().apply { listOf("ana", "beto").forEach { seedAcceptedUser(it, TEST_LEGAL_VERSION) } }
        val ana = identityRepo.user("ana")
        val beto = identityRepo.user("beto")
        val chatRepo = InMemoryChatRepository().apply {
            addUser(row(ana.id, "Ana"), "ana")
            addUser(row(beto.id, "Beto"), "beto")
        }
        val mirror = InMemoryChatMirror()
        val flakyMirror = FlakyMirror(mirror)
        val graph = inMemoryGraphModule(FIXED_CLOCK)
        val flakyChatRepo = FlakyChatRepo(chatRepo)
        val chat = inMemoryChatModule(FIXED_CLOCK, graph.policy, repository = flakyChatRepo, mirror = flakyMirror)
        val safetyRepo = InMemorySafetyRepository(
            people = { chatRepo.person(it) },
            messages = { id -> chatRepo.allMessages.filter { it.chatId == id }.map { me.santiagoreina.parcheu.api.safety.SnapshotMessage(it.id, it.senderId, it.text, it.createdAt, it.hidden) } },
        )
        val safety = inMemorySafetyModule(FIXED_CLOCK, chat, graph, repo = safetyRepo)
        val llm = FakeLlmClient()
        val queue = FakeTaskQueue()
        val flakySafety = FlakySafety(safetyRepo)
        val notifier = FakeNotifier()
        val flakyNotifier = FlakyNotifier(notifier)
        val moderation = ModerationService(
            MessageModerator(testGateway(llm)), chatRepo, chat.service, flakySafety, queue, FIXED_CLOCK, flakyNotifier,
            withinDailyCap, sampleEvery,
        ).also { it.attachTo(chat) }
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

    test("B-M1: pasado el tope diario del autor solo se encola 1 de cada N mensajes, con muestreo determinista por id") {
        val limiter = DailyLimiter(InMemoryDailyCounterRepository(), InMemoryDevEntitlementService(), QuotaLimits(dailyModerations = 3), FIXED_CLOCK)
        val f = Fixture(withinDailyCap = { limiter.tryConsume(it, DailyKind.MODERATION) }, sampleEvery = 4)
        repeat(3) { f.send("hola $it") }
        f.queue.tasks shouldHaveSize 3
        fun ids() = generateSequence { UUID.randomUUID() }
        val sampled = ids().first { Math.floorMod(it.hashCode(), 4) == 0 }
        val skipped = ids().first { Math.floorMod(it.hashCode(), 4) != 0 }
        fun record(id: UUID) = MessageRecord(id, f.room.id, f.beto.id, "otro mensaje", false, false, null, FIXED_CLOCK.instant())
        runBlocking {
            f.moderation.enqueue(record(skipped))
            f.moderation.enqueue(record(sampled))
        }
        f.queue.tasks shouldHaveSize 4
        f.queue.tasks.last().body shouldContain sampled.toString()
        f.llm.calls shouldBe 0
        // El tope es del autor: Ana sigue con el suyo.
        runBlocking { f.moderation.enqueue(record(skipped).copy(id = UUID.randomUUID(), senderId = f.ana.id)) }
        f.queue.tasks shouldHaveSize 5
    }

    test("B-M1: si el contador del tope falla, el mensaje se modera igual (fail-open de la moderación)") {
        val f = Fixture(withinDailyCap = { error("base caída") }, sampleEvery = 1_000_000)
        f.send("hola")
        f.queue.tasks shouldHaveSize 1
    }

    test("MODERATION_SAMPLE_EVERY: 10 por defecto, entero >= 1") {
        moderationSampleEvery(emptyMap()) shouldBe 10
        moderationSampleEvery(mapOf("MODERATION_SAMPLE_EVERY" to "3")) shouldBe 3
        shouldThrow<IllegalStateException> { moderationSampleEvery(mapOf("MODERATION_SAMPLE_EVERY" to "0")) }
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
        // El reporte se inserta antes de ocultar: el snapshot trae el texto original para que el admin lo revise.
        report.snapshot!!.single { it.id == id }.text shouldBe "mensaje agresivo"
    }

    test("reintentar la tarea no duplica el reporte") {
        val f = Fixture()
        f.llm.jsonResponder = { _, _ -> finding("SEXUAL", "HIGH") }
        val id = f.send("algo explícito")
        runBlocking { f.moderation.moderate(id) } shouldBe ModerationOutcome.ACTED
        runBlocking { f.moderation.moderate(id) } shouldBe ModerationOutcome.SKIPPED
        f.safetyRepo.allReports shouldHaveSize 1
    }

    test("autolesión avisa solo al autor con los recursos de ayuda: no entra al chat, ni oculta ni reporta") {
        val f = Fixture()
        f.llm.jsonResponder = { _, _ -> finding("SELF_HARM", "HIGH") }
        val id = f.send("ya no quiero seguir")
        runBlocking { f.moderation.moderate(id) } shouldBe ModerationOutcome.ACTED
        f.notifier.sent shouldHaveSize 1
        f.notifier.sent.single().apply {
            userId shouldBe f.beto.id
            notification.body.apply { shouldContain("192"); shouldContain("106"); shouldContain("123") }
        }
        f.chatRepo.allMessages.none { it.senderId == null } shouldBe true
        f.mirror.messages(f.room.id).none { it.system } shouldBe true
        f.chatRepo.allMessages.single { it.id == id }.hidden shouldBe false
        f.safetyRepo.allReports.shouldBeEmpty()
    }

    test("amenaza y autolesión con el aviso fallando una vez: el reintento entrega la ayuda") {
        val f = Fixture()
        f.llm.jsonResponder = { _, _ ->
            buildJsonObject {
                put("findings", buildJsonArray {
                    add(buildJsonObject { put("category", "THREAT"); put("severity", "HIGH") })
                    add(buildJsonObject { put("category", "SELF_HARM"); put("severity", "HIGH") })
                })
            }
        }
        val id = f.send("me mato y te mato")
        f.flakyNotifier.failOnce = true
        runCatching { runBlocking { f.moderation.moderate(id) } }.isFailure shouldBe true
        f.notifier.sent.shouldBeEmpty()
        // El fallo de la ayuda no frena el reporte ni el ocultado: el contenido dañino ya no queda visible.
        f.chatRepo.allMessages.single { it.id == id }.hidden shouldBe true
        f.safetyRepo.allReports shouldHaveSize 1
        // El reintento no llama al LLM: ve el reporte con la marca de ayuda y la entrega.
        f.llm.jsonResponder = { _, _ -> error("no debe volver a llamar al LLM") }
        runBlocking { f.moderation.moderate(id) } shouldBe ModerationOutcome.SKIPPED
        f.notifier.sent shouldHaveSize 1
        f.notifier.sent.single().userId shouldBe f.beto.id
        f.chatRepo.allMessages.single { it.id == id }.hidden shouldBe true
        f.safetyRepo.allReports shouldHaveSize 1
    }

    test("solo autolesión con el aviso fallando: encola el reintento sin LLM y la ayuda llega") {
        val f = Fixture()
        f.llm.jsonResponder = { _, _ -> finding("SELF_HARM", "HIGH") }
        val id = f.send("ya no quiero seguir")
        f.flakyNotifier.failOnce = true
        runBlocking { f.moderation.moderate(id) } shouldBe ModerationOutcome.ACTED
        f.notifier.sent.shouldBeEmpty()
        f.queue.tasks.single { it.path == ModerationService.HELP_TASK_PATH }.name shouldBe "moderate-help-$id"
        f.llm.jsonResponder = { _, _ -> error("no debe volver a llamar al LLM") }
        runBlocking { f.moderation.deliverHelp(id) }
        f.notifier.sent.single().userId shouldBe f.beto.id
        f.safetyRepo.allReports.shouldBeEmpty()
    }

    test("solo autolesión con el aviso y la cola fallando: se relanza el error") {
        val f = Fixture()
        f.llm.jsonResponder = { _, _ -> finding("SELF_HARM", "HIGH") }
        val id = f.send("ya no quiero seguir")
        f.flakyNotifier.failOnce = true
        val before = f.queue.tasks.size
        f.queue.failWith = IllegalStateException("cola caída")
        runCatching { runBlocking { f.moderation.moderate(id) } }.isFailure shouldBe true
        f.queue.tasks.size shouldBe before
    }

    test("reintento tras fallar insertReport: el mensaje no queda oculto sin reporte") {
        val f = Fixture()
        f.llm.jsonResponder = { _, _ -> finding("THREAT", "HIGH") }
        val id = f.send("te voy a hacer daño")
        f.flakySafety.failInsert = true
        runCatching { runBlocking { f.moderation.moderate(id) } }.isFailure shouldBe true
        // El reporte va antes de ocultar: el fallo no deja el mensaje oculto sin reporte.
        f.chatRepo.allMessages.single { it.id == id }.hidden shouldBe false
        runBlocking { f.moderation.moderate(id) } shouldBe ModerationOutcome.ACTED
        f.chatRepo.allMessages.single { it.id == id }.hidden shouldBe true
        f.safetyRepo.allReports shouldHaveSize 1
    }

    test("reintento tras fallar el espejo: Firestore termina oculto y el reporte no se duplica") {
        val f = Fixture()
        f.llm.jsonResponder = { _, _ -> finding("THREAT", "HIGH") }
        val id = f.send("te voy a hacer daño")
        f.flakyMirror.failOnHide = true
        runCatching { runBlocking { f.moderation.moderate(id) } }.isFailure shouldBe true
        // Postgres ya quedó oculto y el reporte existe, pero Firestore sigue mostrando el texto.
        f.chatRepo.allMessages.single { it.id == id }.hidden shouldBe true
        f.mirror.messages(f.room.id).single { it.id == id }.hidden shouldBe false
        f.safetyRepo.allReports shouldHaveSize 1
        runBlocking { f.moderation.moderate(id) } shouldBe ModerationOutcome.SKIPPED
        f.mirror.messages(f.room.id).single { it.id == id }.apply { hidden shouldBe true; text shouldBe "" }
        f.safetyRepo.allReports shouldHaveSize 1
    }

    test("reintento tras fallar ocultar en Postgres: se decide sin el LLM y el mensaje termina oculto") {
        val f = Fixture()
        f.llm.jsonResponder = { _, _ -> finding("THREAT", "HIGH") }
        val id = f.send("te voy a hacer daño")
        f.flakyChatRepo.failHide = true
        runCatching { runBlocking { f.moderation.moderate(id) } }.isFailure shouldBe true
        f.chatRepo.allMessages.single { it.id == id }.hidden shouldBe false
        f.safetyRepo.allReports shouldHaveSize 1
        // Aunque ahora el LLM falle (fail-open) o no encuentre nada, el reporte AUTO manda: el mensaje se oculta.
        f.llm.jsonResponder = { _, _ -> finding("THREAT", "LOW") }
        runBlocking { f.moderation.moderate(id) } shouldBe ModerationOutcome.SKIPPED
        f.chatRepo.allMessages.single { it.id == id }.hidden shouldBe true
        f.safetyRepo.allReports shouldHaveSize 1
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
