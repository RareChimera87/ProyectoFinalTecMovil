package me.santiagoreina.parcheu.api.social.introductions

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.ai.FakeLlmClient
import me.santiagoreina.parcheu.api.ai.testGateway
import me.santiagoreina.parcheu.api.chat.ChatModule
import me.santiagoreina.parcheu.api.chat.ExposedChatRepository
import me.santiagoreina.parcheu.api.chat.InMemoryChatMirror
import me.santiagoreina.parcheu.api.graph.AnonIdSigner
import me.santiagoreina.parcheu.api.graph.EdgeEvent
import me.santiagoreina.parcheu.api.graph.EdgeSource
import me.santiagoreina.parcheu.api.graph.ExposedGraphRepository
import me.santiagoreina.parcheu.api.graph.GraphModule
import me.santiagoreina.parcheu.api.platform.config.Env
import me.santiagoreina.parcheu.api.platform.db.TestDatabase
import me.santiagoreina.parcheu.api.platform.db.connectExposed
import me.santiagoreina.parcheu.api.platform.db.runMigrations
import me.santiagoreina.parcheu.api.platform.db.tables.ChatMembers
import me.santiagoreina.parcheu.api.platform.db.tables.Chats
import me.santiagoreina.parcheu.api.platform.db.tables.Edges
import me.santiagoreina.parcheu.api.platform.db.tables.IntroRequestStatus
import me.santiagoreina.parcheu.api.platform.db.tables.IntroRequests
import me.santiagoreina.parcheu.api.platform.db.tables.IntroResponse
import me.santiagoreina.parcheu.api.platform.db.tables.Introductions
import me.santiagoreina.parcheu.api.platform.db.tables.Twins
import me.santiagoreina.parcheu.api.platform.db.tables.UsageEvents
import me.santiagoreina.parcheu.api.platform.db.tables.UserModes
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.api.platform.notifier.FakeNotifier
import me.santiagoreina.parcheu.api.platform.tasks.FakeTaskQueue
import me.santiagoreina.parcheu.api.quota.quotaModule
import me.santiagoreina.parcheu.api.simulation.NoopSimulationNotifier
import me.santiagoreina.parcheu.api.simulation.judgeJson
import me.santiagoreina.parcheu.api.simulation.simulationModule
import me.santiagoreina.parcheu.api.twin.TaskPayload
import me.santiagoreina.parcheu.contract.ContractJson
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.IntroRequest
import me.santiagoreina.parcheu.contract.Introduce
import me.santiagoreina.parcheu.contract.IntroductionStatus
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.QuotaKind
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.OffsetDateTime
import java.util.UUID
import me.santiagoreina.parcheu.contract.IntroResponse as IntroResponseBody

/**
 * Presentaciones contra Postgres 16 real (E6.2). Necesita Docker o `PARCHEU_TEST_DB_URL`: con
 * `-PsinContenedores` se omite (ver [TestDatabase]).
 */
