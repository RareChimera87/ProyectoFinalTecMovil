package me.santiagoreina.parcheu.api.admin

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.chat.ChatModule
import me.santiagoreina.parcheu.api.chat.ExposedChatRepository
import me.santiagoreina.parcheu.api.chat.InMemoryChatMirror
import me.santiagoreina.parcheu.api.graph.AnonIdSigner
import me.santiagoreina.parcheu.api.graph.ExposedGraphRepository
import me.santiagoreina.parcheu.api.graph.GraphModule
import me.santiagoreina.parcheu.api.identity.ExposedIdentityRepository
import me.santiagoreina.parcheu.api.identity.IdentityService
import me.santiagoreina.parcheu.api.identity.UserRecord
import me.santiagoreina.parcheu.api.platform.auth.FirebaseUser
import me.santiagoreina.parcheu.api.platform.db.TestDatabase
import me.santiagoreina.parcheu.api.platform.db.connectExposed
import me.santiagoreina.parcheu.api.platform.db.runMigrations
import me.santiagoreina.parcheu.api.platform.errors.Suspended
import me.santiagoreina.parcheu.api.platform.notifier.NoopNotifier
import me.santiagoreina.parcheu.api.safety.ExposedSafetyRepository
import me.santiagoreina.parcheu.api.safety.SafetyService
import me.santiagoreina.parcheu.contract.AdminAction
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.ReportKind
import me.santiagoreina.parcheu.contract.ReportReason
import me.santiagoreina.parcheu.contract.ReportRequest
import me.santiagoreina.parcheu.contract.ReportStatus
import me.santiagoreina.parcheu.contract.Resolve
import me.santiagoreina.parcheu.contract.SendMessage
import org.jetbrains.exposed.v1.jdbc.Database
import java.time.Duration
import java.util.UUID

