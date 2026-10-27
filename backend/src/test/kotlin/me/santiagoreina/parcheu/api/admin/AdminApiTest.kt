package me.santiagoreina.parcheu.api.admin

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import me.santiagoreina.parcheu.api.TEST_LEGAL_VERSION
import me.santiagoreina.parcheu.api.chat.ChatRecord
import me.santiagoreina.parcheu.api.chat.InMemoryChatMirror
import me.santiagoreina.parcheu.api.chat.InMemoryChatRepository
import me.santiagoreina.parcheu.api.chat.inMemoryChatModule
import me.santiagoreina.parcheu.api.identity.InMemoryIdentityRepository
import me.santiagoreina.parcheu.api.matching.MatchFixture
import me.santiagoreina.parcheu.api.platform.auth.FakeTokenVerifier
import me.santiagoreina.parcheu.api.safety.InMemorySafetyRepository
import me.santiagoreina.parcheu.api.safety.SnapshotMessage
import me.santiagoreina.parcheu.api.safety.inMemorySafetyModule
import me.santiagoreina.parcheu.api.startApp
import me.santiagoreina.parcheu.api.testContainer
import me.santiagoreina.parcheu.contract.AdminAction
import me.santiagoreina.parcheu.contract.AdminReport
import me.santiagoreina.parcheu.contract.ApiError
import me.santiagoreina.parcheu.contract.ErrorCode
import me.santiagoreina.parcheu.contract.MessageDto
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Page
import me.santiagoreina.parcheu.contract.ReportKind
import me.santiagoreina.parcheu.contract.ReportReason
import me.santiagoreina.parcheu.contract.ReportRequest
import me.santiagoreina.parcheu.contract.ReportStatus
import me.santiagoreina.parcheu.contract.Resolve
import me.santiagoreina.parcheu.contract.SendMessage
import me.santiagoreina.parcheu.contract.MeDto
import java.time.Duration
import java.util.UUID

