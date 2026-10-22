package me.santiagoreina.parcheu.api.chat

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.TASKS_ACCOUNT
import me.santiagoreina.parcheu.api.TEST_LEGAL_VERSION
import me.santiagoreina.parcheu.api.graph.GraphVisibilityPolicy
import me.santiagoreina.parcheu.api.graph.PersonRow
import me.santiagoreina.parcheu.api.graph.inMemoryGraphModule
import me.santiagoreina.parcheu.api.identity.IdentityService
import me.santiagoreina.parcheu.api.identity.InMemoryIdentityRepository
import me.santiagoreina.parcheu.api.platform.AppContainer
import me.santiagoreina.parcheu.api.platform.auth.FakeOidcVerifier
import me.santiagoreina.parcheu.api.platform.auth.FakeTokenVerifier
import me.santiagoreina.parcheu.api.platform.notifier.FakeNotifier
import me.santiagoreina.parcheu.api.startApp
import me.santiagoreina.parcheu.api.testConfig
import me.santiagoreina.parcheu.contract.ApiError
import me.santiagoreina.parcheu.contract.ChatStatus
import me.santiagoreina.parcheu.contract.ChatSummary
import me.santiagoreina.parcheu.contract.ChatType
import me.santiagoreina.parcheu.contract.ErrorCode
import me.santiagoreina.parcheu.contract.MessageDto
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Mute
import me.santiagoreina.parcheu.contract.NotificationKinds
import me.santiagoreina.parcheu.contract.SendMessage
import me.santiagoreina.parcheu.contract.Visibility
import java.time.Duration
import java.util.UUID

