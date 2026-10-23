package me.santiagoreina.parcheu.api.identity.deletion

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import me.santiagoreina.parcheu.api.identity.IdentityRepository
import me.santiagoreina.parcheu.api.platform.auth.FirebaseUser
import me.santiagoreina.parcheu.api.platform.errors.NotFound
import me.santiagoreina.parcheu.api.platform.tasks.QueueKind
import me.santiagoreina.parcheu.api.platform.tasks.TaskQueue
import me.santiagoreina.parcheu.api.platform.tasks.TaskRequest
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.util.UUID

/** Resultado de una ejecución de `purge-user` (para logs y pruebas). */
enum class PurgeOutcome {
    /** Se purgó en esta ejecución. */
    PURGED,

    /** No había nada que purgar: la cuenta ya no existe (purga previa) o no está marcada como borrada. */
    NOTHING_TO_DO,
}

/**
 * Borrado de cuenta (E8.3, ADR-0025). `DELETE /v1/me` hace el **borrado lógico inmediato**
 * (`deleted_at`: la cuenta deja de verse en grafo, fila, chats y simulaciones, que ya filtran por esa
 * columna) y encola `purge-user` en la cola `maintenance`. La tarea sigue la tabla de retención de
 * arquitectura.md §10.2 en este orden, para que un reintento siempre pueda terminar:
 *
 * 1. arma el plan desde Postgres (uid de Firebase, simulaciones y chats);
 * 2. limpia Firestore ([AccountMirrorCleaner]);
 * 3. borra el usuario de Firebase Auth ([AuthAccountRemover]);
 * 4. purga Postgres en una transacción ([AccountPurgeRepository]); al final ya no queda la fila de `users`.
 *
 * Si falla el paso 2 o 3, Cloud Tasks reintenta y todo sigue en Postgres; si falla el 4, los pasos 2 y
 * 3 son idempotentes. La suscripción de Play no se cancela aquí: la app avisa que se cancela en Play.
 */
class AccountDeletionService(
    private val users: IdentityRepository,
    private val repository: AccountPurgeRepository,
    private val mirror: AccountMirrorCleaner,
    private val auth: AuthAccountRemover,
    private val tasks: TaskQueue,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(AccountDeletionService::class.java)

    /**
     * `DELETE /v1/me`. Idempotente: repetirlo (por ejemplo, si encolar falló) vuelve a encolar la purga;
     * Cloud Tasks deduplica por nombre. Funciona también para cuentas de menor de edad y sin términos.
     */
    suspend fun requestDeletion(firebase: FirebaseUser) {
        val user = users.findByFirebaseUid(firebase.uid) ?: throw NotFound("No hay una cuenta con esta sesión")
        val now = clock.instant()
        users.markDeleted(user.id, now)
        // Si ya estaba borrada, la primera tarea pudo agotar sus reintentos: Cloud Tasks guarda su nombre
        // cerca de una hora, así que la repetición usa uno propio (por minuto) para que sí se encole.
        val retry = if (user.deletedAt != null) "retry-${now.epochSecond / 60}" else null
        tasks.enqueue(purgeTask(user.id, retry))
        log.info("Cuenta {} marcada como borrada; purga encolada", user.id)
    }

    /** Handler de `POST /internal/tasks/purge-user`. Idempotente (ADR-0019). */
    suspend fun purge(userId: UUID): PurgeOutcome {
        val plan = repository.plan(userId) ?: return PurgeOutcome.NOTHING_TO_DO
        mirror.clean(plan)
        auth.remove(plan.firebaseUid)
        repository.purge(plan, clock.instant())
        // Segunda pasada: una simulación en curso pudo publicar `simulations/{id}` entre la limpieza y la purga.
        // Tras la purga queda FAILED y el motor ya no publica; si esta pasada falla no se reintenta la tarea
        // (la cuenta ya no existe), así que solo se registra.
        try {
            mirror.removeSimulations(plan.simulationIds)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Cuenta {} purgada, pero falló la segunda limpieza de simulations/*: {}", userId, e.message)
        }
        log.info("Cuenta {} purgada ({} simulaciones, {} chats)", userId, plan.simulationIds.size, plan.chats.size)
        return PurgeOutcome.PURGED
    }

    /**
     * `POST /internal/jobs/purge-sweep` (Cloud Scheduler, cada hora): vuelve a encolar `purge-user` para las
     * cuentas borradas hace más de [STALLED_AFTER] que siguen en Postgres (la tarea agotó sus reintentos de
     * la cola `maintenance`, p. ej. por una caída larga de Firestore o Firebase Auth). El nombre lleva la
     * hora, así que no choca con la deduplicación de Cloud Tasks y cada barrido reintenta. Devuelve cuántas encoló.
     */
    suspend fun sweepStalled(): Int {
        val now = clock.instant()
        val stalled = repository.stalledDeletions(now.minus(STALLED_AFTER), SWEEP_LIMIT)
        val attempt = "sweep-${now.epochSecond / 3600}"
        stalled.forEach { tasks.enqueue(purgeTask(it, attempt)) }
        if (stalled.isNotEmpty()) log.warn("Barrido de purgas: {} cuentas borradas sin purgar, reencoladas", stalled.size)
        return stalled.size
    }

    companion object {
        const val PURGE_PATH = "/tasks/purge-user"
        const val SWEEP_PATH = "/jobs/purge-sweep"

        /** Una purga que no terminó en este plazo se da por perdida (los reintentos de la cola duran minutos). */
        val STALLED_AFTER: Duration = Duration.ofHours(1)

        /** Máximo de cuentas por barrido (el siguiente barrido toma las demás). */
        const val SWEEP_LIMIT = 200

        /**
         * Tarea `purge-user`: el id de la cuenta es el nombre de deduplicación (ADR-0019). [attempt]
         * distingue un reintento (barrido o `DELETE` repetido) de la primera tarea.
         */
        fun purgeTask(userId: UUID, attempt: String? = null) = TaskRequest(
            queue = QueueKind.MAINTENANCE,
            path = PURGE_PATH,
            name = "purge-user-$userId" + (attempt?.let { "-$it" } ?: ""),
            body = buildJsonObject { put("id", JsonPrimitive(userId.toString())) }.toString(),
        )
    }
}
