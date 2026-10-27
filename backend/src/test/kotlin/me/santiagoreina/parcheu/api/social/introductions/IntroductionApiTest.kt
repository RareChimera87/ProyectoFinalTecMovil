package me.santiagoreina.parcheu.api.social.introductions

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.collections.shouldNotContainAnyOf
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain
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
import kotlinx.serialization.json.jsonObject
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.TEST_LEGAL_VERSION
import me.santiagoreina.parcheu.api.chat.InMemoryChatRepository
import me.santiagoreina.parcheu.api.chat.inMemoryChatModule
import me.santiagoreina.parcheu.api.graph.AnonIdSigner
import me.santiagoreina.parcheu.api.graph.PersonRow
import me.santiagoreina.parcheu.api.identity.InMemoryIdentityRepository
import me.santiagoreina.parcheu.api.platform.AppContainer
import me.santiagoreina.parcheu.api.platform.auth.FakeTokenVerifier
import me.santiagoreina.parcheu.api.demo.DemoConnectPolicy
import me.santiagoreina.parcheu.api.platform.db.tables.IntroRequestStatus
import me.santiagoreina.parcheu.api.platform.notifier.FakeNotifier
import me.santiagoreina.parcheu.api.simulation.SimFixture
import me.santiagoreina.parcheu.api.simulation.inMemorySimulationModule
import me.santiagoreina.parcheu.api.startApp
import me.santiagoreina.parcheu.api.testContainer
import me.santiagoreina.parcheu.contract.ApiError
import me.santiagoreina.parcheu.contract.ContractJson
import me.santiagoreina.parcheu.contract.Decision
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.ErrorCode
import me.santiagoreina.parcheu.contract.GraphDto
import me.santiagoreina.parcheu.contract.IntroRequest
import me.santiagoreina.parcheu.contract.IntroResponse
import me.santiagoreina.parcheu.contract.Introduce
import me.santiagoreina.parcheu.contract.IntroductionDto
import me.santiagoreina.parcheu.contract.IntroductionStatus
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.NotificationKinds
import me.santiagoreina.parcheu.contract.Page
import me.santiagoreina.parcheu.contract.QuotaKind
import me.santiagoreina.parcheu.contract.SimulationOrigin
import me.santiagoreina.parcheu.contract.SimulationStatus
import me.santiagoreina.parcheu.contract.SimulationSummary
import me.santiagoreina.parcheu.contract.Visibility
import java.util.UUID

