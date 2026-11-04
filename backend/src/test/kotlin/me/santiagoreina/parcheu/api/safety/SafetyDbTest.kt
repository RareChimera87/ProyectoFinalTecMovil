package me.santiagoreina.parcheu.api.safety

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.chat.ChatModule
import me.santiagoreina.parcheu.api.chat.ExposedChatRepository
import me.santiagoreina.parcheu.api.chat.InMemoryChatMirror
import me.santiagoreina.parcheu.api.graph.AnonIdSigner
import me.santiagoreina.parcheu.api.graph.EdgeChange
import me.santiagoreina.parcheu.api.graph.EdgeSnapshot
import me.santiagoreina.parcheu.api.graph.ExposedGraphRepository
import me.santiagoreina.parcheu.api.graph.GraphFilter
import me.santiagoreina.parcheu.api.graph.GraphModule
import me.santiagoreina.parcheu.api.identity.ExposedIdentityRepository
import me.santiagoreina.parcheu.api.identity.UserRecord
import me.santiagoreina.parcheu.api.platform.db.TestDatabase
import me.santiagoreina.parcheu.api.platform.db.connectExposed
import me.santiagoreina.parcheu.api.platform.db.runMigrations
import me.santiagoreina.parcheu.api.platform.db.tables.Reports
import me.santiagoreina.parcheu.api.platform.errors.DomainException
import me.santiagoreina.parcheu.api.platform.notifier.NoopNotifier
import me.santiagoreina.parcheu.contract.ChatStatus
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.ErrorCode
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.ReportKind
import me.santiagoreina.parcheu.contract.ReportReason
import me.santiagoreina.parcheu.contract.ReportRequest
import me.santiagoreina.parcheu.contract.ReportStatus
import me.santiagoreina.parcheu.contract.SendMessage
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/** Criterios de E8.1 contra Postgres 16 real (Docker en CI o `PARCHEU_TEST_DB_URL` en local). */
class SafetyDbTest : FunSpec({
    /** Reloj que avanza un segundo por lectura: mensajes con `created_at` distintos y en orden. */
    class TickingClock : Clock() {
        private var now = FIXED_CLOCK.instant()
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        @Synchronized override fun instant(): Instant = now.also { now = now.plusSeconds(1) }
    }

    class Env(val db: Database) {
        val clock = TickingClock()
        val identity = ExposedIdentityRepository(db)
        val graph = GraphModule(ExposedGraphRepository(db), AnonIdSigner.forTests(), clock)
        val chat = ChatModule(ExposedChatRepository(db), InMemoryChatMirror(), NoopNotifier, graph.policy, clock)
        val repo = ExposedSafetyRepository(db)
        val service = SafetyService(repo, chat.service, chat.repository, graph.repository, graph.policy, graph.anonIds, null, clock)

        suspend fun user(name: String): UserRecord =
            identity.createIfAbsent("uid-$name-${UUID.randomUUID()}", null, name, FIXED_CLOCK.instant())
    }

    fun withDb(block: suspend (Env) -> Unit) = TestDatabase.withCleanDatabase { ds ->
        runMigrations(ds)
        kotlinx.coroutines.runBlocking { block(Env(connectExposed(ds))) }
    }

    test("bloquear cierra el chat, el envío responde BLOCKED y el grafo lo ve; desbloquear lo quita").config(enabled = TestDatabase.enabled) {
        withDb { env ->
            val ana = env.user("Ana")
            val beto = env.user("Beto")
            val chat = env.chat.service.createDirect(ana.id, beto.id, Mode.AMISTAD)

            env.service.block(ana.id, beto.id.toString())
            env.service.block(ana.id, beto.id.toString())

            env.chat.repository.chat(chat.id)!!.status shouldBe ChatStatus.CLOSED
            val error = runCatching { env.chat.service.send(beto, chat.id, SendMessage("b-1", "hola")) }.exceptionOrNull()
            (error as DomainException).code shouldBe ErrorCode.BLOCKED
            env.graph.repository.blockedEitherWay(beto.id) shouldBe setOf(ana.id)
            // Sin conexión mutua, la lista presenta a Beto con su anonId, nunca con el uuid interno.
            val anonId = env.graph.anonIds.anonId(ana.id, beto.id)
            env.service.blocks(ana.id).map { it.userId } shouldContainExactly listOf(anonId)
            env.service.unblock(ana.id, beto.id.toString())
            env.service.blocks(ana.id).map { it.userId } shouldContainExactly listOf(anonId)

            env.service.unblock(ana.id, anonId)
            env.service.blocks(ana.id).shouldBeEmpty()
            env.graph.repository.blockedEitherWay(beto.id).shouldBeEmpty()
        }
    }

    test("bloquear termina la conexión CONNECTED y desbloquear no la restaura (E8.7)").config(enabled = TestDatabase.enabled) {
        withDb { env ->
            val ana = env.user("Ana")
            val beto = env.user("Beto")
            val now = FIXED_CLOCK.instant()
            env.graph.repository.updateEdge(ana.id, beto.id, Mode.AMISTAD, now) {
                EdgeChange.Save(EdgeSnapshot(EdgeState.CONNECTED, 80, null, now, now))
            }
            env.graph.repository.connectedWith(ana.id) shouldBe setOf(beto.id)

            env.service.block(ana.id, beto.id.toString())
            // Quien bloquea ya conocía a Beto por nombre: la lista lo presenta con su id y nombre (was_connected).
            val listed = env.service.blocks(ana.id).single()
            listed.userId shouldBe beto.id.toString()
            env.service.unblock(ana.id, listed.userId)
            env.service.blocks(ana.id).shouldBeEmpty()

            env.graph.repository.connectedWith(ana.id).shouldBeEmpty()
            env.graph.repository.egoEdges(ana.id, GraphFilter()).shouldBeEmpty()
        }
    }

    test("el reporte guarda los últimos 20 mensajes del chat en orden y sin los de otros chats").config(enabled = TestDatabase.enabled) {
        withDb { env ->
            val ana = env.user("Ana")
            val beto = env.user("Beto")
            val caro = env.user("Caro")
            val chat = env.chat.service.createDirect(ana.id, beto.id, Mode.AMISTAD)
            val other = env.chat.service.createDirect(ana.id, caro.id, Mode.AMISTAD)
            repeat(23) { i ->
                env.chat.service.send(if (i % 2 == 0) ana else beto, chat.id, SendMessage("m-$i", "mensaje $i"))
                env.chat.service.send(caro, other.id, SendMessage("c-$i", "otro $i"))
            }

            val created = env.service.report(
                ana.id,
                ReportRequest(ReportKind.USER, targetUserId = beto.id.toString(), chatId = chat.id.toString(), reason = ReportReason.SELF_HARM),
            )
            created.helpResources.orEmpty().map { it.phone } shouldContainExactly listOf("192", "106", "123")

            val row = transaction(env.db) { Reports.selectAll().single() }
            row[Reports.id].toString() shouldBe created.id
            row[Reports.reporterId] shouldBe ana.id
            row[Reports.targetId] shouldBe beto.id
            row[Reports.chatId] shouldBe chat.id
            row[Reports.status] shouldBe ReportStatus.OPEN
            row[Reports.priority] shouldBe SafetyService.HIGH_PRIORITY.toShort()
            val snapshot = row[Reports.snapshot].shouldNotBeNull() as JsonArray
            snapshot.map { it.jsonObject["text"]!!.jsonPrimitive.content } shouldContainExactly (3 until 23).map { "mensaje $it" }
        }
    }
})
