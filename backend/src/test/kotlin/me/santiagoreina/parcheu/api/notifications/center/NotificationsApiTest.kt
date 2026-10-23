package me.santiagoreina.parcheu.api.notifications.center

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.TEST_LEGAL_VERSION
import me.santiagoreina.parcheu.api.graph.inMemoryGraphModule
import me.santiagoreina.parcheu.api.identity.InMemoryIdentityRepository
import me.santiagoreina.parcheu.api.platform.auth.FakeTokenVerifier
import me.santiagoreina.parcheu.api.quota.InMemoryDevEntitlementService
import me.santiagoreina.parcheu.api.quota.inMemoryQuotaModule
import me.santiagoreina.parcheu.api.startApp
import me.santiagoreina.parcheu.api.testContainer
import me.santiagoreina.parcheu.contract.HomeDto
import me.santiagoreina.parcheu.contract.MarkRead
import me.santiagoreina.parcheu.contract.NotificationKinds
import me.santiagoreina.parcheu.contract.NotificationsDto
import java.time.Instant
import java.util.UUID

/** `GET /v1/notifications` y `POST /v1/notifications/read` (E5.4) de punta a punta con la bandeja en memoria. */
class NotificationsApiTest : FunSpec({
    // FIXED_CLOCK = 2026-10-25T12:00Z = 07:00 del 25 de octubre en Bogotá (UTC-5).
    class World {
        val identity = InMemoryIdentityRepository().also { repo ->
            listOf("ana", "beto").forEach { repo.seedAcceptedUser(it, TEST_LEGAL_VERSION) }
        }
        val entitlements = InMemoryDevEntitlementService()
        val quota = inMemoryQuotaModule(FIXED_CLOCK, entitlements = entitlements)
        val graph = inMemoryGraphModule(FIXED_CLOCK)
        val store = InMemoryNotificationStore()
        val center = notificationCenterModule(store, FIXED_CLOCK, quota, graph)

        fun id(uid: String): UUID = identity.user(uid).id

        fun add(
            uid: String,
            at: String,
            kind: String = NotificationKinds.SIMULATION_READY,
            title: String = "Resultado listo",
            route: String? = "parcheu://results/s1",
            actor: UUID? = null,
            locked: Boolean = false,
        ): UUID = store.add(
            id(uid),
            kind,
            mapOf<String, JsonElement>(
                "title" to JsonPrimitive(title),
                "body" to JsonPrimitive("cuerpo"),
                "route" to (route?.let(::JsonPrimitive) ?: JsonNull),
                "actorId" to (actor?.let { JsonPrimitive(it.toString()) } ?: JsonNull),
                "locked" to JsonPrimitive(locked),
            ),
            Instant.parse(at),
        )
    }

    fun HttpRequestBuilder.auth(uid: String) = header(HttpHeaders.Authorization, "Bearer ${FakeTokenVerifier.tokenFor(uid)}")

    suspend fun io.ktor.server.testing.ApplicationTestBuilder.app(w: World): HttpClient = startApp(
        testContainer(repo = w.identity, quota = w.quota, graph = w.graph, notifications = w.center),
    )

    suspend fun HttpClient.inbox(uid: String): NotificationsDto = get("/v1/notifications") { auth(uid) }.body()

    test("un aviso de las 23:50 hora Bogotá del día anterior cae en 'Esta semana', no en 'Hoy'") {
        val w = World()
        // 23:50 del 24 de octubre en Bogotá = 04:50Z del 25: en UTC sería "hoy".
        val lastNight = w.add("ana", "2026-10-25T04:50:00Z", title = "anoche")
        val thisMorning = w.add("ana", "2026-10-25T05:00:00Z", title = "hoy 00:00")
        val sixDaysAgo = w.add("ana", "2026-10-19T05:00:00Z", title = "hace 6 días")
        val old = w.add("ana", "2026-10-19T04:59:00Z", title = "hace 7 días")
        w.add("beto", "2026-10-25T11:00:00Z", title = "de otro")
        testApplication {
            val inbox = app(w).inbox("ana")
            inbox.today.map { it.id } shouldContainExactly listOf(thisMorning.toString())
            inbox.week.map { it.id } shouldContainExactly listOf(lastNight.toString(), sixDaysAgo.toString())
            inbox.older.map { it.id } shouldContainExactly listOf(old.toString())
            inbox.today.single().read shouldBe false
            inbox.today.single().route shouldBe "parcheu://results/s1"
        }
    }

    test("POST /v1/notifications/read sin ids marca todas como leídas y la campana de GET /v1/home pasa a 0") {
        val w = World()
        w.add("ana", "2026-10-25T11:00:00Z")
        w.add("ana", "2026-10-24T11:00:00Z")
        w.add("beto", "2026-10-25T11:00:00Z")
        testApplication {
            val client = app(w)
            client.get("/v1/home") { auth("ana") }.body<HomeDto>().unread shouldBe 2

            client.post("/v1/notifications/read") { auth("ana") }.status shouldBe HttpStatusCode.NoContent

            client.get("/v1/home") { auth("ana") }.body<HomeDto>().unread shouldBe 0
            val inbox = client.inbox("ana")
            (inbox.today + inbox.week).all { it.read } shouldBe true
            // Solo las de quien llama.
            client.get("/v1/home") { auth("beto") }.body<HomeDto>().unread shouldBe 1
        }
    }

    test("con cuerpo vacío o ids vacíos también marca todas; con ids, solo esas") {
        val w = World()
        val a = w.add("ana", "2026-10-25T11:00:00Z")
        val b = w.add("ana", "2026-10-25T10:00:00Z")
        val c = w.add("ana", "2026-10-25T09:00:00Z")
        val ajena = w.add("beto", "2026-10-25T09:00:00Z")
        testApplication {
            val client = app(w)
            client.post("/v1/notifications/read") {
                auth("ana")
                contentType(ContentType.Application.Json)
                setBody(MarkRead(ids = listOf(a.toString(), ajena.toString(), "no-es-uuid")))
            }.status shouldBe HttpStatusCode.NoContent
            client.inbox("ana").today.associate { it.id to it.read } shouldBe
                mapOf(a.toString() to true, b.toString() to false, c.toString() to false)
            w.store.unread(w.id("beto")) shouldBe 1

            client.post("/v1/notifications/read") {
                auth("ana")
                contentType(ContentType.Application.Json)
                setBody(MarkRead(ids = emptyList()))
            }.status shouldBe HttpStatusCode.NoContent
            w.store.unread(w.id("ana")) shouldBe 0
        }
    }

    test("un cuerpo mal formado es 400 y no marca nada") {
        val w = World()
        w.add("ana", "2026-10-25T11:00:00Z")
        testApplication {
            val status = app(w).post("/v1/notifications/read") {
                auth("ana")
                contentType(ContentType.Application.Json)
                setBody("""{"ids": 5}""")
            }.status
            status shouldBe HttpStatusCode.BadRequest
            w.store.unread(w.id("ana")) shouldBe 1
        }
    }

    test("'quieren conectar contigo' sale bloqueado y sin actor en el plan gratis; con ParcheU+ se ve") {
        val w = World()
        val beto = w.id("beto")
        w.add(
            "ana", "2026-10-25T11:00:00Z", kind = NotificationKinds.CONNECT_REQUEST,
            title = "2 personas quieren conectar contigo", route = "parcheu://notifications", actor = beto, locked = true,
        )
        testApplication {
            val client = app(w)
            val free = client.inbox("ana").today.single()
            free.locked shouldBe true
            free.actor shouldBe null

            w.entitlements.grantPlus(w.id("ana"), 30, FIXED_CLOCK.instant())
            val plus = client.inbox("ana").today.single()
            plus.locked shouldBe false
            plus.actor?.label shouldBe "Alguien"
        }
    }

    test("sin avisos devuelve los tres grupos vacíos") {
        testApplication {
            val inbox = app(World()).inbox("ana")
            inbox.today.shouldBeEmpty()
            inbox.week.shouldBeEmpty()
            inbox.older.shouldBeEmpty()
        }
    }
})