/** Presentar a dos amigos (backlog E6.2) por HTTP con todo en memoria. */
class IntroductionApiTest : FunSpec({
    // mari presenta; vale y sebas son amigos de mari (Amistad) que no se conocen; juli no es amiga de mari.
    val uids = listOf("mari", "vale", "sebas", "juli", "nico")

    class World(demos: Set<String> = emptySet()) {
        val identity = InMemoryIdentityRepository().also { repo -> uids.forEach { repo.seedAcceptedUser(it, TEST_LEGAL_VERSION) } }
        val sim = SimFixture()
        val ids = uids.associateWith { uid -> sim.person(uid, id = identity.user(uid).id, isDemo = uid in demos) }
        val chatRepo = InMemoryChatRepository().also { repo ->
            ids.forEach { (uid, id) -> repo.addUser(PersonRow(id, uid, null, "UIS", Visibility.ANONYMOUS, false, null), uid) }
        }
        val push = FakeNotifier()
        val store = InMemoryIntroductionRepository()
        val chat = inMemoryChatModule(FIXED_CLOCK, sim.graph.policy, push, chatRepo)
        val module = inMemorySimulationModule(
            FIXED_CLOCK, sim.ai, sim.quota, sim.graph, sim.repo, sim.queue, sim.mirror,
            IntroductionSimulationNotifier(store, sim.notifier, push),
            chat = chat, push = push,
        )

        init {
            friends("mari", "vale")
            friends("mari", "sebas")
            friends("mari", "nico")
        }

        fun id(uid: String) = ids.getValue(uid)

        fun friends(a: String, b: String, mode: Mode = Mode.AMISTAD) =
            sim.graphRepo.putEdge(id(a), id(b), mode, EdgeState.CONNECTED, 70)

        /** El bloqueo vive en la tabla `blocks`, que leen grafo, simulación y chat. */
        fun block(blocker: String, blocked: String) {
            sim.graphRepo.block(id(blocker), id(blocked))
            sim.repo.block(id(blocker), id(blocked))
            chatRepo.block(id(blocker), id(blocked))
        }

        fun container(): AppContainer = testContainer(
            repo = identity, quota = sim.quota, ai = sim.ai, graph = sim.graph, chat = chat, simulation = module,
            notifier = push, introductionStore = store,
        )

        fun sentTo(uid: String) = push.sent.filter { it.userId == id(uid) }.map { it.notification }
    }

    fun HttpRequestBuilder.auth(uid: String) = header(HttpHeaders.Authorization, "Bearer ${FakeTokenVerifier.tokenFor(uid)}")

    fun ApplicationTestBuilder.app(w: World): HttpClient = startApp(w.container())

    suspend fun HttpClient.introduceRaw(by: String, body: Introduce) = post("/v1/introductions") {
        auth(by)
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    suspend fun HttpClient.introduce(w: World, by: String, a: String, b: String, mode: Mode = Mode.AMISTAD) =
        introduceRaw(by, Introduce(mode, w.id(a).toString(), w.id(b).toString()))

    suspend fun HttpClient.intro(uid: String, id: String) = get("/v1/introductions/$id") { auth(uid) }

    suspend fun HttpClient.respond(uid: String, id: String, interested: Boolean) = post("/v1/introductions/$id/response") {
        auth(uid)
        contentType(ContentType.Application.Json)
        setBody(IntroResponse(interested))
    }

    suspend fun HttpClient.requestIntro(uid: String, anonId: String) = post("/v1/introductions/request") {
        auth(uid)
        contentType(ContentType.Application.Json)
        setBody(IntroRequest(anonId))
    }

    suspend fun HttpResponse.error() = body<ApiError>()

    suspend fun HttpClient.decision(uid: String, simulationId: String, connect: Boolean) = post("/v1/simulations/$simulationId/decision") {
        auth(uid)
        contentType(ContentType.Application.Json)
        setBody(Decision(connect))
    }

    /**
     * Por `/v1/simulations` la simulación de la presentación no existe para [uid] (persona presentada):
     * ni en la lista, ni el detalle, la transcripción, el informe, el desbloqueo o la decisión.
     */
    suspend fun HttpClient.simulationHiddenFor(w: World, uid: String, simulationId: String) {
        val list = get("/v1/simulations") { auth(uid) }
        list.status shouldBe HttpStatusCode.OK
        list.body<Page<SimulationSummary>>().items.map { it.id } shouldNotContain simulationId
        list.bodyAsText() shouldNotContain simulationId
        get("/v1/simulations/$simulationId") { auth(uid) }.status shouldBe HttpStatusCode.NotFound
        get("/v1/simulations/$simulationId/transcript") { auth(uid) }.status shouldBe HttpStatusCode.NotFound
        get("/v1/simulations/$simulationId/report") { auth(uid) }.status shouldBe HttpStatusCode.NotFound
        post("/v1/simulations/$simulationId/report:unlock") { auth(uid) }.status shouldBe HttpStatusCode.NotFound
        decision(uid, simulationId, connect = true).status shouldBe HttpStatusCode.NotFound
        // Ni el desbloqueo ni la decisión dejaron rastro.
        w.sim.quota.service.status(w.id(uid)).fullReport.used shouldBe 0
    }

    /**
     * vale y sebas siguen sin conectarse: sin chat y sin ninguna arista entre ellos (ni siquiera SIMULATED,
     * que delataría la simulación y su puntaje en el grafo).
     */
    suspend fun World.notConnected() {
        sim.graphRepo.edge(id("vale"), id("sebas"), Mode.AMISTAD) shouldBe null
        chatRepo.chatsOf(id("vale")).shouldBeEmpty()
        chatRepo.chatsOf(id("sebas")).shouldBeEmpty()
    }

    /** En el ego-grafo de vale y de sebas (D1/D2) solo está mari: ni nodo del otro ni simulación pendiente. */
    suspend fun HttpClient.graphHidesPair(w: World) {
        listOf("vale" to "sebas", "sebas" to "vale").forEach { (viewer, other) ->
            val raw = get("/v1/graph?mode=AMISTAD") { auth(viewer) }
            raw.status shouldBe HttpStatusCode.OK
            val graph = raw.body<GraphDto>()
            graph.nodes.map { it.state }.toSet() shouldBe setOf(EdgeState.CONNECTED)
            graph.nodes shouldHaveSize 1
            graph.edges.map { it.state }.toSet() shouldBe setOf(EdgeState.CONNECTED)
            graph.stats.simulated shouldBe 0
            graph.stats.pending shouldBe 0
            raw.bodyAsText() shouldNotContain w.id(other).toString()
        }
    }

    /** mari presenta a vale y sebas (la simulación corre en línea) y envía la propuesta. */
    suspend fun HttpClient.proposed(w: World): String {
        val created = introduce(w, "mari", "vale", "sebas")
        created.status shouldBe HttpStatusCode.Accepted
        val id = created.body<IntroductionDto>().id
        respond("mari", id, interested = true).status shouldBe HttpStatusCode.OK
        return id
    }

    test("modo Pareja → 400 VALIDATION; un no amigo → 403 FORBIDDEN; nada se cobra") {
        val w = World()
        w.friends("mari", "juli", Mode.PAREJA)
        testApplication {
            val client = app(w)
            val pareja = client.introduce(w, "mari", "vale", "juli", Mode.PAREJA)
            pareja.status shouldBe HttpStatusCode.BadRequest
            pareja.error().code shouldBe ErrorCode.VALIDATION

            // juli solo es de Pareja con mari: en Amistad no es su amiga.
            val stranger = client.introduce(w, "mari", "vale", "juli")
            stranger.status shouldBe HttpStatusCode.Forbidden
            stranger.error().code shouldBe ErrorCode.FORBIDDEN

            client.introduce(w, "mari", "vale", "vale").status shouldBe HttpStatusCode.BadRequest
            client.introduce(w, "mari", "mari", "vale").status shouldBe HttpStatusCode.BadRequest
        }
        w.sim.usage.charges.shouldBeEmpty()
        w.store.all().shouldBeEmpty()
    }

    test("dos amigos ya conectados entre sí en ese modo → 400 VALIDATION") {
        val w = World()
        w.friends("vale", "sebas")
        testApplication {
            val client = app(w)
            client.introduce(w, "mari", "vale", "sebas").error().code shouldBe ErrorCode.VALIDATION
        }
    }

    test("presentar consume 1 simulación de quien presenta (ref_id = introductionId) y nada de los presentados") {
        val w = World()
        testApplication {
            val client = app(w)
            val created = client.introduce(w, "mari", "vale", "sebas")
            created.status shouldBe HttpStatusCode.Accepted
            val dto = created.body<IntroductionDto>()
            dto.simulationId shouldBe dto.id
            w.sim.usage.charges shouldContainExactly listOf(dto.id)
            w.sim.usage.refunded(dto.id) shouldBe false

            val simulation = w.sim.repo.find(UUID.fromString(dto.id))!!
            simulation.origin shouldBe SimulationOrigin.INTRODUCTION
            simulation.requestedBy shouldBe w.id("mari")

            fun used(uid: String) = w.sim.quota.service.status(w.id(uid)).simulations.used
            used("mari") shouldBe 1
            used("vale") shouldBe 0
            used("sebas") shouldBe 0
        }
    }

    test("sin cupo → 402 QUOTA_EXCEEDED(kind=SIMULATION) y no queda la presentación") {
        val w = World()
        repeat(5) { n -> w.sim.quota.service.consume(w.id("mari"), QuotaKind.SIMULATION, "otra-$n") }
        testApplication {
            val client = app(w)
            val response = client.introduce(w, "mari", "vale", "sebas")
            response.status shouldBe HttpStatusCode.PaymentRequired
            response.error().code shouldBe ErrorCode.QUOTA_EXCEEDED
            response.error().kind shouldBe QuotaKind.SIMULATION.name
        }
        w.store.all().shouldBeEmpty()
    }

    test("al terminar la simulación solo se avisa a quien presenta; los presentados no ven nada hasta la propuesta") {
        val w = World()
        testApplication {
            val client = app(w)
            val id = client.introduce(w, "mari", "vale", "sebas").body<IntroductionDto>().id

            w.sentTo("mari").map { it.kind } shouldContainExactly listOf(NotificationKinds.INTRODUCTION_READY)
            w.sentTo("mari").single().route shouldBe "parcheu://intro-result/$id"
            w.sentTo("vale").shouldBeEmpty()
            w.sentTo("sebas").shouldBeEmpty()
            w.sim.notifier.sent.shouldBeEmpty()

            val e2 = client.intro("mari", id).body<IntroductionDto>()
            e2.status shouldBe IntroductionStatus.READY
            e2.introduced.map { it.label } shouldContainExactly listOf("vale", "sebas")
            e2.report shouldNotBe null
            client.intro("vale", id).status shouldBe HttpStatusCode.NotFound
            client.intro("juli", id).status shouldBe HttpStatusCode.NotFound

            client.respond("mari", id, interested = true).body<IntroductionDto>().status shouldBe IntroductionStatus.PROPOSED
            listOf("vale", "sebas").forEach { uid ->
                val proposal = w.sentTo(uid).single()
                proposal.kind shouldBe NotificationKinds.INTRODUCTION_PROPOSAL
                proposal.route shouldBe "parcheu://intro/$id"
                proposal.actorId shouldBe w.id("mari")
            }
            client.respond("mari", id, interested = true).status shouldBe HttpStatusCode.Conflict

            val e3 = client.intro("vale", id).body<IntroductionDto>()
            e3.status shouldBe IntroductionStatus.PROPOSED
            e3.introducer.label shouldBe "mari"
            // Sin conexión directa y con visibilidad ANONYMOUS: 'Alguien de la UIS', nunca el uuid.
            e3.other.id shouldBe null
            e3.other.anonId shouldNotBe null
            e3.other.label shouldBe "Alguien de la UIS"
            e3.introduced.shouldBeEmpty()
            e3.myResponse shouldBe null
        }
    }

    test("'No por ahora' de quien presenta: la propuesta no llega a nadie") {
        val w = World()
        testApplication {
            val client = app(w)
            val id = client.introduce(w, "mari", "vale", "sebas").body<IntroductionDto>().id
            client.respond("mari", id, interested = false).body<IntroductionDto>().status shouldBe IntroductionStatus.DECLINED
            w.sentTo("vale").shouldBeEmpty()
            w.sentTo("sebas").shouldBeEmpty()
            client.intro("vale", id).status shouldBe HttpStatusCode.NotFound
            client.respond("vale", id, interested = true).status shouldBe HttpStatusCode.NotFound
        }
    }

    test("ambos aceptan → un chat DIRECT y una arista CONNECTED; repetir la respuesta → 409") {
        val w = World()
        testApplication {
            val client = app(w)
            val id = client.proposed(w)

            val first = client.respond("vale", id, interested = true).body<IntroductionDto>()
            first.status shouldBe IntroductionStatus.PROPOSED
            first.myResponse shouldBe true
            w.sentTo("sebas").map { it.kind } shouldContainExactly listOf(NotificationKinds.INTRODUCTION_PROPOSAL)

            val second = client.respond("sebas", id, interested = true).body<IntroductionDto>()
            second.status shouldBe IntroductionStatus.CONNECTED
            second.other.label shouldBe "vale"
            second.other.id shouldBe w.id("vale").toString()
            val chatId = second.report?.chatId
            chatId shouldNotBe null

            w.sim.graphRepo.edge(w.id("vale"), w.id("sebas"), Mode.AMISTAD)?.state shouldBe EdgeState.CONNECTED
            val chats = w.chatRepo.chatsOf(w.id("vale"))
            chats shouldHaveSize 1
            chats.single().chat.id.toString() shouldBe chatId
            listOf("vale", "sebas").forEach { uid ->
                w.sentTo(uid).last().kind shouldBe NotificationKinds.NEW_CONNECTION
                w.sentTo(uid).last().route shouldBe "parcheu://chat/$chatId"
            }
            client.intro("vale", id).body<IntroductionDto>().status shouldBe IntroductionStatus.CONNECTED
            client.intro("mari", id).body<IntroductionDto>().status shouldBe IntroductionStatus.CONNECTED

            val again = client.respond("sebas", id, interested = false)
            again.status shouldBe HttpStatusCode.Conflict
            again.error().code shouldBe ErrorCode.CONFLICT
            w.chatRepo.chatsOf(w.id("vale")) shouldHaveSize 1
        }
    }

    test("uno rechaza → nadie recibe aviso del rechazo y la otra persona y quien presenta siguen viendo PROPOSED") {
        val w = World()
        testApplication {
            val client = app(w)
            val id = client.proposed(w)
            val before = w.push.sent.size

            client.respond("vale", id, interested = false).body<IntroductionDto>().status shouldBe IntroductionStatus.DECLINED
            w.push.sent.size shouldBe before

            val sebas = client.respond("sebas", id, interested = true).body<IntroductionDto>()
            sebas.status shouldBe IntroductionStatus.PROPOSED
            sebas.myResponse shouldBe true
            w.push.sent.size shouldBe before
            client.intro("sebas", id).body<IntroductionDto>().status shouldBe IntroductionStatus.PROPOSED
            client.intro("mari", id).body<IntroductionDto>().status shouldBe IntroductionStatus.PROPOSED
            client.intro("vale", id).body<IntroductionDto>().status shouldBe IntroductionStatus.DECLINED

            w.notConnected()
            client.graphHidesPair(w)
            w.chatRepo.chatsOf(w.id("vale")).shouldBeEmpty()
        }
    }

    test("E2 para gratis no incluye reasons (misma redacción que F7); con ParcheU+ sí") {
        val w = World()
        testApplication {
            val client = app(w)
            val id = client.introduce(w, "mari", "vale", "sebas").body<IntroductionDto>().id

            val raw = client.intro("mari", id).bodyAsText()
            val report = ContractJson.parseToJsonElement(raw).jsonObject.getValue("report").jsonObject
            report.keys shouldNotContainAnyOf listOf("reasons", "frictions", "dimensions")
            raw shouldNotContain "Planean parecido"
            val dto = ContractJson.decodeFromString(IntroductionDto.serializer(), raw)
            dto.report!!.locked shouldBe true
            dto.report!!.reasonsCount shouldBe 3
            dto.report!!.unlockAvailable shouldBe true

            // El informe del mes también se puede usar en E2 (misma ruta de F7).
            val unlocked = client.post("/v1/simulations/$id/report:unlock") { auth("mari") }
            unlocked.status shouldBe HttpStatusCode.OK
            client.intro("mari", id).body<IntroductionDto>().report!!.reasons!!.size shouldBe 3
            // Quien presenta no decide sobre la simulación: eso es de las personas presentadas.
            client.post("/v1/simulations/$id/decision") {
                auth("mari")
                contentType(ContentType.Application.Json)
                setBody(Decision(true))
            }.status shouldBe HttpStatusCode.NotFound

            w.sim.plus(w.id("sebas"))
            client.respond("mari", id, interested = true)
            client.intro("sebas", id).body<IntroductionDto>().report!!.reasons!!.size shouldBe 3
            val vale = client.intro("vale", id).bodyAsText()
            ContractJson.parseToJsonElement(vale).jsonObject.getValue("report").jsonObject.keys shouldNotContain "reasons"
        }
    }

    test("bloqueos: con quien presenta o entre las dos personas → 403 BLOCKED; un bloqueado nunca aparece en la lista de E1") {
        val w = World()
        testApplication {
            val client = app(w)
            w.block("vale", "sebas")
            client.introduce(w, "mari", "vale", "sebas").error().code shouldBe ErrorCode.BLOCKED

            w.block("nico", "mari")
            val withIntroducer = client.introduce(w, "mari", "nico", "sebas")
            withIntroducer.status shouldBe HttpStatusCode.Forbidden
            withIntroducer.error().code shouldBe ErrorCode.BLOCKED

            // E1 lista los amigos CONNECTED del modo desde el ego-grafo: nico (bloqueado) no aparece.
            val graph = client.get("/v1/graph?mode=AMISTAD") { auth("mari") }.body<GraphDto>()
            val listed = graph.nodes.filter { it.state == EdgeState.CONNECTED }.map { it.id }
            listed shouldContainExactlyInAnyOrder listOf(w.id("vale").toString(), w.id("sebas").toString())
            listed shouldNotContain w.id("nico").toString()
        }
        w.sim.usage.charges.shouldBeEmpty()
    }

    test("un bloqueo después de la propuesta: la presentación desaparece para quien bloquea y responder → 403 BLOCKED") {
        val w = World()
        testApplication {
            val client = app(w)
            val id = client.proposed(w)
            w.block("vale", "sebas")
            client.intro("vale", id).status shouldBe HttpStatusCode.NotFound
            client.intro("sebas", id).status shouldBe HttpStatusCode.NotFound
            w.block("mari", "nico")
            client.intro("mari", id).status shouldBe HttpStatusCode.OK
        }
    }

    test("las personas presentadas no ven la simulación por /v1/simulations ni se conectan por /decision, antes y después de 'No por ahora'") {
        val w = World()
        w.sim.plus(w.id("sebas"))
        var introId = ""
        testApplication {
            val client = app(w)
            val id = client.introduce(w, "mari", "vale", "sebas").body<IntroductionDto>().id
            introId = id
            // READY: solo quien presenta lo sabe.
            listOf("vale", "sebas").forEach { client.simulationHiddenFor(w, it, id) }
            w.notConnected()
            client.graphHidesPair(w)

            client.respond("mari", id, interested = false).body<IntroductionDto>().status shouldBe IntroductionStatus.DECLINED
            listOf("vale", "sebas").forEach { client.simulationHiddenFor(w, it, id) }
            w.notConnected()
            client.graphHidesPair(w)
            // La decisión de vale de antes no quedó guardada: sebas tampoco completa el par.
            client.decision("sebas", id, connect = true).status shouldBe HttpStatusCode.NotFound
            w.notConnected()
            client.intro("vale", id).status shouldBe HttpStatusCode.NotFound
            client.intro("mari", id).body<IntroductionDto>().status shouldBe IntroductionStatus.DECLINED

            // Quien presenta no participa: tampoco la ve en su lista, aunque sí su informe (E2).
            client.get("/v1/simulations") { auth("mari") }.body<Page<SimulationSummary>>().items.shouldBeEmpty()
            client.get("/v1/simulations/$id/report") { auth("mari") }.status shouldBe HttpStatusCode.OK
        }
        // El espejo de Firestore solo lo lee quien presenta.
        val docs = w.sim.mirror.docs.filter { it.id.toString() == introId }
        docs.map { it.status }.toSet() shouldBe setOf(SimulationStatus.QUEUED, SimulationStatus.RUNNING, SimulationStatus.DONE)
        docs.forEach { it.participants shouldContainExactly listOf("uid-mari") }
    }

    test("con la propuesta enviada, las personas presentadas tampoco se conectan por /decision: solo por la respuesta") {
        val w = World()
        testApplication {
            val client = app(w)
            val id = client.proposed(w)
            listOf("vale", "sebas").forEach { client.simulationHiddenFor(w, it, id) }
            w.notConnected()
            client.graphHidesPair(w)
            client.intro("vale", id).body<IntroductionDto>().status shouldBe IntroductionStatus.PROPOSED
            client.intro("mari", id).body<IntroductionDto>().status shouldBe IntroductionStatus.PROPOSED

            // El informe de E3 sigue llegando por la presentación, sin ofrecer un desbloqueo que no existe.
            val e3 = client.intro("vale", id).body<IntroductionDto>().report!!
            e3.locked shouldBe true
            e3.unlockAvailable shouldBe false

            // La conexión llega solo por /response, y después la simulación sigue fuera de /v1/simulations.
            client.respond("vale", id, interested = true)
            client.respond("sebas", id, interested = true).body<IntroductionDto>().status shouldBe IntroductionStatus.CONNECTED
            listOf("vale", "sebas").forEach { client.simulationHiddenFor(w, it, id) }
            w.chatRepo.chatsOf(w.id("vale")) shouldHaveSize 1
        }
    }

    test("pedir presentación (Plus) crea un intro_request y avisa al amigo en común sin revelar el id del candidato; gratis → 402 PLUS_REQUIRED") {
        val w = World()
        // juli es amiga de sebas (Amistad), y sebas de mari: para juli, mari es amiga de una amiga.
        w.friends("juli", "sebas")
        val anonOfMari = AnonIdSigner.forTests().anonId(w.id("juli"), w.id("mari"))
        testApplication {
            val client = app(w)
            val free = client.requestIntro("juli", anonOfMari)
            free.status shouldBe HttpStatusCode.PaymentRequired
            free.error().code shouldBe ErrorCode.PLUS_REQUIRED
            w.store.requests.shouldBeEmpty()

            w.sim.plus(w.id("juli"))
            val response = client.requestIntro("juli", anonOfMari)
            response.status shouldBe HttpStatusCode.Accepted
            val body = response.bodyAsText()
            body shouldNotContain w.id("mari").toString()
            body shouldNotContain w.id("sebas").toString()

            val saved = w.store.requests.single()
            saved.requesterId shouldBe w.id("juli")
            saved.viaUserId shouldBe w.id("sebas")
            saved.targetUserId shouldBe w.id("mari")
            saved.status shouldBe IntroRequestStatus.PENDING

            val notice = w.sentTo("sebas").single()
            notice.kind shouldBe NotificationKinds.INTRODUCTION_REQUEST
            notice.actorId shouldBe w.id("juli")
            notice.body shouldBe "juli quiere conocer a mari. ¿Los presentas?"
            notice.route shouldBe "parcheu://introduce"
            w.sentTo("juli").shouldBeEmpty()
            w.sentTo("mari").shouldBeEmpty()

            // Idempotente y sin otro aviso.
            client.requestIntro("juli", anonOfMari).status shouldBe HttpStatusCode.Accepted
            w.store.requests shouldHaveSize 1
            w.sentTo("sebas") shouldHaveSize 1

            // Un anonId que no es amigo de un amigo (o un uuid real) → 404, sin revelar nada.
            client.requestIntro("juli", w.id("vale").toString()).status shouldBe HttpStatusCode.NotFound
            client.requestIntro("juli", "a_desconocido").status shouldBe HttpStatusCode.NotFound
        }
    }

    test("un demo presentado responde por su cuenta con la decisión determinista: la persona real no se queda esperando") {
        val w = World(demos = setOf("vale"))
        testApplication {
            val client = app(w)
            val id = client.proposed(w)
            // El demo ya respondió al enviarse la propuesta.
            val wants = DemoConnectPolicy.DEFAULT.wantsToConnect(UUID.fromString(id))
            val result = client.respond("sebas", id, interested = true).body<IntroductionDto>()
            if (wants) {
                result.status shouldBe IntroductionStatus.CONNECTED
                w.sim.graphRepo.edge(w.id("vale"), w.id("sebas"), Mode.AMISTAD)?.state shouldBe EdgeState.CONNECTED
            } else {
                // El 'No' del demo no se revela: sebas sigue viendo su propia respuesta, sin conexión.
                w.sim.graphRepo.edge(w.id("vale"), w.id("sebas"), Mode.AMISTAD) shouldBe null
            }
            client.respond("vale", id, interested = true).status shouldBe HttpStatusCode.Conflict
        }
    }

    test("si uno apagó el modo después de la propuesta, 'Me interesa' → 409 y no se conecta") {
        val w = World()
        testApplication {
            val client = app(w)
            val id = client.proposed(w)
            w.sim.person("sebas", modes = setOf(Mode.ESTUDIO), id = w.id("sebas"))
            client.respond("vale", id, interested = true).status shouldBe HttpStatusCode.Conflict
            val blocked = client.respond("sebas", id, interested = true)
            blocked.status shouldBe HttpStatusCode.Conflict
            w.sim.graphRepo.edge(w.id("vale"), w.id("sebas"), Mode.AMISTAD) shouldBe null
            w.chatRepo.chatsOf(w.id("vale")).shouldBeEmpty()
        }
    }

    test("una solicitud PENDING vieja deja de contar: repetir 'Pedir presentación' después del vencimiento vuelve a avisar") {
        val w = World()
        val requester = w.id("juli")
        val target = w.id("mari")
        val old = FIXED_CLOCK.instant().minus(IntroductionService.REQUEST_TTL).minusSeconds(60)
        w.store.insertRequest(IntroRequestRecord(UUID.randomUUID(), requester, w.id("sebas"), target, IntroRequestStatus.PENDING, old))
        w.store.pendingRequest(requester, target, FIXED_CLOCK.instant().minus(IntroductionService.REQUEST_TTL)) shouldBe null
        w.store.pendingRequest(requester, target, old.minusSeconds(1)) shouldNotBe null
    }

    test("pedir presentación nunca pasa por una arista de Pareja ni llega a un bloqueado") {
        val w = World()
        w.friends("juli", "sebas", Mode.PAREJA)
        w.sim.plus(w.id("juli"))
        val anonOfMari = AnonIdSigner.forTests().anonId(w.id("juli"), w.id("mari"))
        testApplication {
            val client = app(w)
            client.requestIntro("juli", anonOfMari).status shouldBe HttpStatusCode.NotFound
            w.friends("juli", "sebas")
            w.block("mari", "juli")
            client.requestIntro("juli", anonOfMari).status shouldBe HttpStatusCode.NotFound
        }
        w.store.requests.shouldBeEmpty()
    }

    test("simulación fallida: FAILED para quien presenta, cupo devuelto y nada para los presentados") {
        val w = World()
        w.sim.textReply = { error("LLM caído") }
        testApplication {
            val client = app(w)
            val id = client.introduce(w, "mari", "vale", "sebas").body<IntroductionDto>().id
            client.intro("mari", id).body<IntroductionDto>().status shouldBe IntroductionStatus.FAILED
            w.sim.usage.refunded(id) shouldBe true
            w.sentTo("mari").single().title shouldBe "La presentación no se pudo simular"
            w.sentTo("vale").shouldBeEmpty()
            client.respond("mari", id, interested = true).status shouldBe HttpStatusCode.Conflict
            // Al fallar ya no está en curso: se puede volver a intentar.
            w.sim.textReply = { "Listo, me parece bien el plan." }
            client.introduce(w, "mari", "vale", "sebas").status shouldBe HttpStatusCode.Accepted
        }
    }

    test("una presentación en curso entre las mismas personas → 409") {
        val w = World()
        testApplication {
            val client = app(w)
            client.introduce(w, "mari", "vale", "sebas").status shouldBe HttpStatusCode.Accepted
            client.introduce(w, "mari", "sebas", "vale").status shouldBe HttpStatusCode.Conflict
        }
    }

    test("un 'No, gracias' no libera la presentación: el mismo POST sigue en 409 y no hay otra propuesta") {
        val w = World()
        testApplication {
            val client = app(w)
            val id = client.proposed(w)
            val conflict = client.introduce(w, "mari", "vale", "sebas")
            conflict.status shouldBe HttpStatusCode.Conflict
            val before = conflict.error()

            client.respond("vale", id, interested = false).status shouldBe HttpStatusCode.OK
            // La misma respuesta antes y después del rechazo: el POST no lo delata.
            val after = client.introduce(w, "mari", "sebas", "vale")
            after.status shouldBe HttpStatusCode.Conflict
            after.error() shouldBe before
            client.intro("mari", id).body<IntroductionDto>().status shouldBe IntroductionStatus.PROPOSED

            w.sentTo("vale").map { it.kind } shouldContainExactly listOf(NotificationKinds.INTRODUCTION_PROPOSAL)
            w.store.all() shouldHaveSize 1
            w.sim.usage.charges shouldContainExactly listOf(id)
        }
    }

    test("tras 'No por ahora' de quien presenta sí se puede volver a presentar al mismo par") {
        val w = World()
        testApplication {
            val client = app(w)
            val id = client.introduce(w, "mari", "vale", "sebas").body<IntroductionDto>().id
            client.respond("mari", id, interested = false).status shouldBe HttpStatusCode.OK
            client.introduce(w, "mari", "vale", "sebas").status shouldBe HttpStatusCode.Accepted
        }
    }

    test("un id inválido o ajeno → 404") {
        val w = World()
        testApplication {
            val client = app(w)
            client.intro("mari", "no-es-uuid").status shouldBe HttpStatusCode.NotFound
            client.intro("mari", UUID.randomUUID().toString()).status shouldBe HttpStatusCode.NotFound
            client.respond("mari", UUID.randomUUID().toString(), true).status shouldBe HttpStatusCode.NotFound
        }
    }
})
