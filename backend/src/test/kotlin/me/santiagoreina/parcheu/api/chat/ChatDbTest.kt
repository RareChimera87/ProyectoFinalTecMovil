package me.santiagoreina.parcheu.api.chat

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.graph.inMemoryGraphModule
import me.santiagoreina.parcheu.api.identity.ExposedIdentityRepository
import me.santiagoreina.parcheu.api.identity.NewConsent
import me.santiagoreina.parcheu.api.identity.UserRecord
import me.santiagoreina.parcheu.api.platform.db.TestDatabase
import me.santiagoreina.parcheu.api.platform.db.connectExposed
import me.santiagoreina.parcheu.api.platform.db.runMigrations
import me.santiagoreina.parcheu.api.platform.db.tables.Blocks
import me.santiagoreina.parcheu.api.platform.db.tables.ChatMessages
import me.santiagoreina.parcheu.api.platform.db.tables.Chats
import me.santiagoreina.parcheu.api.platform.errors.DomainException
import me.santiagoreina.parcheu.api.platform.notifier.FakeNotifier
import me.santiagoreina.parcheu.contract.ConsentKind
import me.santiagoreina.parcheu.contract.ErrorCode
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.SendMessage
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/**
 * Criterios de E5.1 contra Postgres 16 real (Docker o `PARCHEU_TEST_DB_URL`). Si además corre el
 * emulador de Firestore (`FIRESTORE_EMULATOR_HOST`, como en backend-ci) el espejo es el real; si no,
 * el de memoria.
 */