/** Criterios de E8.5 de punta a punta: claim `admin`, listado, resolución y efectos sobre chat y sesión. */
class AdminApiTest : FunSpec({
    val uids = listOf("ana", "beto", "admin")

    class World {
        val identity = InMemoryIdentityRepository().also { repo -> uids.forEach { repo.seedAcceptedUser(it, TEST_LEGAL_VERSION) } }
        val f = MatchFixture(idFor = { name -> identity.user(name).id }).also { f -> listOf("ana", "beto").forEach { f.person(it) } }
        val ana: UUID = identity.user("ana").id
        val beto: UUID = identity.user("beto").id
        val chatRepo = InMemoryChatRepository().also { repo ->
            listOf("ana", "beto").forEach { uid -> repo.addUser(f.graphRepo.people.getValue(identity.user(uid).id), uid) }
        }
        val mirror = InMemoryChatMirror()
        val chat = inMemoryChatModule(f.clock, f.graph.policy, f.notifier, chatRepo, mirror)
        val safetyRepo = InMemorySafetyRepository(
            people = { id -> f.graphRepo.people[id] },
            messages = { chatId ->
                chatRepo.allMessages.filter { it.chatId == chatId }.map { SnapshotMessage(it.id, it.senderId, it.text, it.createdAt, it.hidden) }
            },
        )
        val safety = inMemorySafetyModule(f.clock, chat, f.graph, f.module, safetyRepo)

        fun direct(): ChatRecord = runBlocking { chat.service.createDirect(ana, beto, Mode.AMISTAD) }
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

    suspend fun HttpClient.resolve(uid: String, id: String, action: AdminAction, note: String? = null): HttpResponse =
        post("/v1/admin/reports/$id/resolve") {
            auth(uid)
            contentType(ContentType.Application.Json)
            setBody(Resolve(action, note))
        }

    /** ana reporta a beto por un mensaje suyo; devuelve el id del reporte y el del mensaje. */
    suspend fun HttpClient.reportOfBetoMessage(w: World, chat: ChatRecord): Pair<String, String> {
        val message = send("beto", chat.id, "b-1", "mensaje ofensivo").body<MessageDto>()
        val created = report(
            "ana",
            ReportRequest(ReportKind.USER, targetUserId = w.beto.toString(), chatId = chat.id.toString(), messageId = message.id, reason = ReportReason.HARASSMENT),
        )
        created.status shouldBe HttpStatusCode.Created
        val id = get("/v1/admin/reports") { auth("admin") }.body<Page<AdminReport>>().items.single().id
        return id to message.id
    }

    test("sin el claim admin las dos rutas responden 403 FORBIDDEN; sin token, 401") {
        val w = World()
        testApplication {
            val client = app(w)
            val list = client.get("/v1/admin/reports") { auth("ana") }
            list.status shouldBe HttpStatusCode.Forbidden
            list.body<ApiError>().code shouldBe ErrorCode.FORBIDDEN
            val resolve = client.resolve("ana", UUID.randomUUID().toString(), AdminAction.DISMISS)
            resolve.status shouldBe HttpStatusCode.Forbidden
            resolve.body<ApiError>().code shouldBe ErrorCode.FORBIDDEN
            client.get("/v1/admin/reports").status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("lista los reportes OPEN con prioridad primero y filtra por kind y status") {
        val w = World()
        val chat = w.direct()
        testApplication {
            val client = app(w)
            client.report("ana", ReportRequest(ReportKind.USER, targetUserId = w.beto.toString(), chatId = chat.id.toString(), reason = ReportReason.OTHER))
            w.f.clock.now = w.f.clock.now.plusSeconds(1)
            client.report("ana", ReportRequest(ReportKind.USER, targetUserId = w.beto.toString(), reason = ReportReason.SELF_HARM))

            val all = client.get("/v1/admin/reports") { auth("admin") }.body<Page<AdminReport>>()
            all.items.map { it.reason } shouldContainExactly listOf(ReportReason.SELF_HARM, ReportReason.OTHER)
            all.items.first().priority shouldBe 2
            all.items.last().snapshot.shouldNotBeNull()
            all.nextCursor.shouldBeNull()
            client.get("/v1/admin/reports?kind=AI_CONTENT") { auth("admin") }.body<Page<AdminReport>>().items.shouldBeEmpty()
            client.get("/v1/admin/reports?kind=USER") { auth("admin") }.body<Page<AdminReport>>().items.size shouldBe 2
            client.get("/v1/admin/reports?status=RESOLVED") { auth("admin") }.body<Page<AdminReport>>().items.shouldBeEmpty()

            val page1 = client.get("/v1/admin/reports?limit=1") { auth("admin") }.body<Page<AdminReport>>()
            page1.items.single().reason shouldBe ReportReason.SELF_HARM
            val page2 = client.get("/v1/admin/reports?limit=1&cursor=${page1.nextCursor}") { auth("admin") }.body<Page<AdminReport>>()
            page2.items.single().reason shouldBe ReportReason.OTHER
            page2.nextCursor.shouldBeNull()
            client.get("/v1/admin/reports?kind=NOPE") { auth("admin") }.status shouldBe HttpStatusCode.BadRequest
            client.get("/v1/admin/reports?limit=0") { auth("admin") }.status shouldBe HttpStatusCode.BadRequest
            client.get("/v1/admin/reports?cursor=abc") { auth("admin") }.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("suspender 24 h pone suspended_until y el usuario recibe 403 SUSPENDED al escribir mensajes") {
        val w = World()
        val chat = w.direct()
        testApplication {
            val client = app(w)
            val (reportId, _) = client.reportOfBetoMessage(w, chat)

            val resolved = client.resolve("admin", reportId, AdminAction.SUSPEND_24H, "acoso reiterado")
            resolved.status shouldBe HttpStatusCode.OK
            resolved.body<AdminReport>().let {
                it.status shouldBe ReportStatus.RESOLVED
                it.resolution shouldBe "SUSPEND_24H: acoso reiterado"
                it.resolvedAt.shouldNotBeNull()
            }
            w.identity.user("beto").suspendedUntil shouldBe w.f.clock.instant().plus(Duration.ofHours(24))
            val blocked = client.send("beto", chat.id, "b-2", "sigo aquí")
            blocked.status shouldBe HttpStatusCode.Forbidden
            blocked.body<ApiError>().code shouldBe ErrorCode.SUSPENDED
            // La otra persona no se ve afectada.
            client.send("ana", chat.id, "a-1", "hola").status shouldBe HttpStatusCode.OK
            // La app se entera por la sesión: `suspendedUntil` viaja en /v1/me.
            client.get("/v1/me") { auth("beto") }.body<MeDto>().suspendedUntil.shouldNotBeNull()
            client.get("/v1/admin/reports?status=RESOLVED") { auth("admin") }.body<Page<AdminReport>>().items.single().id shouldBe reportId
        }
    }

    test("banear suspende hasta el año 2999 y una suspensión de 24 h no acorta un baneo") {
        val w = World()
        val chat = w.direct()
        testApplication {
            val client = app(w)
            val (first, _) = client.reportOfBetoMessage(w, chat)
            client.resolve("admin", first, AdminAction.BAN).status shouldBe HttpStatusCode.OK
            w.identity.user("beto").suspendedUntil shouldBe AdminService.BAN_UNTIL

            client.report("ana", ReportRequest(ReportKind.USER, targetUserId = w.beto.toString(), reason = ReportReason.OTHER))
            val second = client.get("/v1/admin/reports") { auth("admin") }.body<Page<AdminReport>>().items.single().id
            client.resolve("admin", second, AdminAction.SUSPEND_24H).status shouldBe HttpStatusCode.OK
            w.identity.user("beto").suspendedUntil shouldBe AdminService.BAN_UNTIL
        }
    }

    test("ocultar el mensaje lo marca hidden en Postgres y en el espejo, y vacía su texto para la app") {
        val w = World()
        val chat = w.direct()
        testApplication {
            val client = app(w)
            val (reportId, messageId) = client.reportOfBetoMessage(w, chat)
            client.resolve("admin", reportId, AdminAction.HIDE_MESSAGE).status shouldBe HttpStatusCode.OK

            w.chatRepo.allMessages.single().hidden shouldBe true
            val doc = w.mirror.messages(chat.id).single { it.id.toString() == messageId }
            doc.hidden shouldBe true
            doc.senderUid shouldBe "beto"
            w.identity.user("beto").suspendedUntil.shouldBeNull()
        }
    }

    test("descartar solo cierra el reporte; resolver dos veces es 409 y no repite la acción") {
        val w = World()
        val chat = w.direct()
        testApplication {
            val client = app(w)
            val (reportId, _) = client.reportOfBetoMessage(w, chat)
            client.resolve("admin", reportId, AdminAction.DISMISS, "sin falta").status shouldBe HttpStatusCode.OK
            w.identity.user("beto").suspendedUntil.shouldBeNull()
            w.chatRepo.allMessages.single().hidden shouldBe false

            val again = client.resolve("admin", reportId, AdminAction.BAN)
            again.status shouldBe HttpStatusCode.Conflict
            again.body<ApiError>().code shouldBe ErrorCode.CONFLICT
            w.identity.user("beto").suspendedUntil.shouldBeNull()
        }
    }

    test("las acciones que no aplican al reporte son 400 y no lo resuelven; un id desconocido es 404") {
        val w = World()
        testApplication {
            val client = app(w)
            // Reporte sin mensaje: no hay nada que ocultar.
            client.report("ana", ReportRequest(ReportKind.USER, targetUserId = w.beto.toString(), reason = ReportReason.FAKE_OR_SCAM))
            val id = client.get("/v1/admin/reports") { auth("admin") }.body<Page<AdminReport>>().items.single().id
            client.resolve("admin", id, AdminAction.HIDE_MESSAGE).status shouldBe HttpStatusCode.BadRequest
            client.get("/v1/admin/reports") { auth("admin") }.body<Page<AdminReport>>().items.single().status shouldBe ReportStatus.OPEN

            client.resolve("admin", UUID.randomUUID().toString(), AdminAction.DISMISS).status shouldBe HttpStatusCode.NotFound
            client.resolve("admin", "no-es-uuid", AdminAction.DISMISS).status shouldBe HttpStatusCode.NotFound
            client.resolve("admin", id, AdminAction.DISMISS, "x".repeat(501)).status shouldBe HttpStatusCode.BadRequest
        }
    }
})