/** Criterios de E5.1 de punta a punta (rutas, servicio, repositorio y espejo en memoria). */
class ChatApiTest : FunSpec({
    fun HttpRequestBuilder.auth(uid: String) = header(HttpHeaders.Authorization, "Bearer ${FakeTokenVerifier.tokenFor(uid)}")

    class Fixture {
        val identityRepo = InMemoryIdentityRepository().apply {
            listOf("ana", "beto", "carla").forEach { seedAcceptedUser(it, TEST_LEGAL_VERSION) }
        }
        val ana: UUID = identityRepo.user("ana").id
        val beto: UUID = identityRepo.user("beto").id
        val carla: UUID = identityRepo.user("carla").id
        val repo = InMemoryChatRepository().apply {
            addUser(row(ana, "Ana Ruiz"), "ana")
            addUser(row(beto, "Beto Gómez"), "beto")
            addUser(row(carla, "Carla Díaz"), "carla")
        }
        val mirror = InMemoryChatMirror()
        val notifier = FakeNotifier()
        val graph = inMemoryGraphModule(FIXED_CLOCK)
        val chat = inMemoryChatModule(FIXED_CLOCK, graph.policy, notifier, repo, mirror)

        fun container(): AppContainer {
            val identity = IdentityService(identityRepo, FIXED_CLOCK, TEST_LEGAL_VERSION)
            return AppContainer(
                testConfig(), FIXED_CLOCK, FakeTokenVerifier(), FakeOidcVerifier(TASKS_ACCOUNT), identity, graph, chat,
            )
        }

        fun direct(mode: Mode = Mode.AMISTAD) = runBlocking { chat.service.createDirect(ana, beto, mode) }

        private fun row(id: UUID, name: String) = PersonRow(id, name, null, "UIS", Visibility.ANONYMOUS, isDemo = false, deletedAt = null)
    }

    suspend fun io.ktor.client.HttpClient.send(uid: String, chatId: Any, clientId: String, text: String): HttpResponse =
        post("/v1/chats/$chatId/messages") {
            auth(uid)
            contentType(ContentType.Application.Json)
            setBody(SendMessage(clientId, text))
        }

    test("un no miembro recibe 403 FORBIDDEN y un bloqueado 403 BLOCKED") {
        val f = Fixture()
        val chat = f.direct()
        testApplication {
            val client = startApp(f.container())
            val outsider = client.send("carla", chat.id, "c-1", "hola")
            outsider.status shouldBe HttpStatusCode.Forbidden
            outsider.body<ApiError>().code shouldBe ErrorCode.FORBIDDEN

            f.repo.block(f.beto, f.ana)
            val blocked = client.send("ana", chat.id, "a-1", "hola")
            blocked.status shouldBe HttpStatusCode.Forbidden
            blocked.body<ApiError>().code shouldBe ErrorCode.BLOCKED
            // Tampoco aparece en la lista mientras dure el bloqueo.
            client.get("/v1/chats") { auth("ana") }.body<List<ChatSummary>>().shouldBeEmpty()
        }
        f.repo.allMessages.shouldBeEmpty()
        f.mirror.messages(chat.id).shouldBeEmpty()
    }

    test("repetir clientId devuelve el mismo mensaje sin duplicar en la base ni en el espejo, y avisa una sola vez") {
        val f = Fixture()
        val chat = f.direct()
        testApplication {
            val client = startApp(f.container())
            val first = client.send("ana", chat.id, "a-1", "  hola Beto  ")
            first.status shouldBe HttpStatusCode.OK
            val second = client.send("ana", chat.id, "a-1", "hola Beto")
            second.status shouldBe HttpStatusCode.OK
            val a = first.body<MessageDto>()
            second.body<MessageDto>() shouldBe a
            a.text shouldBe "hola Beto"
            a.senderId shouldBe f.ana.toString()
        }
        f.repo.allMessages shouldHaveSize 1
        f.mirror.messages(chat.id) shouldHaveSize 1
        f.notifier.sent shouldHaveSize 1
        f.notifier.sent.single().let {
            it.userId shouldBe f.beto
            it.notification.kind shouldBe NotificationKinds.CHAT_MESSAGE
            it.notification.title shouldBe "Ana Ruiz"
            it.notification.route shouldBe "parcheu://chat/${chat.id}"
        }
    }

    test("learnable sigue el consentimiento del autor para el modo del chat en ese momento") {
        val f = Fixture()
        val pareja = f.direct(Mode.PAREJA)
        val amistad = f.direct(Mode.AMISTAD)
        f.repo.setLearning(f.ana, Mode.AMISTAD, true)
        f.repo.setLearning(f.ana, Mode.PAREJA, false)
        testApplication {
            val client = startApp(f.container())
            client.send("ana", pareja.id, "p-1", "en pareja").status shouldBe HttpStatusCode.OK
            client.send("ana", amistad.id, "a-1", "en amistad").status shouldBe HttpStatusCode.OK
            f.repo.setLearning(f.ana, Mode.PAREJA, true)
            client.send("ana", pareja.id, "p-2", "ya con consentimiento").status shouldBe HttpStatusCode.OK
        }
        val byClient = f.repo.allMessages.associateBy { it.clientId }
        byClient.getValue("p-1").learnable shouldBe false
        byClient.getValue("a-1").learnable shouldBe true
        byClient.getValue("p-2").learnable shouldBe true
    }

    test("el documento de Firestore tiene senderUid, system=false, text, createdAt y hidden=false") {
        val f = Fixture()
        val chat = f.direct()
        testApplication {
            startApp(f.container()).send("ana", chat.id, "a-1", "hola").status shouldBe HttpStatusCode.OK
        }
        val doc = f.mirror.messages(chat.id).single()
        doc.id shouldBe f.repo.allMessages.single().id
        val fields = FirestoreChatMirror.messageFields(doc)
        fields.keys shouldBe setOf("senderUid", "system", "text", "createdAt", "hidden")
        fields["senderUid"] shouldBe "ana"
        fields["system"] shouldBe false
        fields["text"] shouldBe "hola"
        fields["hidden"] shouldBe false
        (fields["createdAt"] as com.google.cloud.Timestamp).toDate().toInstant() shouldBe FIXED_CLOCK.instant()
        // El chat se replicó al crearse, con los uids de Firebase de los miembros (reglas de E0.8).
        val chatDoc = f.mirror.chat(chat.id)!!
        chatDoc.members.toSet() shouldBe setOf("ana", "beto")
        FirestoreChatMirror.chatFields(chatDoc).keys shouldBe setOf("members", "type", "mode", "status", "endsAt")
    }

    test("validación: texto vacío, más de 1.000 caracteres o sin clientId -> 400 VALIDATION; 1.000 justos pasan") {
        val f = Fixture()
        val chat = f.direct()
        testApplication {
            val client = startApp(f.container())
            client.send("ana", chat.id, "a-1", "   ").status shouldBe HttpStatusCode.BadRequest
            client.send("ana", chat.id, "a-2", "x".repeat(1001)).body<ApiError>().code shouldBe ErrorCode.VALIDATION
            client.send("ana", chat.id, " ", "hola").status shouldBe HttpStatusCode.BadRequest
            client.send("ana", chat.id, "a-3", "😀".repeat(1000)).status shouldBe HttpStatusCode.OK
            client.send("ana", "no-es-uuid", "a-4", "hola").status shouldBe HttpStatusCode.NotFound
            client.send("ana", UUID.randomUUID(), "a-5", "hola").status shouldBe HttpStatusCode.NotFound
        }
    }

    test("un usuario suspendido recibe 403 SUSPENDED") {
        val f = Fixture()
        val chat = f.direct()
        f.identityRepo.replace(f.identityRepo.user("ana").copy(suspendedUntil = FIXED_CLOCK.instant().plus(Duration.ofHours(24))))
        testApplication {
            val response = startApp(f.container()).send("ana", chat.id, "a-1", "hola")
            response.status shouldBe HttpStatusCode.Forbidden
            response.body<ApiError>().code shouldBe ErrorCode.SUSPENDED
        }
        f.repo.allMessages.shouldBeEmpty()
    }

    test("GET /v1/chats: otro con nombre, último mensaje y no leídos; read los pone en 0 y mute silencia el aviso") {
        val f = Fixture()
        val chat = f.direct()
        testApplication {
            val client = startApp(f.container())
            client.send("ana", chat.id, "a-1", "uno").status shouldBe HttpStatusCode.OK
            client.send("ana", chat.id, "a-2", "dos").status shouldBe HttpStatusCode.OK

            val list = client.get("/v1/chats") { auth("beto") }.body<List<ChatSummary>>()
            list shouldHaveSize 1
            with(list.single()) {
                id shouldBe chat.id.toString()
                type shouldBe ChatType.DIRECT
                mode shouldBe Mode.AMISTAD
                other.id shouldBe f.ana.toString()
                other.label shouldBe "Ana Ruiz"
                other.initials shouldBe "AR"
                unread shouldBe 2
                muted shouldBe false
                status shouldBe ChatStatus.OPEN
                lastMessage!!.senderId shouldBe f.ana.toString()
            }
            // Para la autora no hay no leídos propios.
            client.get("/v1/chats") { auth("ana") }.body<List<ChatSummary>>().single().unread shouldBe 0

            client.post("/v1/chats/${chat.id}/read") { auth("beto") }.status shouldBe HttpStatusCode.NoContent
            client.post("/v1/chats/${chat.id}/mute") {
                auth("beto")
                contentType(ContentType.Application.Json)
                setBody(Mute(true))
            }.status shouldBe HttpStatusCode.NoContent
            client.get("/v1/chats") { auth("beto") }.body<List<ChatSummary>>().single().let {
                it.unread shouldBe 0
                it.muted shouldBe true
            }
            client.send("ana", chat.id, "a-3", "tres").status shouldBe HttpStatusCode.OK
            client.post("/v1/chats/${chat.id}/read") { auth("carla") }.status shouldBe HttpStatusCode.Forbidden
        }
        // Dos avisos antes de silenciar y ninguno después.
        f.notifier.sent shouldHaveSize 2
    }

    test("chat SCENARIO: el otro sale anónimo (sin uuid) y al vencer ends_at el envío da 409 SCENARIO_ENDED") {
        val f = Fixture()
        val endsAt = FIXED_CLOCK.instant().plus(Duration.ofMinutes(10))
        val scenario = runBlocking { f.chat.service.createScenario(f.ana, f.beto, Mode.AMISTAD, 1, endsAt) }
        testApplication {
            val client = startApp(f.container())
            client.send("beto", scenario.id, "b-1", "hola").status shouldBe HttpStatusCode.OK
            val summary = client.get("/v1/chats") { auth("ana") }.body<List<ChatSummary>>().single()
            summary.type shouldBe ChatType.SCENARIO
            summary.other.id shouldBe null
            summary.other.anonId shouldNotBe null
            summary.other.label shouldBe "${GraphVisibilityPolicy.ANON_LABEL} de la UIS"
            summary.lastMessage!!.senderId shouldBe summary.other.anonId
            summary.endsAt shouldNotBe null
        }
        f.notifier.sent.single().notification.title shouldBe "Alguien de la UIS"

        val ended = Fixture()
        val closed = runBlocking {
            ended.chat.service.createScenario(ended.ana, ended.beto, Mode.AMISTAD, 1, endsAt).also { ended.chat.service.close(it.id) }
        }
        ended.mirror.chat(closed.id)!!.status shouldBe ChatStatus.CLOSED
        testApplication {
            val response = startApp(ended.container()).send("ana", closed.id, "a-1", "¿sigues?")
            response.status shouldBe HttpStatusCode.Conflict
            response.body<ApiError>().code shouldBe ErrorCode.SCENARIO_ENDED
        }
    }

    test("createDirect es idempotente por par y modo; con bloqueo no crea el chat") {
        val f = Fixture()
        val first = f.direct()
        val again = runBlocking { f.chat.service.createDirect(f.beto, f.ana, Mode.AMISTAD) }
        again.id shouldBe first.id
        f.direct(Mode.ESTUDIO).id shouldNotBe first.id
        f.repo.block(f.ana, f.carla)
        val error = runCatching { runBlocking { f.chat.service.createDirect(f.carla, f.ana, Mode.AMISTAD) } }.exceptionOrNull()
        (error as me.santiagoreina.parcheu.api.platform.errors.DomainException).code shouldBe ErrorCode.BLOCKED
    }
})
