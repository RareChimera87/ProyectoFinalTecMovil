package me.santiagoreina.parcheu.api.twin.feedback

import me.santiagoreina.parcheu.api.quota.quotaModule
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.ai.testGateway
import me.santiagoreina.parcheu.api.platform.config.Env
import me.santiagoreina.parcheu.api.platform.db.TestDatabase
import me.santiagoreina.parcheu.api.platform.db.connectExposed
import me.santiagoreina.parcheu.api.platform.db.runMigrations
import me.santiagoreina.parcheu.api.platform.db.tables.Simulations
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.api.platform.tasks.FakeTaskQueue
import me.santiagoreina.parcheu.api.simulation.Side
import me.santiagoreina.parcheu.api.simulation.StoredScenario
import me.santiagoreina.parcheu.api.simulation.StoredTranscript
import me.santiagoreina.parcheu.api.simulation.StoredTurn
import me.santiagoreina.parcheu.api.twin.AccuracyPoint
import me.santiagoreina.parcheu.api.twin.twinModule
import me.santiagoreina.parcheu.contract.ContractJson
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.SimulationOrigin
import me.santiagoreina.parcheu.contract.SimulationStatus
import me.santiagoreina.parcheu.contract.TwinAnswer
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/** `ExposedFeedbackRepository` y el job `twin-accuracy` contra Postgres 16 real (omitido con `-PsinContenedores`). */
class FeedbackDbTest : FunSpec({
    val at = OffsetDateTime.parse("2026-10-01T00:00:00-05:00")
    val now = FIXED_CLOCK.instant()

    fun newUser(db: Database, uid: String): UUID = transaction(db) {
        Users.insert {
            it[firebaseUid] = uid
            it[createdAt] = at
            it[updatedAt] = at
        }[Users.id]
    }

    fun simulation(db: Database, a: UUID, b: UUID, origin: SimulationOrigin = SimulationOrigin.RANDOM): UUID = transaction(db) {
        val transcript = StoredTranscript(
            listOf(
                StoredScenario(1, "Uno", listOf(StoredTurn(Side.A, "a0"), StoredTurn(Side.B, "b1"))),
                StoredScenario(2, "Dos", listOf(StoredTurn(Side.A, "a2"), StoredTurn(Side.B, "b3"))),
            ),
        )
        Simulations.insert {
            it[mode] = Mode.AMISTAD
            it[userA] = a
            it[userB] = b
            it[Simulations.origin] = origin
            it[status] = SimulationStatus.DONE
            it[Simulations.transcript] = ContractJson.encodeToJsonElement(StoredTranscript.serializer(), transcript)
            it[createdAt] = at
        }[Simulations.id]
    }

    fun withDb(block: (Database, ExposedFeedbackRepository) -> Unit) = TestDatabase.withCleanDatabase { ds ->
        runMigrations(ds)
        val db = connectExposed(ds)
        block(db, ExposedFeedbackRepository(db))
    }

    test("Postgres: los turnos por calificar son solo los del gemelo del usuario, con índice plano, y se descartan al calificarlos").config(enabled = TestDatabase.enabled) {
        withDb { db, repo ->
            val ana = newUser(db, "ana")
            val beto = newUser(db, "beto")
            val sim = simulation(db, ana, beto)
            simulation(db, ana, beto, SimulationOrigin.SCENARIO) // los escenarios no se califican

            repo.ratableTurns(ana, 10).map { it.turnIndex to it.text } shouldBe listOf(0 to "a0", 2 to "a2")
            repo.ratableTurns(beto, 10).map { it.turnIndex to it.text } shouldBe listOf(1 to "b1", 3 to "b3")
            repo.ownTurnText(ana, sim, 1) shouldBe null
            repo.ownTurnText(ana, sim, 2) shouldBe "a2"

            repo.addRating(ana, sim, 0, RATING_POSITIVE, now) shouldBe true
            repo.addRating(ana, sim, 0, RATING_NEGATIVE, now) shouldBe false // el índice único evita el duplicado
            repo.ratableTurns(ana, 10).map { it.turnIndex } shouldBe listOf(2)
        }
    }

    test("Postgres: la predicción de control se guarda sin respuesta y la respuesta real solo se fija una vez").config(enabled = TestDatabase.enabled) {
        withDb { db, repo ->
            val ana = newUser(db, "ana")
            val created = repo.createControl(ana, 3, TwinAnswer.B, now)
            repo.controls(ana).single().let {
                it.id shouldBe created.id
                it.predicted shouldBe TwinAnswer.B
                it.actual shouldBe null
            }
            (repo.answerControl(ana, created.id, TwinAnswer.B) is ControlAnswer.Saved) shouldBe true
            (repo.answerControl(ana, created.id, TwinAnswer.A) is ControlAnswer.AlreadyAnswered) shouldBe true
            (repo.answerControl(newUser(db, "beto"), created.id, TwinAnswer.A) is ControlAnswer.NotFound) shouldBe true
            repo.controls(ana).single().actual shouldBe TwinAnswer.B
        }
    }

    test("Postgres: twin-accuracy escribe un punto por usuario con datos, ninguno sin datos, y repetirlo no duplica").config(enabled = TestDatabase.enabled) {
        TestDatabase.withCleanDatabase { ds ->
            runMigrations(ds)
            val db = connectExposed(ds)
            val module = twinModule(Env.DEV, db, FIXED_CLOCK, testGateway(), FakeTaskQueue(), quotaModule(Env.DEV, db, FIXED_CLOCK).daily)
            val repo = ExposedFeedbackRepository(db)
            val ana = newUser(db, "ana")
            val beto = newUser(db, "beto")
            val carla = newUser(db, "carla")
            val sim = simulation(db, ana, beto)
            repo.addRating(ana, sim, 0, RATING_POSITIVE, now.minus(Duration.ofHours(30)))
            repo.addRating(ana, sim, 2, RATING_NEGATIVE, now.minus(Duration.ofHours(2)))
            val control = repo.createControl(ana, 1, TwinAnswer.A, now.minus(Duration.ofHours(5)))
            repo.answerControl(ana, control.id, TwinAnswer.A)
            repo.createControl(beto, 2, TwinAnswer.B, now.minus(Duration.ofHours(3))) // sin responder: no cuenta
            repo.addRating(carla, sim, 1, RATING_POSITIVE, now.minus(Duration.ofDays(7))) // semana anterior

            module.feedback.computeAccuracy() shouldBe 1
            module.feedback.computeAccuracy() shouldBe 1

            val monday = LocalDate.parse("2026-10-19")
            module.repository.accuracyPoints(ana) shouldBe listOf(AccuracyPoint(monday, 75, ratings = 2, predictions = 1))
            module.repository.accuracyPoints(beto).shouldBeEmpty()
            module.repository.accuracyPoints(carla).shouldBeEmpty()
        }
    }
})
