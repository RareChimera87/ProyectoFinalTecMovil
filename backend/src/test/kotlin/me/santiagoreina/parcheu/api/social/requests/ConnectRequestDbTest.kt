package me.santiagoreina.parcheu.api.social.requests

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.ai.FakeLlmClient
import me.santiagoreina.parcheu.api.ai.testGateway
import me.santiagoreina.parcheu.api.chat.ChatModule
import me.santiagoreina.parcheu.api.chat.ExposedChatRepository
import me.santiagoreina.parcheu.api.chat.InMemoryChatMirror
import me.santiagoreina.parcheu.api.graph.AnonIdSigner
import me.santiagoreina.parcheu.api.graph.ExposedGraphRepository
import me.santiagoreina.parcheu.api.graph.GraphModule
import me.santiagoreina.parcheu.api.platform.config.Env
import me.santiagoreina.parcheu.api.platform.db.TestDatabase
import me.santiagoreina.parcheu.api.platform.db.connectExposed
import me.santiagoreina.parcheu.api.platform.db.runMigrations
import me.santiagoreina.parcheu.api.platform.db.tables.Twins
import me.santiagoreina.parcheu.api.platform.db.tables.UserModes
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.api.platform.notifier.FakeNotifier
import me.santiagoreina.parcheu.api.platform.tasks.FakeTaskQueue
import me.santiagoreina.parcheu.api.quota.quotaModule
import me.santiagoreina.parcheu.api.simulation.NoopSimulationNotifier
import me.santiagoreina.parcheu.api.simulation.judgeJson
import me.santiagoreina.parcheu.api.simulation.simulationModule
import me.santiagoreina.parcheu.api.twin.TaskPayload
import me.santiagoreina.parcheu.api.simulation.createRandom
import me.santiagoreina.parcheu.contract.ContractJson
import me.santiagoreina.parcheu.contract.Decision
import me.santiagoreina.parcheu.contract.Mode
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Solicitudes de conexión contra Postgres 16 real (join de `connection_decisions` con `simulations`).
 * Necesita Docker o `PARCHEU_TEST_DB_URL`: con `-PsinContenedores` se omite.
 */
class ConnectRequestDbTest : FunSpec({
    val now = OffsetDateTime.parse("2026-10-20T10:00:00-05:00")

    fun Database.user(name: String): UUID = transaction(this) {
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

    test("pending devuelve solo decisiones ajenas de conectar que el observador aún no contesta").config(enabled = TestDatabase.enabled) {
        TestDatabase.withCleanDatabase { ds ->
            runMigrations(ds)
            val db = connectExposed(ds)
            val ai = testGateway(FakeLlmClient(textResponder = { "Dale, de una." }, jsonResponder = { _, _ -> judgeJson(70) }))
            val quota = quotaModule(Env.DEV, db, FIXED_CLOCK)
            val graph = GraphModule(ExposedGraphRepository(db), AnonIdSigner.forTests(), FIXED_CLOCK)
            val push = FakeNotifier()
            val chat = ChatModule(ExposedChatRepository(db), InMemoryChatMirror(), push, graph.policy, FIXED_CLOCK)
            val queue = FakeTaskQueue()
            val module = simulationModule(
                Env.DEV, db, FIXED_CLOCK, ai, quota, graph, queue, firebaseProjectId = null,
                notifier = NoopSimulationNotifier, chat = chat, push = push,
            ).also { m -> queue.inline = { task -> m.engine.run(ContractJson.decodeFromString<TaskPayload>(task.body).uuid()) } }
            val repo = ExposedConnectRequestRepository(db)
            val ana = db.user("ana")
            val beto = db.user("beto")
            val caro = db.user("caro")
            val dani = db.user("dani")
            fun done(a: UUID, b: UUID) = runBlocking { module.service.createRandom(a, b).id.toString() }
            fun decide(viewer: UUID, id: String, connect: Boolean) = runBlocking { module.report.decide(viewer, id, Decision(connect)) }

            val withBeto = done(ana, beto)
            val withCaro = done(ana, caro)
            val withDani = done(ana, dani)
            runBlocking { repo.pending(ana, 10) }.shouldBeEmpty()

            decide(beto, withBeto, true)
            decide(caro, withCaro, true)
            // Dani dijo 'Ahora no': no es una solicitud.
            decide(dani, withDani, false)
            runBlocking { repo.pending(ana, 10) }.map { it.otherId } shouldContainExactlyInAnyOrder listOf(caro, beto)
            // Quien decidió no se ve a sí mismo como solicitud.
            runBlocking { repo.pending(beto, 10) }.shouldBeEmpty()
            runBlocking { repo.pending(ana, 1) }.size shouldBe 1

            // Ana contesta a Beto con 'Ahora no': sale. A Caro con conectar: hay conexión mutua y sale.
            decide(ana, withBeto, false)
            decide(ana, withCaro, true)
            runBlocking { repo.pending(ana, 10) }.shouldBeEmpty()
        }
    }
})
