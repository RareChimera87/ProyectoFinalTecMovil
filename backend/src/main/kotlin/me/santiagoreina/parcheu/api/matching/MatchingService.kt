package me.santiagoreina.parcheu.api.matching

import kotlinx.coroutines.CancellationException
import me.santiagoreina.parcheu.api.graph.AnonIdSigner
import me.santiagoreina.parcheu.api.graph.GraphVisibilityPolicy
import me.santiagoreina.parcheu.api.identity.profile.AgeRules
import me.santiagoreina.parcheu.api.platform.errors.AgeRequired
import me.santiagoreina.parcheu.api.platform.errors.AiBudgetExceeded
import me.santiagoreina.parcheu.api.platform.errors.Conflict
import me.santiagoreina.parcheu.api.platform.errors.DomainException
import me.santiagoreina.parcheu.api.platform.errors.NotFound
import me.santiagoreina.parcheu.api.platform.errors.QuotaExceeded
import me.santiagoreina.parcheu.api.platform.errors.Underage
import me.santiagoreina.parcheu.api.platform.errors.ValidationFailed
import me.santiagoreina.parcheu.api.platform.notifier.Notification
import me.santiagoreina.parcheu.api.platform.notifier.Notifier
import me.santiagoreina.parcheu.api.quota.QuotaService
import me.santiagoreina.parcheu.api.simulation.SimulationService
import me.santiagoreina.parcheu.contract.AnonProfile
import me.santiagoreina.parcheu.contract.MatchChoice
import me.santiagoreina.parcheu.contract.MatchDto
import me.santiagoreina.parcheu.contract.MatchStatus
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.NotificationKinds
import me.santiagoreina.parcheu.contract.QuotaKind
import me.santiagoreina.parcheu.contract.SimulationOrigin
import me.santiagoreina.parcheu.contract.TicketDto
import me.santiagoreina.parcheu.contract.TicketStatus
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlin.time.Instant as KInstant

/** Cuántos tickets y matches resolvió un barrido (`/internal/jobs/match-sweep`). */
data class SweepResult(val expiredTickets: Int, val settledMatches: Int, val retriedSimulations: Int)

/**
 * Fila de match y matches (backlog E4.2, arquitectura.md §7.4 y §7.5).
 *
 * - **Cupo:** entrar a la fila solo comprueba que haya cupo; el cobro de `RANDOM_MATCH` (a cada
 *   persona, con `ref_id = matchId:userId`) ocurre al encontrar pareja. Cancelar o vencer no cobra.
 * - **Vencimientos:** el job `match-sweep` los resuelve, pero cada consulta también resuelve lo
 *   vencido que toca, así que la API es correcta aunque el job tarde.
 * - **Espejo y avisos:** Postgres manda; fallar en Firestore o en el `Notifier` se registra y no
 *   deshace nada.
 */
