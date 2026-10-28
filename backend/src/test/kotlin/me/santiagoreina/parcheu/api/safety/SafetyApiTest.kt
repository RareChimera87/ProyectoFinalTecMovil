package me.santiagoreina.parcheu.api.safety

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.santiagoreina.parcheu.api.TEST_LEGAL_VERSION
import me.santiagoreina.parcheu.api.chat.ChatRecord
import me.santiagoreina.parcheu.api.chat.InMemoryChatRepository
import me.santiagoreina.parcheu.api.chat.inMemoryChatModule
import me.santiagoreina.parcheu.api.identity.InMemoryIdentityRepository
import me.santiagoreina.parcheu.api.matching.MatchFixture
import me.santiagoreina.parcheu.api.platform.auth.FakeTokenVerifier
import me.santiagoreina.parcheu.api.platform.errors.QuotaExceeded
import me.santiagoreina.parcheu.api.safety.AnonymousContacts
import me.santiagoreina.parcheu.api.simulation.Side
import me.santiagoreina.parcheu.api.social.introductions.InMemoryIntroductionRepository
import me.santiagoreina.parcheu.api.social.introductions.IntroductionRecord
import me.santiagoreina.parcheu.api.social.invites.InMemoryInviteRepository
import me.santiagoreina.parcheu.contract.IntroductionStatus
import me.santiagoreina.parcheu.contract.SimulationOrigin
import me.santiagoreina.parcheu.api.simulation.SimulationRecord
import me.santiagoreina.parcheu.contract.SimulationStatus
import me.santiagoreina.parcheu.api.simulation.StoredScenario
import me.santiagoreina.parcheu.api.simulation.StoredTranscript
import me.santiagoreina.parcheu.api.simulation.StoredTurn
import me.santiagoreina.parcheu.api.simulation.UsageTotals
import me.santiagoreina.parcheu.api.simulation.initialProgress
import me.santiagoreina.parcheu.api.startApp
import me.santiagoreina.parcheu.api.testContainer
import me.santiagoreina.parcheu.contract.ApiError
import me.santiagoreina.parcheu.contract.BlockedDto
import me.santiagoreina.parcheu.contract.ChatStatus
import me.santiagoreina.parcheu.contract.ChatSummary
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.Enqueue
import me.santiagoreina.parcheu.contract.ErrorCode
import me.santiagoreina.parcheu.contract.GraphDto
import me.santiagoreina.parcheu.contract.MatchDto
import me.santiagoreina.parcheu.contract.MatchStatus
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Mute
import me.santiagoreina.parcheu.contract.QuotaKind
import me.santiagoreina.parcheu.contract.ReportCreated
import me.santiagoreina.parcheu.contract.ReportKind
import me.santiagoreina.parcheu.contract.ReportReason
import me.santiagoreina.parcheu.contract.ReportRequest
import me.santiagoreina.parcheu.contract.SendMessage
import me.santiagoreina.parcheu.contract.TicketDto
import me.santiagoreina.parcheu.contract.TicketStatus
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.UUID

