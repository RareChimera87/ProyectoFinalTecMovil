package me.santiagoreina.parcheu.api.twin

import me.santiagoreina.parcheu.api.ai.AiGateway
import me.santiagoreina.parcheu.api.platform.config.Env
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.api.platform.tasks.FakeTaskQueue
import me.santiagoreina.parcheu.api.platform.tasks.TaskQueue
import me.santiagoreina.parcheu.api.quota.DailyKind
import me.santiagoreina.parcheu.api.quota.DailyLimiter
import me.santiagoreina.parcheu.api.twin.memories.ExposedMemoryRepository
import me.santiagoreina.parcheu.api.twin.memories.InMemoryMemoryRepository
import me.santiagoreina.parcheu.api.twin.memories.MemoryExtractor
import me.santiagoreina.parcheu.api.twin.memories.MemoryService
import me.santiagoreina.parcheu.api.twin.feedback.ExposedFeedbackRepository
import me.santiagoreina.parcheu.api.twin.feedback.FeedbackRepository
import me.santiagoreina.parcheu.api.twin.feedback.FeedbackService
import me.santiagoreina.parcheu.api.twin.feedback.InMemoryFeedbackRepository
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
    /** Extracción y decisión de memorias (E9.2). */
    val memories: MemoryService,
    /** Calificaciones, pregunta de control y job `twin-accuracy` (E9.3). */
    val feedback: FeedbackService,
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
fun twinModule(env: Env, db: Database?, clock: Clock, ai: AiGateway, tasks: TaskQueue, daily: DailyLimiter): TwinModule {
    val chargeAnswers: (UUID) -> Unit = { daily.consume(it, DailyKind.TWIN_ANSWERS) }
    if (db == null) {
        check(env == Env.LOCAL) { "El gemelo necesita la base de datos en $env: define DB_NAME y CLOUD_SQL_INSTANCE" }
        return inMemoryTwinModule(clock, ai, chargeAnswers = chargeAnswers)
    }
    val repo = ExposedTwinRepository(db)
    val memories = MemoryService(ExposedMemoryRepository(db), MemoryExtractor(ai), tasks, clock)
    val service = TwinService(repo, PersonaBuilder(ai), tasks, clock, chargeAnswers)
    val feedback = FeedbackService(ExposedFeedbackRepository(db), repo, service, clock)
    return TwinModule(service, ExposedTwinUserLookup(db), repo, memories, feedback)
}

/**
 * Todo en memoria (pruebas y `ENV=local` sin base). Con [runInline] las tareas `build-persona` y
 * `extract-memories` corren al encolarlas.
 */
fun inMemoryTwinModule(
    clock: Clock,
    ai: AiGateway,
    repo: InMemoryTwinRepository = InMemoryTwinRepository(),
    queue: FakeTaskQueue = FakeTaskQueue(),
    runInline: Boolean = true,
    memoryRepo: InMemoryMemoryRepository = InMemoryMemoryRepository(repo),
    feedbackRepo: FeedbackRepository = InMemoryFeedbackRepository(),
    chargeAnswers: (UUID) -> Unit = {},
): TwinModule {
    val service = TwinService(repo, PersonaBuilder(ai), queue, clock, chargeAnswers)
    val memories = MemoryService(memoryRepo, MemoryExtractor(ai), queue, clock)
    if (runInline) {
        queue.inline = { task ->
            val id = ContractJson.decodeFromString<TaskPayload>(task.body).uuid()
            when (task.path) {
                MemoryService.TASK_PATH -> memories.extract(id)
                else -> service.buildPersona(id)
            }
        }
    }
    return TwinModule(service, DerivedTwinUserLookup, repo, memories, FeedbackService(feedbackRepo, repo, service, clock))
}
