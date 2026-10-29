package me.santiagoreina.parcheu.api.scenario

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import me.santiagoreina.parcheu.api.simulation.JudgeVerdict
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
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
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import me.santiagoreina.parcheu.api.TEST_LEGAL_VERSION
import me.santiagoreina.parcheu.api.ai.FakeLlmClient
import me.santiagoreina.parcheu.api.ai.LlmRequest
import me.santiagoreina.parcheu.api.ai.testGateway
import me.santiagoreina.parcheu.api.chat.InMemoryChatMirror
import me.santiagoreina.parcheu.api.chat.InMemoryChatRepository
import me.santiagoreina.parcheu.api.chat.inMemoryChatModule
import me.santiagoreina.parcheu.api.graph.PersonRow
import me.santiagoreina.parcheu.api.graph.inMemoryGraphModule
import me.santiagoreina.parcheu.api.identity.InMemoryIdentityRepository
import me.santiagoreina.parcheu.api.matching.MatchingFlags
import me.santiagoreina.parcheu.api.platform.auth.FakeOidcVerifier
import me.santiagoreina.parcheu.api.platform.config.Env
import me.santiagoreina.parcheu.api.platform.auth.FakeTokenVerifier
import me.santiagoreina.parcheu.api.platform.notifier.FakeNotifier
import me.santiagoreina.parcheu.api.platform.tasks.FakeTaskQueue
import me.santiagoreina.parcheu.api.quota.InMemoryDevEntitlementService
import me.santiagoreina.parcheu.api.quota.MovableClock
import me.santiagoreina.parcheu.api.quota.inMemoryQuotaModule
import me.santiagoreina.parcheu.api.simulation.InMemorySimulationRepository
import me.santiagoreina.parcheu.api.simulation.Participant
import me.santiagoreina.parcheu.api.simulation.TwinMemory
import me.santiagoreina.parcheu.api.simulation.TwinProfile
import me.santiagoreina.parcheu.api.simulation.inMemorySimulationModule
import me.santiagoreina.parcheu.api.simulation.judgeJson
import me.santiagoreina.parcheu.api.startApp
import me.santiagoreina.parcheu.api.testContainer
import me.santiagoreina.parcheu.contract.ApiError
import me.santiagoreina.parcheu.contract.ChatStatus
import me.santiagoreina.parcheu.contract.ErrorCode
import me.santiagoreina.parcheu.contract.MessageDto
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.NotificationKinds
import me.santiagoreina.parcheu.contract.ScenarioDto
import me.santiagoreina.parcheu.contract.ScenarioSessionDto
import me.santiagoreina.parcheu.contract.ScenarioSessionStatus
import me.santiagoreina.parcheu.contract.SendMessage
import me.santiagoreina.parcheu.contract.SimulationDto
import me.santiagoreina.parcheu.contract.SimulationOrigin
import me.santiagoreina.parcheu.contract.SimulationStatus
import me.santiagoreina.parcheu.contract.Visibility
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/** Criterios de E4.8 (modo escenario) de punta a punta con todo en memoria y el juez Fake. */
class ScenarioApiTest : FunSpec({
    val start: Instant = Instant.parse("2026-10-26T15:00:00Z")

    fun HttpRequestBuilder.auth(uid: String) = header(HttpHeaders.Authorization, "Bearer ${FakeTokenVerifier.tokenFor(uid)}")

    class Fixture(judgeFails: Boolean = false, moderation: (() -> JsonObject)? = null) {
        val clock = MovableClock(start)
        val identityRepo = InMemoryIdentityRepository().apply {
            listOf("ana", "beto", "carla").forEach { seedAcceptedUser(it, TEST_LEGAL_VERSION) }
        }
        val ana: UUID = identityRepo.user("ana").id
        val beto: UUID = identityRepo.user("beto").id
        val carla: UUID = identityRepo.user("carla").id
        val chatRepo = InMemoryChatRepository()
        val mirror = InMemoryChatMirror()
        val notifier = FakeNotifier()
        val graph = inMemoryGraphModule(clock)
        val chat = inMemoryChatModule(clock, graph.policy, notifier, chatRepo, mirror)
        val simRepo = InMemorySimulationRepository()
        val requests = CopyOnWriteArrayList<LlmRequest>()
        val fake = FakeLlmClient(
            textResponder = { requests += it; "texto" },
            jsonResponder = { r, _ ->
                requests += r
                if (r.system?.contains("moderador") == true && moderation != null) {
                    moderation()
                } else if (judgeFails) {
                    error("el juez falló")
                } else {
                    judgeJson(70)
                }
            },
        )
        val ai = testGateway(fake)
        val entitlements = InMemoryDevEntitlementService()
        val quota = inMemoryQuotaModule(clock, entitlements = entitlements)
        val simQueue = FakeTaskQueue()
        val simulation = inMemorySimulationModule(clock, ai, quota, graph, simRepo, simQueue, chat = chat, runInline = false)
        val queue = FakeTaskQueue()
        val scenario = inMemoryScenarioModule(clock, ai, quota, graph, chat, simulation, notifier, queue, runInline = false)

        init {
            listOf(ana to "ana", beto to "beto", carla to "carla").forEach { (id, name) ->
                chatRepo.addUser(PersonRow(id, "Nombre Secreto de $name", null, "UIS", Visibility.ANONYMOUS, isDemo = false, deletedAt = null), name)
                simRepo.putParticipant(Participant(id, name, Mode.entries.toSet(), true, true, isDemo = false, deleted = false))
                simRepo.putTwin(TwinProfile(id, 1, List(6) { 0.5 }, "Persona secreta del gemelo de $name", personaReady = true))
                simRepo.putMemory(id, TwinMemory(Mode.AMISTAD, "Memoria privada de $name"))
            }
        }

        /** Chat SCENARIO (escenario 1: 'Planear el fin de semana', 80.000 COP) que termina en [seconds]. */
        fun scenarioChat(seconds: Long = 600) = runBlocking {
            chat.service.createScenario(ana, beto, Mode.AMISTAD, 1, clock.instant().plusSeconds(seconds))
        }

        fun judgeRequests() = requests.filter { it.system?.contains("juez imparcial") == true }

        fun judgeTasks() = queue.tasks.filter { it.path == "/tasks/judge-scenario" }

        fun container() = testContainer(
            repo = identityRepo, clock = clock, quota = quota, ai = ai, graph = graph, chat = chat,
            simulation = simulation, notifier = notifier, scenario = scenario,
        )
    }

    suspend fun HttpClient.say(uid: String, chatId: Any, clientId: String, text: String): HttpResponse =
        post("/v1/chats/$chatId/messages") {
            auth(uid)
            contentType(ContentType.Application.Json)
            setBody(SendMessage(clientId, text))
        }

    suspend fun HttpClient.agree(uid: String, chatId: Any): HttpResponse = post("/v1/scenario-sessions/$chatId/agreement") { auth(uid) }

    suspend fun HttpClient.session(uid: String, chatId: Any): ScenarioSessionDto =
        get("/v1/scenario-sessions/$chatId") { auth(uid) }.body()

    suspend fun HttpClient.judgeTask(id: Any): HttpResponse = post("/internal/tasks/judge-scenario") {
        header(HttpHeaders.Authorization, "Bearer ${FakeOidcVerifier.VALID_TOKEN}")
        contentType(ContentType.Application.Json)
        setBody("""{"id":"$id"}""")
    }

    test("el acuerdo requiere a los dos: con uno solo el estado es WAITING_OTHER y el chat sigue abierto") {
        val f = Fixture()
        val chat = f.scenarioChat()
        testApplication {
            val client = startApp(f.container())
            client.say("ana", chat.id, "a-1", "Propongo ir al Chicamocha").status shouldBe HttpStatusCode.OK
            client.say("beto", chat.id, "b-1", "Listo, yo pongo el transporte").status shouldBe HttpStatusCode.OK

            val first = client.agree("ana", chat.id)
            first.status shouldBe HttpStatusCode.OK
            val waiting = first.body<ScenarioSessionDto>()
            waiting.status shouldBe ScenarioSessionStatus.WAITING_OTHER
            waiting.iAgreed shouldBe true
            waiting.otherAgreed shouldBe false
            // Repetirlo no cambia nada.
            client.agree("ana", chat.id).body<ScenarioSessionDto>().status shouldBe ScenarioSessionStatus.WAITING_OTHER
            // Desde la otra persona sigue en curso y ve que Ana ya marcó.
            val seenByBeto = client.session("beto", chat.id)
            seenByBeto.status shouldBe ScenarioSessionStatus.OPEN
            seenByBeto.otherAgreed shouldBe true
            seenByBeto.iAgreed shouldBe false
            f.chatRepo.chat(chat.id)!!.status shouldBe ChatStatus.OPEN
            f.judgeTasks().shouldBeEmpty()
            f.simRepo.find(chat.id).shouldBeNull()

            val both = client.agree("beto", chat.id).body<ScenarioSessionDto>()
            both.status shouldBe ScenarioSessionStatus.JUDGING
            both.title shouldBe "Planear el fin de semana"
            both.budgetCop shouldBe 80_000
            f.chatRepo.chat(chat.id)!!.status shouldBe ChatStatus.CLOSED
            f.mirror.chat(chat.id)!!.status shouldBe ChatStatus.CLOSED
            f.judgeTasks() shouldHaveSize 1
            // Repetir el acuerdo con el chat cerrado por acuerdo sigue siendo idempotente (no 409) y no duplica el juez.
            client.agree("ana", chat.id).body<ScenarioSessionDto>().status shouldBe ScenarioSessionStatus.JUDGING
            f.judgeTasks() shouldHaveSize 1
        }
    }

    test("después de ends_at enviar un mensaje responde 409 SCENARIO_ENDED y el juez se dispara una sola vez") {
        val f = Fixture()
        val chat = f.scenarioChat(seconds = 600)
        testApplication {
            val client = startApp(f.container())
            client.say("ana", chat.id, "a-1", "Hola, ¿qué plan hacemos?").status shouldBe HttpStatusCode.OK
            client.say("beto", chat.id, "b-1", "Podemos ir a Girón").status shouldBe HttpStatusCode.OK
            f.clock.now = start.plusSeconds(601)

            val late = client.say("ana", chat.id, "a-2", "¿Sigues ahí?")
            late.status shouldBe HttpStatusCode.Conflict
            late.body<ApiError>().code shouldBe ErrorCode.SCENARIO_ENDED
            client.say("beto", chat.id, "b-2", "Sí").status shouldBe HttpStatusCode.Conflict
            client.say("ana", chat.id, "a-3", "Otra vez").status shouldBe HttpStatusCode.Conflict

            f.judgeTasks() shouldHaveSize 1
            f.chatRepo.chat(chat.id)!!.status shouldBe ChatStatus.CLOSED
            f.mirror.chat(chat.id)!!.status shouldBe ChatStatus.CLOSED
            // Los mensajes rechazados no se guardaron.
            f.chatRepo.allMessages shouldHaveSize 2

            client.judgeTask(chat.id).status shouldBe HttpStatusCode.NoContent
            client.judgeTask(chat.id).status shouldBe HttpStatusCode.NoContent
            f.judgeRequests() shouldHaveSize 1
        }
    }

    test("el juez recibe solo la transcripción del chat: sin nombres, personas ni memorias de los gemelos") {
        val f = Fixture()
        val chat = f.scenarioChat()
        testApplication {
            val client = startApp(f.container())
            client.say("ana", chat.id, "a-1", "Propongo ir al Chicamocha el domingo").status shouldBe HttpStatusCode.OK
            client.say("beto", chat.id, "b-1", "Perfecto, repartimos los gastos").status shouldBe HttpStatusCode.OK
            client.agree("ana", chat.id)
            client.agree("beto", chat.id)
            client.judgeTask(chat.id).status shouldBe HttpStatusCode.NoContent
        }
        val request = f.judgeRequests().single()
        val sent = request.prompt + "\n" + request.system
        request.prompt shouldContain "Planear el fin de semana"
        request.prompt shouldContain "Presupuesto: 80000 COP"
        request.prompt shouldContain "Propongo ir al Chicamocha el domingo"
        request.prompt shouldContain "Perfecto, repartimos los gastos"
        request.prompt shouldContain "<turno hablante=\"A\">"
        request.prompt shouldContain "<turno hablante=\"B\">"
        request.system.shouldNotBeNullForTest() shouldContain "conversación real"
        listOf("Persona secreta", "Memoria privada", "Nombre Secreto").forEach { secret -> sent shouldNotContain secret }
        // Los nombres de usuario (uid) tampoco viajan como palabra suelta.
        listOf("ana", "beto", "carla").forEach { uid -> Regex("\\b$uid\\b", RegexOption.IGNORE_CASE).containsMatchIn(sent) shouldBe false }
        // Y el orden de la transcripción es el del chat: A escribió primero.
        request.prompt.indexOf("Propongo ir al Chicamocha") shouldBeLessThan request.prompt.indexOf("Perfecto, repartimos")
        // Dos llamadas al LLM: el juez y la moderación de su texto; ningún turno de gemelo.
        f.fake.calls shouldBe 2
    }

    test("con los dos acuerdos ya guardados (carrera sin finalize), el siguiente GET de sesión cierra y pasa a JUDGING") {
        val f = Fixture()
        val chat = f.scenarioChat()
        testApplication {
            val client = startApp(f.container())
            client.say("ana", chat.id, "a-1", "Hola").status shouldBe HttpStatusCode.OK
            client.say("beto", chat.id, "b-1", "Hola, ¿planeamos?").status shouldBe HttpStatusCode.OK
            // Las dos transacciones confirmaron sin verse: ambos agreed_at quedan puestos y nadie llamó a finalize.
            runBlocking {
                f.chatRepo.agree(chat.id, f.ana, f.clock.instant())
                f.chatRepo.agree(chat.id, f.beto, f.clock.instant())
            }
            f.chatRepo.chat(chat.id)!!.status shouldBe ChatStatus.OPEN
            client.session("ana", chat.id).status shouldBe ScenarioSessionStatus.JUDGING
            f.chatRepo.chat(chat.id)!!.status shouldBe ChatStatus.CLOSED
            f.judgeTasks() shouldHaveSize 1
        }
    }

    test("el resultado es una simulación SCENARIO con el mismo DTO: puntaje, sesión DONE, arista y aviso a los dos") {
        val f = Fixture()
        val chat = f.scenarioChat()
        testApplication {
            val client = startApp(f.container())
            client.say("ana", chat.id, "a-1", "Hola").status shouldBe HttpStatusCode.OK
            client.say("beto", chat.id, "b-1", "Hola, ¿planeamos?").status shouldBe HttpStatusCode.OK
            client.agree("ana", chat.id)
            client.agree("beto", chat.id)
            client.session("ana", chat.id).status shouldBe ScenarioSessionStatus.JUDGING

            client.judgeTask(chat.id).status shouldBe HttpStatusCode.NoContent
            val done = client.session("ana", chat.id)
            done.status shouldBe ScenarioSessionStatus.DONE
            done.simulationId shouldBe chat.id.toString()

            val sim = client.get("/v1/simulations/${done.simulationId}") { auth("beto") }.body<SimulationDto>()
            sim.summary.status shouldBe SimulationStatus.DONE
            sim.summary.origin shouldBe SimulationOrigin.SCENARIO
            // .35 · similitud de rasgos (100: rasgos iguales) + .65 · afinidad del juez (70) = 80,5.
            sim.score shouldBe 81

            val stored = f.simRepo.find(chat.id).shouldNotBeNull()
            stored.transcript.scenarios.single().turns.map { it.text } shouldBe listOf("Hola", "Hola, ¿planeamos?")
            stored.transcript.scenarios.single().verdict.shouldNotBeNull()
            f.notifier.sent.count { it.notification.kind == NotificationKinds.SIMULATION_READY } shouldBe 2
            // La sesión de una persona ajena no existe para quien pregunta.
            client.get("/v1/scenario-sessions/${chat.id}") { auth("carla") }.status shouldBe HttpStatusCode.Forbidden
        }
    }

    fun moderationJson(category: String, severity: String) = buildJsonObject {
        putJsonArray("findings") { addJsonObject { put("category", category); put("severity", severity) } }
    }

    suspend fun HttpClient.judgedVerdict(f: Fixture): JudgeVerdict {
        val chat = f.scenarioChat()
        say("ana", chat.id, "a-1", "Hola").status shouldBe HttpStatusCode.OK
        say("beto", chat.id, "b-1", "Hola, ¿planeamos?").status shouldBe HttpStatusCode.OK
        agree("ana", chat.id)
        agree("beto", chat.id)
        judgeTask(chat.id).status shouldBe HttpStatusCode.NoContent
        return f.simRepo.find(chat.id).shouldNotBeNull().transcript.scenarios.single().verdict.shouldNotBeNull()
    }

    test("un hallazgo HIGH en el texto del veredicto lo sustituye por uno genérico y conserva los puntajes") {
        val f = Fixture(moderation = { moderationJson("HARASSMENT", "HIGH") })
        testApplication {
            val v = startApp(f.container()).judgedVerdict(f)
            v.summary shouldBe ScenarioService.REDACTED_SUMMARY
            v.reasons.shouldBeEmpty()
            v.frictions.shouldBeEmpty()
            v.dimensions.all { it.note == null } shouldBe true
            v.affinity shouldBe 70
            v.dimensions.isNotEmpty() shouldBe true
        }
    }

    test("SELF_HARM, hallazgos LOW o una moderación que falla dejan el veredicto intacto (fail-open)") {
        listOf<() -> JsonObject>(
            { moderationJson("SELF_HARM", "HIGH") },
            { moderationJson("HARASSMENT", "LOW") },
            { error("la moderación falló") },
        ).forEach { moderation ->
            val f = Fixture(moderation = moderation)
            testApplication {
                val v = startApp(f.container()).judgedVerdict(f)
                v.summary shouldNotBe ScenarioService.REDACTED_SUMMARY
                v.summary.isNotEmpty() shouldBe true
            }
        }
    }

    test("un chat sin mensajes al vencer se cierra sin evaluar: sesión DONE sin simulationId") {
        val f = Fixture()
        val chat = f.scenarioChat()
        testApplication {
            val client = startApp(f.container())
            f.clock.now = start.plusSeconds(700)
            val session = client.session("ana", chat.id)
            session.status shouldBe ScenarioSessionStatus.DONE
            session.simulationId.shouldBeNull()
            f.chatRepo.chat(chat.id)!!.status shouldBe ChatStatus.CLOSED
            f.judgeTasks().shouldBeEmpty()
            f.simRepo.find(chat.id).shouldBeNull()
            // Con el tiempo vencido ya no hay acuerdo posible.
            val late = client.agree("ana", chat.id)
            late.status shouldBe HttpStatusCode.Conflict
            late.body<ApiError>().code shouldBe ErrorCode.SCENARIO_ENDED
        }
    }

    test("el barrido cierra y evalúa los escenarios vencidos aunque nadie vuelva a escribir, y no duplica") {
        val f = Fixture()
        val chat = f.scenarioChat()
        testApplication {
            val client = startApp(f.container())
            client.say("ana", chat.id, "a-1", "Hola").status shouldBe HttpStatusCode.OK
            f.clock.now = start.plusSeconds(601)
            runBlocking { f.scenario.service.sweep() }.closed shouldBe 1
            runBlocking { f.scenario.service.sweep() }.closed shouldBe 0
            f.chatRepo.chat(chat.id)!!.status shouldBe ChatStatus.CLOSED
            f.judgeTasks() shouldHaveSize 1
            // Aún sin pasar 2 minutos no se re-encola; después sí (misma tarea, por nombre no se duplica).
            runBlocking { f.scenario.service.sweep() }.requeued shouldBe 0
            f.clock.now = start.plusSeconds(601 + 180)
            runBlocking { f.scenario.service.sweep() }.requeued shouldBe 1
            f.judgeTasks() shouldHaveSize 1
            client.post("/internal/jobs/scenario-sweep") {
                header(HttpHeaders.Authorization, "Bearer ${FakeOidcVerifier.VALID_TOKEN}")
            }.status shouldBe HttpStatusCode.NoContent
        }
    }

    test("si el juez falla tres veces la evaluación queda FAILED y la sesión DONE sin simulationId") {
        val f = Fixture(judgeFails = true)
        val chat = f.scenarioChat()
        testApplication {
            val client = startApp(f.container())
            client.say("ana", chat.id, "a-1", "Hola").status shouldBe HttpStatusCode.OK
            client.agree("ana", chat.id)
            client.agree("beto", chat.id)
            client.judgeTask(chat.id).status shouldBe HttpStatusCode.NoContent
            f.simRepo.find(chat.id)!!.status shouldBe SimulationStatus.FAILED
            f.judgeRequests() shouldHaveSize 3
            val session = client.session("beto", chat.id)
            session.status shouldBe ScenarioSessionStatus.DONE
            session.simulationId.shouldBeNull()
            f.notifier.sent.count { it.notification.kind == NotificationKinds.SIMULATION_READY } shouldBe 0
        }
    }

    test("el motor de gemelos nunca toca una evaluación de escenario y la caché de simulaciones no la reutiliza") {
        val f = Fixture()
        val chat = f.scenarioChat()
        testApplication {
            val client = startApp(f.container())
            client.say("ana", chat.id, "a-1", "Hola").status shouldBe HttpStatusCode.OK
            client.agree("ana", chat.id)
            client.agree("beto", chat.id)
            runBlocking { f.simulation.engine.run(chat.id) }
            f.simRepo.find(chat.id)!!.status shouldBe SimulationStatus.QUEUED
            f.fake.calls shouldBe 0
            // La recuperación de simulaciones no la vuelve a encolar con `simulate`.
            runBlocking { f.simRepo.unfinished(start.plusSeconds(3600), start.minusSeconds(3600), 10) }.shouldBeEmpty()
            runBlocking { f.simRepo.unfinishedScenarios(start.plusSeconds(3600), 10) } shouldHaveSize 1
            client.judgeTask(chat.id).status shouldBe HttpStatusCode.NoContent
            runBlocking { f.simRepo.findCached(f.ana, 1, f.beto, 1, Mode.AMISTAD, start.minusSeconds(3600), UUID.randomUUID()) }.shouldBeNull()
            runBlocking { f.simRepo.findCached(f.beto, 1, f.ana, 1, Mode.AMISTAD, start.minusSeconds(3600), UUID.randomUUID()) }.shouldBeNull()
        }
    }

    test("GET /v1/scenarios lista los del modo y marca como bloqueados los premium para quien no es Plus") {
        val f = Fixture()
        testApplication {
            val client = startApp(f.container())
            val free = client.get("/v1/scenarios?mode=AMISTAD") { auth("ana") }.body<List<ScenarioDto>>()
            free.map { it.id } shouldBe listOf(1, 2, 3, 4, 5)
            free.filter { it.isBasic }.all { !it.locked } shouldBe true
            free.filter { !it.isBasic }.all { it.locked } shouldBe true
            f.entitlements.grantPlus(f.ana, 30, f.clock.instant())
            client.get("/v1/scenarios?mode=AMISTAD") { auth("ana") }.body<List<ScenarioDto>>().none { it.locked } shouldBe true
            client.get("/v1/scenarios?mode=otro") { auth("ana") }.status shouldBe HttpStatusCode.BadRequest
            client.get("/v1/scenarios") { auth("ana") }.body<List<ScenarioDto>>() shouldHaveSize 15
        }
    }

    test("una sesión de un chat DIRECT o un id inválido no existen; un bloqueo impide el acuerdo") {
        val f = Fixture()
        val direct = runBlocking { f.chat.service.createDirect(f.ana, f.beto, Mode.AMISTAD) }
        val chat = f.scenarioChat()
        testApplication {
            val client = startApp(f.container())
            client.get("/v1/scenario-sessions/${direct.id}") { auth("ana") }.status shouldBe HttpStatusCode.NotFound
            client.agree("ana", "no-es-uuid").status shouldBe HttpStatusCode.NotFound
            f.chatRepo.block(f.beto, f.ana)
            val blocked = client.agree("ana", chat.id)
            blocked.status shouldBe HttpStatusCode.Forbidden
            blocked.body<ApiError>().code shouldBe ErrorCode.BLOCKED
        }
    }

    test("SCENARIO_MODE_ENABLED: encendida por defecto en local y dev, apagada en prod, y manda la variable") {
        MatchingFlags.fromEnv(Env.LOCAL, emptyMap()).scenarioModeEnabled shouldBe true
        MatchingFlags.fromEnv(Env.DEV, emptyMap()).scenarioModeEnabled shouldBe true
        MatchingFlags.fromEnv(Env.PROD, emptyMap()).scenarioModeEnabled shouldBe false
        MatchingFlags.fromEnv(Env.PROD, mapOf("SCENARIO_MODE_ENABLED" to "true")).scenarioModeEnabled shouldBe true
        MatchingFlags.fromEnv(Env.DEV, mapOf("SCENARIO_MODE_ENABLED" to "false")).scenarioModeEnabled shouldBe false
    }

    test("un mensaje sigue funcionando mientras el escenario está en curso y el chat sin escenario no cuenta") {
        val f = Fixture()
        val chat = f.scenarioChat()
        testApplication {
            val client = startApp(f.container())
            val ok = client.say("ana", chat.id, "a-1", "Hola")
            ok.status shouldBe HttpStatusCode.OK
            ok.body<MessageDto>().text shouldBe "Hola"
            f.judgeTasks().shouldBeEmpty()
        }
    }
})

private fun String?.shouldNotBeNullForTest(): String = this.shouldNotBeNull()