class MatchingService(
    private val repo: MatchingRepository,
    private val quota: QuotaService,
    private val simulation: SimulationService,
    private val anonIds: AnonIdSigner,
    private val notifier: Notifier,
    private val mirror: MatchTicketMirror,
    private val flags: MatchingFlags,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(MatchingService::class.java)

    // ---- Fila -------------------------------------------------------------------------------

    /** `POST /v1/match/queue`. */
    suspend fun enqueue(userId: UUID, mode: Mode): TicketDto {
        val person = repo.person(userId)?.takeIf { !it.deleted } ?: throw NotFound("Usuario no encontrado")
        validateCanQueue(person, mode)
        expireDueTickets()
        if (repo.pendingMatchOf(userId)?.let { settleIfDue(it) }?.status == MatchStatus.PENDING_CHOICE) {
            throw Conflict("Ya tienes un match esperando tu elección")
        }
        quota.ensureAvailable(userId, QuotaKind.RANDOM_MATCH)

        val now = clock.instant()
        val entry = QueueEntry(
            userId = userId,
            mode = mode,
            priority = if (quota.isPlus(userId)) 1 else 0,
            enqueuedAt = now,
            expiresAt = now.plus(MatchingRules.TICKET_TTL),
        )
        var outcome = repo.enqueueAndPair(entry, flags.demoUsersEnabled, now)
        var attempts = 1
        while (true) {
            when (val current = outcome) {
                PairOutcome.AlreadyQueued -> throw Conflict("Ya estás en la fila")
                is PairOutcome.Waiting -> {
                    publish(person.firebaseUid, current.entry)
                    return ticketDto(current.entry)
                }
                is PairOutcome.Paired -> {
                    if (charge(current)) {
                        onPaired(current)
                        return ticketDto(current.own)
                    }
                    // El cobro del otro falló y su ticket se descartó: se vuelve a buscar con el propio.
                    if (attempts++ >= MatchingRules.MAX_PAIRING_ATTEMPTS) {
                        val waiting = repo.ticket(userId) ?: entry
                        publish(person.firebaseUid, waiting)
                        return ticketDto(waiting)
                    }
                    outcome = repo.enqueueAndPair(entry, flags.demoUsersEnabled, clock.instant(), allowExisting = true)
                }
            }
        }
    }

    private fun validateCanQueue(person: MatchPerson, mode: Mode) {
        if (person.underage) throw Underage()
        if (mode !in person.activeModes) throw ValidationFailed("El modo ${mode.name} no está activo")
        if (mode == Mode.PAREJA) {
            if (person.isDemo) throw ValidationFailed("Los usuarios de demostración no participan en Pareja")
            if (!person.ageVerified || !person.parejaConfirmed) throw AgeRequired()
        }
        if (person.isDemo && !flags.demoUsersEnabled) throw ValidationFailed("Los usuarios de demostración están desactivados")
    }

    /**
     * Cobra `RANDOM_MATCH` a las dos personas. Devuelve true si ambas pagaron. Si no hay cupo para la
     * pareja que esperaba, su ticket vence y se devuelve false (quien llegó vuelve a buscar); si no hay
     * cupo para quien llegó, se deshace el match, la pareja vuelve a esperar y se lanza 402.
     */
    private suspend fun charge(paired: PairOutcome.Paired): Boolean {
        val matchId = paired.match.id
        val partnerId = paired.partner.userId
        val ownId = paired.own.userId
        try {
            quota.consume(partnerId, QuotaKind.RANDOM_MATCH, "$matchId:$partnerId")
        } catch (e: QuotaExceeded) {
            repo.undoPairing(matchId, requeue = requeueOf(paired.own), dropped = partnerId)
            repo.person(partnerId)?.let { publish(it.firebaseUid, paired.partner.copy(status = TicketStatus.EXPIRED, matchId = null)) }
            return false
        }
        try {
            quota.consume(ownId, QuotaKind.RANDOM_MATCH, "$matchId:$ownId")
        } catch (e: QuotaExceeded) {
            quota.refund(QuotaKind.RANDOM_MATCH, "$matchId:$partnerId")
            repo.undoPairing(matchId, requeue = requeueOf(paired.partner), dropped = ownId)
            repo.person(partnerId)?.let { publish(it.firebaseUid, requeueOf(paired.partner)) }
            throw e
        }
        return true
    }

    private fun requeueOf(entry: QueueEntry) = entry.copy(status = TicketStatus.WAITING, matchId = null)

    private suspend fun onPaired(paired: PairOutcome.Paired) {
        for (entry in listOf(paired.own, paired.partner)) {
            val person = repo.person(entry.userId) ?: continue
            publish(person.firebaseUid, entry)
            safely("avisar el match ${paired.match.id}") {
                notifier.notify(
                    entry.userId,
                    Notification(
                        kind = NotificationKinds.MATCH_FOUND,
                        title = "¡Encontramos un parche para ti!",
                        body = "Tienes 10 minutos para elegir cómo conocerse.",
                        route = "parcheu://match/${paired.match.id}",
                    ),
                )
            }
        }
    }

    /** `GET /v1/match/queue`: el último ticket de la persona; 404 si nunca entró a la fila. */
    suspend fun ticket(userId: UUID): TicketDto {
        expireDueTickets()
        val entry = repo.ticket(userId) ?: throw NotFound("No estás en la fila")
        return ticketDto(entry)
    }

    /** `DELETE /v1/match/queue`: sale de la fila sin cobrar nada. Idempotente. */
    suspend fun cancel(userId: UUID) {
        val entry = repo.ticket(userId)
        if (repo.cancel(userId) && entry != null) {
            repo.person(userId)?.let { person -> safely("borrar matchTickets/${person.firebaseUid}") { mirror.remove(person.firebaseUid) } }
        }
    }

    private fun ticketDto(entry: QueueEntry) = TicketDto(
        status = entry.status,
        mode = entry.mode,
        expiresAt = entry.expiresAt.kotlin(),
        matchId = entry.matchId?.toString(),
    )

    private suspend fun expireDueTickets(): Int {
        val expired = repo.expireTickets(clock.instant(), MatchingRules.SWEEP_BATCH)
        for (entry in expired) {
            val person = repo.person(entry.userId) ?: continue
            publish(person.firebaseUid, entry)
            safely("avisar el vencimiento del ticket de ${entry.userId}") {
                notifier.notify(
                    entry.userId,
                    Notification(
                        kind = NotificationKinds.MATCH_EXPIRED,
                        title = "Nadie por ahora",
                        body = "No encontramos a nadie en 24 horas. No se gastó tu cupo: puedes intentarlo de nuevo.",
                        route = "parcheu://match",
                    ),
                )
            }
        }
        return expired.size
    }

    // ---- Matches ----------------------------------------------------------------------------

    /** `GET /v1/matches/{id}`. */
    suspend fun match(viewer: UUID, rawId: String): MatchDto {
        val found = visible(viewer, rawId)
        return dto(viewer, settleIfDue(found))
    }

    /** `POST /v1/matches/{id}/choice`. */
    suspend fun choose(viewer: UUID, rawId: String, choice: MatchChoice): MatchDto {
        val found = visible(viewer, rawId)
        val settled = settleIfDue(found)
        if (settled.status != MatchStatus.PENDING_CHOICE) throw Conflict("Este match ya se resolvió")
        if (choice == MatchChoice.SCENARIO && !flags.scenarioModeEnabled) throw ValidationFailed("El modo escenario todavía no está disponible")

        val update = repo.updateMatch(found.id) { current ->
            if (current.status != MatchStatus.PENDING_CHOICE || !clock.instant().isBefore(current.decideBy)) {
                throw Conflict("El tiempo para elegir terminó")
            }
            if (current.choiceOf(viewer) != null) throw Conflict("Ya elegiste")
            resolve(current.withChoice(viewer, choice), timedOut = false)
        } ?: throw NotFound()
        afterTransition(update)
        return dto(viewer, update.after)
    }

    /** Resuelve el match si su plazo venció; si no, lo devuelve igual. */
    private suspend fun settleIfDue(match: MatchRecord): MatchRecord {
        if (match.status != MatchStatus.PENDING_CHOICE || clock.instant().isBefore(match.decideBy)) return match
        val update = repo.updateMatch(match.id) { current -> resolve(current, timedOut = true) } ?: return match
        afterTransition(update)
        return update.after
    }

    private suspend fun visible(viewer: UUID, rawId: String): MatchRecord {
        val id = runCatching { UUID.fromString(rawId) }.getOrNull() ?: throw NotFound()
        val found = repo.match(id) ?: throw NotFound()
        if (!found.involves(viewer)) throw NotFound()
        return found
    }

    /**
     * Reglas de elección (arquitectura.md §7.5), puras para poder probarlas con reloj fijo:
     * - Ambos SCENARIO -> SCENARIO (si la bandera sigue encendida; si no, SIMULATION).
     * - Cualquiera elige SIMULATION -> SIMULATION.
     * - Al vencer: una sola elección -> SIMULATION; ninguna -> EXPIRED.
     */
    private fun resolve(match: MatchRecord, timedOut: Boolean): MatchRecord {
        if (match.status != MatchStatus.PENDING_CHOICE) return match
        val a = match.choiceA
        val b = match.choiceB
        return when {
            a == MatchChoice.SCENARIO && b == MatchChoice.SCENARIO ->
                match.copy(status = if (flags.scenarioModeEnabled) MatchStatus.SCENARIO else MatchStatus.SIMULATION)
            a == MatchChoice.SIMULATION || b == MatchChoice.SIMULATION -> match.copy(status = MatchStatus.SIMULATION)
            timedOut && (a != null || b != null) -> match.copy(status = MatchStatus.SIMULATION)
            timedOut -> match.copy(status = MatchStatus.EXPIRED)
            else -> match
        }
    }

    /** Efectos de una resolución: crear la simulación o el chat, o avisar el vencimiento. */
    private suspend fun afterTransition(update: MatchUpdate) {
        val before = update.before
        val after = update.after
        if (before.status != MatchStatus.PENDING_CHOICE || after.status == MatchStatus.PENDING_CHOICE) return
        when (after.status) {
            MatchStatus.SIMULATION -> startSimulation(after)
            MatchStatus.SCENARIO -> safely("crear el chat del match ${after.id}") {
                repo.createScenarioChat(after.id, clock.instant(), allowPremium = quota.isPlus(after.userA) && quota.isPlus(after.userB))
            }
            MatchStatus.EXPIRED -> listOf(after.userA, after.userB).forEach { user ->
                safely("avisar el vencimiento del match ${after.id}") {
                    notifier.notify(
                        user,
                        Notification(
                            kind = NotificationKinds.MATCH_EXPIRED,
                            title = "Se acabó el tiempo",
                            body = "Nadie eligió cómo conocerse. Puedes buscar otro parche cuando quieras.",
                            route = "parcheu://match",
                        ),
                    )
                }
            }
            MatchStatus.PENDING_CHOICE -> Unit
        }
    }

    /**
     * Crea la simulación del match con origen RANDOM y **sin** cobrar el cupo semanal: ya lo cubre el
     * cupo diario del match. Un fallo pasajero (p. ej. presupuesto de IA) deja el match en SIMULATION
     * sin simulación y el barrido lo reintenta; un fallo definitivo (bloqueo, gemelo no listo) cierra
     * el match como EXPIRED y devuelve el cupo.
     */
    private suspend fun startSimulation(match: MatchRecord) {
        // Quien pidió la simulación: quien eligió SIMULATION o, si venció el plazo, quien sí eligió.
        val requestedBy = when {
            match.choiceA == MatchChoice.SIMULATION -> match.userA
            match.choiceB == MatchChoice.SIMULATION -> match.userB
            match.choiceA != null -> match.userA
            else -> match.userB
        }
        try {
            val record = simulation.create(
                requestedBy = requestedBy,
                userA = match.userA,
                userB = match.userB,
                mode = match.mode,
                origin = SimulationOrigin.RANDOM,
                chargeQuota = false,
            )
            repo.attachSimulation(match.id, record.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiBudgetExceeded) {
            log.warn("Sin presupuesto de IA para el match {}: el barrido lo reintentará", match.id)
        } catch (e: DomainException) {
            log.warn("No se pudo crear la simulación del match {}: {}", match.id, e.message)
            repo.updateMatch(match.id) { current -> current.copy(status = MatchStatus.EXPIRED).takeIf { current.simulationId == null } }
            listOf(match.userA, match.userB).forEach { quota.refund(QuotaKind.RANDOM_MATCH, "${match.id}:$it") }
        } catch (e: Exception) {
            log.error("Falló la creación de la simulación del match {}: el barrido lo reintentará", match.id, e)
        }
    }

    private suspend fun dto(viewer: UUID, match: MatchRecord): MatchDto {
        val otherId = match.otherThan(viewer)
        return MatchDto(
            id = match.id.toString(),
            mode = match.mode,
            other = anonProfile(viewer, otherId),
            status = match.status,
            myChoice = match.choiceOf(viewer),
            decideBy = match.decideBy.kotlin(),
            scenarioAvailable = flags.scenarioModeEnabled,
        )
    }

    /**
     * Perfil anónimo de la otra persona: solo rango de edad, universidad e intereses (datos de B0).
     * Nunca el nombre, la foto, la carrera ni el id interno; `anonId` es el seudónimo del observador.
     */
    private suspend fun anonProfile(viewer: UUID, subject: UUID): AnonProfile {
        val person = repo.person(subject)
        return AnonProfile(
            anonId = anonIds.anonId(viewer, subject),
            label = GraphVisibilityPolicy.anonymousLabel(person?.universityShort),
            ageRange = person?.birthDate?.let { runCatching { AgeRules.ageRange(it, AgeRules.todayInBogota(clock)) }.getOrNull() } ?: "18+",
            university = person?.universityShort,
            interests = person?.interests ?: emptyList(),
            isDemo = person?.isDemo ?: false,
        )
    }

    // ---- Barrido ----------------------------------------------------------------------------

    /**
     * `POST /internal/jobs/match-sweep` (Cloud Scheduler, cada minuto): vence tickets de más de 24 h,
     * resuelve matches cuyo plazo terminó y reintenta las simulaciones que quedaron pendientes.
     */
    suspend fun sweep(): SweepResult {
        val expired = expireDueTickets()
        var settled = 0
        for (id in repo.dueMatches(clock.instant(), MatchingRules.SWEEP_BATCH)) {
            val current = repo.match(id) ?: continue
            if (settleIfDue(current).status != MatchStatus.PENDING_CHOICE) settled++
        }
        var retried = 0
        for (match in repo.matchesMissingSimulation(MatchingRules.SWEEP_BATCH)) {
            startSimulation(match)
            retried++
        }
        return SweepResult(expired, settled, retried)
    }

    // ---- Utilidades -------------------------------------------------------------------------

    private suspend fun publish(firebaseUid: String, entry: QueueEntry) {
        safely("publicar matchTickets/$firebaseUid") {
            mirror.publish(TicketMirrorDoc(firebaseUid, entry.status, entry.mode, entry.matchId, entry.expiresAt))
        }
    }

    private suspend fun safely(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("No se pudo {}: {}", what, e.message)
        }
    }

    private fun Instant.kotlin(): KInstant = KInstant.fromEpochSeconds(epochSecond, nano)
}
