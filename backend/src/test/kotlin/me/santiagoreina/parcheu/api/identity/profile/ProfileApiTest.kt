package me.santiagoreina.parcheu.api.identity.profile

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import me.santiagoreina.parcheu.api.TEST_LEGAL_VERSION
import me.santiagoreina.parcheu.api.identity.InMemoryIdentityRepository
import me.santiagoreina.parcheu.api.platform.auth.FakeTokenVerifier
import me.santiagoreina.parcheu.api.startApp
import me.santiagoreina.parcheu.api.testContainer
import me.santiagoreina.parcheu.contract.AcceptTerms
import me.santiagoreina.parcheu.contract.AgeVerification
import me.santiagoreina.parcheu.contract.ApiError
import me.santiagoreina.parcheu.contract.CatalogDto
import me.santiagoreina.parcheu.contract.ErrorCode
import me.santiagoreina.parcheu.contract.IsoDate
import me.santiagoreina.parcheu.contract.MeDto
import me.santiagoreina.parcheu.contract.OnboardingStep
import me.santiagoreina.parcheu.contract.SessionDto
import me.santiagoreina.parcheu.contract.UpdateMe
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Criterios de E2.6 contra la API (Ktor test host, repositorios en memoria). El reloj fijo marca las
 * 23:30 del 23 de octubre de 2026 en Bogotá, que en UTC ya es el 24: si la edad se calculara en UTC,
 * quien cumple 18 el 24 pasaría por mayor de edad.
 */
