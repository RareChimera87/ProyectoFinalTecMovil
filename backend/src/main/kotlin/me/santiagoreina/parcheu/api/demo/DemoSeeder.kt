package me.santiagoreina.parcheu.api.demo

import me.santiagoreina.parcheu.api.platform.db.tables.MatchQueue
import me.santiagoreina.parcheu.api.platform.db.tables.Matches
import me.santiagoreina.parcheu.api.platform.db.tables.TwinAnswers
import me.santiagoreina.parcheu.api.platform.db.tables.TwinVersions
import me.santiagoreina.parcheu.api.platform.db.tables.Twins
import me.santiagoreina.parcheu.api.platform.db.tables.UsageEvents
import me.santiagoreina.parcheu.api.platform.db.tables.UserModes
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.api.quota.QuotaPeriod
import me.santiagoreina.parcheu.api.quota.windowFor
import me.santiagoreina.parcheu.api.twin.ExposedTwinRepository
import me.santiagoreina.parcheu.api.twin.TraitEngine
import me.santiagoreina.parcheu.contract.MatchStatus
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.QuotaKind
import me.santiagoreina.parcheu.contract.TicketStatus
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** Resultado de `seed-demo`. */
sealed interface SeedResult {
    /** `DEMO_USERS_ENABLED=false`: no se creó nada y los tickets demo en espera se vencieron. */
    data class Disabled(val expiredTickets: Int) : SeedResult

    /** [created] + [updated] = 20; [waiting] = demos que quedaron esperando en la fila. */
    data class Seeded(val created: Int, val updated: Int, val waiting: Int) : SeedResult
}

/**
 * `seed-demo` (ADR-0027): crea o actualiza los 20 usuarios `is_demo` de [DemoProfiles] con sus respuestas,
 * gemelo listo y modos Amistad y Estudio (Pareja **siempre** apagado), y los deja esperando en la fila.
 *
 * - **Idempotente:** la llave es `users.firebase_uid` (`demo-01` .. `demo-20`); repetirlo no duplica nada y
 *   vuelve a poner en la fila a los demo que ya se emparejaron (ver [refreshTickets]).
 * - **Sin IA:** el resumen de la persona está escrito a mano y los rasgos salen de `TraitEngine` con las mismas
 *   preguntas de la base, así que el job no gasta presupuesto de Vertex.
 * - Todo ocurre en una sola transacción.
 */
class DemoSeeder(private val db: Database, private val settings: DemoSettings) {
    private val log = LoggerFactory.getLogger(DemoSeeder::class.java)

    fun seed(now: Instant): SeedResult {
        if (!settings.enabled) {
            val expired = transaction(db) { expireDemoTickets(now) }
            log.warn("DEMO_USERS_ENABLED=false: seed-demo no crea demos; tickets demo vencidos: {}", expired)
            return SeedResult.Disabled(expired)
        }
        val questions = ExposedTwinRepository(db).questions()
        check(questions.size == DemoProfiles.QUESTIONS) {
            "seed-demo espera ${DemoProfiles.QUESTIONS} preguntas en la base y hay ${questions.size}"
        }
        return transaction(db) {
            val at = now.offset()
            var created = 0
            var updated = 0
            for (profile in DemoProfiles.all) {
                val existing = Users.select(Users.id, Users.isDemo).where { Users.firebaseUid eq profile.firebaseUid }.singleOrNull()
                val userId = if (existing == null) {
                    created++
                    Users.insert {
                        it[firebaseUid] = profile.firebaseUid
                        it[createdAt] = at
                        fill(it, profile, at)
                    }[Users.id]
                } else {
                    // Nunca se convierte en demo a una persona real (no debería existir un UID con este prefijo).
                    check(existing[Users.isDemo]) { "El usuario ${profile.firebaseUid} existe y no es demo: no se toca" }
                    updated++
                    val id = existing[Users.id]
                    Users.update({ Users.id eq id }) { fill(it, profile, at) }
                    id
                }
                Mode.entries.forEach { mode ->
                    val on = mode in DemoProfiles.MODES
                    UserModes.upsert {
                        it[UserModes.userId] = userId
                        it[UserModes.mode] = mode
                        it[enabled] = on
                        it[enabledAt] = if (on) at else null
                    }
                }
                val answers = questions.withIndex().associate { (i, q) -> q.id to profile.answerFor(i) }
                answers.forEach { (questionId, answer) ->
                    TwinAnswers.upsert {
                        it[TwinAnswers.userId] = userId
                        it[TwinAnswers.questionId] = questionId.toShort()
                        it[TwinAnswers.answer] = answer
                        it[answeredAt] = at
                    }
                }
                val traits = TraitEngine.compute(questions, answers).map { it.toFloat() }
                Twins.upsert {
                    it[Twins.userId] = userId
                    it[Twins.traits] = traits
                    it[personaSummary] = profile.persona
                    it[personaReady] = true
                    it[version] = 1
                    it[updatedAt] = at
                }
                TwinVersions.upsert {
                    it[TwinVersions.userId] = userId
                    it[TwinVersions.version] = 1
                    it[TwinVersions.traits] = traits
                    it[personaSummary] = profile.persona
                    it[createdAt] = at
                }
            }
            val waiting = refreshTickets(now)
            log.info("seed-demo: {} demos creados, {} actualizados, {} esperando en la fila", created, updated, waiting)
            SeedResult.Seeded(created, updated, waiting)
        }
    }