/** Criterios de E8.5 contra Postgres 16 real (Docker en CI o `PARCHEU_TEST_DB_URL` en local). */
class AdminDbTest : FunSpec({
    class Env(val db: Database) {
        val clock = FIXED_CLOCK
        val identity = ExposedIdentityRepository(db)
        val graph = GraphModule(ExposedGraphRepository(db), AnonIdSigner.forTests(), clock)
        val mirror = InMemoryChatMirror()
        val chat = ChatModule(ExposedChatRepository(db), mirror, NoopNotifier, graph.policy, clock)
        val safety = SafetyService(ExposedSafetyRepository(db), chat.service, chat.repository, graph.repository, graph.policy, graph.anonIds, null, clock)
        val admin = AdminService(ExposedAdminRepository(db), identity, chat.service, clock)

        suspend fun user(name: String): UserRecord =
            identity.createIfAbsent("uid-$name-${UUID.randomUUID()}", null, name, clock.instant())
    }

    fun withDb(block: suspend (Env) -> Unit) = TestDatabase.withCleanDatabase { ds ->
        runMigrations(ds)
        kotlinx.coroutines.runBlocking { block(Env(connectExposed(ds))) }
    }

    test("lista por prioridad y kind, y resolver oculta el mensaje en Postgres y en el espejo").config(enabled = TestDatabase.enabled) {
        withDb { env ->
            val ana = env.user("Ana")
            val beto = env.user("Beto")
            val chat = env.chat.service.createDirect(ana.id, beto.id, Mode.AMISTAD)
            val message = env.chat.service.send(beto, chat.id, SendMessage("b-1", "mensaje ofensivo"))
            env.safety.report(
                ana.id,
                ReportRequest(ReportKind.USER, targetUserId = beto.id.toString(), chatId = chat.id.toString(), messageId = message.id, reason = ReportReason.HARASSMENT),
            )
            env.safety.report(ana.id, ReportRequest(ReportKind.USER, targetUserId = beto.id.toString(), reason = ReportReason.UNDERAGE))

            val open = env.admin.reports(null, ReportStatus.OPEN, null, null)
            open.items.map { it.reason } shouldContainExactly listOf(ReportReason.UNDERAGE, ReportReason.HARASSMENT)
            env.admin.reports(ReportKind.AI_CONTENT, ReportStatus.OPEN, null, null).items.size shouldBe 0
            val harassment = open.items.last()
            harassment.snapshot.shouldNotBeNull()

            val resolved = env.admin.resolve(null, harassment.id, Resolve(AdminAction.HIDE_MESSAGE, "texto ofensivo"))
            resolved.status shouldBe ReportStatus.RESOLVED
            resolved.resolution shouldBe "HIDE_MESSAGE: texto ofensivo"
            env.chat.repository.findByClientId(beto.id, "b-1")!!.hidden shouldBe true
            val doc = env.mirror.messages(chat.id).single { it.id.toString() == message.id }
            doc.hidden shouldBe true
            doc.text shouldBe ""
            env.admin.reports(null, ReportStatus.RESOLVED, null, null).items.single().id shouldBe harassment.id
        }
    }

    test("dos resolve a la vez: solo uno gana la marca y la sanción queda aplicada solo si ganó el BAN").config(enabled = TestDatabase.enabled) {
        withDb { env ->
            val ana = env.user("Ana")
            val beto = env.user("Beto")
            repeat(5) {
                env.safety.report(ana.id, ReportRequest(ReportKind.USER, targetUserId = beto.id.toString(), reason = ReportReason.OTHER))
            }
            for (id in env.admin.reports(null, ReportStatus.OPEN, null, null).items.map { it.id }) {
                val results = listOf(AdminAction.DISMISS, AdminAction.BAN).map { action ->
                    async(Dispatchers.Default) { runCatching { env.admin.resolve(null, id, Resolve(action)) } }
                }.awaitAll()
                results.count { it.isSuccess } shouldBe 1
                val report = env.admin.reports(null, ReportStatus.RESOLVED, null, null).items.single { it.id == id }
                val banned = env.identity.findByFirebaseUid(beto.firebaseUid)!!.suspendedUntil != null
                // Si ganó DISMISS no hay baneo; si ganó BAN, el reporte lo dice. Nunca un baneo con DISMISS.
                if (report.resolution == "DISMISS") banned shouldBe false else report.resolution shouldBe "BAN"
                if (banned) break
            }
        }
    }

    test("suspender 24 h fija suspended_until: escribir y entrar a la sesión dan SUSPENDED; banear no se acorta").config(enabled = TestDatabase.enabled) {
        withDb { env ->
            val ana = env.user("Ana")
            val beto = env.user("Beto")
            val chat = env.chat.service.createDirect(ana.id, beto.id, Mode.AMISTAD)
            env.safety.report(ana.id, ReportRequest(ReportKind.USER, targetUserId = beto.id.toString(), chatId = chat.id.toString(), reason = ReportReason.HARASSMENT))
            env.safety.report(ana.id, ReportRequest(ReportKind.USER, targetUserId = beto.id.toString(), reason = ReportReason.OTHER))
            val (first, second) = env.admin.reports(null, ReportStatus.OPEN, null, null).items.map { it.id }

            env.admin.resolve(null, first, Resolve(AdminAction.SUSPEND_24H))
            val suspended = env.identity.findByFirebaseUid(beto.firebaseUid)!!
            suspended.suspendedUntil shouldBe env.clock.instant().plus(Duration.ofHours(24))
            val write = runCatching { env.chat.service.send(suspended, chat.id, SendMessage("b-2", "sigo")) }.exceptionOrNull()
            (write is Suspended) shouldBe true
            val session = runCatching { IdentityService(env.identity, env.clock, "v1").requireFullAccess(FirebaseUser(beto.firebaseUid, null, null)) }.exceptionOrNull()
            (session is Suspended) shouldBe true
            env.identity.findByFirebaseUid(ana.firebaseUid)!!.suspendedUntil.shouldBeNull()

            env.admin.resolve(null, second, Resolve(AdminAction.BAN))
            env.identity.findByFirebaseUid(beto.firebaseUid)!!.suspendedUntil shouldBe AdminService.BAN_UNTIL
            // Una segunda suspensión corta no levanta el baneo.
            env.identity.suspendUntil(beto.id, env.clock.instant().plusSeconds(60), env.clock.instant()) shouldBe AdminService.BAN_UNTIL
        }
    }
})
