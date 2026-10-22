package me.santiagoreina.parcheu.api.simulation.report

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContainAnyOf
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.TASKS_ACCOUNT
import me.santiagoreina.parcheu.api.TEST_LEGAL_VERSION
import me.santiagoreina.parcheu.api.chat.ChatDoc
import me.santiagoreina.parcheu.api.chat.ChatMirror
import me.santiagoreina.parcheu.api.chat.ChatRepository
import me.santiagoreina.parcheu.api.chat.CreatedChat
import me.santiagoreina.parcheu.api.chat.InMemoryChatRepository
import me.santiagoreina.parcheu.api.chat.MessageDoc
import me.santiagoreina.parcheu.api.chat.inMemoryChatModule
import me.santiagoreina.parcheu.api.graph.PersonRow
import me.santiagoreina.parcheu.api.identity.IdentityService
import me.santiagoreina.parcheu.api.identity.InMemoryIdentityRepository
import me.santiagoreina.parcheu.api.platform.AppContainer
import me.santiagoreina.parcheu.api.platform.allDomains
import me.santiagoreina.parcheu.api.platform.auth.FakeOidcVerifier
import me.santiagoreina.parcheu.api.platform.auth.FakeTokenVerifier
import me.santiagoreina.parcheu.api.platform.notifier.FakeNotifier
import me.santiagoreina.parcheu.api.simulation.SimFixture
import me.santiagoreina.parcheu.api.simulation.inMemorySimulationModule
import me.santiagoreina.parcheu.api.startApp
import me.santiagoreina.parcheu.api.testConfig
import me.santiagoreina.parcheu.api.twin.inMemoryTwinModule
import me.santiagoreina.parcheu.contract.ApiError
import me.santiagoreina.parcheu.contract.ContractJson
import me.santiagoreina.parcheu.contract.Decision
import me.santiagoreina.parcheu.contract.DecisionResult
import me.santiagoreina.parcheu.contract.DecisionStatus
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.ErrorCode
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.NotificationKinds
import me.santiagoreina.parcheu.contract.QuotaKind
import me.santiagoreina.parcheu.contract.ReportDto
import me.santiagoreina.parcheu.contract.StartSimulation
import me.santiagoreina.parcheu.contract.TranscriptDto
import me.santiagoreina.parcheu.contract.Visibility
import java.util.UUID

