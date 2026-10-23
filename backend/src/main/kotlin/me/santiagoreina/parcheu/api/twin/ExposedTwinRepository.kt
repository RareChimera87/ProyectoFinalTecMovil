package me.santiagoreina.parcheu.api.twin

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.double
import me.santiagoreina.parcheu.api.platform.db.tables.Questions
import me.santiagoreina.parcheu.api.platform.db.tables.TwinAccuracy
import me.santiagoreina.parcheu.api.platform.db.tables.TwinAnswers
import me.santiagoreina.parcheu.api.platform.db.tables.TwinVersions
import me.santiagoreina.parcheu.api.platform.db.tables.Twins
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.api.platform.errors.NotFound
import me.santiagoreina.parcheu.contract.TraitKey
import me.santiagoreina.parcheu.contract.TwinAnswer
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.math.round

/**
 * Gemelo en Postgres. Todo lo que cambia el estado del gemelo de un usuario bloquea primero su
 * fila de `users` (`SELECT ... FOR UPDATE`), así que dos envíos de respuestas o dos ejecuciones de
 * `build-persona` del mismo usuario se serializan.
 */
class ExposedTwinRepository(private val db: Database) : TwinRepository {
    override fun questions(): List<WeightedQuestion> = transaction(db) {
        Questions.selectAll().orderBy(Questions.ord).map { it.toQuestion() }
    }

    override fun saveAnswers(userId: UUID, answers: Map<Int, TwinAnswer>, traits: List<Double>, now: Instant): SavedAnswers =
        transaction(db) {
            lockUser(userId)
            val previous = TwinAnswers.selectAll().where { TwinAnswers.userId eq userId }.count()
            val at = now.toOffset()
            answers.forEach { (questionId, answer) ->
                TwinAnswers.upsert {
                    it[TwinAnswers.userId] = userId
                    it[TwinAnswers.questionId] = questionId.toShort()
                    it[TwinAnswers.answer] = answer
                    it[answeredAt] = at
                }
            }
            val stored = traits.map { it.toFloat() }
            val row = Twins.selectAll().where { Twins.userId eq userId }.singleOrNull()
            if (row == null) {
                val xp = answers.size * XP_PER_ANSWER
                Twins.insert {
                    it[Twins.userId] = userId
                    it[Twins.traits] = stored
                    it[personaReady] = false
                    it[version] = 1
                    it[level] = levelFor(xp)
                    it[Twins.xp] = xp
                    it[updatedAt] = at
                }
            } else {
                val first = previous == 0L
                val xp = row[Twins.xp] + if (first) answers.size * XP_PER_ANSWER else 0
                Twins.update({ Twins.userId eq userId }) {
                    it[Twins.traits] = stored
                    // Un reenvío es una persona nueva: el resumen anterior sigue hasta que la reemplace, pero ya no está 'lista'.
                    if (!first) {
                        it[version] = row[Twins.version] + 1
                        it[personaReady] = false
                    }
                    it[Twins.xp] = xp
                    it[level] = levelFor(xp)
                    it[updatedAt] = at
                }
            }
            val state = stateOf(userId) ?: error("twins sin fila tras guardar respuestas de $userId")
            SavedAnswers(state, state.version)
        }

    override fun state(userId: UUID): TwinState? = transaction(db) { stateOf(userId) }

    private fun stateOf(userId: UUID): TwinState? {
        val row = Twins.selectAll().where { Twins.userId eq userId }.singleOrNull() ?: return null
        val accuracy = TwinAccuracy.selectAll().where { TwinAccuracy.userId eq userId }
            .orderBy(TwinAccuracy.week, SortOrder.DESC).limit(1).singleOrNull()?.get(TwinAccuracy.accuracy)?.toInt()
        return TwinState(
            traits = row[Twins.traits]?.map { round(it.toDouble() * TraitEngine.DECIMALS) / TraitEngine.DECIMALS },
            personaReady = row[Twins.personaReady],
            version = row[Twins.version],
            level = row[Twins.level],
            xp = row[Twins.xp],
            accuracy = accuracy,
        )
    }

    override fun accuracyPoints(userId: UUID): List<AccuracyPoint> = transaction(db) {
        TwinAccuracy.selectAll().where { TwinAccuracy.userId eq userId }.orderBy(TwinAccuracy.week)
            .map { AccuracyPoint(it[TwinAccuracy.week], it[TwinAccuracy.accuracy].toInt()) }
    }

    override fun personaInput(userId: UUID): PersonaInput? = transaction(db) {
        val twin = Twins.selectAll().where { Twins.userId eq userId }.singleOrNull() ?: return@transaction null
        val traits = twin[Twins.traits]?.map { round(it.toDouble() * TraitEngine.DECIMALS) / TraitEngine.DECIMALS } ?: return@transaction null
        // Solo preguntas y respuestas: ninguna otra tabla (ni users ni twin_memories) entra aquí.
        val answers = (TwinAnswers innerJoin Questions).selectAll()
            .where { TwinAnswers.userId eq userId }
            .orderBy(Questions.ord)
            .map { AnsweredQuestion(it.toQuestion(), it[TwinAnswers.answer]) }
        if (answers.isEmpty()) return@transaction null
        PersonaInput(twin[Twins.version], twin[Twins.personaReady], traits, answers)
    }

    override fun savePersona(userId: UUID, version: Int, summary: String, now: Instant): Boolean = transaction(db) {
        lockUser(userId)
        val twin = Twins.selectAll().where { Twins.userId eq userId }.singleOrNull() ?: return@transaction false
        if (twin[Twins.version] != version || twin[Twins.personaReady]) return@transaction false
        val at = now.toOffset()
        TwinVersions.insertIgnore {
            it[TwinVersions.userId] = userId
            it[TwinVersions.version] = version
            it[traits] = twin[Twins.traits]
            it[personaSummary] = summary
            it[createdAt] = at
        }
        Twins.update({ (Twins.userId eq userId) and (Twins.version eq version) }) {
            it[personaSummary] = summary
            it[personaReady] = true
            it[updatedAt] = at
        }
        true
    }

    override fun addAccuracyPoint(userId: UUID, point: AccuracyPoint) {
        transaction(db) {
            TwinAccuracy.upsert {
                it[TwinAccuracy.userId] = userId
                it[week] = point.week
                it[accuracy] = point.accuracy.toShort()
            }
        }
    }

    private fun lockUser(userId: UUID) {
        if (Users.select(Users.id).where { Users.id eq userId }.forUpdate().empty()) throw NotFound("Usuario no encontrado")
    }

    private fun ResultRow.toQuestion() = WeightedQuestion(
        id = this[Questions.id].toInt(),
        ord = this[Questions.ord].toInt(),
        text = this[Questions.text],
        optionA = this[Questions.optionA],
        optionB = this[Questions.optionB],
        weights = (this[Questions.weights] as JsonObject).entries.associate { (k, v) -> TraitKey.valueOf(k) to v.jsonPrimitive.double },
    )
}

private fun Instant.toOffset(): OffsetDateTime = OffsetDateTime.ofInstant(this, ZoneOffset.UTC)
