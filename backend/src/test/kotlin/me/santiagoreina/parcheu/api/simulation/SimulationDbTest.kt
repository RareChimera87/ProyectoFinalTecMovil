package me.santiagoreina.parcheu.api.simulation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeBetween
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.runBlocking
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.ai.FakeLlmClient
import me.santiagoreina.parcheu.api.ai.LlmRequest
import me.santiagoreina.parcheu.api.ai.testGateway
import me.santiagoreina.parcheu.api.chat.ChatModule
import me.santiagoreina.parcheu.api.chat.ExposedChatRepository
import me.santiagoreina.parcheu.api.chat.InMemoryChatMirror
import me.santiagoreina.parcheu.api.platform.notifier.NoopNotifier
import me.santiagoreina.parcheu.api.graph.AnonIdSigner
import me.santiagoreina.parcheu.api.graph.ExposedGraphRepository
import me.santiagoreina.parcheu.api.graph.GraphModule
import me.santiagoreina.parcheu.api.platform.config.Env
import me.santiagoreina.parcheu.api.platform.db.TestDatabase
import me.santiagoreina.parcheu.api.platform.db.connectExposed
import me.santiagoreina.parcheu.api.platform.db.runMigrations
import me.santiagoreina.parcheu.api.platform.db.tables.Blocks
import me.santiagoreina.parcheu.api.platform.db.tables.Edges
import me.santiagoreina.parcheu.api.platform.db.tables.Simulations
import me.santiagoreina.parcheu.api.platform.db.tables.TwinMemories
import me.santiagoreina.parcheu.api.platform.db.tables.Twins
import me.santiagoreina.parcheu.api.platform.db.tables.UsageEvents
import me.santiagoreina.parcheu.api.platform.db.tables.UserModes
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.api.platform.tasks.FakeTaskQueue
import me.santiagoreina.parcheu.api.quota.quotaModule
import me.santiagoreina.parcheu.api.twin.TaskPayload
import me.santiagoreina.parcheu.contract.ContractJson
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.MemoryStatus
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Progress
import me.santiagoreina.parcheu.contract.SimulationOrigin
import me.santiagoreina.parcheu.contract.SimulationStatus
import me.santiagoreina.parcheu.contract.StartSimulation
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * El motor contra Postgres 16 real (esquema y semillas de Flyway). Necesita Docker o
 * `PARCHEU_TEST_DB_URL`: con `-PsinContenedores` se omite (ver [TestDatabase]).
 */
