package me.santiagoreina.parcheu.api.billing

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.TEST_LEGAL_VERSION
import me.santiagoreina.parcheu.api.TEST_OIDC_TOKEN
import me.santiagoreina.parcheu.api.identity.InMemoryIdentityRepository
import me.santiagoreina.parcheu.api.platform.auth.FakeTokenVerifier
import me.santiagoreina.parcheu.api.platform.config.Env
import me.santiagoreina.parcheu.api.quota.inMemoryQuotaModule
import me.santiagoreina.parcheu.api.startApp
import me.santiagoreina.parcheu.api.testConfig
import me.santiagoreina.parcheu.api.testContainer
import me.santiagoreina.parcheu.contract.ApiError
import me.santiagoreina.parcheu.contract.EntitlementDto
import me.santiagoreina.parcheu.contract.EntitlementState
import me.santiagoreina.parcheu.contract.ErrorCode
import me.santiagoreina.parcheu.contract.GrantPlus
import me.santiagoreina.parcheu.contract.MeDto
import me.santiagoreina.parcheu.contract.VerifyPurchase
import java.util.UUID

private fun bearer(token: String) = "Bearer $token"
private val ANA = bearer(FakeTokenVerifier.tokenFor("ana"))
private val BETO = bearer(FakeTokenVerifier.tokenFor("beto"))
private val OIDC = bearer(TEST_OIDC_TOKEN)

private suspend fun HttpClient.userId(auth: String): UUID =
    UUID.fromString(get("/v1/me") { header(HttpHeaders.Authorization, auth) }.body<MeDto>().id)

private suspend fun HttpClient.verify(auth: String, token: String) = post("/v1/billing/verify") {
    header(HttpHeaders.Authorization, auth)
    contentType(ContentType.Application.Json)
    setBody(VerifyPurchase(token, BillingService.PLUS_PRODUCT_ID))
}

private suspend fun HttpClient.rtdn(body: String, auth: String? = OIDC) = post("/internal/billing/rtdn") {
    auth?.let { header(HttpHeaders.Authorization, it) }
    contentType(ContentType.Application.Json)
    setBody(body)
}

