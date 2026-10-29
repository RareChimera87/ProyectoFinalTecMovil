package me.santiagoreina.parcheu.api.simulation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import me.santiagoreina.parcheu.api.platform.db.tables.Blocks
import me.santiagoreina.parcheu.api.platform.db.tables.Scenarios
import me.santiagoreina.parcheu.api.platform.db.tables.Simulations
import me.santiagoreina.parcheu.api.platform.db.tables.TwinMemories
import me.santiagoreina.parcheu.api.platform.db.tables.Twins
import me.santiagoreina.parcheu.api.platform.db.tables.UserModes
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.contract.ContractJson
import me.santiagoreina.parcheu.contract.MemoryStatus
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Progress
import me.santiagoreina.parcheu.contract.SimulationOrigin
import me.santiagoreina.parcheu.contract.SimulationStatus
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Simulaciones sobre Postgres. Los pasos del motor bloquean la fila (`SELECT ... FOR UPDATE`) y
 * comprueban el número de pasos guardados antes de escribir: dos entregas simultáneas de la misma
 * tarea de Cloud Tasks no duplican turnos (arquitectura.md §7.2, punto 9).
 */
class ExposedSimulationRepository(private val db: Database) : SimulationRepository {

    private suspend fun <T> tx(block: () -> T): T = withContext(Dispatchers.IO) { transaction(db) { block() } }

    override suspend fun participant(userId: UUID): Participant? = tx {
        val user = Users.selectAll().where { Users.id eq userId }.singleOrNull() ?: return@tx null
        val modes = UserModes.select(UserModes.mode)
            .where { (UserModes.userId eq userId) and (UserModes.enabled eq true) }
            .map { it[UserModes.mode] }
            .toSet()
        Participant(
            id = userId,
            firebaseUid = user[Users.firebaseUid],
            activeModes = modes,
            ageVerified = user[Users.ageVerifiedAt] != null && user[Users.underageAt] == null,
            parejaConfirmed = user[Users.parejaConfirmedAt] != null,
            isDemo = user[Users.isDemo],
            deleted = user[Users.deletedAt] != null,
        )
    }

    override suspend fun blockedEitherWay(a: UUID, b: UUID): Boolean = tx {
        Blocks.select(Blocks.blockerId)
            .where { ((Blocks.blockerId eq a) and (Blocks.blockedId eq b)) or ((Blocks.blockerId eq b) and (Blocks.blockedId eq a)) }
            .any()
    }

    override suspend fun twinProfile(userId: UUID): TwinProfile? = tx {
        Twins.selectAll().where { Twins.userId eq userId }.singleOrNull()?.let { row ->
            val traits = row[Twins.traits] ?: return@let null
            TwinProfile(
                userId = userId,
                version = row[Twins.version],
                traits = traits.map { it.toDouble() },
                persona = row[Twins.personaSummary],
                personaReady = row[Twins.personaReady],
            )
        }
    }

    override suspend fun acceptedMemories(userId: UUID, mode: Mode): List<TwinMemory> = tx {
        TwinMemories.select(TwinMemories.mode, TwinMemories.text)
            .where {
                (TwinMemories.userId eq userId) and (TwinMemories.status eq MemoryStatus.ACCEPTED) and (TwinMemories.mode eq mode)
            }
            .orderBy(TwinMemories.decidedAt to SortOrder.DESC_NULLS_LAST, TwinMemories.createdAt to SortOrder.DESC)
            .limit(MAX_MEMORIES_IN_PROMPT)
            .map { TwinMemory(it[TwinMemories.mode], it[TwinMemories.text]) }
    }

    override suspend fun scenarios(mode: Mode): List<ScenarioDef> = tx {
        Scenarios.selectAll().where { Scenarios.mode eq mode }.orderBy(Scenarios.id).map { row ->
            ScenarioDef(
                id = row[Scenarios.id].toInt(),
                mode = row[Scenarios.mode],
                slug = row[Scenarios.slug],
                title = row[Scenarios.title],
                prompt = row[Scenarios.prompt],
                isBasic = row[Scenarios.isBasic],
                timerSec = row[Scenarios.timerSec],
                budgetCop = row[Scenarios.budgetCop],
                rubric = ContractJson.decodeFromJsonElement(ListSerializer(RubricItem.serializer()), row[Scenarios.rubric]),
            )
        }
    }

