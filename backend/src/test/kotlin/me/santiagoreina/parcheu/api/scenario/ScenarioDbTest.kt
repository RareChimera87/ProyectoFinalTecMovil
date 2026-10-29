package me.santiagoreina.parcheu.api.scenario

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import me.santiagoreina.parcheu.api.ai.FakeLlmClient
import me.santiagoreina.parcheu.api.ai.LlmRequest
import me.santiagoreina.parcheu.api.ai.testGateway
import me.santiagoreina.parcheu.api.chat.ChatModule
import me.santiagoreina.parcheu.api.chat.ExposedChatRepository
import me.santiagoreina.parcheu.api.chat.InMemoryChatMirror
import me.santiagoreina.parcheu.api.graph.AnonIdSigner
import me.santiagoreina.parcheu.api.graph.ExposedGraphRepository
import me.santiagoreina.parcheu.api.graph.GraphModule
import me.santiagoreina.parcheu.api.identity.ExposedIdentityRepository
import me.santiagoreina.parcheu.api.identity.UserRecord
import me.santiagoreina.parcheu.api.platform.config.Env
import me.santiagoreina.parcheu.api.platform.db.TestDatabase
import me.santiagoreina.parcheu.api.platform.db.connectExposed
import me.santiagoreina.parcheu.api.platform.db.runMigrations
import me.santiagoreina.parcheu.api.platform.db.tables.ChatMembers
import me.santiagoreina.parcheu.api.platform.db.tables.Edges
import me.santiagoreina.parcheu.api.platform.db.tables.Twins
import me.santiagoreina.parcheu.api.platform.db.tables.UserModes
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.api.platform.notifier.FakeNotifier
import me.santiagoreina.parcheu.api.platform.tasks.FakeTaskQueue
import me.santiagoreina.parcheu.api.quota.quotaModule
import me.santiagoreina.parcheu.api.simulation.NoopSimulationNotifier
import me.santiagoreina.parcheu.api.simulation.judgeJson
import me.santiagoreina.parcheu.api.simulation.simulationModule
import me.santiagoreina.parcheu.contract.ChatStatus
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.ScenarioSessionStatus
import me.santiagoreina.parcheu.contract.SendMessage
import me.santiagoreina.parcheu.contract.SimulationOrigin
import me.santiagoreina.parcheu.contract.SimulationStatus
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/** E4.8 contra Postgres 16 real (Docker o `PARCHEU_TEST_DB_URL`): acuerdo, cierre atómico y juez sobre las tablas reales. */
class ScenarioDbTest : FunSpec({
    class Movable(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
    }

    val start = Instant.parse("2026-10-20T15:00:00Z")

    class World(val db: Database, val clock: Movable) {
        val requests = CopyOnWriteArrayList<LlmRequest>()
        val ai = testGateway(FakeLlmClient(jsonResponder = { r, _ -> requests += r; judgeJson(60) }))
        val quota = quotaModule(Env.DEV, db, clock)
        val graph = GraphModule(ExposedGraphRepository(db), AnonIdSigner.forTests(), clock)
        val queue = FakeTaskQueue()
        val notifier = FakeNotifier()
        val chat = ChatModule(ExposedChatRepository(db), InMemoryChatMirror(), notifier, graph.policy, clock)
        val simulation = simulationModule(
            Env.DEV, db, clock, ai, quota, graph, FakeTaskQueue(), firebaseProjectId = null,
            notifier = NoopSimulationNotifier, chat = chat, push = notifier,
        )
        val scenario = scenarioModule(clock, ai, quota, graph, chat, simulation, queue, notifier)
        val identity = ExposedIdentityRepository(db)

        fun user(name: String): UserRecord {
            val at = OffsetDateTime.ofInstant(start, ZoneOffset.UTC)
            val id = transaction(db) {
                val id = Users.insert {
                    it[firebaseUid] = "uid-$name"
                    it[displayName] = name
                    it[ageVerifiedAt] = at
                    it[createdAt] = at
                    it[updatedAt] = at
                }[Users.id]
                Mode.entries.forEach { m ->
                    UserModes.insert {
                        it[userId] = id
                        it[mode] = m
                        it[enabled] = true
                        it[enabledAt] = at
                    }
                }
                Twins.insert {
                    it[userId] = id
                    it[traits] = listOf(0.2f, 0.4f, 0.6f, 0.8f, 0.5f, 0.5f)
                    it[personaSummary] = "Persona de $name"
                    it[personaReady] = true
                    it[version] = 1
                    it[updatedAt] = at
                }
                id
            }
            return runBlocking { identity.findByFirebaseUid("uid-$name") }!!
        }
    }

    fun withWorld(block: suspend (World) -> Unit) = TestDatabase.withCleanDatabase { ds ->
        runMigrations(ds)
        runBlocking { block(World(connectExposed(ds), Movable(start))) }
    }

    test("acuerdo de los dos, cierre atómico y juez sobre Postgres: simulación SCENARIO DONE, arista y un solo juez").config(enabled = TestDatabase.enabled) {
        withWorld { w ->
            val ana = w.user("Ana")
            val beto = w.user("Beto")
            val chat = w.chat.service.createScenario(ana.id, beto.id, Mode.AMISTAD, 1, start.plusSeconds(600))
            w.chat.service.send(ana, chat.id, SendMessage("a-1", "Vamos al Chicamocha"))
            w.chat.service.send(beto, chat.id, SendMessage("b-1", "Dale, yo llevo el almuerzo"))

            val first = w.scenario.service.agree(ana.id, chat.id.toString())
            first.status shouldBe ScenarioSessionStatus.WAITING_OTHER
            w.scenario.service.session(beto.id, chat.id.toString()).status shouldBe ScenarioSessionStatus.OPEN
            transaction(w.db) { ChatMembers.selectAll().count { it[ChatMembers.agreedAt] != null } } shouldBe 1

            val both = w.scenario.service.agree(beto.id, chat.id.toString())
            both.status shouldBe ScenarioSessionStatus.JUDGING
            both.budgetCop shouldBe 80_000
            w.chat.repository.chat(chat.id)!!.status shouldBe ChatStatus.CLOSED
            // Cierre atómico: cualquier otra llamada concurrente o repetida no vuelve a encolar.
            w.scenario.service.finalize(chat.id)
            w.queue.tasks shouldHaveSize 1

            w.scenario.service.judge(chat.id)
            w.scenario.service.judge(chat.id)
            // El juez (una llamada) y la moderación lite de su texto.
            w.requests.filter { it.system?.contains("juez imparcial") == true } shouldHaveSize 1
            w.requests shouldHaveSize 2
            val sim = w.simulation.repository.find(chat.id).shouldNotBeNull()
            sim.origin shouldBe SimulationOrigin.SCENARIO
            sim.status shouldBe SimulationStatus.DONE
            sim.transcript.scenarios.single().turns.map { it.text }.toSet() shouldBe setOf("Vamos al Chicamocha", "Dale, yo llevo el almuerzo")
            transaction(w.db) { Edges.selectAll().count() } shouldBe 1L
            w.scenario.service.session(ana.id, chat.id.toString()).simulationId shouldBe chat.id.toString()
        }
    }

    test("ocultar un mensaje tras juzgar retira su turno en Postgres y replaceDoneTranscript es compare-and-set (B-B7)").config(enabled = TestDatabase.enabled) {
        withWorld { w ->
            val ana = w.user("Ana")
            val beto = w.user("Beto")
            val chat = w.chat.service.createScenario(ana.id, beto.id, Mode.AMISTAD, 1, start.plusSeconds(600))
            w.chat.service.send(ana, chat.id, SendMessage("a-1", "Vamos al Chicamocha"))
            val oculto = w.chat.service.send(beto, chat.id, SendMessage("b-1", "texto que se oculta luego"))
            w.scenario.service.agree(ana.id, chat.id.toString())
            w.scenario.service.agree(beto.id, chat.id.toString())
            w.scenario.service.judge(chat.id)
            val before = w.simulation.repository.find(chat.id).shouldNotBeNull().transcript
            before.scenarios.single().turns shouldHaveSize 2

            w.chat.service.hideMessage(java.util.UUID.fromString(oculto.id))
            val after = w.simulation.repository.find(chat.id).shouldNotBeNull().transcript
            after.scenarios.single().turns.map { it.text } shouldBe listOf("Vamos al Chicamocha")
            // Con una transcripción esperada distinta a la guardada, no escribe.
            w.simulation.repository.replaceDoneTranscript(chat.id, before, after) shouldBe false
            w.simulation.repository.replaceDoneTranscript(chat.id, after, before) shouldBe true
        }
    }

    test("tras ends_at: el envío responde 409, el barrido cierra y una segunda pasada no duplica").config(enabled = TestDatabase.enabled) {
        withWorld { w ->
            val ana = w.user("Ana")
            val beto = w.user("Beto")
            val chat = w.chat.service.createScenario(ana.id, beto.id, Mode.AMISTAD, 2, start.plusSeconds(600))
            w.chat.service.send(ana, chat.id, SendMessage("a-1", "Hola"))
            w.clock.now = start.plusSeconds(601)
            val error = runCatching { w.chat.service.send(beto, chat.id, SendMessage("b-1", "Tarde")) }.exceptionOrNull()
            (error as? me.santiagoreina.parcheu.api.platform.errors.DomainException)?.code shouldBe me.santiagoreina.parcheu.contract.ErrorCode.SCENARIO_ENDED
            w.queue.tasks shouldHaveSize 1
            w.scenario.service.sweep().closed shouldBe 0
            w.chat.repository.expiredOpenScenarios(w.clock.instant(), 10) shouldHaveSize 0
        }
    }
})
