package me.santiagoreina.parcheu.api.identity.deletion

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
        users.markDeleted(user.id, clock.instant())
        tasks.enqueue(purgeTask(user.id))
        log.info("Cuenta {} marcada como borrada; purga encolada", user.id)
    }

    /** Handler de `POST /internal/tasks/purge-user`. Idempotente (ADR-0019). */
    suspend fun purge(userId: UUID): PurgeOutcome {
        val plan = repository.plan(userId) ?: return PurgeOutcome.NOTHING_TO_DO
        mirror.clean(plan)
        auth.remove(plan.firebaseUid)
        repository.purge(plan, clock.instant())
        log.info("Cuenta {} purgada ({} simulaciones, {} chats)", userId, plan.simulationIds.size, plan.chats.size)
        return PurgeOutcome.PURGED
    }

    companion object {
        const val PURGE_PATH = "/tasks/purge-user"

        /** Tarea `purge-user`: el id de la cuenta es el nombre de deduplicación (ADR-0019). */
        fun purgeTask(userId: UUID) = TaskRequest(
            queue = QueueKind.MAINTENANCE,
            path = PURGE_PATH,
            name = "purge-user-$userId",
            body = buildJsonObject { put("id", JsonPrimitive(userId.toString())) }.toString(),
        )
    }
}
