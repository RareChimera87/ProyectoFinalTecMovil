package me.santiagoreina.parcheu.api.twin

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.runBlocking
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.ai.FakeLlmClient
import me.santiagoreina.parcheu.api.ai.testGateway
import me.santiagoreina.parcheu.api.platform.config.Env
import me.santiagoreina.parcheu.api.platform.db.TestDatabase
import me.santiagoreina.parcheu.api.platform.db.connectExposed
import me.santiagoreina.parcheu.api.platform.db.runMigrations
import me.santiagoreina.parcheu.api.platform.db.tables.TwinMemories
import me.santiagoreina.parcheu.api.platform.db.tables.TwinVersions
import me.santiagoreina.parcheu.api.platform.db.tables.Twins
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.api.platform.errors.ValidationFailed
import me.santiagoreina.parcheu.api.platform.tasks.FakeTaskQueue
import me.santiagoreina.parcheu.contract.MemoryStatus
import me.santiagoreina.parcheu.contract.Mode
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Las reglas del gemelo contra Postgres 16 real (`questions`, `twin_answers`, `twins`,
 * `twin_versions`, `twin_accuracy`, `twin_memories`). Necesita Docker o `PARCHEU_TEST_DB_URL`: con
 * `-PsinContenedores` se omiten (ver [TestDatabase]).
 */
class TwinDbTest : FunSpec({
    val at = OffsetDateTime.parse("2026-10-01T00:00:00-05:00")

    fun newUser(db: Database, uid: String, email: String? = null, name: String? = null, birth: LocalDate? = null): UUID = transaction(db) {
        Users.insert {
            it[firebaseUid] = uid
            it[Users.email] = email
            it[displayName] = name
            it[birthDate] = birth
            it[createdAt] = at
            it[updatedAt] = at
        }[Users.id]
    }

    fun withTwin(fake: FakeLlmClient = FakeLlmClient(textResponder = { PERSONA_TEXT }), block: (TwinService, TwinRepository, Database, FakeLlmClient, FakeTaskQueue) -> Unit) =
        TestDatabase.withCleanDatabase { ds ->
            runMigrations(ds)
            val db = connectExposed(ds)
            val queue = FakeTaskQueue()
            val module = twinModule(Env.DEV, db, FIXED_CLOCK, testGateway(fake), queue)
            block(module.service, module.repository, db, fake, queue)
        }

    test("Postgres: las preguntas de la base coinciden con las semillas que usa el modo local").config(enabled = TestDatabase.enabled) {
        withTwin { _, repo, _, _, _ ->
            repo.questions() shouldBe SeedQuestions.all
        }
    }

    test("Postgres: 12 respuestas guardan rasgos reales; 11 dan VALIDATION; el reenvío sube la versión").config(enabled = TestDatabase.enabled) {
        withTwin { service, repo, db, _, queue ->
            val ana = newUser(db, "ana")
            runBlocking {
                val twin = service.submitAnswers(ana, answersOf())
                twin.traits.map { it.value } shouldBe listOf(0.6818, 0.4231, 0.2826, 0.8333, 0.4, 0.5577)
                twin.xp shouldBe 120
                twin.version shouldBe 1
                queue.tasks.size shouldBe 1

                val error = runCatching { service.submitAnswers(ana, answersOf().let { it.copy(items = it.items.take(11)) }) }.exceptionOrNull()
                (error is ValidationFailed) shouldBe true

                val again = service.submitAnswers(ana, answersOf("BBBBBBBBBBBB"))
                again.version shouldBe 2
                again.xp shouldBe 120
                again.personaReady shouldBe false
                queue.tasks.size shouldBe 2
            }
            repo.state(ana)!!.traits!!.first() shouldBe 0.0
        }
    }

    test("Postgres: build-persona dos veces no duplica versiones y el prompt no trae datos personales ni memorias").config(enabled = TestDatabase.enabled) {
        val fake = FakeLlmClient(textResponder = { PERSONA_TEXT })
        withTwin(fake) { service, _, db, _, _ ->
            val ana = newUser(db, "uid-ana", "ana.gomez@correo.test", "Ana María Gómez Pardo", LocalDate.of(2004, 3, 17))
            transaction(db) {
                listOf(Mode.AMISTAD to "le encanta el senderismo los domingos", Mode.PAREJA to "su ex se llama Camilo", Mode.ESTUDIO to "estudia en la UIS").forEach { (m, t) ->
                    TwinMemories.insert {
                        it[userId] = ana
                        it[mode] = m
                        it[text] = t
                        it[status] = MemoryStatus.ACCEPTED
                        it[createdAt] = at
                    }
                }
            }
            runBlocking {
                service.submitAnswers(ana, answersOf())
                service.buildPersona(ana)
                service.buildPersona(ana)
            }
            fake.calls shouldBe 1
            val request = checkNotNull(fake.lastRequest)
            val sent = (request.system ?: "") + "\n" + request.prompt
            listOf("ana.gomez", "correo.test", "Ana", "Gómez", "Pardo", "2004", "17", "senderismo", "Camilo", "UIS", "uid-ana", ana.toString()).forEach {
                sent shouldNotContain it
            }
            request.prompt shouldContain "Le escribo al parche para salir"

            transaction(db) {
                TwinVersions.selectAll().where { TwinVersions.userId eq ana }.count() shouldBe 1L
                val twin = Twins.selectAll().where { Twins.userId eq ana }.single()
                twin[Twins.personaReady] shouldBe true
                twin[Twins.personaSummary] shouldBe PERSONA_TEXT
                twin[Twins.version] shouldBe 1
            }
        }
    }

    test("Postgres: una tarea de una versión vieja no pisa la persona de la versión nueva").config(enabled = TestDatabase.enabled) {
        withTwin { service, _, db, fake, _ ->
            val ana = newUser(db, "ana")
            runBlocking {
                service.submitAnswers(ana, answersOf())
                service.submitAnswers(ana, answersOf("BBBBBBBBBBBB"))
                service.buildPersona(ana)
            }
            fake.calls shouldBe 1
            transaction(db) {
                TwinVersions.selectAll().where { TwinVersions.userId eq ana }.map { it[TwinVersions.version] } shouldBe listOf(2)
            }
        }
    }

    test("Postgres: evolution devuelve [] sin datos y 3 puntos ordenados por semana con 3 filas de twin_accuracy").config(enabled = TestDatabase.enabled) {
        withTwin { service, repo, db, _, _ ->
            val ana = newUser(db, "ana")
            service.evolution(ana).shouldBeEmpty()
            runBlocking { service.submitAnswers(ana, answersOf()) }
            repo.addAccuracyPoint(ana, AccuracyPoint(LocalDate.of(2026, 12, 7), 71))
            repo.addAccuracyPoint(ana, AccuracyPoint(LocalDate.of(2026, 11, 23), 40))
            repo.addAccuracyPoint(ana, AccuracyPoint(LocalDate.of(2026, 11, 30), 58))
            val points = service.evolution(ana)
            points.map { it.accuracy } shouldBe listOf(40, 58, 71)
            points.map { it.month } shouldBe listOf("2026-11", "2026-11", "2026-12")
            service.twin(ana).accuracy shouldBe 71
        }
    }
})
