package me.santiagoreina.parcheu.api.twin.feedback

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
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
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.TEST_LEGAL_VERSION
import me.santiagoreina.parcheu.api.TEST_OIDC_TOKEN
import me.santiagoreina.parcheu.api.ai.FakeLlmClient
import me.santiagoreina.parcheu.api.ai.testGateway
import me.santiagoreina.parcheu.api.identity.InMemoryIdentityRepository
import me.santiagoreina.parcheu.api.platform.auth.FakeTokenVerifier
import me.santiagoreina.parcheu.api.platform.tasks.FakeTaskQueue
import me.santiagoreina.parcheu.api.startApp
import me.santiagoreina.parcheu.api.testContainer
import me.santiagoreina.parcheu.api.twin.AccuracyPoint
import me.santiagoreina.parcheu.api.twin.DerivedTwinUserLookup
import me.santiagoreina.parcheu.api.twin.InMemoryTwinRepository
import me.santiagoreina.parcheu.api.twin.PERSONA_TEXT
import me.santiagoreina.parcheu.api.twin.SeedQuestions
import me.santiagoreina.parcheu.api.twin.answersOf
import me.santiagoreina.parcheu.api.twin.inMemoryTwinModule
import me.santiagoreina.parcheu.contract.ApiError
import me.santiagoreina.parcheu.contract.ContractJson
import me.santiagoreina.parcheu.contract.ErrorCode
import me.santiagoreina.parcheu.contract.Feedback
import me.santiagoreina.parcheu.contract.FeedbackKind
import me.santiagoreina.parcheu.contract.FeedbackPrompt
import me.santiagoreina.parcheu.contract.TwinAnswer
import me.santiagoreina.parcheu.contract.TwinDto
import java.time.Duration
import java.time.LocalDate
import java.util.UUID

