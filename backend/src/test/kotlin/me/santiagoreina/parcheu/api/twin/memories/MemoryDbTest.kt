package me.santiagoreina.parcheu.api.twin.memories

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.ai.FakeLlmClient
import me.santiagoreina.parcheu.api.ai.testGateway
import me.santiagoreina.parcheu.api.platform.config.Env
import me.santiagoreina.parcheu.api.platform.db.TestDatabase
import me.santiagoreina.parcheu.api.platform.db.connectExposed
import me.santiagoreina.parcheu.api.platform.db.runMigrations
import me.santiagoreina.parcheu.api.platform.db.tables.ChatMessages
import me.santiagoreina.parcheu.api.platform.db.tables.Chats
import me.santiagoreina.parcheu.api.platform.db.tables.Consents
import me.santiagoreina.parcheu.api.platform.db.tables.TwinLearningCursor
import me.santiagoreina.parcheu.api.platform.db.tables.TwinMemories
import me.santiagoreina.parcheu.api.platform.db.tables.Twins
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.api.platform.tasks.FakeTaskQueue
import me.santiagoreina.parcheu.api.twin.PERSONA_TEXT
import me.santiagoreina.parcheu.api.twin.TwinModule
import me.santiagoreina.parcheu.api.twin.answersOf
import me.santiagoreina.parcheu.api.twin.twinModule
import me.santiagoreina.parcheu.contract.ChatType
import me.santiagoreina.parcheu.contract.ConsentKind
import me.santiagoreina.parcheu.contract.MemoryStatus
import me.santiagoreina.parcheu.contract.Mode
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Collections
import java.util.UUID

/**
 * La extracción y la decisión de memorias contra Postgres 16 real (`chat_messages`, `chats`,
 * `consents`, `twin_learning_cursor`, `twin_memories`, `twins`). Necesita Docker o
 * `PARCHEU_TEST_DB_URL`: con `-PsinContenedores` se omiten (ver [TestDatabase]).
 */