class ProfileApiTest : FunSpec({
    val bogota2330 = Clock.fixed(Instant.parse("2026-10-24T04:30:00Z"), ZoneOffset.UTC)

    fun HttpRequestBuilder.auth(uid: String) = header(HttpHeaders.Authorization, "Bearer ${FakeTokenVerifier.tokenFor(uid)}")

    suspend fun HttpClient.verifyAge(uid: String, date: String): HttpResponse = post("/v1/me/age-verification") {
        auth(uid)
        contentType(ContentType.Application.Json)
        setBody(AgeVerification(IsoDate(date)))
    }

    suspend fun HttpClient.verifyAgeRaw(uid: String, json: String): HttpResponse = post("/v1/me/age-verification") {
        auth(uid)
        contentType(ContentType.Application.Json)
        setBody(json)
    }

    suspend fun HttpClient.patchMe(uid: String, body: UpdateMe): HttpResponse = patch("/v1/me") {
        auth(uid)
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    suspend fun HttpResponse.error() = body<ApiError>().code

    fun app(repo: InMemoryIdentityRepository, block: suspend (HttpClient) -> Unit) = testApplication {
        block(startApp(testContainer(repo = repo, clock = bogota2330)))
    }

    fun seeded() = InMemoryIdentityRepository().also { it.seedAcceptedUser("ana", TEST_LEGAL_VERSION) }

    test("cumple 18 hoy a las 23:30 hora Bogotá: la fecha queda verificada") {
        val repo = seeded()
        app(repo) { client ->
            val response = client.verifyAge("ana", "2008-10-23")
            response.status shouldBe HttpStatusCode.OK
            val me = response.body<MeDto>()
            me.ageVerified shouldBe true
            me.underage shouldBe false
            me.birthDate shouldBe IsoDate("2008-10-23")
            repo.user("ana").ageVerifiedAt shouldBe bogota2330.instant()
            repo.user("ana").underageAt shouldBe null
        }
    }

    test("le falta un día: 403 UNDERAGE, underage_at fijado y la cuenta queda bloqueada salvo GET y DELETE /v1/me y la exportación") {
        val repo = seeded()
        app(repo) { client ->
            val response = client.verifyAge("ana", "2008-10-24")
            response.status shouldBe HttpStatusCode.Forbidden
            response.error() shouldBe ErrorCode.UNDERAGE
            val user = repo.user("ana")
            user.underageAt shouldBe bogota2330.instant()
            user.ageVerifiedAt shouldBe null
            user.birthDate shouldBe LocalDate.parse("2008-10-24")

            // Lo único permitido: ver la cuenta y borrarla.
            val me = client.get("/v1/me") { auth("ana") }
            me.status shouldBe HttpStatusCode.OK
            me.body<MeDto>().underage shouldBe true
            // 'Descargar mis datos primero' desde el bloqueo por edad (E8.3): la exportación también se permite.
            client.get("/v1/me/export") { auth("ana") }.status shouldBe HttpStatusCode.OK

            // Todo lo demás, incluidas las rutas exentas de términos, responde 403 UNDERAGE.
            client.post("/v1/session") { auth("ana") }.error() shouldBe ErrorCode.UNDERAGE
            client.post("/v1/me/terms") {
                auth("ana"); contentType(ContentType.Application.Json); setBody(AcceptTerms(TEST_LEGAL_VERSION))
            }.error() shouldBe ErrorCode.UNDERAGE
            client.patchMe("ana", UpdateMe(career = "Medicina")).error() shouldBe ErrorCode.UNDERAGE
            client.get("/v1/catalog") { auth("ana") }.error() shouldBe ErrorCode.UNDERAGE
            client.get("/v1/me/consents") { auth("ana") }.error() shouldBe ErrorCode.UNDERAGE
            client.get("/v1/twin") { auth("ana") }.error() shouldBe ErrorCode.UNDERAGE
            client.get("/v1/graph") { auth("ana") }.error() shouldBe ErrorCode.UNDERAGE
            client.verifyAge("ana", "2000-01-01").error() shouldBe ErrorCode.UNDERAGE
            repo.user("ana").birthDate shouldBe LocalDate.parse("2008-10-24")

            // Y borrarla (E8.3): al final, porque el borrado deja la cuenta inactiva.
            val delete = client.delete("/v1/me") { auth("ana") }
            delete.status shouldBe HttpStatusCode.Accepted
            repo.user("ana").deletedAt shouldNotBe null
        }
    }

    test("cambiar la fecha de nacimiento una segunda vez responde 409 CONFLICT; repetir la misma no cambia nada") {
        val repo = seeded()
        app(repo) { client ->
            client.verifyAge("ana", "2003-04-05").status shouldBe HttpStatusCode.OK
            val again = client.verifyAge("ana", "2001-01-01")
            again.status shouldBe HttpStatusCode.Conflict
            again.error() shouldBe ErrorCode.CONFLICT
            repo.user("ana").birthDate shouldBe LocalDate.parse("2003-04-05")

            val same = client.verifyAge("ana", "2003-04-05")
            same.status shouldBe HttpStatusCode.OK
            same.body<MeDto>().birthDate shouldBe IsoDate("2003-04-05")
        }
    }

    test("fechas futuras, imposibles o mal formadas responden 400 VALIDATION y no guardan nada") {
        val repo = seeded()
        app(repo) { client ->
            client.verifyAge("ana", "2026-10-24").error() shouldBe ErrorCode.VALIDATION // mañana en Bogotá
            client.verifyAge("ana", "1899-12-31").error() shouldBe ErrorCode.VALIDATION
            client.verifyAge("ana", "2003-02-30").error() shouldBe ErrorCode.VALIDATION
            client.verifyAgeRaw("ana", """{"birthDate":"05/04/2003"}""").error() shouldBe ErrorCode.VALIDATION
            client.verifyAgeRaw("ana", """{}""").error() shouldBe ErrorCode.VALIDATION
            repo.user("ana").birthDate shouldBe null
        }
    }

    test("más de 5 intereses, un interés fuera del catálogo o una universidad desconocida → 400 VALIDATION") {
        val repo = seeded()
        app(repo) { client ->
            val six = client.patchMe("ana", UpdateMe(interests = listOf("musica", "futbol", "cine", "lectura", "gym", "series")))
            six.status shouldBe HttpStatusCode.BadRequest
            six.error() shouldBe ErrorCode.VALIDATION
            client.patchMe("ana", UpdateMe(interests = listOf("musica", "astrologia"))).error() shouldBe ErrorCode.VALIDATION
            client.patchMe("ana", UpdateMe(universityId = 1234)).error() shouldBe ErrorCode.VALIDATION
            // Fuera del rango de smallint: 65537 se truncaría a 1 en Postgres.
            listOf(65537, 65635, 32768, 0, -1).forEach { id ->
                client.patchMe("ana", UpdateMe(universityId = id)).error() shouldBe ErrorCode.VALIDATION
            }
            client.patchMe("ana", UpdateMe(career = "x".repeat(61))).error() shouldBe ErrorCode.VALIDATION

            val ok = client.patchMe(
                "ana",
                UpdateMe(career = "x".repeat(60), universityId = 99, interests = listOf("musica", "futbol", "cine", "lectura", "gym")),
            )
            ok.status shouldBe HttpStatusCode.OK
            ok.body<MeDto>().interests.size shouldBe 5
        }
    }

    test("GET /v1/catalog devuelve universidades e intereses") {
        app(seeded()) { client ->
            val catalog = client.get("/v1/catalog") { auth("ana") }.body<CatalogDto>()
            catalog.universities.map { it.id } shouldContain 99
            catalog.interests.map { it.slug } shouldContain "musica"
        }
    }

    test("B0 completo: NEEDS_PROFILE → edad + universidad, carrera e intereses → NEEDS_TWIN") {
        val repo = InMemoryIdentityRepository()
        app(repo) { client ->
            suspend fun session() = client.post("/v1/session") { auth("lucia") }.body<SessionDto>()
            session()
            client.post("/v1/me/terms") {
                auth("lucia"); contentType(ContentType.Application.Json); setBody(AcceptTerms(TEST_LEGAL_VERSION))
            }
            session().onboarding shouldBe OnboardingStep.NEEDS_PROFILE

            client.verifyAge("lucia", "2004-05-17").status shouldBe HttpStatusCode.OK
            session().onboarding shouldBe OnboardingStep.NEEDS_PROFILE // falta la universidad

            val me = client.patchMe("lucia", UpdateMe(career = "Ing. Industrial", universityId = 1, interests = listOf("musica", "cine")))
                .body<MeDto>()
            me.interests shouldContainExactly listOf("musica", "cine")
            val done = session()
            done.onboarding shouldBe OnboardingStep.NEEDS_TWIN
            done.me.ageVerified shouldBe true
            done.me.universityId shouldBe 1
        }
    }

    test("sin términos aceptados la verificación de edad responde 403 TERMS_REQUIRED") {
        val repo = InMemoryIdentityRepository()
        app(repo) { client ->
            client.post("/v1/session") { auth("lucia") }
            client.verifyAge("lucia", "2004-05-17").error() shouldBe ErrorCode.TERMS_REQUIRED
            repo.user("lucia").birthDate shouldBe null
        }
    }
})