class IntroductionDbTest : FunSpec({
    val now = OffsetDateTime.parse("2026-10-20T10:00:00-05:00")

    fun Database.user(name: String): UUID = transaction(this) {
        val id = Users.insert {
            it[firebaseUid] = "uid-$name"
            it[displayName] = name
            it[ageVerifiedAt] = now
            it[createdAt] = now
            it[updatedAt] = now
        }[Users.id]
        listOf(Mode.AMISTAD, Mode.ESTUDIO).forEach { m ->
            UserModes.insert {
                it[userId] = id
                it[mode] = m
                it[enabled] = true
                it[enabledAt] = now
            }
        }
        Twins.insert {
            it[userId] = id
            it[traits] = listOf(0.2f, 0.4f, 0.6f, 0.8f, 0.5f, 0.5f)
            it[personaSummary] = "Persona de $name"
            it[personaReady] = true
            it[version] = 1
            it[updatedAt] = now
        }
        id
    }

    class Db(val db: Database) {
        val ai = testGateway(FakeLlmClient(textResponder = { "Dale, de una." }, jsonResponder = { _, _ -> judgeJson(75) }))
        val quota = quotaModule(Env.DEV, db, FIXED_CLOCK)
        val graph = GraphModule(ExposedGraphRepository(db), AnonIdSigner.forTests(), FIXED_CLOCK)
        val push = FakeNotifier()
        val chat = ChatModule(ExposedChatRepository(db), InMemoryChatMirror(), push, graph.policy, FIXED_CLOCK)
        val queue = FakeTaskQueue()
        val store = ExposedIntroductionRepository(db)
        val simulation = simulationModule(
            Env.DEV, db, FIXED_CLOCK, ai, quota, graph, queue, firebaseProjectId = null,
            notifier = IntroductionSimulationNotifier(store, NoopSimulationNotifier, push), chat = chat, push = push,
        ).also { m -> queue.inline = { task -> m.engine.run(ContractJson.decodeFromString<TaskPayload>(task.body).uuid()) } }
        val module = introductionModule(store, simulation, graph, chat, quota, push, FIXED_CLOCK)

        fun friends(a: UUID, b: UUID, mode: Mode = Mode.AMISTAD) = runBlocking {
            graph.edges.apply(EdgeEvent.Connected(a, b, mode, EdgeSource.INVITE))
        }
    }

    fun withDb(block: (Database) -> Unit) = TestDatabase.withCleanDatabase { ds ->
        runMigrations(ds)
        block(connectExposed(ds))
    }

    test("Postgres: presentar cobra una SIMULATION a quien presenta con ref_id = id; dos 'Me interesa' simultáneos → un chat y una arista CONNECTED")
        .config(enabled = TestDatabase.enabled) {
            withDb { db ->
                val w = Db(db)
                repeat(3) { round ->
                    val mari = db.user("mari$round")
                    val vale = db.user("vale$round")
                    val sebas = db.user("sebas$round")
                    w.friends(mari, vale)
                    w.friends(mari, sebas)
                    val dto = runBlocking { w.module.service.introduce(mari, Introduce(Mode.AMISTAD, vale.toString(), sebas.toString())) }
                    val id = UUID.fromString(dto.id)
                    transaction(db) {
                        val usage = UsageEvents.selectAll().where { UsageEvents.refId eq dto.id }.single()
                        usage[UsageEvents.userId] shouldBe mari
                        usage[UsageEvents.kind] shouldBe QuotaKind.SIMULATION
                        UsageEvents.selectAll().where { UsageEvents.userId inList listOf(vale, sebas) }.count() shouldBe 0L
                        val row = Introductions.selectAll().where { Introductions.id eq id }.single()
                        row[Introductions.simulationId] shouldBe id
                        row[Introductions.status] shouldBe IntroductionStatus.SIMULATING
                    }
                    runBlocking {
                        w.module.service.get(mari, dto.id).status shouldBe IntroductionStatus.READY
                        w.module.service.respond(mari, dto.id, IntroResponseBody(true)).status shouldBe IntroductionStatus.PROPOSED
                    }
                    val results = runBlocking(Dispatchers.IO) {
                        listOf(vale, sebas).map { user -> async { w.module.service.respond(user, dto.id, IntroResponseBody(true)) } }.awaitAll()
                    }
                    results.map { it.status } shouldContainExactlyInAnyOrder listOf(IntroductionStatus.PROPOSED, IntroductionStatus.CONNECTED)
                    transaction(db) {
                        val row = Introductions.selectAll().where { Introductions.id eq id }.single()
                        row[Introductions.aResponse] shouldBe IntroResponse.INTERESTED
                        row[Introductions.bResponse] shouldBe IntroResponse.INTERESTED
                        row[Introductions.status] shouldBe IntroductionStatus.CONNECTED
                        Chats.innerJoin(ChatMembers).selectAll().where { ChatMembers.userId eq vale }.count() shouldBe 1L
                        Edges.selectAll()
                            .where { (Edges.userLow inList listOf(vale, sebas)) and (Edges.userHigh inList listOf(vale, sebas)) }
                            .map { it[Edges.mode] to it[Edges.state] } shouldBe listOf(Mode.AMISTAD to EdgeState.CONNECTED)
                    }
                }
            }
        }

    test("Postgres: 'No, gracias' queda guardado sin revelarse y pedir presentación guarda un intro_request PENDING")
        .config(enabled = TestDatabase.enabled) {
            withDb { db ->
                val w = Db(db)
                val mari = db.user("mari")
                val vale = db.user("vale")
                val sebas = db.user("sebas")
                val juli = db.user("juli")
                w.friends(mari, vale)
                w.friends(mari, sebas)
                w.friends(juli, sebas)
                runBlocking {
                    val id = w.module.service.introduce(mari, Introduce(Mode.AMISTAD, vale.toString(), sebas.toString())).id
                    w.module.service.respond(mari, id, IntroResponseBody(true))
                    w.module.service.respond(vale, id, IntroResponseBody(false)).status shouldBe IntroductionStatus.DECLINED
                    w.module.service.get(sebas, id).status shouldBe IntroductionStatus.PROPOSED
                    w.module.service.get(mari, id).status shouldBe IntroductionStatus.PROPOSED
                }
                w.quota.devPlus!!.grantPlus(juli, 30, FIXED_CLOCK.instant())
                runBlocking { w.module.service.request(juli, IntroRequest(AnonIdSigner.forTests().anonId(juli, mari))) }
                transaction(db) {
                    val row = IntroRequests.selectAll().single()
                    row[IntroRequests.requesterId] shouldBe juli
                    row[IntroRequests.viaUserId] shouldBe sebas
                    row[IntroRequests.targetUserId] shouldBe mari
                    row[IntroRequests.status] shouldBe IntroRequestStatus.PENDING
                }
                w.push.sent.last().userId shouldBe sebas
                w.push.sent.last().notification.actorId shouldNotBe null
            }
        }
})
