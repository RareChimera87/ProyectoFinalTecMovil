package me.santiagoreina.parcheu.api.twin

import me.santiagoreina.parcheu.api.ai.AiGateway
import me.santiagoreina.parcheu.api.platform.config.Env
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.api.platform.tasks.FakeTaskQueue
import me.santiagoreina.parcheu.api.platform.tasks.TaskQueue
import me.santiagoreina.parcheu.contract.ContractJson
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Clock
import java.util.UUID

/** Lo que el dominio del gemelo expone al resto del backend. */
class TwinModule(
    val service: TwinService,
    val users: TwinUserLookup,
    val repository: TwinRepository,
)

/** Traduce el UID de Firebase del token al `users.id` (mismo contrato que el de cuotas). */
fun interface TwinUserLookup {
    /** `users.id` del usuario vivo (no borrado) con ese UID, o null si aún no creó sesión. */
    fun userIdFor(firebaseUid: String): UUID?
}

class ExposedTwinUserLookup(private val db: Database) : TwinUserLookup {
    override fun userIdFor(firebaseUid: String): UUID? = transaction(db) {
        Users.select(Users.id)
            .where { (Users.firebaseUid eq firebaseUid) and Users.deletedAt.isNull() }
            .singleOrNull()
            ?.get(Users.id)
    }
}

/** `ENV=local` sin base y pruebas: cada UID tiene un id estable derivado de él. */
object DerivedTwinUserLookup : TwinUserLookup {
    fun idFor(firebaseUid: String): UUID = UUID.nameUUIDFromBytes("parcheu:$firebaseUid".toByteArray())
    override fun userIdFor(firebaseUid: String): UUID = idFor(firebaseUid)
}

/**
 * Cableado según el entorno: con base usa Postgres y la cola real [tasks]; sin base solo se admite
 * con `ENV=local` (memoria y cola Fake que ejecuta en línea).
 */
fun twinModule(env: Env, db: Database?, clock: Clock, ai: AiGateway, tasks: TaskQueue): TwinModule {
    if (db == null) {
        check(env == Env.LOCAL) { "El gemelo necesita la base de datos en $env: define DB_NAME y CLOUD_SQL_INSTANCE" }
        return inMemoryTwinModule(clock, ai)
    }
    val repo = ExposedTwinRepository(db)
    return TwinModule(TwinService(repo, PersonaBuilder(ai), tasks, clock), ExposedTwinUserLookup(db), repo)
}

/** Todo en memoria (pruebas y `ENV=local` sin base). Con [runInline] la tarea `build-persona` corre al encolarla. */
fun inMemoryTwinModule(
    clock: Clock,
    ai: AiGateway,
    repo: InMemoryTwinRepository = InMemoryTwinRepository(),
    queue: FakeTaskQueue = FakeTaskQueue(),
    runInline: Boolean = true,
): TwinModule {
    val service = TwinService(repo, PersonaBuilder(ai), queue, clock)
    if (runInline) queue.inline = { task -> service.buildPersona(ContractJson.decodeFromString<TaskPayload>(task.body).uuid()) }
    return TwinModule(service, DerivedTwinUserLookup, repo)
}