/** Criterios de E8.1 de punta a punta: rutas, servicio y los dominios en memoria que lee el bloqueo. */
class SafetyApiTest : FunSpec({
    val uids = listOf("ana", "beto", "caro")

    class World {
        val identity = InMemoryIdentityRepository().also { repo -> uids.forEach { repo.seedAcceptedUser(it, TEST_LEGAL_VERSION) } }
        val f = MatchFixture(idFor = { name -> identity.user(name).id }).also { f -> uids.forEach { f.person(it) } }
        val ana: UUID = identity.user("ana").id
        val beto: UUID = identity.user("beto").id
        val caro: UUID = identity.user("caro").id
        val chatRepo = InMemoryChatRepository().also { repo ->
            uids.forEach { uid -> repo.addUser(f.graphRepo.people.getValue(identity.user(uid).id), uid) }
        }
        val chat = inMemoryChatModule(f.clock, f.graph.policy, f.notifier, chatRepo)
        val safetyRepo = InMemorySafetyRepository(
            people = { id -> f.graphRepo.people[id] },
            messages = { chatId ->
                chatRepo.allMessages.filter { it.chatId == chatId }.map { SnapshotMessage(it.id, it.senderId, it.text, it.createdAt, it.hidden) }
            },
            simulations = { id -> f.simRepo.find(id)?.let { it.userA to it.userB } },
            turns = { id -> f.simRepo.find(id)?.transcript?.scenarios?.flatMap { sc -> sc.turns.map { it.text } }.orEmpty() },
        )
        val invites = InMemoryInviteRepository()
        val introductions = InMemoryIntroductionRepository()
        val safety = inMemorySafetyModule(
            f.clock, chat, f.graph, f.module, safetyRepo,
            contacts = listOf(AnonymousContacts.pendingInvites(invites), AnonymousContacts.introductions(introductions)),
        )

        /** Simulación entre [a] y [b] con dos escenarios (2 + 1 turnos): el turno 2 es el único del segundo. */
        fun simulation(a: UUID = ana, b: UUID = beto): UUID = runBlocking {
            val id = UUID.randomUUID()
            f.simRepo.create(
                SimulationRecord(
                    id = id, mode = Mode.AMISTAD, userA = a, userB = b, requestedBy = a, origin = SimulationOrigin.PARCHE,
                    status = SimulationStatus.DONE, progress = initialProgress(), score = 70, report = null,
                    transcript = StoredTranscript(
                        listOf(
                            StoredScenario(1, "Cafe", listOf(StoredTurn(Side.A, "Hola, que tal"), StoredTurn(Side.B, "Todo bien"))),
                            StoredScenario(2, "Estudio", listOf(StoredTurn(Side.B, "turno ofensivo de la IA"))),
                        ),
                    ),
                    twinVersionA = 1, twinVersionB = 1, cachedFrom = null, provider = null, usage = UsageTotals(), error = null,
                    createdAt = f.clock.instant(), finishedAt = f.clock.instant(),
                ),
            )
            id
        }

        fun direct(a: UUID = ana, b: UUID = beto): ChatRecord = runBlocking { chat.service.createDirect(a, b, Mode.AMISTAD) }
    }

    fun HttpRequestBuilder.auth(uid: String) = header(HttpHeaders.Authorization, "Bearer ${FakeTokenVerifier.tokenFor(uid)}")

    fun ApplicationTestBuilder.app(w: World): HttpClient = startApp(
        testContainer(
            repo = w.identity, quota = w.f.quota, graph = w.f.graph, simulation = w.f.simulation, matching = w.f.module,
            clock = w.f.clock, notifier = w.f.notifier, chat = w.chat, safety = w.safety,
        ),
    )

    suspend fun HttpClient.send(uid: String, chatId: Any, clientId: String, text: String): HttpResponse =
        post("/v1/chats/$chatId/messages") {
            auth(uid)
            contentType(ContentType.Application.Json)
            setBody(SendMessage(clientId, text))
        }

    suspend fun HttpClient.report(uid: String, request: ReportRequest): HttpResponse = post("/v1/reports") {
        auth(uid)
        contentType(ContentType.Application.Json)
        setBody(request)
    }

    suspend fun HttpClient.enqueue(uid: String): HttpResponse = post("/v1/match/queue") {
        auth(uid)
        contentType(ContentType.Application.Json)
        setBody(Enqueue(Mode.AMISTAD))
    }

    test("tras bloquear, el bloqueado desaparece del grafo y de la fila, y el chat se cierra (envío → 403 BLOCKED)") {
        val w = World()
        w.f.graphRepo.putEdge(w.ana, w.beto, Mode.AMISTAD, EdgeState.CONNECTED, 80)
        val chat = w.direct()
        testApplication {
            val client = app(w)
            client.send("beto", chat.id, "b-1", "hola Ana").status shouldBe HttpStatusCode.OK
            client.get("/v1/graph") { auth("ana") }.body<GraphDto>().nodes.map { it.id } shouldContainExactly listOf(w.beto.toString())

            client.post("/v1/blocks/${w.beto}") { auth("ana") }.status shouldBe HttpStatusCode.NoContent

            // Grafo: ni el que bloquea ni el bloqueado se ven.
            client.get("/v1/graph") { auth("ana") }.body<GraphDto>().nodes.shouldBeEmpty()
            client.get("/v1/graph") { auth("beto") }.body<GraphDto>().nodes.shouldBeEmpty()
            // Chat: cerrado, fuera de la lista y el envío en cualquier sentido → 403 BLOCKED.
            w.chatRepo.chat(chat.id)!!.status shouldBe ChatStatus.CLOSED
            client.get("/v1/chats") { auth("ana") }.body<List<ChatSummary>>().shouldBeEmpty()
            client.get("/v1/chats") { auth("beto") }.body<List<ChatSummary>>().shouldBeEmpty()
            listOf("beto" to "b-2", "ana" to "a-1").forEach { (uid, clientId) ->
                val sent = client.send(uid, chat.id, clientId, "¿sigues ahí?")
                sent.status shouldBe HttpStatusCode.Forbidden
                sent.body<ApiError>().code shouldBe ErrorCode.BLOCKED
            }
            // Fila: beto espera y ana entra; no se emparejan entre ellos.
            client.enqueue("beto").body<TicketDto>().status shouldBe TicketStatus.WAITING
            client.enqueue("ana").body<TicketDto>().status shouldBe TicketStatus.WAITING
            // Un tercero sí se empareja con quien espera primero.
            client.enqueue("caro").body<TicketDto>().status shouldBe TicketStatus.MATCHED
        }
    }

    test("bloquear cancela el match pendiente entre los dos y es idempotente") {
        val w = World()
        testApplication {
            val client = app(w)
            client.enqueue("ana")
            val matchId = client.enqueue("beto").body<TicketDto>().matchId.shouldNotBeNull()
            client.post("/v1/blocks/${w.ana}") { auth("beto") }.status shouldBe HttpStatusCode.NoContent
            client.post("/v1/blocks/${w.ana}") { auth("beto") }.status shouldBe HttpStatusCode.NoContent
            client.get("/v1/matches/$matchId") { auth("ana") }.body<MatchDto>().status shouldBe MatchStatus.EXPIRED
            w.safetyRepo.allBlocks shouldBe setOf(w.beto to w.ana)
        }
    }

    test("un anonId del chat de escenario se resuelve; GET /v1/blocks lista y DELETE desbloquea") {
        val w = World()
        val scenario = runBlocking { w.chat.service.createScenario(w.ana, w.caro, Mode.AMISTAD, 1, w.f.clock.instant().plus(Duration.ofMinutes(10))) }
        testApplication {
            val client = app(w)
            val anon = client.get("/v1/chats") { auth("ana") }.body<List<ChatSummary>>().single().other
            anon.id.shouldBeNull()
            val anonId = anon.anonId.shouldNotBeNull()

            client.post("/v1/blocks/$anonId") { auth("ana") }.status shouldBe HttpStatusCode.NoContent
            w.chatRepo.chat(scenario.id)!!.status shouldBe ChatStatus.CLOSED
            val blocked = client.get("/v1/blocks") { auth("ana") }.body<List<BlockedDto>>().single()
            blocked.userId shouldBe anonId
            // Regla 3: la lista no revela el uuid interno de una persona anónima.
            client.get("/v1/blocks") { auth("ana") }.bodyAsText() shouldNotContain w.caro.toString()
            // Sin conexión mutua sigue anónimo: el nombre nunca se revela al bloquear.
            blocked.label shouldNotContain "Nombre Secreto"

            // DELETE con el uuid interno no sirve de oráculo: responde igual que un uuid cualquiera y no desbloquea.
            client.delete("/v1/blocks/${w.caro}") { auth("ana") }.status shouldBe HttpStatusCode.NoContent
            client.delete("/v1/blocks/${UUID.randomUUID()}") { auth("ana") }.status shouldBe HttpStatusCode.NoContent
            w.safetyRepo.allBlocks shouldBe setOf(w.ana to w.caro)

            client.delete("/v1/blocks/${blocked.userId}") { auth("ana") }.status shouldBe HttpStatusCode.NoContent
            client.get("/v1/blocks") { auth("ana") }.body<List<BlockedDto>>().shouldBeEmpty()
            w.safetyRepo.allBlocks.shouldBeEmpty()
            // Un anonId que no es de nadie de su contexto → 404.
            client.post("/v1/blocks/a_desconocido") { auth("ana") }.status shouldBe HttpStatusCode.NotFound
            client.post("/v1/blocks/${w.ana}") { auth("ana") }.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("un usuario gratis con cupos en 0 puede reportar y bloquear") {
        val w = World()
        val chat = w.direct()
        val quota = w.f.quota.service
        repeat(5) { quota.consume(w.ana, QuotaKind.SIMULATION, "sim-$it") }
        quota.consume(w.ana, QuotaKind.RANDOM_MATCH, "match-0")
        quota.consume(w.ana, QuotaKind.FULL_REPORT, "report-0")
        val status = quota.status(w.ana)
        status.plus shouldBe false
        listOf(status.simulations, status.randomMatch, status.fullReport).forEach { it.used shouldBe it.limit }
        QuotaKind.entries.forEach { kind -> shouldThrow<QuotaExceeded> { quota.ensureAvailable(w.ana, kind) } }
        testApplication {
            val client = app(w)

            val reported = client.report("ana", ReportRequest(ReportKind.USER, targetUserId = w.beto.toString(), chatId = chat.id.toString(), reason = ReportReason.HARASSMENT))
            reported.status shouldBe HttpStatusCode.Created
            client.post("/v1/blocks/${w.beto}") { auth("ana") }.status shouldBe HttpStatusCode.NoContent
        }
        w.safetyRepo.allReports shouldHaveSize 1
        w.safetyRepo.allBlocks shouldBe setOf(w.ana to w.beto)
    }

    test("el snapshot guarda los últimos 20 mensajes del chat, en orden y sin mensajes de otros chats") {
        val w = World()
        val chat = w.direct()
        val other = w.direct(w.ana, w.caro)
        testApplication {
            val client = app(w)
            repeat(25) { i ->
                w.f.clock.now = w.f.clock.now.plusSeconds(1)
                val uid = if (i % 2 == 0) "ana" else "beto"
                client.send(uid, chat.id, "m-$i", "mensaje $i").status shouldBe HttpStatusCode.OK
                client.send("caro", other.id, "c-$i", "otro chat $i").status shouldBe HttpStatusCode.OK
            }
            client.report("ana", ReportRequest(ReportKind.USER, targetUserId = w.beto.toString(), chatId = chat.id.toString(), reason = ReportReason.OTHER))
                .status shouldBe HttpStatusCode.Created
        }
        val report = w.safetyRepo.allReports.single()
        report.snapshot.shouldNotBeNull().map { it.text } shouldContainExactly (5 until 25).map { "mensaje $it" }
        val json = snapshotJson(report.snapshot!!)
        json shouldHaveSize 20
        json.first().jsonObject["text"]!!.jsonPrimitive.content shouldBe "mensaje 5"
        json.last().jsonObject["senderId"]!!.jsonPrimitive.content shouldBe w.ana.toString()
        report.targetId shouldBe w.beto
        report.chatId shouldBe chat.id
    }

    test("con menos de 20 mensajes guarda todos; sin chat no hay snapshot") {
        val w = World()
        val chat = w.direct()
        testApplication {
            val client = app(w)
            repeat(3) { i ->
                w.f.clock.now = w.f.clock.now.plusSeconds(1)
                client.send("beto", chat.id, "b-$i", "hola $i")
            }
            client.report("ana", ReportRequest(ReportKind.USER, targetUserId = w.beto.toString(), chatId = chat.id.toString(), reason = ReportReason.HARASSMENT))
            client.report("ana", ReportRequest(ReportKind.USER, targetUserId = w.caro.toString(), reason = ReportReason.FAKE_OR_SCAM))
        }
        val (withChat, withoutChat) = w.safetyRepo.allReports
        withChat.snapshot.shouldNotBeNull().map { it.text } shouldContainExactly listOf("hola 0", "hola 1", "hola 2")
        withoutChat.snapshot.shouldBeNull()
    }

    test("motivo SELF_HARM → helpResources no vacío; otros motivos → vacío") {
        val w = World()
        testApplication {
            val client = app(w)
            ReportReason.entries.filter { it != ReportReason.AI_HARMFUL }.forEach { reason ->
                val created = client.report("ana", ReportRequest(ReportKind.USER, targetUserId = w.beto.toString(), reason = reason))
                created.status shouldBe HttpStatusCode.Created
                val help = created.body<ReportCreated>().helpResources.orEmpty()
                if (reason == ReportReason.SELF_HARM) {
                    help.shouldNotBeEmpty()
                    help.map { it.phone } shouldContainExactly listOf("192", "106", "123")
                } else {
                    help.shouldBeEmpty()
                }
            }
        }
        w.safetyRepo.allReports.single { it.reason == ReportReason.SELF_HARM }.priority shouldBe SafetyService.HIGH_PRIORITY
    }

    test("crear un reporte escribe el log report_created sin textos de mensajes ni detalle") {
        val w = World()
        val chat = w.direct()
        val logger = LoggerFactory.getLogger(SafetyService::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            testApplication {
                val client = app(w)
                client.send("beto", chat.id, "b-1", "texto muy privado")
                client.report(
                    "ana",
                    ReportRequest(ReportKind.USER, targetUserId = w.beto.toString(), chatId = chat.id.toString(), reason = ReportReason.HARASSMENT, details = "detalle confidencial"),
                ).status shouldBe HttpStatusCode.Created
            }
        } finally {
            logger.detachAppender(appender)
        }
        val event = appender.list.single { it.message == SafetyService.REPORT_CREATED }
        val fields = event.keyValuePairs.associate { it.key to it.value }
        fields["event"] shouldBe "report_created"
        fields["reportId"] shouldBe w.safetyRepo.allReports.single().id.toString()
        fields["reason"] shouldBe "HARASSMENT"
        fields["snapshotMessages"] shouldBe 1
        val everything = event.formattedMessage + fields.values.joinToString()
        everything shouldNotContain "texto muy privado"
        everything shouldNotContain "detalle confidencial"
    }

    test("validaciones: objetivo único, chat ajeno → 404 y la persona debe estar en el chat") {
        val w = World()
        val chat = w.direct()
        testApplication {
            val client = app(w)
            client.report("ana", ReportRequest(ReportKind.USER, reason = ReportReason.OTHER)).status shouldBe HttpStatusCode.BadRequest
            client.report("ana", ReportRequest(ReportKind.USER, targetUserId = w.beto.toString(), anonId = "a_x", reason = ReportReason.OTHER))
                .status shouldBe HttpStatusCode.BadRequest
            client.report("caro", ReportRequest(ReportKind.USER, targetUserId = w.beto.toString(), chatId = chat.id.toString(), reason = ReportReason.OTHER))
                .status shouldBe HttpStatusCode.NotFound
            client.report("ana", ReportRequest(ReportKind.USER, targetUserId = w.caro.toString(), chatId = chat.id.toString(), reason = ReportReason.OTHER))
                .status shouldBe HttpStatusCode.BadRequest
            client.report("ana", ReportRequest(ReportKind.USER, targetUserId = UUID.randomUUID().toString(), reason = ReportReason.OTHER))
                .status shouldBe HttpStatusCode.NotFound
            client.report("ana", ReportRequest(ReportKind.USER, targetUserId = w.beto.toString(), reason = ReportReason.OTHER, details = "x".repeat(1001)))
                .status shouldBe HttpStatusCode.BadRequest
        }
        w.safetyRepo.allReports.shouldBeEmpty()
    }

    test("reportar por anonId crea el reporte contra la persona correcta sin exponer su id; el anonId de otro observador da 404") {
        val w = World()
        w.runCatching { runBlocking { chat.service.createScenario(ana, caro, Mode.AMISTAD, 1, f.clock.instant().plus(Duration.ofMinutes(10))) } }.getOrThrow()
        testApplication {
            val client = app(w)
            val anonId = client.get("/v1/chats") { auth("ana") }.body<List<ChatSummary>>().single().other.anonId.shouldNotBeNull()

            // El anonId que ve beto para la misma persona no es el que ve ana (la HMAC depende del observador).
            client.report("beto", ReportRequest(ReportKind.USER, anonId = anonId, reason = ReportReason.HARASSMENT)).status shouldBe HttpStatusCode.NotFound
            w.safetyRepo.allReports.shouldBeEmpty()

            val created = client.report("ana", ReportRequest(ReportKind.USER, anonId = anonId, reason = ReportReason.HARASSMENT))
            created.status shouldBe HttpStatusCode.Created
            created.bodyAsText() shouldNotContain w.caro.toString()
            w.safetyRepo.allReports.single().targetId shouldBe w.caro
        }
    }

    test("el anonId de un canje de C5 o de una presentación de E3 se resuelve solo para su observador") {
        val w = World()
        runBlocking {
            // beto canjeó el código de ana (C5); ana es una de las presentadas a caro por beto (E3).
            w.invites.addRedemption(w.ana, w.beto, Mode.AMISTAD, w.f.clock.instant())
            w.introductions.insert(
                IntroductionRecord(
                    id = UUID.randomUUID(), introducerId = w.beto, mode = Mode.AMISTAD, userA = w.ana, userB = w.caro, simulationId = null,
                    aResponse = null, bResponse = null, status = IntroductionStatus.PROPOSED, createdAt = w.f.clock.instant(),
                ),
            )
        }
        val betoAsSeenByAna = w.f.graph.anonIds.anonId(w.ana, w.beto)
        val caroAsSeenByAna = w.f.graph.anonIds.anonId(w.ana, w.caro)
        testApplication {
            val client = app(w)
            // Un observador sin ese contexto no los resuelve.
            client.report("caro", ReportRequest(ReportKind.USER, anonId = betoAsSeenByAna, reason = ReportReason.OTHER)).status shouldBe HttpStatusCode.NotFound
            client.report("ana", ReportRequest(ReportKind.USER, anonId = betoAsSeenByAna, reason = ReportReason.FAKE_OR_SCAM)).status shouldBe HttpStatusCode.Created
            client.post("/v1/blocks/$caroAsSeenByAna") { auth("ana") }.status shouldBe HttpStatusCode.NoContent
        }
        w.safetyRepo.allReports.single().targetId shouldBe w.beto
        w.safetyRepo.allBlocks shouldBe setOf(w.ana to w.caro)
    }

    test("un reporte AI_CONTENT guarda simulationId, turnIndex y el texto del turno en snapshot") {
        val w = World()
        val sim = w.simulation()
        testApplication {
            val client = app(w)
            val created = client.report(
                "ana",
                ReportRequest(ReportKind.AI_CONTENT, simulationId = sim.toString(), turnIndex = 2, reason = ReportReason.AI_HARMFUL),
            )
            created.status shouldBe HttpStatusCode.Created
            // Un turno que no existe, o una simulación ajena, no crea nada.
            client.report("ana", ReportRequest(ReportKind.AI_CONTENT, simulationId = sim.toString(), turnIndex = 3, reason = ReportReason.AI_HARMFUL))
                .status shouldBe HttpStatusCode.NotFound
            client.report("caro", ReportRequest(ReportKind.AI_CONTENT, simulationId = sim.toString(), turnIndex = 0, reason = ReportReason.AI_HARMFUL))
                .status shouldBe HttpStatusCode.NotFound
        }
        val report = w.safetyRepo.allReports.single()
        report.kind shouldBe ReportKind.AI_CONTENT
        report.simulationId shouldBe sim
        report.turnIndex shouldBe 2
        report.targetId.shouldBeNull()
        report.snapshot.shouldNotBeNull().single().text shouldBe "turno ofensivo de la IA"
    }

    test("bloquear desde F2 cancela el match y los tickets de ambos; desbloquear no restaura nada") {
        val w = World()
        w.f.graphRepo.putEdge(w.ana, w.caro, Mode.AMISTAD, EdgeState.CONNECTED, 80)
        val chat = w.direct(w.ana, w.caro)
        testApplication {
            val client = app(w)
            client.enqueue("ana")
            val matchId = client.enqueue("beto").body<TicketDto>().matchId.shouldNotBeNull()
            client.get("/v1/match/queue") { auth("ana") }.body<TicketDto>().status shouldBe TicketStatus.MATCHED

            client.post("/v1/blocks/${w.ana}") { auth("beto") }.status shouldBe HttpStatusCode.NoContent

            client.get("/v1/matches/$matchId") { auth("ana") }.body<MatchDto>().status shouldBe MatchStatus.EXPIRED
            listOf("ana", "beto").forEach { uid ->
                client.get("/v1/match/queue") { auth(uid) }.body<TicketDto>().status shouldBe TicketStatus.EXPIRED
                w.f.mirror.last("uid-$uid").shouldNotBeNull().status shouldBe TicketStatus.EXPIRED
            }

            // Bloquear a una persona conectada cierra el chat; desbloquear no lo reabre ni restaura la conexión.
            client.post("/v1/blocks/${w.caro}") { auth("ana") }.status shouldBe HttpStatusCode.NoContent
            w.chatRepo.chat(chat.id)!!.status shouldBe ChatStatus.CLOSED
            val listed = client.get("/v1/blocks") { auth("ana") }.body<List<BlockedDto>>().single()
            client.delete("/v1/blocks/${listed.userId}") { auth("ana") }.status shouldBe HttpStatusCode.NoContent
            client.get("/v1/blocks") { auth("beto") }.body<List<BlockedDto>>().single()
            client.delete("/v1/blocks/${client.get("/v1/blocks") { auth("beto") }.body<List<BlockedDto>>().single().userId}") { auth("beto") }.status shouldBe HttpStatusCode.NoContent
            w.safetyRepo.allBlocks.shouldBeEmpty()
            w.chatRepo.chat(chat.id)!!.status shouldBe ChatStatus.CLOSED
            client.get("/v1/matches/$matchId") { auth("ana") }.body<MatchDto>().status shouldBe MatchStatus.EXPIRED
            client.get("/v1/match/queue") { auth("ana") }.body<TicketDto>().status shouldBe TicketStatus.EXPIRED
            client.get("/v1/graph") { auth("ana") }.body<GraphDto>().nodes.shouldBeEmpty()
        }
    }

    test("silenciar (H1) solo cambia muted: no bloquea") {
        val w = World()
        val chat = w.direct()
        testApplication {
            val client = app(w)
            client.post("/v1/chats/${chat.id}/mute") {
                auth("ana")
                contentType(ContentType.Application.Json)
                setBody(Mute(true))
            }.status shouldBe HttpStatusCode.NoContent
            client.get("/v1/chats") { auth("ana") }.body<List<ChatSummary>>().single().muted shouldBe true
            client.send("beto", chat.id, "b-1", "hola").status shouldBe HttpStatusCode.OK
        }
        w.safetyRepo.allBlocks.shouldBeEmpty()
    }
})
