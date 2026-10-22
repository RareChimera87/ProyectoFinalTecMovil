package me.santiagoreina.parcheu.api.twin

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import me.santiagoreina.parcheu.api.startApp
import me.santiagoreina.parcheu.api.testContainer
import me.santiagoreina.parcheu.api.platform.auth.FakeOidcVerifier
import me.santiagoreina.parcheu.api.platform.auth.FakeTokenVerifier
import me.santiagoreina.parcheu.contract.Answers
import me.santiagoreina.parcheu.contract.ApiError
import me.santiagoreina.parcheu.contract.ErrorCode
import me.santiagoreina.parcheu.contract.QuestionDto
import me.santiagoreina.parcheu.contract.TraitKey
import me.santiagoreina.parcheu.contract.TwinDto
import me.santiagoreina.parcheu.contract.TwinPoint
import java.time.LocalDate
import java.util.UUID

class TwinApiTest : FunSpec({
    fun userId(uid: String): UUID = DerivedTwinUserLookup.idFor(uid)

    suspend fun ApplicationTestBuilder.app(fixture: TwinFixture): HttpClient =
        startApp(testContainer(twin = fixture.module, ai = fixture.gateway))

    suspend fun HttpClient.answer(uid: String, body: Answers) = post("/v1/twin/answers") {
        header(HttpHeaders.Authorization, "Bearer ${FakeTokenVerifier.tokenFor(uid)}")
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    suspend fun HttpClient.getAs(uid: String, path: String) =
        get(path) { header(HttpHeaders.Authorization, "Bearer ${FakeTokenVerifier.tokenFor(uid)}") }

    suspend fun HttpClient.buildPersonaTask(id: String) = post("/internal/tasks/build-persona") {
        header(HttpHeaders.Authorization, "Bearer ${FakeOidcVerifier.VALID_TOKEN}")
        contentType(ContentType.Application.Json)
        setBody("""{"id":"$id"}""")
    }

    test("GET /v1/twin/questions devuelve las 12 preguntas en orden, sin pesos") {
        testApplication {
            val client = app(TwinFixture())
            val response = client.getAs("ana", "/v1/twin/questions")
            response.status shouldBe HttpStatusCode.OK
            val list = response.body<List<QuestionDto>>()
            list.map { it.id } shouldBe (1..12).toList()
            response.bodyAsText() shouldNotContain "weights"
        }
    }

    test("11 respuestas dan 400 VALIDATION y no se guarda nada") {
        testApplication {
            val fixture = TwinFixture()
            val client = app(fixture)
            val response = client.answer("ana", Answers(answersOf().items.take(11)))
            response.status shouldBe HttpStatusCode.BadRequest
            response.body<ApiError>().code shouldBe ErrorCode.VALIDATION
            fixture.repo.state(userId("ana")) shouldBe null
            fixture.queue.tasks.shouldBeEmpty()
        }
    }

    test("13 respuestas, una pregunta repetida o una pregunta inexistente también dan 400 VALIDATION") {
        testApplication {
            val client = app(TwinFixture())
            val items = answersOf().items
            client.answer("ana", Answers(items + items.first())).status shouldBe HttpStatusCode.BadRequest
            client.answer("ana", Answers(items.drop(1) + items.first())).status shouldBe HttpStatusCode.OK
            client.answer("ana", Answers(items.take(11) + items.first())).status shouldBe HttpStatusCode.BadRequest
            client.answer("ana", Answers(items.take(11) + items.last().copy(questionId = 99))).status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("12 respuestas guardan, calculan los rasgos, suman XP y encolan build-persona una vez") {
        testApplication {
            val fixture = TwinFixture()
            val client = app(fixture)
            val response = client.answer("ana", answersOf())
            response.status shouldBe HttpStatusCode.OK
            val twin = response.body<TwinDto>()
            twin.traits.map { it.key } shouldBe TraitKey.entries
            twin.traits.map { it.value } shouldBe listOf(0.6818, 0.4231, 0.2826, 0.8333, 0.4, 0.5577)
            twin.personaReady shouldBe false
            twin.version shouldBe 1
            twin.xp shouldBe 120
            twin.level shouldBe 1
            twin.xpNext shouldBe 500

            fixture.queue.tasks shouldHaveSize 1
            val task = fixture.queue.tasks.single()
            task.path shouldBe "/tasks/build-persona"
            task.body shouldBe """{"id":"${userId("ana")}"}"""

            client.getAs("ana", "/v1/twin").body<TwinDto>() shouldBe twin
        }
    }

    test("GET /v1/twin sin respuestas devuelve un gemelo vacío") {
        testApplication {
            val twin = app(TwinFixture()).getAs("ana", "/v1/twin").body<TwinDto>()
            twin.traits.shouldBeEmpty()
            twin.personaReady shouldBe false
            twin.level shouldBe 1
            twin.accuracy shouldBe null
        }
    }

    test("build-persona deja personaReady y no duplica versiones al ejecutarse dos veces con el mismo id") {
        testApplication {
            val fixture = TwinFixture()
            val client = app(fixture)
            client.answer("ana", answersOf())
            val id = userId("ana")

            client.buildPersonaTask(id.toString()).status shouldBe HttpStatusCode.NoContent
            client.buildPersonaTask(id.toString()).status shouldBe HttpStatusCode.NoContent

            fixture.fake.calls shouldBe 1
            fixture.repo.versions.getValue(id).keys shouldBe setOf(1)
            fixture.repo.personaOf(id) shouldBe PERSONA_TEXT
            client.getAs("ana", "/v1/twin").body<TwinDto>().personaReady shouldBe true
        }
    }

    test("reenviar las respuestas sube la versión y pide una persona nueva sin volver a sumar XP") {
        testApplication {
            val fixture = TwinFixture()
            val client = app(fixture)
            client.answer("ana", answersOf())
            client.buildPersonaTask(userId("ana").toString())
            val second = client.answer("ana", answersOf("BBBBBBBBBBBB")).body<TwinDto>()
            second.version shouldBe 2
            second.personaReady shouldBe false
            second.xp shouldBe 120
            fixture.queue.tasks shouldHaveSize 2
            client.buildPersonaTask(userId("ana").toString())
            fixture.repo.versions.getValue(userId("ana")).keys shouldBe setOf(1, 2)
        }
    }

    test("build-persona sin respuestas, con id inválido o sin OIDC no llama al modelo") {
        testApplication {
            val fixture = TwinFixture()
            val client = app(fixture)
            client.buildPersonaTask(UUID.randomUUID().toString()).status shouldBe HttpStatusCode.NoContent
            client.buildPersonaTask("no-es-uuid").status shouldBe HttpStatusCode.BadRequest
            client.post("/internal/tasks/build-persona") {
                contentType(ContentType.Application.Json)
                setBody("""{"id":"${UUID.randomUUID()}"}""")
            }.status shouldBe HttpStatusCode.Forbidden
            fixture.fake.calls shouldBe 0
        }
    }

    test("el prompt de persona no contiene el correo ni el nombre del token") {
        testApplication {
            val fixture = TwinFixture()
            val client = app(fixture)
            val uid = "carolina.restrepo"
            client.answer(uid, answersOf())
            client.buildPersonaTask(userId(uid).toString())
            val request = checkNotNull(fixture.fake.lastRequest)
            val sent = request.system + "\n" + request.prompt
            sent shouldNotContain "carolina"
            sent shouldNotContain "restrepo"
            sent shouldNotContain "@"
            sent shouldNotContain userId(uid).toString()
            request.prompt shouldContain "Le escribo al parche para salir"
        }
    }

    test("GET /v1/twin/evolution devuelve [] sin datos y 3 puntos ordenados por semana con 3 filas") {
        testApplication {
            val fixture = TwinFixture()
            val client = app(fixture)
            client.getAs("ana", "/v1/twin/evolution").body<List<TwinPoint>>().shouldBeEmpty()

            client.answer("ana", answersOf())
            fixture.repo.addAccuracyPoint(userId("ana"), AccuracyPoint(LocalDate.of(2026, 12, 7), 71))
            fixture.repo.addAccuracyPoint(userId("ana"), AccuracyPoint(LocalDate.of(2026, 11, 23), 40))
            fixture.repo.addAccuracyPoint(userId("ana"), AccuracyPoint(LocalDate.of(2026, 11, 30), 58))
            val points = client.getAs("ana", "/v1/twin/evolution").body<List<TwinPoint>>()
            points.map { it.accuracy } shouldBe listOf(40, 58, 71)
            points.map { it.month } shouldBe listOf("2026-11", "2026-11", "2026-12")
            points.map { it.level } shouldBe listOf(1, 1, 1)
            client.getAs("ana", "/v1/twin").body<TwinDto>().accuracy shouldBe 71
        }
    }

    test("las rutas de /v1/twin exigen token y las de memorias siguen en 501") {
        testApplication {
            val client = app(TwinFixture())
            client.get("/v1/twin").status shouldBe HttpStatusCode.Unauthorized
            client.getAs("ana", "/v1/twin/memories").status shouldBe HttpStatusCode.NotImplemented
        }
    }
})