/** Informe, desbloqueo y decisión mutua (backlog E4.6) por HTTP con todo en memoria. */
class ReportApiTest : FunSpec({
    val uids = listOf("ana", "beto", "caro")

    class FlakyMirror(var failing: Boolean = false) : ChatMirror {
        override suspend fun upsertChat(doc: ChatDoc) { if (failing) error("Firestore no disponible") }
        override suspend fun upsertMessage(chatId: UUID, doc: MessageDoc) { if (failing) error("Firestore no disponible") }
    }

    class World {
        val mirror = FlakyMirror()
        var failCreate = false
        val identity = InMemoryIdentityRepository().also { repo -> uids.forEach { repo.seedAcceptedUser(it, TEST_LEGAL_VERSION) } }
        val sim = SimFixture()
        val ids = uids.associateWith { uid -> sim.person(uid, id = identity.user(uid).id) }
        val chatRepo = InMemoryChatRepository().also { repo ->
            ids.forEach { (uid, id) -> repo.addUser(PersonRow(id, uid, null, "UIS", Visibility.ANONYMOUS, false, null), uid) }
        }
        val push = FakeNotifier()
        private val flakyRepo = object : ChatRepository by chatRepo {
            override suspend fun getOrCreateDirect(a: UUID, b: UUID, mode: Mode, now: java.time.Instant): CreatedChat {
                if (failCreate) error("Postgres no disponible")
                return chatRepo.getOrCreateDirect(a, b, mode, now)
            }
        }
        val chat = inMemoryChatModule(FIXED_CLOCK, sim.graph.policy, push, flakyRepo, mirror)
        val module = inMemorySimulationModule(
            FIXED_CLOCK, sim.ai, sim.quota, sim.graph, sim.repo, sim.queue, sim.mirror, sim.notifier,
            chat = chat, push = push,
        )

        fun id(uid: String) = ids.getValue(uid)

        /** Simulación DONE (el motor corre en línea) pedida por [a] con [b]. */
        fun done(a: String, b: String, mode: Mode = Mode.AMISTAD): String =
            runBlocking { module.service.start(id(a), StartSimulation(id(b).toString(), mode)).id }

        fun container(): AppContainer {
            val identityService = IdentityService(identity, FIXED_CLOCK, TEST_LEGAL_VERSION)
            val twin = inMemoryTwinModule(FIXED_CLOCK, sim.ai)
            return AppContainer(
                testConfig(), FIXED_CLOCK, FakeTokenVerifier(), FakeOidcVerifier(TASKS_ACCOUNT), identityService,
                ai = sim.ai, twin = twin, quota = sim.quota, graph = sim.graph, notifier = push, chat = chat, simulation = module,
                domains = allDomains(identityService, twin, sim.quota, sim.graph, module, chat),
            )
        }
    }

    fun HttpRequestBuilder.auth(uid: String) = header(HttpHeaders.Authorization, "Bearer ${FakeTokenVerifier.tokenFor(uid)}")

    fun ApplicationTestBuilder.app(w: World): HttpClient = startApp(w.container())

    suspend fun HttpClient.report(uid: String, id: String) = get("/v1/simulations/$id/report") { auth(uid) }

    suspend fun HttpClient.unlock(uid: String, id: String) = post("/v1/simulations/$id/report:unlock") { auth(uid) }

    suspend fun HttpClient.decide(uid: String, id: String, connect: Boolean) = post("/v1/simulations/$id/decision") {
        auth(uid)
        contentType(ContentType.Application.Json)
        setBody(Decision(connect))
    }

    suspend fun HttpResponse.error() = body<ApiError>()

    test("la respuesta gratis no contiene reasons, frictions, dimensions ni transcript: solo puntaje, resumen y conteos") {
        val w = World()
        val id = w.done("ana", "beto")
        testApplication {
            val client = app(w)
            val response = client.report("ana", id)
            response.status shouldBe HttpStatusCode.OK
            val keys = ContractJson.parseToJsonElement(response.bodyAsText()).jsonObject.keys
            keys shouldNotContainAnyOf listOf("reasons", "frictions", "dimensions", "transcript")
            val dto = ContractJson.decodeFromString(ReportDto.serializer(), response.bodyAsText())
            dto.locked shouldBe true
            dto.unlockAvailable shouldBe true
            dto.summary shouldBe "Encajan bien."
            dto.reasonsCount shouldBe 3
            dto.frictionsCount shouldBe 2
            dto.otherName shouldBe null
            dto.myDecision shouldBe null
            dto.otherDecided shouldBe false
            // La transcripción tampoco: sin Plus ni desbloqueo es 402.
            client.get("/v1/simulations/$id/transcript") { auth("ana") }.error().code shouldBe ErrorCode.PLUS_REQUIRED
        }
    }

    test("el primer unlock del mes funciona y abre la transcripción; el segundo → 402 QUOTA_EXCEEDED(kind=FULL_REPORT)") {
        val w = World()
        val first = w.done("ana", "beto")
        val second = w.done("ana", "caro")
        testApplication {
            val client = app(w)
            val unlocked = client.unlock("ana", first)
            unlocked.status shouldBe HttpStatusCode.OK
            val full = unlocked.body<ReportDto>()
            full.locked shouldBe false
            full.reasons shouldBe listOf("Planean parecido", "Se escuchan", "Mismo humor")
            full.frictions shouldBe listOf("Plata", "Horarios")
            full.dimensions!!.isEmpty() shouldBe false
            // Repetir el unlock de la misma simulación no cobra otra vez.
            client.unlock("ana", first).status shouldBe HttpStatusCode.OK
            w.sim.usage.charges.filter { it.startsWith("report:") } shouldHaveSize 1
            client.report("ana", first).body<ReportDto>().locked shouldBe false
            client.get("/v1/simulations/$first/transcript") { auth("ana") }.body<TranscriptDto>().scenarios shouldHaveSize 3
            // El desbloqueo es personal: beto sigue viendo el informe gratis.
            client.report("beto", first).body<ReportDto>().locked shouldBe true

            client.report("ana", second).body<ReportDto>().unlockAvailable shouldBe false
            val again = client.unlock("ana", second)
            again.status shouldBe HttpStatusCode.PaymentRequired
            val error = again.error()
            error.code shouldBe ErrorCode.QUOTA_EXCEEDED
            error.kind shouldBe QuotaKind.FULL_REPORT.name
            error.resetsAt shouldNotBe null
        }
    }

    test("ParcheU+ ve el informe completo sin gastar el informe mensual") {
        val w = World()
        val id = w.done("ana", "beto")
        w.sim.plus(w.id("ana"))
        testApplication {
            val client = app(w)
            val dto = client.report("ana", id).body<ReportDto>()
            dto.locked shouldBe false
            dto.unlockAvailable shouldBe false
            dto.reasons!! shouldHaveSize 3
            client.unlock("ana", id).status shouldBe HttpStatusCode.OK
            w.sim.usage.charges.filter { it.startsWith("report:") }.shouldBeEmpty()
        }
    }

    test("ambos conectan: arista CONNECTED, chat DIRECT, aviso a los dos y el nombre se revela solo entonces") {
        val w = World()
        val id = w.done("ana", "beto")
        testApplication {
            val client = app(w)
            client.decide("ana", id, connect = true).body<DecisionResult>() shouldBe DecisionResult(DecisionStatus.WAITING)
            w.sim.graphRepo.edge(w.id("ana"), w.id("beto"), Mode.AMISTAD)!!.state shouldBe EdgeState.PENDING
            val waiting = client.report("ana", id).body<ReportDto>()
            waiting.myDecision shouldBe true
            waiting.otherName shouldBe null
            waiting.chatId shouldBe null
            client.report("beto", id).body<ReportDto>().otherDecided shouldBe false
            w.push.sent.shouldBeEmpty()

            val result = client.decide("beto", id, connect = true).body<DecisionResult>()
            result.status shouldBe DecisionStatus.CONNECTED
            result.chatId shouldNotBe null
            w.sim.graphRepo.edge(w.id("ana"), w.id("beto"), Mode.AMISTAD)!!.state shouldBe EdgeState.CONNECTED
            w.push.sent.map { it.userId } shouldContainExactlyInAnyOrder listOf(w.id("ana"), w.id("beto"))
            w.push.sent.map { it.notification.kind }.toSet() shouldBe setOf(NotificationKinds.NEW_CONNECTION)
            w.push.sent.forEach { it.notification.route shouldBe "parcheu://chat/${result.chatId}" }

            val connected = client.report("ana", id).body<ReportDto>()
            connected.otherName shouldBe "beto"
            connected.chatId shouldBe result.chatId
            // Decidir otra vez → 409.
            val repeated = client.decide("ana", id, connect = false)
            repeated.status shouldBe HttpStatusCode.Conflict
            repeated.error().code shouldBe ErrorCode.CONFLICT
        }
    }

    test("'Ahora no' no genera notificación (ni antes ni después de que la otra persona conecte)") {
        val w = World()
        val first = w.done("ana", "beto")
        val second = w.done("ana", "caro")
        testApplication {
            val client = app(w)
            client.decide("ana", first, connect = false).body<DecisionResult>().status shouldBe DecisionStatus.DECLINED
            // Quien conecta después de un "Ahora no" no se entera: ve WAITING, en la decisión y en el informe.
            client.decide("beto", first, connect = true).body<DecisionResult>().status shouldBe DecisionStatus.WAITING
            val afterDecline = client.report("beto", first).body<ReportDto>()
            afterDecline.otherDecided shouldBe false
            afterDecline.myDecision shouldBe true
            afterDecline.chatId shouldBe null

            client.decide("caro", second, connect = true).body<DecisionResult>().status shouldBe DecisionStatus.WAITING
            client.decide("ana", second, connect = false).body<DecisionResult>().status shouldBe DecisionStatus.DECLINED
            // Caro conectó primero y Ana rechazó después: al reabrir sigue sin saberlo.
            val reopened = client.report("caro", second).body<ReportDto>()
            reopened.otherDecided shouldBe false
            reopened.chatId shouldBe null
            // La arista PENDING vuelve a SIMULATED.
            w.sim.graphRepo.edge(w.id("ana"), w.id("caro"), Mode.AMISTAD)!!.state shouldBe EdgeState.SIMULATED

            w.push.sent.shouldBeEmpty()
            w.chatRepo.chatsOf(w.id("ana")).shouldBeEmpty()
            client.report("caro", second).body<ReportDto>().otherName shouldBe null
        }
    }

    test("GET /report no escribe: con conexión mutua solo lee el chat y sobrevive a un Firestore caído") {
        val w = World()
        val id = w.done("ana", "beto")
        testApplication {
            val client = app(w)
            client.decide("ana", id, connect = true)
            val chatId = client.decide("beto", id, connect = true).body<DecisionResult>().chatId
            w.mirror.failing = true
            val dto = client.report("ana", id)
            dto.status shouldBe HttpStatusCode.OK
            dto.body<ReportDto>().chatId shouldBe chatId
            // Si el chat no existiera, leer no lo crea.
            w.failCreate = true
            client.report("beto", id).status shouldBe HttpStatusCode.OK
        }
    }

    test("si Firestore falla al conectar, la conexión se completa igual y los avisos salen") {
        val w = World()
        val id = w.done("ana", "beto")
        w.mirror.failing = true
        testApplication {
            val client = app(w)
            client.decide("ana", id, connect = true)
            val result = client.decide("beto", id, connect = true)
            result.status shouldBe HttpStatusCode.OK
            result.body<DecisionResult>().status shouldBe DecisionStatus.CONNECTED
            w.push.sent shouldHaveSize 2
        }
    }

    test("si la conexión falla antes de crear el chat, repetir la decisión la repara en vez de dar 409") {
        val w = World()
        val id = w.done("ana", "beto")
        testApplication {
            val client = app(w)
            client.decide("ana", id, connect = true)
            w.failCreate = true
            client.decide("beto", id, connect = true).status shouldBe HttpStatusCode.InternalServerError
            w.push.sent.shouldBeEmpty()
            w.failCreate = false
            val retry = client.decide("beto", id, connect = true)
            retry.status shouldBe HttpStatusCode.OK
            val result = retry.body<DecisionResult>()
            result.status shouldBe DecisionStatus.CONNECTED
            w.push.sent shouldHaveSize 2
            // Ya reparado, una tercera vez sí es 409 y no reenvía avisos.
            client.decide("beto", id, connect = true).status shouldBe HttpStatusCode.Conflict
            w.push.sent shouldHaveSize 2
            w.sim.graphRepo.edge(w.id("ana"), w.id("beto"), Mode.AMISTAD)!!.state shouldBe EdgeState.CONNECTED
        }
    }

    test("concurrencia: dos decisiones simultáneas connect=true crean exactamente un chat y una arista CONNECTED") {
        repeat(25) {
            val w = World()
            val id = w.done("ana", "beto")
            val results = withContext(Dispatchers.Default) {
                listOf("ana", "beto").map { uid ->
                    async { w.module.report.decide(w.id(uid), id, Decision(connect = true)) }
                }.awaitAll()
            }
            results.map { it.status } shouldContainExactlyInAnyOrder listOf(DecisionStatus.WAITING, DecisionStatus.CONNECTED)
            w.chatRepo.chatsOf(w.id("ana")) shouldHaveSize 1
            w.chatRepo.chatsOf(w.id("beto")).single().chat.id.toString() shouldBe results.single { it.chatId != null }.chatId
            w.sim.graphRepo.edge(w.id("ana"), w.id("beto"), Mode.AMISTAD)!!.state shouldBe EdgeState.CONNECTED
            w.push.sent shouldHaveSize 2
        }
    }

    test("un tercero o una simulación inexistente dan 404; con bloqueo la decisión da 403 BLOCKED") {
        val w = World()
        val id = w.done("ana", "beto")
        testApplication {
            val client = app(w)
            client.report("caro", id).status shouldBe HttpStatusCode.NotFound
            client.report("ana", UUID.randomUUID().toString()).status shouldBe HttpStatusCode.NotFound
            client.report("ana", "no-es-uuid").status shouldBe HttpStatusCode.NotFound
            client.decide("caro", id, connect = true).status shouldBe HttpStatusCode.NotFound

            w.sim.repo.block(w.id("beto"), w.id("ana"))
            val blocked = client.decide("ana", id, connect = true)
            blocked.status shouldBe HttpStatusCode.Forbidden
            blocked.error().code shouldBe ErrorCode.BLOCKED
        }
    }
})
