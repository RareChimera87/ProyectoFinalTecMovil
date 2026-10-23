package me.santiagoreina.parcheu.api.demo

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.TEST_LEGAL_VERSION
import me.santiagoreina.parcheu.api.chat.InMemoryChatMirror
import me.santiagoreina.parcheu.api.chat.InMemoryChatRepository
import me.santiagoreina.parcheu.api.chat.inMemoryChatModule
import me.santiagoreina.parcheu.api.graph.PersonRow
import me.santiagoreina.parcheu.api.identity.InMemoryIdentityRepository
import me.santiagoreina.parcheu.api.matching.MatchFixture
import me.santiagoreina.parcheu.api.matching.MatchingFlags
import me.santiagoreina.parcheu.api.matching.MatchingRules
import me.santiagoreina.parcheu.api.matching.QueueEntry
import me.santiagoreina.parcheu.api.platform.auth.FakeTokenVerifier
import me.santiagoreina.parcheu.api.platform.notifier.FakeNotifier
import me.santiagoreina.parcheu.api.simulation.Participant
import me.santiagoreina.parcheu.api.simulation.SimFixture
import me.santiagoreina.parcheu.api.simulation.TwinProfile
import me.santiagoreina.parcheu.api.simulation.inMemorySimulationModule
import me.santiagoreina.parcheu.api.startApp
import me.santiagoreina.parcheu.api.testContainer
import me.santiagoreina.parcheu.contract.ApiError
import me.santiagoreina.parcheu.contract.ChatSummary
import me.santiagoreina.parcheu.contract.Decision
import me.santiagoreina.parcheu.contract.DecisionResult
import me.santiagoreina.parcheu.contract.DecisionStatus
import me.santiagoreina.parcheu.contract.ErrorCode
import me.santiagoreina.parcheu.contract.GraphDto
import me.santiagoreina.parcheu.contract.MatchDto
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.SendMessage
import me.santiagoreina.parcheu.contract.StartSimulation
import me.santiagoreina.parcheu.contract.TicketStatus
import me.santiagoreina.parcheu.contract.Visibility

/**
 * E4.9 de punta a punta en memoria: los DTO que ve un tester traen `isDemo=true` cuando la otra persona es un
 * demo (`MatchDto`, `NodeDto`, `ChatSummary`), un demo nunca escribe (`POST /v1/chats/{id}/messages` → 403) y
 * el demo decide conectar según la probabilidad configurada (ADR-0027).
 */
