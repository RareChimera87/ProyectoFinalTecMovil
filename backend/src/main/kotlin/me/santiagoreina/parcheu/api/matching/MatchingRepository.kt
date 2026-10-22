package me.santiagoreina.parcheu.api.matching

import java.time.Instant
import java.util.UUID

/**
 * Acceso a datos de la fila y los matches. [ExposedMatchingRepository] usa Postgres con
 * `FOR UPDATE SKIP LOCKED`; [InMemoryMatchingRepository] replica la semántica para pruebas de rutas.
 * Todas las operaciones son atómicas: la lógica de negocio vive en [MatchingService].
 */
interface MatchingRepository {
    suspend fun person(userId: UUID): MatchPerson?

    /** Último ticket de la persona, en cualquier estado. */
    suspend fun ticket(userId: UUID): QueueEntry?

    /** Match más reciente de la persona que sigue esperando su elección. */
    suspend fun pendingMatchOf(userId: UUID): MatchRecord?

    /**
     * Mete a [entry] en la fila (reemplazando un ticket anterior que ya no espere) y, en la misma
     * transacción, busca pareja: mismo modo, tickets vigentes, no bloqueados, no conectados ya, sin
     * demo si [demoUsersEnabled] es falso ni en Pareja; prioridad Plus y luego antigüedad.
     * Si hay pareja, crea el match y deja los dos tickets en MATCHED.
     */
    suspend fun enqueueAndPair(entry: QueueEntry, demoUsersEnabled: Boolean, now: Instant, allowExisting: Boolean = false): PairOutcome

    /**
     * Deshace un emparejamiento cuyo cobro falló: el match pasa a EXPIRED y los tickets se ajustan.
     * [requeue] vuelve a esperar con su turno original; [dropped] queda EXPIRED.
     */
    suspend fun undoPairing(matchId: UUID, requeue: QueueEntry?, dropped: UUID?)

    /** Borra el ticket si todavía espera; false si no había ticket en espera. */
    suspend fun cancel(userId: UUID): Boolean

    /** Pasa a EXPIRED los tickets en espera vencidos a [now] y los devuelve (ya en EXPIRED). */
    suspend fun expireTickets(now: Instant, limit: Int): List<QueueEntry>

    suspend fun match(id: UUID): MatchRecord?

    /** Bloquea el match, aplica [change] (null = sin cambios) y guarda el resultado. Null si no existe. */
    suspend fun updateMatch(id: UUID, change: (MatchRecord) -> MatchRecord?): MatchUpdate?

    /** Matches sin decidir cuyo `decide_by` ya pasó. */
    suspend fun dueMatches(now: Instant, limit: Int): List<UUID>

    /** Matches en SIMULATION a los que nunca se les creó la simulación (reintento del barrido). */
    suspend fun matchesMissingSimulation(limit: Int): List<MatchRecord>

    suspend fun attachSimulation(matchId: UUID, simulationId: UUID)

    /**
     * Crea el chat SCENARIO del match con un escenario básico del modo (o cualquiera si [allowPremium])
     * y lo asocia al match. Idempotente: si el match ya tiene chat devuelve ese. Si el modo no
     * tiene escenarios devuelve [ScenarioChatOutcome.NoScenarios]. En la misma transacción vuelve a comprobar
     * bloqueos (en ambos sentidos), borrados y suspensión: si alguno falla no crea el chat, deja el
     * match en EXPIRED y devuelve [ScenarioChatOutcome.Unavailable].
     */
    suspend fun createScenarioChat(matchId: UUID, now: Instant, allowPremium: Boolean): ScenarioChatOutcome

    /** False si hay bloqueo (en cualquier sentido), borrado o suspensión entre las dos personas. */
    suspend fun canInteract(a: UUID, b: UUID, now: Instant): Boolean
}