/** E9.3: métrica 'Qué tanto te conoce', pregunta de control, calificaciones y job `twin-accuracy`. */
class FeedbackApiTest : FunSpec({
    val now = FIXED_CLOCK.instant() // domingo 2026-10-25 07:00 en Bogotá; la semana empezó el lunes 2026-10-19.
    val monday = LocalDate.parse("2026-10-19")
    val ana = DerivedTwinUserLookup.idFor("ana")
    val beto = DerivedTwinUserLookup.idFor("beto")
    val carla = DerivedTwinUserLookup.idFor("carla")

    class Fixture {
        val twins = InMemoryTwinRepository()
        val feedback = InMemoryFeedbackRepository()
        val gateway = testGateway(FakeLlmClient(textResponder = { PERSONA_TEXT }))
        val module = inMemoryTwinModule(FIXED_CLOCK, gateway, twins, FakeTaskQueue(), runInline = false, feedbackRepo = feedback)
    }

    suspend fun ApplicationTestBuilder.app(f: Fixture): HttpClient = startApp(
        testContainer(
            repo = InMemoryIdentityRepository().also { r -> listOf("ana", "beto", "carla").forEach { r.seedAcceptedUser(it, TEST_LEGAL_VERSION) } },
            twin = f.module,
            ai = f.gateway,
        ),
    )

    fun bearer(uid: String) = "Bearer ${FakeTokenVerifier.tokenFor(uid)}"

    suspend fun HttpClient.answer(uid: String) = post("/v1/twin/answers") {
        header(HttpHeaders.Authorization, bearer(uid))
        contentType(ContentType.Application.Json)
        setBody(ContractJson.encodeToString(me.santiagoreina.parcheu.contract.Answers.serializer(), answersOf()))
    }

    suspend fun HttpClient.prompts(uid: String): List<FeedbackPrompt> =
        ContractJson.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(FeedbackPrompt.serializer()),
            get("/v1/twin/feedback-prompts") { header(HttpHeaders.Authorization, bearer(uid)) }.body<String>(),
        )

    suspend fun HttpClient.send(uid: String, body: Feedback) = post("/v1/twin/feedback") {
        header(HttpHeaders.Authorization, bearer(uid))
        contentType(ContentType.Application.Json)
        setBody(ContractJson.encodeToString(Feedback.serializer(), body))
    }

    test("métrica: 8 de 10 positivas y 3 de 4 aciertos dan 77,5 %") {
        AccuracyMetric.percent(WeekStats(ratings = 10, positive = 8, predictions = 4, correct = 3))!!.shouldBe(77.5.plusOrMinus(1e-9))
        AccuracyMetric.stored(WeekStats(10, 8, 4, 3)) shouldBe 78
    }

    test("métrica: sin datos no hay punto y con una sola mitad esa mitad vale por sí sola") {
        AccuracyMetric.percent(WeekStats(0, 0, 0, 0)).shouldBeNull()
        AccuracyMetric.percent(WeekStats(4, 3, 0, 0))!!.shouldBe(75.0.plusOrMinus(1e-9))
        AccuracyMetric.percent(WeekStats(0, 0, 2, 2))!!.shouldBe(100.0.plusOrMinus(1e-9))
    }

    test("el predictor es determinista y no usa la respuesta de la pregunta objetivo") {
        val questions = SeedQuestions.all
        val target = questions.first { it.id == 1 }
        val base = answersOf().items.associate { it.questionId to it.answer }
        val a = ControlPredictor.predict(questions, base, target)
        val flipped = ControlPredictor.predict(questions, base + (1 to TwinAnswer.B), target)
        a shouldBe flipped
        ControlPredictor.predict(questions, base, target) shouldBe a
    }

    test("la predicción se guarda antes de mostrar la pregunta de control al usuario") {
        val f = Fixture()
        testApplication {
            val client = app(f)
            client.answer("ana")
            f.feedback.controls(ana).shouldBeEmpty()

            val shown = client.prompts("ana").single { it.kind == FeedbackKind.CONTROL }

            val saved = f.feedback.controls(ana).single()
            saved.id.toString() shouldBe shown.id
            saved.actual.shouldBeNull()
            saved.predicted.shouldNotBeNull()
            shown.options.shouldNotBeNull() shouldHaveSize 3
            // Pedirla otra vez muestra la misma pregunta sin crear otra predicción.
            client.prompts("ana").single { it.kind == FeedbackKind.CONTROL }.id shouldBe shown.id
            f.feedback.controls(ana) shouldHaveSize 1
        }
    }

    test("responder la pregunta de control guarda la respuesta real; repetirla igual es inocuo y cambiarla es 409") {
        val f = Fixture()
        testApplication {
            val client = app(f)
            client.answer("ana")
            val control = client.prompts("ana").single { it.kind == FeedbackKind.CONTROL }

            val ok = client.send("ana", Feedback(control.id, answer = "A"))
            ok.status shouldBe HttpStatusCode.OK
            ok.body<TwinDto>().level shouldBe 1
            f.feedback.controls(ana).single().actual shouldBe TwinAnswer.A
            client.send("ana", Feedback(control.id, answer = "A")).status shouldBe HttpStatusCode.OK
            val conflict = client.send("ana", Feedback(control.id, answer = "B"))
            conflict.status shouldBe HttpStatusCode.Conflict
            conflict.body<ApiError>().code shouldBe ErrorCode.CONFLICT
            // Respondida la de esta semana, ya no se ofrece otra.
            client.prompts("ana").filter { it.kind == FeedbackKind.CONTROL }.shouldBeEmpty()
            // Otro usuario no puede responder la pregunta ajena.
            client.send("beto", Feedback(control.id, answer = "A")).status shouldBe HttpStatusCode.NotFound
        }
    }

    test("las respuestas simuladas del propio gemelo se ofrecen para calificar y se califican una sola vez") {
        val f = Fixture()
        val sim = UUID.randomUUID()
        f.feedback.addTurn(ana, sim, 0, "Yo digo que sí a todo parche")
        f.feedback.addTurn(ana, sim, 2, "Prefiero planear antes")
        f.feedback.addTurn(beto, sim, 1, "Turno de otro usuario")
        testApplication {
            val client = app(f)
            client.answer("ana")
            val ratings = client.prompts("ana").filter { it.kind == FeedbackKind.RATING }
            ratings.map { it.id }.toSet() shouldBe setOf("$sim:0", "$sim:2")

            client.send("ana", Feedback("$sim:0", rating = 1)).status shouldBe HttpStatusCode.OK
            client.send("ana", Feedback("$sim:0", rating = 0)).status shouldBe HttpStatusCode.OK // no duplica ni cambia
            f.feedback.weekStats(ana, now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(1))) shouldBe WeekStats(1, 1, 0, 0)
            client.prompts("ana").filter { it.kind == FeedbackKind.RATING }.map { it.id } shouldBe listOf("$sim:2")

            client.send("ana", Feedback("$sim:2", rating = 5)).status shouldBe HttpStatusCode.BadRequest
            client.send("ana", Feedback("$sim:2")).status shouldBe HttpStatusCode.BadRequest
            // El turno 1 es de beto: para ana no existe.
            client.send("ana", Feedback("$sim:1", rating = 1)).status shouldBe HttpStatusCode.NotFound
            client.send("ana", Feedback("no-es-un-id", answer = "A")).status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("sin gemelo no hay pregunta de control y la lista viene vacía") {
        val f = Fixture()
        testApplication { app(f).prompts("ana").shouldBeEmpty() }
    }

    test("el job twin-accuracy escribe un punto por usuario con datos en la semana, ninguno sin datos, y repetirlo no duplica") {
        val f = Fixture()
        val sim = UUID.randomUUID()
        val weekEarlier = now.minus(Duration.ofDays(7))
        // ana: 1 de 2 calificaciones positivas y 1 de 1 predicción acertada esta semana -> (50 + 100) / 2 = 75.
        f.feedback.addRating(ana, sim, 0, 1, now.minus(Duration.ofHours(30)))
        f.feedback.addRating(ana, sim, 1, 0, now.minus(Duration.ofHours(2)))
        val control = f.feedback.createControl(ana, 1, TwinAnswer.A, now.minus(Duration.ofHours(5)))
        f.feedback.answerControl(ana, control.id, TwinAnswer.A)
        // beto: una pregunta de control sin responder y nada más -> sin datos.
        f.feedback.createControl(beto, 2, TwinAnswer.B, now.minus(Duration.ofHours(3)))
        // carla: solo datos de la semana anterior -> sin punto esta semana.
        f.feedback.addRating(carla, sim, 0, 1, weekEarlier)
        testApplication {
            val client = app(f)
            listOf("ana", "beto", "carla").forEach { client.answer(it) }
            val job = suspend {
                client.post("/internal/jobs/twin-accuracy") { header(HttpHeaders.Authorization, "Bearer ${TEST_OIDC_TOKEN}") }.status
            }
            job() shouldBe HttpStatusCode.NoContent
            job() shouldBe HttpStatusCode.NoContent

            f.twins.accuracyPoints(ana) shouldBe listOf(AccuracyPoint(monday, 75, ratings = 2, predictions = 1))
            f.twins.accuracyPoints(beto).shouldBeEmpty()
            f.twins.accuracyPoints(carla).shouldBeEmpty()
            f.module.feedback.computeAccuracy() shouldBe 1
            f.twins.accuracyPoints(ana) shouldHaveSize 1
        }
    }

    test("GET /v1/twin refleja la precisión de la semana después del job") {
        val f = Fixture()
        f.feedback.addRating(ana, UUID.randomUUID(), 0, 1, now.minus(Duration.ofHours(1)))
        testApplication {
            val client = app(f)
            client.answer("ana")
            f.module.feedback.computeAccuracy()
            val twin = client.get("/v1/twin") { header(HttpHeaders.Authorization, bearer("ana")) }.body<TwinDto>()
            twin.accuracy shouldBe 100
        }
    }
})
