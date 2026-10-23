package me.santiagoreina.parcheu.api.matching

import me.santiagoreina.parcheu.api.platform.config.Env
import me.santiagoreina.parcheu.contract.MatchChoice
import me.santiagoreina.parcheu.contract.MatchStatus
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.TicketStatus
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Plazos de la fila y del match (arquitectura.md §7.4 y §7.5). */
object MatchingRules {
    /** Un ticket en espera vence a las 24 h. */
    val TICKET_TTL: Duration = Duration.ofHours(24)

    /** Tiempo para elegir Escenario o Simulación tras el match. */
    val DECIDE_WINDOW: Duration = Duration.ofMinutes(10)

    /** Intentos de emparejar antes de dejar al usuario esperando (cada reintento descarta un candidato sin cupo). */
    const val MAX_PAIRING_ATTEMPTS = 5

    /** Tamaño de cada tanda del barrido (`/internal/jobs/match-sweep`). */
    const val SWEEP_BATCH = 200
}

/**
 * Banderas que cambian el comportamiento de la fila.
 *
 * - `DEMO_USERS_ENABLED` (ADR-0027): sin ella nadie se empareja con un usuario `is_demo`. Por defecto
 *   encendida en local y dev, apagada en prod (la prueba cerrada la enciende a propósito).
 * - `SCENARIO_MODE_ENABLED`: apagada hasta que E4.8 se mezcle; con ella apagada elegir SCENARIO es 400.
 */
data class MatchingFlags(val demoUsersEnabled: Boolean, val scenarioModeEnabled: Boolean) {
    companion object {
        fun fromEnv(env: Env, vars: Map<String, String> = System.getenv()): MatchingFlags = MatchingFlags(
            demoUsersEnabled = vars["DEMO_USERS_ENABLED"].toBoolOrNull() ?: (env != Env.PROD),
            scenarioModeEnabled = vars["SCENARIO_MODE_ENABLED"].toBoolOrNull() ?: false,
        )

        private fun String?.toBoolOrNull(): Boolean? = when (this?.trim()?.lowercase()) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }
}

/** Ticket de `match_queue`: el último de cada persona, con su estado. */
data class QueueEntry(
    val userId: UUID,
    val mode: Mode,
    /** 1 para Plus, 0 para gratis. */
    val priority: Int,
    val enqueuedAt: Instant,
    val expiresAt: Instant,
    val status: TicketStatus = TicketStatus.WAITING,
    val matchId: UUID? = null,
    /** Versión monótona por ticket (trigger de Postgres): ordena las publicaciones al espejo. 0 = sin guardar. */
    val version: Long = 0,
)

/** Fila de `matches`. `userA` es quien esperaba en la fila y `userB` quien llegó y lo emparejó. */
data class MatchRecord(
    val id: UUID,
    val mode: Mode,
    val userA: UUID,
    val userB: UUID,
    val choiceA: MatchChoice?,
    val choiceB: MatchChoice?,
    val status: MatchStatus,
    val simulationId: UUID?,
    val chatId: UUID?,
    val createdAt: Instant,
    val decideBy: Instant,
) {
    fun involves(userId: UUID) = userId == userA || userId == userB

    fun otherThan(userId: UUID): UUID = if (userId == userA) userB else userA

    fun choiceOf(userId: UUID): MatchChoice? = if (userId == userA) choiceA else choiceB

    fun withChoice(userId: UUID, choice: MatchChoice): MatchRecord =
        if (userId == userA) copy(choiceA = choice) else copy(choiceB = choice)
}

/**
 * Lo que la fila necesita saber de una persona. Nunca expone nombre, foto ni carrera: de aquí solo
 * salen el rango de edad, la universidad y los intereses del `AnonProfile` (ADR-0031).
 */
data class MatchPerson(
    val id: UUID,
    val firebaseUid: String,
    val activeModes: Set<Mode>,
    val ageVerified: Boolean,
    val underage: Boolean,
    val parejaConfirmed: Boolean,
    val isDemo: Boolean,
    val deleted: Boolean,
    val birthDate: LocalDate?,
    val universityShort: String?,
    val interests: List<String>,
    /** Fin de la suspensión vigente (null si no está suspendida). */
    val suspendedUntil: Instant? = null,
)

/** Resultado de entrar a la fila e intentar emparejar en la misma transacción. */
sealed interface PairOutcome {
    /** La persona ya tenía un ticket en espera. */
    data object AlreadyQueued : PairOutcome

    /** No había con quién: queda esperando. */
    data class Waiting(val entry: QueueEntry) : PairOutcome

    /** Se creó el match; [own] y [partner] son los tickets ya en estado MATCHED. */
    data class Paired(val match: MatchRecord, val own: QueueEntry, val partner: QueueEntry) : PairOutcome
}

/** Estado de un match antes y después de [MatchingRepository.updateMatch]. */
data class MatchUpdate(val before: MatchRecord, val after: MatchRecord)

/** Documento `matchTickets/{uid}` de Firestore (arquitectura.md §8.2): solo lectura para el dueño. */
data class TicketMirrorDoc(
    /** UID de Firebase del dueño: es el id del documento. */
    val firebaseUid: String,
    val status: TicketStatus,
    val mode: Mode,
    val matchId: UUID?,
    val expiresAt: Instant,
    /** Versión del ticket en Postgres: el espejo solo escribe si es mayor o igual a la que ya tiene. */
    val version: Long = 0,
)

/** Resultado de crear el chat SCENARIO de un match. */
sealed interface ScenarioChatOutcome {
    data class Created(val chatId: UUID) : ScenarioChatOutcome

    /** El modo no tiene escenarios disponibles. */
    data object NoScenarios : ScenarioChatOutcome

    /**
     * Hay bloqueo, borrado o suspensión entre las dos personas: el match se cerró como EXPIRED.
     * [causedBy] son quienes provocaron el cierre (quien bloqueó, quien se borró o fue suspendido):
     * a ellas no se les devuelve el cupo.
     */
    data class Unavailable(val causedBy: Set<UUID>) : ScenarioChatOutcome
}