class SimulationDbTest : FunSpec({
    val now = OffsetDateTime.parse("2026-10-20T10:00:00-05:00")

    fun Database.user(name: String, modes: Set<Mode> = Mode.entries.toSet(), persona: String = "Persona de $name"): UUID = transaction(this) {
        val id = Users.insert {
            it[firebaseUid] = "uid-$name"
            it[displayName] = name
            it[ageVerifiedAt] = now
            it[parejaConfirmedAt] = now
            it[createdAt] = now
            it[updatedAt] = now
        }[Users.id]
        Mode.entries.forEach { m ->
            UserModes.insert {
                it[userId] = id
                it[mode] = m
                it[enabled] = m in modes
                it[enabledAt] = now
            }
        }
        Twins.insert {
            it[userId] = id
            it[traits] = listOf(0.2f, 0.4f, 0.6f, 0.8f, 0.5f, 0.5f)
            it[personaSummary] = persona
            it[personaReady] = true
            it[version] = 1
            it[updatedAt] = now
        }
        id
    }

    fun Database.memory(user: UUID, mode: Mode, text: String, status: MemoryStatus = MemoryStatus.ACCEPTED) = transaction(this) {
        TwinMemories.insert {
            it[userId] = user
            it[TwinMemories.mode] = mode
            it[TwinMemories.text] = text
            it[TwinMemories.status] = status
            it[createdAt] = now
            it[decidedAt] = now
        }
    }

    class Db(val db: Database, judgeFails: Boolean = false) {
        val requests = CopyOnWriteArrayList<LlmRequest>()
        val fake = FakeLlmClient(
            textResponder = { r -> requests += r; r.system.orEmpty().lines().filter { it.startsWith("- ") }.joinToString(" ").ifBlank { "Dale, de una." } },
            jsonResponder = { r, _ -> requests += r; if (judgeFails) error("juez caído") else judgeJson(70) },
        )
        val ai = testGateway(fake)
        val quota = quotaModule(Env.DEV, db, FIXED_CLOCK)
        val graph = GraphModule(ExposedGraphRepository(db), AnonIdSigner.forTests(), FIXED_CLOCK)
        val queue = FakeTaskQueue()
        val module = simulationModule(Env.DEV, db, FIXED_CLOCK, ai, quota, graph, queue, firebaseProjectId = null, notifier = NoopSimulationNotifier,
            chat = ChatModule(ExposedChatRepository(db), InMemoryChatMirror(), NoopNotifier, graph.policy, FIXED_CLOCK), push = NoopNotifier,
        ).also { m ->
            queue.inline = { task -> m.engine.run(ContractJson.decodeFromString<TaskPayload>(task.body).uuid()) }
        }

        fun start(a: UUID, b: UUID, mode: Mode = Mode.AMISTAD) = runBlocking {
            checkNotNull(module.repository.find(UUID.fromString(module.service.start(a, StartSimulation(b.toString(), mode)).id)))
        }
    }

    fun withDb(block: (Database) -> Unit) = TestDatabase.withCleanDatabase { ds ->
        runMigrations(ds)
        block(connectExposed(ds))
    }

    test("Postgres: los escenarios sembrados coinciden con DefaultScenarios (rúbricas incluidas)").config(enabled = TestDatabase.enabled) {
        withDb { db ->
            val repo = ExposedSimulationRepository(db)
            val fromDb = runBlocking { Mode.entries.flatMap { repo.scenarios(it) } }.sortedBy { it.id }
            fromDb shouldContainExactly DefaultScenarios.all
        }
    }

    test("Postgres: participante, bloqueos, gemelo y memorias aceptadas filtradas por modo en SQL").config(enabled = TestDatabase.enabled) {
        withDb { db ->
            val ana = db.user("ana", modes = setOf(Mode.AMISTAD))
            val beto = db.user("beto")
            db.memory(ana, Mode.AMISTAD, "amistad ok")
            db.memory(ana, Mode.PAREJA, "pareja no")
            db.memory(ana, Mode.AMISTAD, "pendiente no", MemoryStatus.PENDING)
            db.memory(ana, Mode.AMISTAD, "rechazada no", MemoryStatus.REJECTED)
            transaction(db) {
                Blocks.insert {
                    it[blockerId] = beto
                    it[blockedId] = ana
                    it[createdAt] = now
                }
            }
            val repo = ExposedSimulationRepository(db)
            runBlocking {
                val p = repo.participant(ana)!!
                p.activeModes shouldBe setOf(Mode.AMISTAD)
                p.ageVerified shouldBe true
                p.firebaseUid shouldBe "uid-ana"
                repo.participant(UUID.randomUUID()) shouldBe null
                repo.blockedEitherWay(ana, beto) shouldBe true
                repo.blockedEitherWay(beto, ana) shouldBe true
                repo.twinProfile(ana)!!.traits.size shouldBe 6
                repo.acceptedMemories(ana, Mode.AMISTAD) shouldBe listOf(TwinMemory(Mode.AMISTAD, "amistad ok"))
                repo.acceptedMemories(ana, Mode.PAREJA) shouldBe listOf(TwinMemory(Mode.PAREJA, "pareja no"))
                repo.acceptedMemories(ana, Mode.ESTUDIO) shouldBe emptyList()
            }
        }
    }

    test("Postgres: la caché de 7 días no reutiliza una simulación INTRODUCTION (E6.2)").config(enabled = TestDatabase.enabled) {
        withDb { db ->
            val ana = db.user("ana")
            val beto = db.user("beto")
            val intro = Db(db).start(ana, beto)
            transaction(db) {
                Simulations.update({ Simulations.id eq intro.id }) { it[origin] = SimulationOrigin.INTRODUCTION }
            }
            val w = Db(db)
            val own = w.start(beto, ana)
            // No sale de la presentación oculta: corre de verdad (llama al LLM) y no apunta a ella.
            own.cachedFrom shouldBe null
            (w.fake.calls > 0) shouldBe true
        }
    }

    test("Postgres: simulación completa, arista SIMULATED, un solo cobro, caché y memorias por modo").config(enabled = TestDatabase.enabled) {
        withDb { db ->
            val ana = db.user("ana")
            val beto = db.user("beto")
            db.memory(ana, Mode.AMISTAD, "AMISTAD-DATO juega microfútbol")
            db.memory(ana, Mode.PAREJA, "PAREJA-SECRETO terminó hace poco")
            val w = Db(db)
            val sim = w.start(ana, beto)
            sim.status shouldBe SimulationStatus.DONE
            sim.score!!.shouldBeBetween(0, 100)
            sim.transcript.scenarios shouldHaveSize 3
            sim.usage.tokensIn shouldNotBe 0
            sim.usage.model shouldBe "gemini-3.8-flash"
            sim.provider shouldBe "fake"

            val prompts = w.requests.joinToString("\n") { it.system + "\n" + it.prompt }
            prompts shouldContain "AMISTAD-DATO"
            prompts shouldNotContain "PAREJA-SECRETO"
            transaction(db) {
                val row = Simulations.selectAll().where { Simulations.id eq sim.id }.single()
                row[Simulations.transcript].toString() shouldNotContain "PAREJA-SECRETO"
                row[Simulations.report].toString() shouldNotContain "PAREJA-SECRETO"
                row[Simulations.finishedAt] shouldNotBe null
                val edge = Edges.selectAll().single()
                edge[Edges.state] shouldBe EdgeState.SIMULATED
                edge[Edges.affinity]!!.toInt() shouldBe sim.score
                edge[Edges.lastSimulationId] shouldBe sim.id
                UsageEvents.selectAll().count() shouldBe 1L
            }

            // Reintentar la tarea no repite nada.
            val calls = w.fake.calls
            runBlocking { w.module.engine.run(sim.id) }
            w.fake.calls shouldBe calls

            // Desde el otro lado del par, con un Fake nuevo: sale de la caché sin llamar al LLM.
            val other = Db(db)
            val copy = other.start(beto, ana)
            other.fake.calls shouldBe 0
            copy.cachedFrom shouldBe sim.id
            copy.score shouldBe sim.score
            copy.transcript shouldBe sim.transcript.swapped()

            // Lista paginada por cursor sobre Postgres.
            runBlocking {
                val first = w.module.service.list(ana, null, 1)
                first.items shouldHaveSize 1
                val second = w.module.service.list(ana, first.nextCursor, 1)
                second.items shouldHaveSize 1
                second.nextCursor shouldBe null
                (first.items + second.items).map { it.id }.toSet() shouldBe setOf(sim.id.toString(), copy.id.toString())
            }
        }
    }

    test("Postgres: el juez falla 3 veces → FAILED y usage_events.refunded = true").config(enabled = TestDatabase.enabled) {
        withDb { db ->
            val ana = db.user("ana")
            val beto = db.user("beto")
            val w = Db(db, judgeFails = true)
            val sim = w.start(ana, beto)
            sim.status shouldBe SimulationStatus.FAILED
            transaction(db) {
                UsageEvents.selectAll().single()[UsageEvents.refunded] shouldBe true
                Simulations.selectAll().single()[Simulations.error]!! shouldContain "juez"
                Edges.selectAll().count() shouldBe 0L
            }
        }
    }

    test("Postgres: saveStep no guarda dos veces el mismo paso").config(enabled = TestDatabase.enabled) {
        withDb { db ->
            val ana = db.user("ana")
            val beto = db.user("beto")
            val w = Db(db)
            w.queue.inline = null
            val id = UUID.fromString(runBlocking { w.module.service.start(ana, StartSimulation(beto.toString(), Mode.ESTUDIO)).id })
            val repo = w.module.repository
            runBlocking {
                repo.markRunning(id) shouldBe true
                repo.markRunning(id) shouldBe false
                val base = repo.find(id)!!
                base.transcript.scenarios.map { it.scenarioId }.all { s -> DefaultScenarios.all.first { it.id == s }.isBasic } shouldBe true
                val next = base.transcript.copy(
                    scenarios = base.transcript.scenarios.toMutableList().also { it[0] = it[0].copy(turns = listOf(StoredTurn(Side.A, "hola"))) },
                )
                repo.saveStep(id, 0, next, Progress(0, 1, 8), UsageTotals(10, 5, 7, "m")) shouldBe true
                repo.saveStep(id, 0, next, Progress(0, 1, 8), UsageTotals(10, 5, 7, "m")) shouldBe false
                val saved = repo.find(id)!!
                saved.transcript.steps shouldBe 1
                saved.usage shouldBe UsageTotals(10, 5, 7, "m")
                saved.progress shouldBe Progress(0, 1, 8)
                repo.markFailed(id, "x", FIXED_CLOCK.instant()) shouldBe true
                repo.markFailed(id, "x", FIXED_CLOCK.instant()) shouldBe false
            }
        }
    }
})