    private fun fill(it: UpdateBuilder<*>, profile: DemoProfile, at: OffsetDateTime) {
        it[Users.displayName] = profile.name
        it[Users.email] = null
        it[Users.photoUrl] = null
        it[Users.career] = profile.career
        it[Users.universityId] = profile.universityId.toShort()
        it[Users.interests] = profile.interests
        it[Users.birthDate] = profile.birthDate
        it[Users.ageVerifiedAt] = at
        it[Users.underageAt] = null
        // Pareja nunca: sin confirmación de Pareja aunque alguien activara el modo a mano.
        it[Users.parejaConfirmedAt] = null
        it[Users.isDemo] = true
        it[Users.isTest] = false
        it[Users.suspendedUntil] = null
        it[Users.deletedAt] = null
        it[Users.updatedAt] = at
    }

    /**
     * Deja esperando en la fila (en su [DemoProfile.queueMode]) a cada demo que pueda emparejarse otra vez. Sus
     * tickets llevan `priority = -1`: la fila ordena por prioridad descendente, así que una persona real que
     * espera siempre se empareja antes que un demo. Se salta al demo que tiene un match esperando elección o que
     * ya gastó su match al azar del día (el cobro fallaría y su ticket se descartaría).
     * Devuelve cuántos demo quedaron en WAITING.
     */
    fun JdbcTransaction.refreshTickets(now: Instant): Int {
        val at = now.offset()
        val expires = now.plus(settings.ticketTtl).offset()
        val demos = Users.select(Users.id, Users.firebaseUid)
            .where { (Users.isDemo eq true) and Users.deletedAt.isNull() }
            .associate { it[Users.id] to it[Users.firebaseUid] }
        if (demos.isEmpty()) return 0
        val ids = demos.keys.toList()
        val pending = Matches.select(Matches.userA, Matches.userB)
            .where { (Matches.status eq MatchStatus.PENDING_CHOICE) and ((Matches.userA inList ids) or (Matches.userB inList ids)) }
            .flatMap { listOf(it[Matches.userA], it[Matches.userB]) }
            .toSet()
        val dayStart = windowFor(QuotaPeriod.DAY, now).start.offset()
        val usedToday = UsageEvents.select(UsageEvents.userId)
            .where {
                (UsageEvents.userId inList ids) and (UsageEvents.kind eq QuotaKind.RANDOM_MATCH) and
                    (UsageEvents.refunded eq false) and (UsageEvents.createdAt greaterEq dayStart)
            }
            .map { it[UsageEvents.userId] }
            .toSet()
        val tickets = MatchQueue.selectAll().where { MatchQueue.userId inList ids }.associateBy { it[MatchQueue.userId] }
        val modeByUid = DemoProfiles.all.associate { it.firebaseUid to it.queueMode }
        var waiting = 0
        for ((id, uid) in demos) {
            val mode = modeByUid[uid] ?: Mode.AMISTAD
            val ticket = tickets[id]
            val waitingNow = ticket?.get(MatchQueue.status) == TicketStatus.WAITING
            if (!waitingNow && (id in pending || id in usedToday)) continue
            if (ticket == null) {
                MatchQueue.insert {
                    it[userId] = id
                    it[MatchQueue.mode] = mode
                    it[priority] = DEMO_PRIORITY
                    it[enqueuedAt] = at
                    it[expiresAt] = expires
                    it[status] = TicketStatus.WAITING
                    it[matchId] = null
                }
            } else {
                MatchQueue.update({ MatchQueue.userId eq id }) {
                    it[MatchQueue.mode] = mode
                    it[priority] = DEMO_PRIORITY
                    if (!waitingNow) it[enqueuedAt] = at
                    it[expiresAt] = expires
                    it[status] = TicketStatus.WAITING
                    it[matchId] = null
                }
            }
            waiting++
        }
        return waiting
    }

    /** Fuera de la fila: los tickets WAITING de los demo pasan a EXPIRED. */
    private fun JdbcTransaction.expireDemoTickets(now: Instant): Int {
        val ids = Users.select(Users.id).where { Users.isDemo eq true }.map { it[Users.id] }
        if (ids.isEmpty()) return 0
        return MatchQueue.update({ (MatchQueue.userId inList ids) and (MatchQueue.status eq TicketStatus.WAITING) }) {
            it[status] = TicketStatus.EXPIRED
            it[expiresAt] = now.offset()
        }
    }

    /**
     * Vuelve a poner en la fila a los demo disponibles sin tocar sus perfiles. Con la bandera apagada vence sus
     * tickets y devuelve 0.
     */
    fun refresh(now: Instant): Int = transaction(db) {
        if (settings.enabled) {
            refreshTickets(now)
        } else {
            expireDemoTickets(now)
            0
        }
    }

    companion object {
        /** Prioridad de los tickets demo: por debajo de gratis (0) y Plus (1). */
        const val DEMO_PRIORITY: Short = -1

        internal fun Instant.offset(): OffsetDateTime = OffsetDateTime.ofInstant(this, ZoneOffset.UTC)
    }
}