    override suspend fun create(record: SimulationRecord) {
        tx {
            Simulations.insert {
                it[id] = record.id
                it[mode] = record.mode
                it[userA] = record.userA
                it[userB] = record.userB
                it[requestedBy] = record.requestedBy
                it[origin] = record.origin
                it[status] = record.status
                it[progress] = record.progress.json()
                it[transcript] = record.transcript.json()
                it[twinVersionA] = record.twinVersionA
                it[twinVersionB] = record.twinVersionB
                it[provider] = record.provider
                it[createdAt] = record.createdAt.odt()
            }
        }
    }

    override suspend fun find(id: UUID): SimulationRecord? = tx {
        Simulations.selectAll().where { Simulations.id eq id }.singleOrNull()?.toRecord()
    }

    override suspend fun listFor(userId: UUID, before: SimulationCursor?, limit: Int): List<SimulationRecord> = tx {
        val mine: Op<Boolean> = ((Simulations.userA eq userId) or (Simulations.userB eq userId)) and
            (Simulations.origin neq SimulationOrigin.INTRODUCTION)
        val page = if (before == null) {
            mine
        } else {
            val at = before.createdAt.odt()
            mine and ((Simulations.createdAt less at) or ((Simulations.createdAt eq at) and (Simulations.id less before.id)))
        }
        Simulations.selectAll().where(page)
            .orderBy(Simulations.createdAt to SortOrder.DESC, Simulations.id to SortOrder.DESC)
            .limit(limit)
            .map { it.toRecord() }
    }