class ChatDbTest : FunSpec({
    /** Reloj que avanza un segundo por lectura: mensajes con `created_at` distintos y en orden. */
    class TickingClock : Clock() {
        private var now = FIXED_CLOCK.instant()
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        @Synchronized override fun instant(): Instant = now.also { now = now.plusSeconds(1) }
    }

    class Env(val db: Database, clock: Clock = FIXED_CLOCK) {
        val identity = ExposedIdentityRepository(db)
        val repo = ExposedChatRepository(db)
        val memoryMirror = InMemoryChatMirror()
        val mirror: ChatMirror = if (FirestoreEmulator.enabled) FirestoreChatMirror { FirestoreEmulator.firestore } else memoryMirror
        val notifier = FakeNotifier()
        val service = ChatModule(repo, mirror, notifier, inMemoryGraphModule(FIXED_CLOCK).policy, clock).service

        suspend fun user(name: String): UserRecord =
            identity.createIfAbsent("uid-$name-${UUID.randomUUID()}", null, name, FIXED_CLOCK.instant())

        fun messageRows(chatId: UUID) = transaction(db) { ChatMessages.selectAll().toList().filter { it[ChatMessages.chatId] == chatId } }

        fun firestoreMessages(chatId: UUID): Int = if (FirestoreEmulator.enabled) {
            FirestoreEmulator.firestore.collection(FirestoreChatMirror.CHATS).document(chatId.toString())
                .collection(FirestoreChatMirror.MESSAGES).get().get().size()
        } else {
            memoryMirror.messages(chatId).size
        }
    }

    fun withDb(clock: Clock = FIXED_CLOCK, block: suspend (Env) -> Unit) = TestDatabase.withCleanDatabase { ds ->
        runMigrations(ds)
        kotlinx.coroutines.runBlocking { block(Env(connectExposed(ds), clock)) }
    }

    suspend fun expectCode(code: ErrorCode, block: suspend () -> Unit) {
        val error = runCatching { block() }.exceptionOrNull()
        (error as? DomainException)?.code shouldBe code
    }

    test("no miembro -> FORBIDDEN; bloqueado -> BLOCKED; nada se guarda").config(enabled = TestDatabase.enabled) {
        withDb { env ->
            val ana = env.user("Ana")
            val beto = env.user("Beto")
            val carla = env.user("Carla")
            val chat = env.service.createDirect(ana.id, beto.id, Mode.AMISTAD)
            expectCode(ErrorCode.FORBIDDEN) { env.service.send(carla, chat.id, SendMessage("c-1", "hola")) }
            transaction(env.db) {
                Blocks.insert {
                    it[blockerId] = beto.id
                    it[blockedId] = ana.id
                    it[createdAt] = OffsetDateTime.ofInstant(FIXED_CLOCK.instant(), ZoneOffset.UTC)
                }
            }
            expectCode(ErrorCode.BLOCKED) { env.service.send(ana, chat.id, SendMessage("a-1", "hola")) }
            env.messageRows(chat.id) shouldHaveSize 0
            env.service.list(ana) shouldHaveSize 0
        }
    }

    test("un DIRECT cerrado se reabre al desbloquear y vuelve a aceptar mensajes").config(enabled = TestDatabase.enabled) {
        withDb { env ->
            val ana = env.user("Ana")
            val beto = env.user("Beto")
            val chat = env.service.createDirect(ana.id, beto.id, Mode.AMISTAD)
            env.service.close(chat.id)
            expectCode(ErrorCode.FORBIDDEN) { env.service.send(ana, chat.id, SendMessage("a-0", "hola")) }
            val reopened = env.service.createDirect(beto.id, ana.id, Mode.AMISTAD)
            reopened.id shouldBe chat.id
            reopened.status shouldBe me.santiagoreina.parcheu.contract.ChatStatus.OPEN
            env.service.send(ana, chat.id, SendMessage("a-1", "hola otra vez"))
            env.messageRows(chat.id) shouldHaveSize 1
        }
    }

    test("repetir clientId no duplica en Postgres ni en Firestore").config(enabled = TestDatabase.enabled) {
        withDb { env ->
            val ana = env.user("Ana")
            val beto = env.user("Beto")
            val chat = env.service.createDirect(ana.id, beto.id, Mode.AMISTAD)
            val results = coroutineScope {
                (1..4).map { async(Dispatchers.IO) { env.service.send(ana, chat.id, SendMessage("a-1", "hola")) } }.awaitAll()
            }
            results.map { it.id }.toSet() shouldHaveSize 1
            env.messageRows(chat.id) shouldHaveSize 1
            env.firestoreMessages(chat.id) shouldBe 1
            env.notifier.sent shouldHaveSize 1
            transaction(env.db) { Chats.selectAll().single()[Chats.lastMessageAt] } shouldBe
                OffsetDateTime.ofInstant(FIXED_CLOCK.instant(), ZoneOffset.UTC)
        }
    }

    test("con el consentimiento de Pareja apagado el mensaje queda learnable=false").config(enabled = TestDatabase.enabled) {
        withDb { env ->
            val ana = env.user("Ana")
            val beto = env.user("Beto")
            val now = FIXED_CLOCK.instant()
            env.identity.appendConsents(ana.id, listOf(NewConsent(ConsentKind.CHAT_LEARNING_AMISTAD, true, "v1")), now)
            env.identity.appendConsents(ana.id, listOf(NewConsent(ConsentKind.CHAT_LEARNING_PAREJA, true, "v1")), now)
            env.identity.appendConsents(ana.id, listOf(NewConsent(ConsentKind.CHAT_LEARNING_PAREJA, false, "v1")), now)
            val pareja = env.service.createDirect(ana.id, beto.id, Mode.PAREJA)
            val amistad = env.service.createDirect(ana.id, beto.id, Mode.AMISTAD)
            env.service.send(ana, pareja.id, SendMessage("p-1", "en pareja"))
            env.service.send(ana, amistad.id, SendMessage("a-1", "en amistad"))
            env.messageRows(pareja.id).single()[ChatMessages.learnable] shouldBe false
            env.messageRows(amistad.id).single()[ChatMessages.learnable] shouldBe true
        }
    }

    test("createDirect concurrente deja un solo chat; lista, no leídos, read y mute").config(enabled = TestDatabase.enabled) {
        withDb(TickingClock()) { env ->
            val ana = env.user("Ana")
            val beto = env.user("Beto")
            val chats = coroutineScope {
                (1..6).map { i ->
                    async(Dispatchers.IO) { if (i % 2 == 0) env.service.createDirect(ana.id, beto.id, Mode.AMISTAD) else env.service.createDirect(beto.id, ana.id, Mode.AMISTAD) }
                }.awaitAll()
            }
            chats.map { it.id }.toSet() shouldHaveSize 1
            transaction(env.db) { Chats.selectAll().count() } shouldBe 1L
            val chat = chats.first()

            env.service.send(ana, chat.id, SendMessage("a-1", "uno"))
            env.service.send(ana, chat.id, SendMessage("a-2", "dos"))
            env.service.list(beto).single().let {
                it.unread shouldBe 2
                it.other.label shouldBe "Ana"
                it.lastMessage!!.text shouldBe "dos"
            }
            env.service.markRead(beto, chat.id)
            env.service.mute(beto, chat.id, true)
            env.service.list(beto).single().let {
                it.unread shouldBe 0
                it.muted shouldBe true
            }
            env.service.send(ana, chat.id, SendMessage("a-3", "tres"))
            env.notifier.sent shouldHaveSize 2
        }
    }

    test("chat SCENARIO vencido -> SCENARIO_ENDED").config(enabled = TestDatabase.enabled) {
        withDb { env ->
            val ana = env.user("Ana")
            val beto = env.user("Beto")
            val chat = env.service.createScenario(ana.id, beto.id, Mode.AMISTAD, 1, FIXED_CLOCK.instant().plus(Duration.ofMinutes(5)))
            env.service.send(ana, chat.id, SendMessage("a-1", "hola"))
            env.service.close(chat.id)
            expectCode(ErrorCode.SCENARIO_ENDED) { env.service.send(ana, chat.id, SendMessage("a-2", "¿sigues?")) }
        }
    }
})
