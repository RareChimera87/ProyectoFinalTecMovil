package me.santiagoreina.parcheu.api.home

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
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
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.TEST_LEGAL_VERSION
import me.santiagoreina.parcheu.api.identity.InMemoryIdentityRepository
import me.santiagoreina.parcheu.api.matching.MatchingFlags
import me.santiagoreina.parcheu.api.matching.inMemoryMatchingModule
import me.santiagoreina.parcheu.api.platform.notifier.NoopNotifier
import me.santiagoreina.parcheu.api.platform.auth.FakeTokenVerifier
import me.santiagoreina.parcheu.api.simulation.SimFixture
import me.santiagoreina.parcheu.api.startApp
import me.santiagoreina.parcheu.api.testContainer
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.HomeDto
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.SimulationStatus
import me.santiagoreina.parcheu.contract.SimulationSummary
import me.santiagoreina.parcheu.contract.StartSimulation
import java.util.UUID

/** `GET /v1/home` (E4.7) de punta a punta con Fakes: cupos, simulaciones, sugerencias y no leídas. */
class HomeApiTest : FunSpec({
    val uids = listOf("ana", "beto", "caro", "dani", "eli")

    class World(val demoUsersEnabled: Boolean = true) {
        val identity = InMemoryIdentityRepository().also { repo -> uids.forEach { repo.seedAcceptedUser(it, TEST_LEGAL_VERSION) } }

        /** La tarea `simulate` no corre sola: la prueba decide cuándo avanza cada simulación. */
        val sim = SimFixture(inline = false, demoUsersEnabled = demoUsersEnabled)
        val ids = uids.associateWith { uid -> sim.person(uid, id = identity.user(uid).id) }
        val unread = mutableMapOf<UUID, Int>()

        fun id(uid: String) = ids.getValue(uid)

        fun connect(a: String, b: String, mode: Mode = Mode.AMISTAD, affinity: Int? = 70) =
            sim.graphRepo.putEdge(id(a), id(b), mode, EdgeState.CONNECTED, affinity)
    }

    fun HttpRequestBuilder.auth(uid: String) = header(HttpHeaders.Authorization, "Bearer ${FakeTokenVerifier.tokenFor(uid)}")

    suspend fun ApplicationTestBuilder.app(w: World): HttpClient = startApp(
        testContainer(
            repo = w.identity,
            ai = w.sim.ai,
            quota = w.sim.quota,
            graph = w.sim.graph,
            simulation = w.sim.module,
            matching = inMemoryMatchingModule(
                FIXED_CLOCK, w.sim.quota, w.sim.module, w.sim.graph, NoopNotifier,
                flags = MatchingFlags(demoUsersEnabled = w.demoUsersEnabled, scenarioModeEnabled = false),
            ),
            unread = UnreadCounter { w.unread[it] ?: 0 },
        ),
    )

    suspend fun HttpClient.start(uid: String, other: String): SimulationSummary = post("/v1/simulations") {
        auth(uid)
        contentType(ContentType.Application.Json)
        setBody(StartSimulation(other, Mode.AMISTAD))
    }.body()

    test("home refleja el cupo de QuotaService y la simulación RUNNING del usuario") {
        val w = World()
        w.unread[w.id("ana")] = 4
        testApplication {
            val client = app(w)
            // Una simulación terminada (resultado listo) y otra en curso: dos usos de la semana.
            val done = client.start("ana", w.id("caro").toString())
            w.sim.module.engine.run(UUID.fromString(done.id))
            val running = client.start("ana", w.id("beto").toString())
            w.sim.repo.markRunning(UUID.fromString(running.id)) shouldBe true

            val response = client.get("/v1/home") { auth("ana") }
            response.status shouldBe HttpStatusCode.OK
            val home = response.body<HomeDto>()
            // Mismo DTO que arma QuotaService (el de GET /v1/quota) para el users.id de Ana.
            home.quota shouldBe w.sim.quota.service.status(w.id("ana"))
            home.quota.simulations.used shouldBe 2
            home.quota.simulations.limit shouldBe 5
            home.plus shouldBe false

            home.running shouldNotBe null
            home.running!!.id shouldBe running.id
            home.running!!.status shouldBe SimulationStatus.RUNNING
            home.ready.map { it.id } shouldContainExactly listOf(done.id)
            home.ready.single().status shouldBe SimulationStatus.DONE
            home.unread shouldBe 4

            // Beto no ve la simulación de Ana con Caro, pero sí la que tiene en curso con ella.
            val beto = client.get("/v1/home") { auth("beto") }.body<HomeDto>()
            beto.running!!.id shouldBe running.id
            beto.ready shouldBe emptyList()
            beto.quota.simulations.used shouldBe 0
            beto.unread shouldBe 0
        }
    }

    test("con ParcheU+ el cupo sale sin límite y plus = true") {
        val w = World()
        w.sim.plus(w.id("ana"))
        testApplication {
            val home = app(w).get("/v1/home") { auth("ana") }.body<HomeDto>()
            home.plus shouldBe true
            home.quota.plus shouldBe true
            home.quota.simulations.limit shouldBe null
            home.running shouldBe null
        }
    }

    test("para gratis las sugerencias no incluyen mutualCount; con ParcheU+ sí") {
        val w = World()
        // Ana está conectada con Beto y Caro, que también se conocen (1 amigo en común cada uno).
        w.connect("ana", "beto", affinity = 82)
        w.connect("ana", "caro", Mode.ESTUDIO, affinity = 64)
        w.connect("beto", "caro")
        // Pareja nunca se sugiere.
        w.connect("ana", "dani", Mode.PAREJA, affinity = 95)
        testApplication {
            val client = app(w)
            val raw = client.get("/v1/home") { auth("ana") }.bodyAsText()
            raw shouldNotContain "mutualCount"
            val free = client.get("/v1/home") { auth("ana") }.body<HomeDto>()
            free.suggestions.map { it.other.label } shouldContainExactly listOf("beto", "caro")
            free.suggestions.forEach { it.mutualCount shouldBe null }
            free.suggestions.map { it.mode } shouldContainExactly listOf(Mode.AMISTAD, Mode.ESTUDIO)
            free.suggestions.first().other.id shouldBe w.id("beto").toString()
            free.suggestions.first().estimatedAffinity shouldBe 82
            free.suggestions.forEach { it.reason!! shouldNotContain "común" }

            w.sim.plus(w.id("ana"))
            val plus = client.get("/v1/home") { auth("ana") }.body<HomeDto>()
            plus.suggestions shouldHaveSize 2
            plus.suggestions.map { it.mutualCount } shouldContainExactly listOf(1, 1)
        }
    }

    test("las sugerencias respetan los modos activos y 'Simular' con la sugerida funciona") {
        val w = World()
        w.connect("ana", "beto", Mode.ESTUDIO, affinity = 90)
        w.connect("ana", "beto", Mode.AMISTAD, affinity = 60)
        // Beto apaga Estudio: la arista sigue en el grafo, pero ya no se puede simular en ese modo.
        w.sim.person("beto", modes = setOf(Mode.AMISTAD), id = w.id("beto"))
        testApplication {
            val client = app(w)
            val suggestion = client.get("/v1/home") { auth("ana") }.body<HomeDto>().suggestions.single()
            suggestion.other.label shouldBe "beto"
            suggestion.mode shouldBe Mode.AMISTAD
            suggestion.estimatedAffinity shouldBe 60
            val started = client.post("/v1/simulations") {
                auth("ana")
                contentType(ContentType.Application.Json)
                setBody(StartSimulation(suggestion.other.id!!, suggestion.mode))
            }
            started.status shouldBe HttpStatusCode.Accepted
        }
    }

    test("quien está simulando con el usuario no aparece en sugerencias") {
        val w = World()
        w.connect("ana", "beto")
        w.connect("ana", "eli", affinity = 50)
        testApplication {
            val client = app(w)
            client.start("ana", w.id("beto").toString())
            val home = client.get("/v1/home") { auth("ana") }.body<HomeDto>()
            home.running!!.status shouldBe SimulationStatus.QUEUED
            home.suggestions.map { it.other.label } shouldContainExactly listOf("eli")
        }
    }

    test("con DEMO_USERS_ENABLED=false las sugerencias excluyen al demo aunque haya arista CONNECTED con él") {
        val w = World(demoUsersEnabled = false)
        // Valentina (demo) dijo "Conectar" durante la prueba cerrada; luego se apagó la bandera sin purgar aún.
        val valentina = w.sim.person("valentina", id = UUID.randomUUID(), isDemo = true)
        w.sim.graphRepo.putEdge(w.id("ana"), valentina, Mode.AMISTAD, EdgeState.CONNECTED, 95)
        w.connect("ana", "beto", affinity = 60)
        testApplication {
            val client = app(w)
            val home = client.get("/v1/home") { auth("ana") }.body<HomeDto>()
            home.suggestions.map { it.other.label } shouldContainExactly listOf("beto")
            home.suggestions.none { it.other.isDemo } shouldBe true
            // Defensa en profundidad: simular con el demo se rechaza sin gastar cupo.
            val started = client.post("/v1/simulations") {
                auth("ana")
                contentType(ContentType.Application.Json)
                setBody(StartSimulation(valentina.toString(), Mode.AMISTAD))
            }
            started.status shouldBe HttpStatusCode.BadRequest
            client.get("/v1/home") { auth("ana") }.body<HomeDto>().quota.simulations.used shouldBe 0
        }
    }

    test("con DEMO_USERS_ENABLED=true el demo conectado sí se sugiere, marcado como demo") {
        val w = World(demoUsersEnabled = true)
        val valentina = w.sim.person("valentina", id = UUID.randomUUID(), isDemo = true)
        w.sim.graphRepo.putEdge(w.id("ana"), valentina, Mode.AMISTAD, EdgeState.CONNECTED, 95)
        testApplication {
            val suggestion = app(w).get("/v1/home") { auth("ana") }.body<HomeDto>().suggestions.single()
            suggestion.other.id shouldBe valentina.toString()
            suggestion.other.isDemo shouldBe true
        }
    }

    test("sin token responde 401") {
        val w = World()
        testApplication {
            app(w).get("/v1/home").status shouldBe HttpStatusCode.Unauthorized
        }
    }
})