    override suspend fun findCached(
        userA: UUID,
        versionA: Int,
        userB: UUID,
        versionB: Int,
        mode: Mode,
        since: Instant,
        excluding: UUID,
    ): SimulationRecord? = tx {
        val samePair = (
            (Simulations.userA eq userA) and (Simulations.twinVersionA eq versionA) and
                (Simulations.userB eq userB) and (Simulations.twinVersionB eq versionB)
            ) or (
            (Simulations.userA eq userB) and (Simulations.twinVersionA eq versionB) and
                (Simulations.userB eq userA) and (Simulations.twinVersionB eq versionA)
            )
        Simulations.selectAll()
            .where {
                samePair and (Simulations.mode eq mode) and (Simulations.status eq SimulationStatus.DONE) and
                    (Simulations.finishedAt greater since.odt()) and (Simulations.id neq excluding) and
                    (Simulations.origin neq SimulationOrigin.INTRODUCTION) and
                    // Una conversación humana (E4.8) nunca sirve de caché de una simulación entre gemelos.
                    (Simulations.origin neq SimulationOrigin.SCENARIO)
            }
            .orderBy(Simulations.finishedAt to SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.toRecord()
            ?.takeIf { it.score != null && it.report != null }
    }

    override suspend fun unfinished(createdBefore: Instant, createdAfter: Instant, limit: Int): List<SimulationRecord> = tx {
        Simulations.selectAll()
            .where {
                (Simulations.status inList UNFINISHED) and (Simulations.origin neq SimulationOrigin.SCENARIO) and
                    (Simulations.createdAt less createdBefore.odt()) and (Simulations.createdAt greater createdAfter.odt())
            }
            .orderBy(Simulations.createdAt to SortOrder.ASC, Simulations.id to SortOrder.ASC)
            .limit(limit)
            .map { it.toRecord() }
    }

    override suspend fun unfinishedScenarios(createdBefore: Instant, limit: Int): List<SimulationRecord> = tx {
        Simulations.selectAll()
            .where {
                (Simulations.status inList UNFINISHED) and (Simulations.origin eq SimulationOrigin.SCENARIO) and
                    (Simulations.createdAt less createdBefore.odt())
            }
            .orderBy(Simulations.createdAt to SortOrder.ASC, Simulations.id to SortOrder.ASC)
            .limit(limit)
            .map { it.toRecord() }
    }

    override suspend fun markRunning(id: UUID): Boolean = tx {
        Simulations.update({ (Simulations.id eq id) and (Simulations.status eq SimulationStatus.QUEUED) }) {
            it[status] = SimulationStatus.RUNNING
        } == 1
    }

    override suspend fun saveStep(id: UUID, expectedSteps: Int, transcript: StoredTranscript, progress: Progress, usage: UsageTotals): Boolean =
        tx {
            val row = Simulations.selectAll().where { Simulations.id eq id }.forUpdate().singleOrNull() ?: return@tx false
            val current = row.toRecord()
            if (current.status != SimulationStatus.RUNNING || current.transcript.steps != expectedSteps) return@tx false
            val total = current.usage + usage
            Simulations.update({ Simulations.id eq id }) {
                it[Simulations.transcript] = transcript.json()
                it[Simulations.progress] = progress.json()
                it[tokensIn] = total.tokensIn
                it[tokensOut] = total.tokensOut
                it[costMicros] = total.costMicros
                it[model] = total.model
            }
            true
        }

    override suspend fun replaceDoneTranscript(id: UUID, expected: StoredTranscript, transcript: StoredTranscript): Boolean = tx {
        val row = Simulations.selectAll().where { Simulations.id eq id }.forUpdate().singleOrNull() ?: return@tx false
        val current = row.toRecord()
        if (current.status != SimulationStatus.DONE || current.origin != SimulationOrigin.SCENARIO || current.transcript != expected) return@tx false
        Simulations.update({ Simulations.id eq id }) { it[Simulations.transcript] = transcript.json() } == 1
    }

    override suspend fun markDone(
        id: UUID,
        score: Int,
        report: StoredReport,
        transcript: StoredTranscript,
        progress: Progress,
        cachedFrom: UUID?,
        provider: String?,
        usage: UsageTotals,
        now: Instant,
    ): Boolean = tx {
        val row = Simulations.selectAll().where { Simulations.id eq id }.forUpdate().singleOrNull() ?: return@tx false
        val current = row.toRecord()
        if (current.status == SimulationStatus.DONE || current.status == SimulationStatus.FAILED) return@tx false
        val total = current.usage + usage
        Simulations.update({ Simulations.id eq id }) {
            it[status] = SimulationStatus.DONE
            it[Simulations.score] = score.toShort()
            it[Simulations.report] = report.json()
            it[Simulations.transcript] = transcript.json()
            it[Simulations.progress] = progress.json()
            it[Simulations.cachedFrom] = cachedFrom
            if (provider != null) it[Simulations.provider] = provider
            it[tokensIn] = total.tokensIn
            it[tokensOut] = total.tokensOut
            it[costMicros] = total.costMicros
            it[model] = total.model
            it[finishedAt] = now.odt()
        }
        true
    }

    override suspend fun markFailed(id: UUID, error: String, now: Instant): Boolean = tx {
        Simulations.update({
            (Simulations.id eq id) and (Simulations.status inList listOf(SimulationStatus.QUEUED, SimulationStatus.RUNNING))
        }) {
            it[status] = SimulationStatus.FAILED
            it[Simulations.error] = error.take(500)
            it[finishedAt] = now.odt()
        } == 1
    }

    override suspend fun requeueAfterEnqueueFailure(id: UUID): Boolean = tx {
        Simulations.update({
            (Simulations.id eq id) and (Simulations.status eq SimulationStatus.FAILED) and
                (Simulations.error like "${SimulationService.ENQUEUE_FAILED_PREFIX}%")
        }) {
            it[status] = SimulationStatus.QUEUED
            it[Simulations.error] = null
            it[finishedAt] = null
        } == 1
    }
}

private fun Instant.odt(): OffsetDateTime = OffsetDateTime.ofInstant(this, ZoneOffset.UTC)

private fun Progress.json(): JsonElement = ContractJson.encodeToJsonElement(Progress.serializer(), this)

private fun StoredTranscript.json(): JsonElement = ContractJson.encodeToJsonElement(StoredTranscript.serializer(), this)

private fun StoredReport.json(): JsonElement = ContractJson.encodeToJsonElement(StoredReport.serializer(), this)

private fun ResultRow.toRecord() = SimulationRecord(
    id = this[Simulations.id],
    mode = this[Simulations.mode],
    userA = this[Simulations.userA],
    userB = this[Simulations.userB],
    requestedBy = this[Simulations.requestedBy],
    origin = this[Simulations.origin],
    status = this[Simulations.status],
    progress = this[Simulations.progress]?.let { ContractJson.decodeFromJsonElement(Progress.serializer(), it) } ?: initialProgress(),
    score = this[Simulations.score]?.toInt(),
    report = this[Simulations.report]?.let { ContractJson.decodeFromJsonElement(StoredReport.serializer(), it) },
    transcript = this[Simulations.transcript]?.let { ContractJson.decodeFromJsonElement(StoredTranscript.serializer(), it) } ?: StoredTranscript(),
    twinVersionA = this[Simulations.twinVersionA],
    twinVersionB = this[Simulations.twinVersionB],
    cachedFrom = this[Simulations.cachedFrom],
    provider = this[Simulations.provider],
    usage = UsageTotals(
        tokensIn = this[Simulations.tokensIn] ?: 0,
        tokensOut = this[Simulations.tokensOut] ?: 0,
        costMicros = this[Simulations.costMicros] ?: 0,
        model = this[Simulations.model],
    ),
    error = this[Simulations.error],
    createdAt = this[Simulations.createdAt].toInstant(),
    finishedAt = this[Simulations.finishedAt]?.toInstant(),
)