class DemoApiTest : FunSpec({
    fun HttpRequestBuilder.auth(uid: String) = header(HttpHeaders.Authorization, "Bearer ${FakeTokenVerifier.tokenFor(uid)}")

    /** Ana (tester) y `demo-01`, con gemelos listos, grafo, chat y simulación en memoria. */
    class World(probability: Double) {
        val demoUid = DemoProfiles.all.first().firebaseUid
        val identity = InMemoryIdentityRepository().also { repo ->
            listOf("ana", demoUid).forEach { repo.seedAcceptedUser(it, TEST_LEGAL_VERSION) }
            repo.replace(repo.user(demoUid).copy(isDemo = true, displayName = DemoProfiles.all.first().name))
        }
        val ana = identity.user("ana").id
        val demo = identity.user(demoUid).id
        val sim = SimFixture()
        val chatRepo = InMemoryChatRepository()
        val mirror = InMemoryChatMirror()
        val push = FakeNotifier()
        val chat = inMemoryChatModule(FIXED_CLOCK, sim.graph.policy, push, chatRepo, mirror)
        val module = inMemorySimulationModule(
            FIXED_CLOCK, sim.ai, sim.quota, sim.graph, sim.repo, sim.queue, sim.mirror, sim.notifier,
            chat = chat, push = push, demo = DemoConnectPolicy(probability),
        )

        init {
            sim.person("ana", id = ana)
            sim.repo.putParticipant(Participant(demo, demoUid, DemoProfiles.MODES, true, false, isDemo = true, deleted = false))
            sim.repo.putTwin(TwinProfile(demo, 1, List(6) { 0.5 }, DemoProfiles.all.first().persona, personaReady = true))
            sim.graphRepo.addPerson(PersonRow(demo, DemoProfiles.all.first().name, null, "UIS", Visibility.ANONYMOUS, true, null))
            chatRepo.addUser(PersonRow(ana, "ana", null, "UIS", Visibility.ANONYMOUS, false, null), "ana")
            chatRepo.addUser(PersonRow(demo, DemoProfiles.all.first().name, null, "UIS", Visibility.ANONYMOUS, true, null), demoUid)
        }

        /** Simulación DONE (el motor corre en línea) pedida por Ana con el demo. */
        fun simulation(): String = runBlocking { module.service.start(ana, StartSimulation(demo.toString(), Mode.AMISTAD)).id }

        fun container() = testContainer(
            repo = identity, quota = sim.quota, ai = sim.ai, graph = sim.graph, chat = chat, simulation = module, notifier = push,
        )
    }

    suspend fun HttpClient.decide(uid: String, id: String, connect: Boolean) = post("/v1/simulations/$id/decision") {
        auth(uid)
        contentType(ContentType.Application.Json)
        setBody(Decision(connect))
    }

    test("MatchDto de un demo trae isDemo=true") {
        val f = MatchFixture(flags = MatchingFlags(demoUsersEnabled = true, scenarioModeEnabled = false))
        val demo = f.person("demo-01", modes = DemoProfiles.MODES, isDemo = true)
        val ana = f.person("ana")
        runBlocking {
            // Ticket sembrado como lo hace seed-demo (prioridad -1).
            f.matchRepo.enqueueAndPair(
                QueueEntry(demo, Mode.AMISTAD, DemoSeeder.DEMO_PRIORITY.toInt(), f.clock.now.minusSeconds(30), f.clock.now.plus(MatchingRules.TICKET_TTL)),
                true, f.clock.now,
            )
            val ticket = f.service.enqueue(ana, Mode.AMISTAD)
            ticket.status shouldBe TicketStatus.MATCHED
            val match: MatchDto = f.service.match(ana, ticket.matchId.shouldNotBeNull())
            match.other.isDemo shouldBe true
            // Y al revés no aplica: el demo no usa la app, pero su vista de Ana no la marca como demo.
            f.service.match(demo, ticket.matchId!!).other.isDemo shouldBe false
        }
    }

    test("con probabilidad 1 el demo conecta: NodeDto y ChatSummary traen isDemo=true y el demo no puede escribir") {
        val w = World(probability = 1.0)
        val id = w.simulation()
        testApplication {
            val client = startApp(w.container())
            val result = client.decide("ana", id, connect = true).body<DecisionResult>()
            result.status shouldBe DecisionStatus.CONNECTED
            val chatId = result.chatId.shouldNotBeNull()

            val graph = client.get("/v1/graph") { auth("ana") }.body<GraphDto>()
            graph.nodes.single().isDemo shouldBe true
            graph.me.isDemo shouldBe false

            val chats = client.get("/v1/chats") { auth("ana") }.body<List<ChatSummary>>()
            chats.single().other.isDemo shouldBe true

            // Un demo nunca genera POST /v1/chats/{id}/messages: si alguien lo intenta con su identidad, 403.
            val attempt = client.post("/v1/chats/$chatId/messages") {
                auth(w.demoUid)
                contentType(ContentType.Application.Json)
                setBody(SendMessage("demo-1", "hola"))
            }
            attempt.status shouldBe HttpStatusCode.Forbidden
            attempt.body<ApiError>().code shouldBe ErrorCode.FORBIDDEN
            // Los mensajes del tester sí se guardan con las reglas normales.
            client.post("/v1/chats/$chatId/messages") {
                auth("ana")
                contentType(ContentType.Application.Json)
                setBody(SendMessage("ana-1", "hola, demo"))
            }.status shouldBe HttpStatusCode.OK
        }
        w.chatRepo.allMessages.filter { it.senderId == w.demo }.shouldBeEmpty()
        w.chatRepo.allMessages.single().senderId shouldBe w.ana
        w.mirror.messages(w.chatRepo.allMessages.single().chatId).none { it.senderUid == w.demoUid } shouldBe true
    }

    test("con probabilidad 0 el demo dice 'Ahora no': el tester ve WAITING y no se crea chat") {
        val w = World(probability = 0.0)
        val id = w.simulation()
        testApplication {
            val client = startApp(w.container())
            client.decide("ana", id, connect = true).body<DecisionResult>().status shouldBe DecisionStatus.WAITING
            client.get("/v1/chats") { auth("ana") }.body<List<ChatSummary>>().shouldBeEmpty()
        }
    }
})