class BillingApiTest : FunSpec({
    val repo = { InMemoryIdentityRepository().also { it.seedAcceptedUser("ana", TEST_LEGAL_VERSION); it.seedAcceptedUser("beto", TEST_LEGAL_VERSION) } }

    test("verify con una compra ACTIVE devuelve Plus, la reconoce una vez y el cupo la ve (EntitlementService real)") {
        val play = FakePlayApi()
        val billing = inMemoryBillingModule(FIXED_CLOCK, play.client)
        val quota = inMemoryQuotaModule(FIXED_CLOCK, entitlements = billing.entitlements)
        testApplication {
            val client = startApp(testContainer(repo = repo(), quota = quota, billing = billing))
            val ana = client.userId(ANA)

            val antes = client.get("/v1/billing/entitlement") { header(HttpHeaders.Authorization, ANA) }
            antes.status shouldBe HttpStatusCode.OK
            antes.body<EntitlementDto>() shouldBe EntitlementDto(plus = false, state = EntitlementState.NONE)

            play.purchase("tok-1", "active", owner = ana)
            val res = client.verify(ANA, "tok-1")
            res.status shouldBe HttpStatusCode.OK
            val dto = res.body<EntitlementDto>()
            dto.plus shouldBe true
            dto.state shouldBe EntitlementState.ACTIVE
            dto.basePlan shouldBe "mensual"
            play.acknowledgeCalls("tok-1") shouldBe 1

            client.get("/v1/billing/entitlement") { header(HttpHeaders.Authorization, ANA) }.body<EntitlementDto>() shouldBe dto
            quota.service.isPlus(ana) shouldBe true
        }
    }

    test("compra con otro obfuscatedAccountId → 403 FORBIDDEN") {
        val play = FakePlayApi()
        val billing = inMemoryBillingModule(FIXED_CLOCK, play.client)
        testApplication {
            val client = startApp(testContainer(repo = repo(), billing = billing))
            play.purchase("tok-ajeno", "active", owner = UUID.randomUUID())
            val res = client.verify(ANA, "tok-ajeno")
            res.status shouldBe HttpStatusCode.Forbidden
            res.body<ApiError>().code shouldBe ErrorCode.FORBIDDEN
            play.acknowledgeCalls("tok-ajeno") shouldBe 0
        }
    }

    test("el mismo purchaseToken desde otra cuenta → 403 y la primera conserva su derecho") {
        val play = FakePlayApi()
        val billing = inMemoryBillingModule(FIXED_CLOCK, play.client)
        testApplication {
            val client = startApp(testContainer(repo = repo(), billing = billing))
            val ana = client.userId(ANA)
            play.purchase("tok-ana", "active", owner = ana)
            val primera = client.verify(ANA, "tok-ana").body<EntitlementDto>()

            val res = client.verify(BETO, "tok-ana")
            res.status shouldBe HttpStatusCode.Forbidden
            res.body<ApiError>().code shouldBe ErrorCode.FORBIDDEN

            client.get("/v1/billing/entitlement") { header(HttpHeaders.Authorization, ANA) }.body<EntitlementDto>() shouldBe primera
            client.get("/v1/billing/entitlement") { header(HttpHeaders.Authorization, BETO) }.body<EntitlementDto>().plus shouldBe false
        }
    }

    test("verify valida el cuerpo y exige token de Firebase") {
        testApplication {
            val client = startApp(testContainer(repo = repo(), billing = inMemoryBillingModule(FIXED_CLOCK, FakePlayApi().client)))
            client.post("/v1/billing/verify") {
                contentType(ContentType.Application.Json)
                setBody(VerifyPurchase("t", BillingService.PLUS_PRODUCT_ID))
            }.status shouldBe HttpStatusCode.Unauthorized
            client.get("/v1/billing/entitlement").status shouldBe HttpStatusCode.Unauthorized
            val otro = client.post("/v1/billing/verify") {
                header(HttpHeaders.Authorization, ANA)
                contentType(ContentType.Application.Json)
                setBody(VerifyPurchase("t", "otro"))
            }
            otro.status shouldBe HttpStatusCode.BadRequest
            otro.body<ApiError>().code shouldBe ErrorCode.VALIDATION
            client.verify(ANA, "tok-que-play-no-conoce").body<ApiError>().code shouldBe ErrorCode.VALIDATION
        }
    }

    test("POST /internal/billing/rtdn exige OIDC, responde 204 y la misma RTDN dos veces se procesa una vez") {
        val play = FakePlayApi()
        val billing = inMemoryBillingModule(FIXED_CLOCK, play.client)
        testApplication {
            val client = startApp(testContainer(repo = repo(), billing = billing))
            val ana = client.userId(ANA)
            play.purchase("tok-r", "active-anual-acknowledged", owner = ana)
            client.verify(ANA, "tok-r").status shouldBe HttpStatusCode.OK
            val gets = play.getCalls("tok-r")

            val body = rtdnBody("msg-1", "tok-r", RtdnTypes.SUBSCRIPTION_REVOKED)
            client.rtdn(body, auth = null).status shouldBe HttpStatusCode.Forbidden
            client.rtdn(body, auth = ANA).status shouldBe HttpStatusCode.Forbidden
            play.getCalls("tok-r") shouldBe gets

            client.rtdn(body).status shouldBe HttpStatusCode.NoContent
            client.rtdn(body).status shouldBe HttpStatusCode.NoContent
            play.getCalls("tok-r") shouldBe gets + 1

            val dto = client.get("/v1/billing/entitlement") { header(HttpHeaders.Authorization, ANA) }.body<EntitlementDto>()
            dto.state shouldBe EntitlementState.REVOKED
            dto.plus shouldBe false

            client.rtdn("{}").status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("con ENV=prod, POST /internal/dev/grant-plus → 404 aunque el cupo traiga concesión") {
        testApplication {
            val client = startApp(testContainer(repo = repo(), config = testConfig().copy(env = Env.PROD)))
            val res = client.post("/internal/dev/grant-plus") {
                header(HttpHeaders.Authorization, OIDC)
                contentType(ContentType.Application.Json)
                setBody(GrantPlus(UUID.randomUUID().toString(), 7))
            }
            res.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("fuera de prod, grant-plus escribe la fila dev-grant que lee GET /v1/billing/entitlement") {
        val billing = inMemoryBillingModule(FIXED_CLOCK, FakePlayApi().client)
        val quota = inMemoryQuotaModule(FIXED_CLOCK, entitlements = billing.entitlements)
        testApplication {
            val client = startApp(testContainer(repo = repo(), quota = quota, billing = billing, config = testConfig().copy(env = Env.DEV)))
            val ana = client.userId(ANA)
            client.post("/internal/dev/grant-plus") {
                header(HttpHeaders.Authorization, OIDC)
                contentType(ContentType.Application.Json)
                setBody(GrantPlus(ana.toString(), 7))
            }.status shouldBe HttpStatusCode.NoContent
            val dto = client.get("/v1/billing/entitlement") { header(HttpHeaders.Authorization, ANA) }.body<EntitlementDto>()
            dto.plus shouldBe true
            dto.state shouldBe EntitlementState.ACTIVE
            quota.service.isPlus(ana) shouldBe true
        }
    }
})
