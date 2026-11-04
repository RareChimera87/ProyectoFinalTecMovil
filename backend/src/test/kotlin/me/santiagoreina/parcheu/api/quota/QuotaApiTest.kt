package me.santiagoreina.parcheu.api.quota

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.testing.testApplication
import me.santiagoreina.parcheu.api.TEST_OIDC_TOKEN
import me.santiagoreina.parcheu.api.platform.DomainRoutes
import me.santiagoreina.parcheu.api.platform.auth.FakeTokenVerifier
import me.santiagoreina.parcheu.api.platform.auth.FirebaseUser
import me.santiagoreina.parcheu.api.platform.config.Env
import me.santiagoreina.parcheu.api.startApp
import me.santiagoreina.parcheu.api.testConfig
import me.santiagoreina.parcheu.api.testContainer
import me.santiagoreina.parcheu.contract.ApiError
import me.santiagoreina.parcheu.contract.ErrorCode
import me.santiagoreina.parcheu.contract.GrantPlus
import me.santiagoreina.parcheu.contract.QuotaDto
import me.santiagoreina.parcheu.contract.QuotaKind
import kotlin.time.Instant as KInstant

/** Ruta de prueba que cobra una simulación, como hará `POST /v1/simulations` (E4.3). */
private class ChargeRoute(private val quota: QuotaModule) : DomainRoutes {
    override fun Route.v1() {
        post("/test/charge/{ref}") {
            val uid = call.principal<FirebaseUser>()!!.uid
            val userId = quota.users.userIdFor(uid)!!
            quota.service.consume(userId, QuotaKind.SIMULATION, call.parameters["ref"]!!)
            call.respond(HttpStatusCode.Accepted)
        }
    }
}

class QuotaApiTest : FunSpec({
    fun bearer(token: String) = "Bearer $token"
    val anaToken = bearer(FakeTokenVerifier.tokenFor("ana"))

    test("GET /v1/quota devuelve el estado gratis y la 6.a simulación responde 402 QUOTA_EXCEEDED con resetsAt") {
        val clock = MovableClock(bogota("2026-10-28T15:00-05:00"))
        val quota = inMemoryQuotaModule(clock)
        testApplication {
            val client = startApp(testContainer(extra = listOf(ChargeRoute(quota)), quota = quota))
            val inicial = client.get("/v1/quota") { header(HttpHeaders.Authorization, anaToken) }
            inicial.status shouldBe HttpStatusCode.OK
            val dto = inicial.body<QuotaDto>()
            dto.plus shouldBe false
            dto.simulations.used shouldBe 0
            dto.simulations.limit shouldBe 5

            repeat(5) {
                client.post("/v1/test/charge/sim-$it") { header(HttpHeaders.Authorization, anaToken) }.status shouldBe HttpStatusCode.Accepted
            }
            val sexta = client.post("/v1/test/charge/sim-5") { header(HttpHeaders.Authorization, anaToken) }
            sexta.status shouldBe HttpStatusCode.PaymentRequired
            val error = sexta.body<ApiError>()
            error.code shouldBe ErrorCode.QUOTA_EXCEEDED
            error.kind shouldBe "SIMULATION"
            error.resetsAt shouldBe KInstant.parse("2026-11-02T00:00:00-05:00")

            client.get("/v1/quota") { header(HttpHeaders.Authorization, anaToken) }.body<QuotaDto>().simulations.used shouldBe 5
        }
    }

    test("GET /v1/quota exige token") {
        testApplication {
            val client = startApp()
            client.get("/v1/quota").status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("GET /v1/quota responde 404 si el usuario aún no tiene sesión") {
        val quota = inMemoryQuotaModule(MovableClock(bogota("2026-10-28T15:00-05:00")))
        val sinUsuario = QuotaModule(quota.service, { null }, quota.devPlus, quota.clock, quota.daily)
        testApplication {
            val client = startApp(testContainer(quota = sinUsuario))
            val res = client.get("/v1/quota") { header(HttpHeaders.Authorization, anaToken) }
            res.status shouldBe HttpStatusCode.NotFound
            res.body<ApiError>().code shouldBe ErrorCode.NOT_FOUND
        }
    }

    test("POST /internal/dev/grant-plus con OIDC concede Plus y el cupo semanal deja de aplicar") {
        val clock = MovableClock(bogota("2026-10-28T15:00-05:00"))
        val quota = inMemoryQuotaModule(clock)
        val anaId = quota.users.userIdFor("ana")!!
        testApplication {
            val client = startApp(testContainer(extra = listOf(ChargeRoute(quota)), quota = quota))
            val sinToken = client.post("/internal/dev/grant-plus") {
                contentType(ContentType.Application.Json)
                setBody(GrantPlus(anaId.toString(), 7))
            }
            sinToken.status shouldBe HttpStatusCode.Forbidden

            val res = client.post("/internal/dev/grant-plus") {
                header(HttpHeaders.Authorization, bearer(TEST_OIDC_TOKEN))
                contentType(ContentType.Application.Json)
                setBody(GrantPlus(anaId.toString(), 7))
            }
            res.status shouldBe HttpStatusCode.NoContent

            repeat(7) {
                client.post("/v1/test/charge/plus-$it") { header(HttpHeaders.Authorization, anaToken) }.status shouldBe HttpStatusCode.Accepted
            }
            val dto = client.get("/v1/quota") { header(HttpHeaders.Authorization, anaToken) }.body<QuotaDto>()
            dto.plus shouldBe true
            dto.simulations.limit shouldBe null
            dto.simulations.used shouldBe 7

            val malo = client.post("/internal/dev/grant-plus") {
                header(HttpHeaders.Authorization, bearer(TEST_OIDC_TOKEN))
                contentType(ContentType.Application.Json)
                setBody(GrantPlus("no-es-uuid", 7))
            }
            malo.status shouldBe HttpStatusCode.BadRequest
            malo.body<ApiError>().code shouldBe ErrorCode.VALIDATION
            val dias = client.post("/internal/dev/grant-plus") {
                header(HttpHeaders.Authorization, bearer(TEST_OIDC_TOKEN))
                contentType(ContentType.Application.Json)
                setBody(GrantPlus(anaId.toString(), 0))
            }
            dias.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("sin concesión dev (prod) POST /internal/dev/grant-plus responde 404") {
        val base = inMemoryQuotaModule(MovableClock(bogota("2026-10-28T15:00-05:00")))
        val prod = QuotaModule(base.service, base.users, devPlus = null, clock = base.clock, daily = base.daily)
        testApplication {
            val client = startApp(testContainer(quota = prod, config = testConfig().copy(env = Env.PROD)))
            val res = client.post("/internal/dev/grant-plus") {
                header(HttpHeaders.Authorization, bearer(TEST_OIDC_TOKEN))
                contentType(ContentType.Application.Json)
                setBody(GrantPlus("00000000-0000-0000-0000-00000000000a", 7))
            }
            res.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("el cableado exige base de datos fuera de local y en local sin base usa memoria") {
        val clock = MovableClock(bogota("2026-10-28T15:00-05:00"))
        shouldThrow<IllegalStateException> { quotaModule(Env.PROD, db = null, clock = clock, limits = QuotaLimits()) }
        shouldThrow<IllegalStateException> { quotaModule(Env.DEV, db = null, clock = clock, limits = QuotaLimits()) }
        val local = quotaModule(Env.LOCAL, db = null, clock = clock, limits = QuotaLimits())
        (local.devPlus != null) shouldBe true
    }
})