class MemoryDbTest : FunSpec({
    val now: OffsetDateTime = OffsetDateTime.ofInstant(FIXED_CLOCK.instant(), ZoneOffset.UTC)
    fun minutesAgo(m: Long) = now.minusMinutes(m)

    fun newUser(db: Database, uid: String): UUID = transaction(db) {
        Users.insert {
            it[firebaseUid] = uid
            it[createdAt] = now.minusDays(30)
            it[updatedAt] = now.minusDays(30)
        }[Users.id]
    }

    fun newChat(db: Database, mode: Mode): UUID = transaction(db) {
        Chats.insert {
            it[type] = ChatType.DIRECT
            it[Chats.mode] = mode
            it[createdAt] = now.minusDays(1)
        }[Chats.id]
    }

    fun message(db: Database, chat: UUID, sender: UUID?, text: String, at: OffsetDateTime, learnable: Boolean = true, hidden: Boolean = false) =
        transaction(db) {
            ChatMessages.insert {
                it[chatId] = chat
                it[senderId] = sender
                it[ChatMessages.text] = text
                it[ChatMessages.learnable] = learnable
                it[ChatMessages.hidden] = hidden
                it[createdAt] = at
            }
        }

    fun consent(db: Database, user: UUID, kind: ConsentKind, granted: Boolean, at: OffsetDateTime) = transaction(db) {
        Consents.insert {
            it[userId] = user
            it[Consents.kind] = kind
            it[Consents.granted] = granted
            it[textVersion] = "2026-10-29"
            it[createdAt] = at
        }
    }

    fun memories(db: Database, user: UUID) = transaction(db) {
        TwinMemories.selectAll().where { TwinMemories.userId eq user }
            .map { Triple(it[TwinMemories.mode], it[TwinMemories.text], it[TwinMemories.sourceChatId]) to it[TwinMemories.status] }
    }

    /** Fake que guarda cada prompt y responde una memoria por mensaje numerado. */
    class Capture {
        val prompts: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val fake = FakeLlmClient(
            textResponder = { PERSONA_TEXT },
            jsonResponder = { request, _ ->
                prompts += request.prompt
                echo(request.prompt)
            },
        )

        private fun echo(prompt: String): JsonObject = buildJsonObject {
            putJsonArray("memories") {
                Regex("^(\\d+)\\. (.+)$", RegexOption.MULTILINE).findAll(prompt).forEach { m ->
                    addJsonObject {
                        put("text", "Recuerdo de: ${m.groupValues[2]}")
                        put("source", m.groupValues[1].toInt())
                    }
                }
            }
        }
    }

    fun withMemories(block: (TwinModule, Database, Capture, FakeTaskQueue) -> Unit) = TestDatabase.withCleanDatabase { ds ->
        runMigrations(ds)
        val db = connectExposed(ds)
        val capture = Capture()
        val queue = FakeTaskQueue()
        block(twinModule(Env.DEV, db, FIXED_CLOCK, testGateway(capture.fake), queue), db, capture, queue)
    }

    test("Postgres: los mensajes de otras personas y los no aprendibles nunca entran al prompt").config(enabled = TestDatabase.enabled) {
        withMemories { twin, db, capture, _ ->
            val ana = newUser(db, "ana")
            val beto = newUser(db, "beto")
            val chat = newChat(db, Mode.AMISTAD)
            consent(db, ana, ConsentKind.CHAT_LEARNING_AMISTAD, true, minutesAgo(1000))
            message(db, chat, ana, "Me encanta montar bici los domingos", minutesAgo(30))
            message(db, chat, beto, "SECRETO-DE-BETO odio el ciclismo", minutesAgo(29))
            message(db, chat, null, "MENSAJE-DEL-SISTEMA", minutesAgo(28))
            message(db, chat, ana, "ESCRITO-SIN-CONSENTIMIENTO", minutesAgo(27), learnable = false)
            message(db, chat, ana, "OCULTO-POR-MODERACION", minutesAgo(26), hidden = true)

            runBlocking { twin.memories.extract(ana) } shouldBe ExtractionOutcome.PROCESSED

            val prompt = capture.prompts.single()
            prompt shouldContain "montar bici"
            listOf("SECRETO-DE-BETO", "MENSAJE-DEL-SISTEMA", "ESCRITO-SIN-CONSENTIMIENTO", "OCULTO-POR-MODERACION").forEach { prompt shouldNotContain it }
            memories(db, ana) shouldBe listOf(Triple(Mode.AMISTAD, "Recuerdo de: Me encanta montar bici los domingos", chat) to MemoryStatus.PENDING)
            memories(db, beto).shouldBeEmpty()
            transaction(db) { TwinLearningCursor.selectAll().single()[TwinLearningCursor.lastMessageAt]!!.toInstant() } shouldBe
                minutesAgo(30).toInstant()

            // Un reintento no encuentra nada nuevo.
            runBlocking { twin.memories.extract(ana) } shouldBe ExtractionOutcome.NOTHING_TO_DO
            capture.fake.calls shouldBe 1
        }
    }

    test("Postgres: con el consentimiento revocado no se extrae nada aunque haya mensajes learnable=true").config(enabled = TestDatabase.enabled) {
        withMemories { twin, db, capture, _ ->
            val ana = newUser(db, "ana")
            val chat = newChat(db, Mode.ESTUDIO)
            consent(db, ana, ConsentKind.CHAT_LEARNING_ESTUDIO, true, minutesAgo(1000))
            message(db, chat, ana, "Repaso con mapas mentales", minutesAgo(30), learnable = true)
            consent(db, ana, ConsentKind.CHAT_LEARNING_ESTUDIO, false, minutesAgo(20))

            runBlocking { twin.memories.extract(ana) } shouldBe ExtractionOutcome.PROCESSED

            capture.fake.calls shouldBe 0
            memories(db, ana).shouldBeEmpty()
        }
    }

    test("Postgres: Pareja y Estudio van en llamadas separadas y cada memoria queda con el modo de su chat").config(enabled = TestDatabase.enabled) {
        withMemories { twin, db, capture, _ ->
            val ana = newUser(db, "ana")
            val pareja = newChat(db, Mode.PAREJA)
            val estudio = newChat(db, Mode.ESTUDIO)
            consent(db, ana, ConsentKind.CHAT_LEARNING_PAREJA, true, minutesAgo(1000))
            consent(db, ana, ConsentKind.CHAT_LEARNING_ESTUDIO, true, minutesAgo(1000))
            message(db, pareja, ana, "PAREJA-1 me gustan las cenas tranquilas", minutesAgo(50))
            message(db, estudio, ana, "ESTUDIO-1 repaso con mapas mentales", minutesAgo(49))
            message(db, pareja, ana, "PAREJA-2 detesto las sorpresas", minutesAgo(48))

            runBlocking { twin.memories.extract(ana) }

            capture.prompts shouldHaveSize 2
            capture.prompts.forEach { (("PAREJA-" in it) xor ("ESTUDIO-" in it)) shouldBe true }
            memories(db, ana).map { it.first } shouldContainExactlyInAnyOrder listOf(
                Triple(Mode.PAREJA, "Recuerdo de: PAREJA-1 me gustan las cenas tranquilas", pareja),
                Triple(Mode.PAREJA, "Recuerdo de: PAREJA-2 detesto las sorpresas", pareja),
                Triple(Mode.ESTUDIO, "Recuerdo de: ESTUDIO-1 repaso con mapas mentales", estudio),
            )
        }
    }

    test("Postgres: un consentimiento revocado durante la llamada al modelo no deja memorias de ese modo").config(enabled = TestDatabase.enabled) {
        withMemories { twin, db, capture, _ ->
            val ana = newUser(db, "ana")
            val pareja = newChat(db, Mode.PAREJA)
            val estudio = newChat(db, Mode.ESTUDIO)
            consent(db, ana, ConsentKind.CHAT_LEARNING_PAREJA, true, minutesAgo(1000))
            consent(db, ana, ConsentKind.CHAT_LEARNING_ESTUDIO, true, minutesAgo(1000))
            message(db, pareja, ana, "PAREJA-1 me gustan las cenas tranquilas", minutesAgo(50))
            message(db, estudio, ana, "ESTUDIO-1 repaso con mapas mentales", minutesAgo(49))
            val echo = capture.fake.jsonResponder!!
            var revoked = false
            capture.fake.jsonResponder = { request, schema ->
                if (!revoked) {
                    revoked = true
                    consent(db, ana, ConsentKind.CHAT_LEARNING_PAREJA, false, minutesAgo(5))
                }
                echo(request, schema)
            }

            runBlocking { twin.memories.extract(ana) } shouldBe ExtractionOutcome.PROCESSED

            memories(db, ana).map { it.first.first } shouldBe listOf(Mode.ESTUDIO)
        }
    }

    test("Postgres: aceptar una memoria sube twins.version en 1; repetir o rechazar otra no").config(enabled = TestDatabase.enabled) {
        withMemories { twin, db, _, _ ->
            val ana = newUser(db, "ana")
            runBlocking { twin.service.submitAnswers(ana, answersOf()) }
            fun version() = transaction(db) { Twins.selectAll().where { Twins.userId eq ana }.single()[Twins.version] }
            val before = version()
            val (first, second) = transaction(db) {
                listOf(Mode.AMISTAD, Mode.ESTUDIO).map { mode ->
                    TwinMemories.insert {
                        it[userId] = ana
                        it[TwinMemories.mode] = mode
                        it[text] = "Memoria de $mode"
                        it[createdAt] = minutesAgo(5)
                    }[TwinMemories.id]
                }
            }

            twin.memories.accept(ana, first.toString()).status shouldBe MemoryStatus.ACCEPTED
            version() shouldBe before + 1
            twin.memories.accept(ana, first.toString()).status shouldBe MemoryStatus.ACCEPTED
            version() shouldBe before + 1
            twin.memories.reject(ana, second.toString()).status shouldBe MemoryStatus.REJECTED
            version() shouldBe before + 1
            transaction(db) { TwinMemories.selectAll().where { TwinMemories.id eq first }.single()[TwinMemories.decidedAt]?.toInstant() } shouldBe now.toInstant()

            val page = twin.memories.list(ana, null, null, null)
            page.items.map { it.mode } shouldContainExactlyInAnyOrder listOf(Mode.AMISTAD, Mode.ESTUDIO)
            twin.memories.list(ana, "ACCEPTED", null, null).items.single().id shouldBe first.toString()
        }
    }

    test("Postgres: el job encola solo usuarios vivos con mensajes aprendibles posteriores a su cursor").config(enabled = TestDatabase.enabled) {
        withMemories { twin, db, _, queue ->
            val ana = newUser(db, "ana")
            val beto = newUser(db, "beto")
            val carla = newUser(db, "carla")
            val dani = newUser(db, "dani")
            val chat = newChat(db, Mode.AMISTAD)
            message(db, chat, ana, "hola", minutesAgo(10))
            message(db, chat, beto, "ya procesado", minutesAgo(100))
            transaction(db) {
                TwinLearningCursor.insert {
                    it[userId] = beto
                    it[lastMessageAt] = minutesAgo(100)
                }
            }
            message(db, chat, carla, "sin consentimiento al escribir", minutesAgo(10), learnable = false)
            message(db, chat, dani, "cuenta borrada", minutesAgo(10))
            transaction(db) { Users.update({ Users.id eq dani }) { it[deletedAt] = minutesAgo(1) } }

            runBlocking { twin.memories.enqueueNightly() } shouldBe 1
            queue.tasks.single().name shouldBe "extract-memories-$ana-20261025"
        }
    }
})
