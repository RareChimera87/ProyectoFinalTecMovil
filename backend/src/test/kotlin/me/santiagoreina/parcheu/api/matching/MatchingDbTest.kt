package me.santiagoreina.parcheu.api.matching

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.santiagoreina.parcheu.api.ai.testGateway
import me.santiagoreina.parcheu.api.chat.ChatModule
import me.santiagoreina.parcheu.api.chat.ExposedChatRepository
import me.santiagoreina.parcheu.api.chat.InMemoryChatMirror
import me.santiagoreina.parcheu.api.platform.notifier.NoopNotifier
import me.santiagoreina.parcheu.api.graph.AnonIdSigner
import me.santiagoreina.parcheu.api.graph.ExposedGraphRepository
import me.santiagoreina.parcheu.api.graph.GraphModule
import me.santiagoreina.parcheu.api.platform.config.Env
import me.santiagoreina.parcheu.api.platform.db.TestDatabase
import me.santiagoreina.parcheu.api.platform.db.connectExposed
import me.santiagoreina.parcheu.api.platform.db.runMigrations
import me.santiagoreina.parcheu.api.platform.db.tables.Blocks
import me.santiagoreina.parcheu.api.platform.db.tables.ChatMembers
import me.santiagoreina.parcheu.api.platform.db.tables.Chats
import me.santiagoreina.parcheu.api.platform.db.tables.Edges
import me.santiagoreina.parcheu.api.platform.db.tables.MatchQueue
import me.santiagoreina.parcheu.api.platform.db.tables.Matches
import me.santiagoreina.parcheu.api.platform.db.tables.Simulations
import me.santiagoreina.parcheu.api.platform.db.tables.Twins
import me.santiagoreina.parcheu.api.platform.db.tables.UsageEvents
import me.santiagoreina.parcheu.api.platform.db.tables.UserModes
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.api.platform.errors.NotFound
import me.santiagoreina.parcheu.api.platform.notifier.FakeNotifier
import me.santiagoreina.parcheu.api.platform.tasks.FakeTaskQueue
import me.santiagoreina.parcheu.api.quota.MovableClock
import me.santiagoreina.parcheu.api.quota.quotaModule
import me.santiagoreina.parcheu.api.simulation.NoopSimulationNotifier
import me.santiagoreina.parcheu.api.simulation.simulationModule
import me.santiagoreina.parcheu.contract.ChatType
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.MatchChoice
import me.santiagoreina.parcheu.contract.MatchStatus
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.SimulationOrigin
import me.santiagoreina.parcheu.contract.TicketStatus
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

/**
 * La fila contra Postgres 16 real (esquema y semillas de Flyway): emparejamiento con
 * `FOR UPDATE SKIP LOCKED`, filtros en SQL y concurrencia. Necesita Docker o `PARCHEU_TEST_DB_URL`;
 * con `-PsinContenedores` se omite (ver [TestDatabase]).
 */
class MatchingDbTest : FunSpec({
    val start = MATCH_START
    val now = OffsetDateTime.parse("2026-10-20T10:00:00-05:00")

    fun Database.user(
        name: String,
        modes: Set<Mode> = Mode.entries.toSet(),
        isDemo: Boolean = false,
        ageVerified: Boolean = true,
        deleted: Boolean = false,
        suspended: Boolean = false,
    ): UUID = transaction(this) {
        val id = Users.insert {
            it[firebaseUid] = "uid-$name"
            it[displayName] = "Nombre Secreto de $name"
            it[birthDate] = LocalDate.of(2004, 3, 14)
            it[universityId] = 1
            it[interests] = listOf("musica", "cine")
            if (ageVerified) {
                it[ageVerifiedAt] = now
                it[parejaConfirmedAt] = now
            }
            it[Users.isDemo] = isDemo
            if (deleted) it[deletedAt] = now
            if (suspended) it[suspendedUntil] = start.plus(Duration.ofDays(1)).atOffset(java.time.ZoneOffset.UTC)
            it[createdAt] = now
            it[updatedAt] = now
        }[Users.id]
        Mode.entries.forEach { m ->
            UserModes.insert {
                it[userId] = id
                it[mode] = m
                it[enabled] = m in modes
                it[enabledAt] = now
            }
        }
        Twins.insert {
            it[userId] = id
            it[traits] = listOf(0.2f, 0.4f, 0.6f, 0.8f, 0.5f, 0.5f)
            it[personaSummary] = "Persona de $name"
            it[personaReady] = true
            it[version] = 1
            it[updatedAt] = now
        }
        id
    }

    /** Ticket WAITING sembrado directo en la tabla (sin intentar emparejar), como lo haría el job de demos. */
    fun Database.seed(userId: UUID, mode: Mode, priority: Int = 0, ageSeconds: Long = 60) = transaction(this) {
        MatchQueue.insert {
            it[MatchQueue.userId] = userId
            it[MatchQueue.mode] = mode
            it[MatchQueue.priority] = priority.toShort()
            it[enqueuedAt] = start.minusSeconds(ageSeconds).atOffset(java.time.ZoneOffset.UTC)
            it[expiresAt] = start.plus(MatchingRules.TICKET_TTL).atOffset(java.time.ZoneOffset.UTC)
        }
    }

    class World(val db: Database, val clock: MovableClock = MovableClock(start), flags: MatchingFlags = MatchingFlags(true, false)) {
        val quota = quotaModule(Env.DEV, db, clock)
        val graph = GraphModule(ExposedGraphRepository(db), AnonIdSigner.forTests(), clock)
        val simulation = simulationModule(Env.DEV, db, clock, testGateway(), quota, graph, FakeTaskQueue(), firebaseProjectId = null, notifier = NoopSimulationNotifier,
            chat = ChatModule(ExposedChatRepository(db), InMemoryChatMirror(), NoopNotifier, graph.policy, clock), push = NoopNotifier,
        )
        val repo = ExposedMatchingRepository(db)
        val mirror = RecordingMatchTicketMirror()
        val notifier = FakeNotifier()
        val service = matchingModule(Env.DEV, db, clock, quota, simulation, graph, notifier, null, flags).let { module ->
            // Misma lógica con el espejo que registra, en vez del Noop de ENV=DEV sin proyecto.
            MatchingModule(MatchingService(repo, quota.service, simulation.service, graph.anonIds, notifier, mirror, flags, clock), repo, flags)
        }.service
    }

    fun withDb(block: suspend (Database) -> Unit) = TestDatabase.withCleanDatabase { ds ->
        runMigrations(ds)
        runBlocking { block(connectExposed(ds)) }
    }

    fun Database.countMatches() = transaction(this) { Matches.selectAll().count().toInt() }

    test("Postgres: 10 usuarios encolados en paralelo producen 5 matches sin duplicados y 10 cobros").config(enabled = TestDatabase.enabled) {
        withDb { db ->
            val w = World(db)
            val users = (1..10).map { db.user("u$it") }
            runBlocking {
                users.map { id -> async(Dispatchers.Default) { w.service.enqueue(id, Mode.AMISTAD) } }.awaitAll()
            }
            db.countMatches() shouldBe 5
            val members = transaction(db) { Matches.selectAll().flatMap { listOf(it[Matches.userA], it[Matches.userB]) } }
            members shouldHaveSize 10
            members shouldContainExactlyInAnyOrder users
            transaction(db) {
                MatchQueue.selectAll().all { it[MatchQueue.status] == TicketStatus.MATCHED && it[MatchQueue.matchId] != null } shouldBe true
                UsageEvents.selectAll().count() shouldBe 10
            }
        }
    }

    test("Postgres: SKIP LOCKED: un candidato bloqueado por otra transacción se salta en vez de esperar").config(enabled = TestDatabase.enabled) {
        withDb { db ->
            val w = World(db)
            val ana = db.user("ana")
            val beto = db.user("beto")
            val caro = db.user("caro")
            w.service.enqueue(ana, Mode.AMISTAD).status shouldBe TicketStatus.WAITING
            w.service.enqueue(beto, Mode.AMISTAD).status shouldBe TicketStatus.MATCHED
            // Ana y Beto ya se emparejaron; Caro entra y debe quedar esperando.
            // Ahora bloqueamos la fila de un nuevo candidato (Dani) y verificamos que Caro no se cuelga.
            val dani = db.user("dani")
            w.repo.let { runBlocking { it.cancel(caro) } }
            val holdLock = java.util.concurrent.CountDownLatch(1)
            val release = java.util.concurrent.CountDownLatch(1)
            runBlocking {
                // Dani espera en la fila.
                w.service.enqueue(dani, Mode.AMISTAD).status shouldBe TicketStatus.WAITING
            }
            val holder = Thread {
                transaction(db) {
                    MatchQueue.selectAll().where { MatchQueue.userId eq dani }.forUpdate(ForUpdateOption.PostgreSQL.ForUpdate()).toList()
                    holdLock.countDown()
                    release.await()
                }
            }
            holder.start()
            holdLock.await()
            try {
                val ticket = runBlocking { withTimeout(10_000) { w.service.enqueue(caro, Mode.AMISTAD) } }
                // Dani está bloqueada por otra transacción: Caro no la toma ni espera, queda esperando.
                ticket.status shouldBe TicketStatus.WAITING
            } finally {
                release.countDown()
                holder.join()
            }
            // Cuando se libera, el siguiente en llegar sí encuentra pareja.
            val eli = db.user("eli")
            runBlocking { w.service.enqueue(eli, Mode.AMISTAD) }.status shouldBe TicketStatus.MATCHED
        }
    }

    test("Postgres: bloqueos, aristas CONNECTED, modo apagado, borrados y demo se filtran en SQL").config(enabled = TestDatabase.enabled) {
        withDb { db ->
            val w = World(db, flags = MatchingFlags(demoUsersEnabled = false, scenarioModeEnabled = false))
            val ana = db.user("ana")
            val bloqueado = db.user("bloqueado")
            val conectado = db.user("conectado")
            val apagado = db.user("apagado", modes = setOf(Mode.AMISTAD))
            val borrado = db.user("borrado", deleted = true)
            val demo = db.user("demo", isDemo = true)
            val suspendido = db.user("suspendido", suspended = true)
            transaction(db) {
                Blocks.insert {
                    it[blockerId] = bloqueado
                    it[blockedId] = ana
                    it[createdAt] = now
                }
                val (low, high) = me.santiagoreina.parcheu.api.platform.db.tables.Edges.orderedPair(ana, conectado)
                Edges.insert {
                    it[userLow] = low
                    it[userHigh] = high
                    it[mode] = Mode.ESTUDIO
                    it[state] = EdgeState.CONNECTED
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            }
            // Los candidatos entran directo al repositorio (la API no deja entrar a borrados ni a demos sin bandera).
            listOf(bloqueado, conectado, apagado, borrado, demo, suspendido).forEach { id ->
                db.seed(id, Mode.ESTUDIO, 0, 60)
            }
            w.service.enqueue(ana, Mode.ESTUDIO).status shouldBe TicketStatus.WAITING
            db.countMatches() shouldBe 0
            // Control: esos mismos tickets sí son válidos para alguien sin bloqueo ni conexión con ellos
            // (se empareja con uno de los que no dependen de la bandera de demo ni del modo apagado).
            val limpia = db.user("limpia")
            w.service.enqueue(limpia, Mode.ESTUDIO).status shouldBe TicketStatus.MATCHED
            val pair = transaction(db) { Matches.selectAll().single().let { setOf(it[Matches.userA], it[Matches.userB]) } }
            val partner = pair.first { it != limpia }
            listOf(bloqueado, conectado).contains(partner) shouldBe true
        }
    }

    test("Postgres: con DEMO_USERS_ENABLED=true empareja con demo en Amistad pero no en Pareja").config(enabled = TestDatabase.enabled) {
        withDb { db ->
            val w = World(db)
            val demoPareja = db.user("demo1", isDemo = true)
            val demoAmistad = db.user("demo2", isDemo = true)
            val ana = db.user("ana")
            val beto = db.user("beto")
            runBlocking {
                db.seed(demoPareja, Mode.PAREJA, 0, 60)
                db.seed(demoAmistad, Mode.AMISTAD, 0, 60)
            }
            w.service.enqueue(ana, Mode.PAREJA).status shouldBe TicketStatus.WAITING
            w.service.enqueue(beto, Mode.AMISTAD).status shouldBe TicketStatus.MATCHED
            transaction(db) { Matches.selectAll().single().let { setOf(it[Matches.userA], it[Matches.userB]) } } shouldBe setOf(demoAmistad, beto)
        }
    }

    test("Postgres: Pareja solo con edad verificada y confirmación; prioridad Plus y antigüedad ordenan a los candidatos").config(enabled = TestDatabase.enabled) {
        withDb { db ->
            val w = World(db)
            val sinEdad = db.user("sinedad", ageVerified = false)
            val ana = db.user("ana")
            runBlocking {
                // Un ticket de Pareja sembrado de alguien sin edad verificada nunca es candidato.
                db.seed(sinEdad, Mode.PAREJA, 1, 60)
            }
            w.service.enqueue(ana, Mode.PAREJA).status shouldBe TicketStatus.WAITING

            val libre = db.user("libre")
            val plus = db.user("plus")
            val llegada = db.user("llegada")
            transaction(db) {
                me.santiagoreina.parcheu.api.platform.db.tables.Blocks.insert {
                    it[blockerId] = libre
                    it[blockedId] = plus
                    it[createdAt] = now
                }
            }
            runBlocking {
                db.seed(libre, Mode.AMISTAD, 0, 120)
                db.seed(plus, Mode.AMISTAD, 1, 60)
            }
            w.service.enqueue(llegada, Mode.AMISTAD).status shouldBe TicketStatus.MATCHED
            transaction(db) {
                Matches.selectAll().where { Matches.mode eq Mode.AMISTAD }.single().let { setOf(it[Matches.userA], it[Matches.userB]) }
            } shouldBe setOf(plus, llegada)
        }
    }

    test("Postgres: cancelar no crea usage_events; un ticket de más de 24 h vence y se puede reemplazar").config(enabled = TestDatabase.enabled) {
        withDb { db ->
            val w = World(db)
            val ana = db.user("ana")
            val beto = db.user("beto")
            w.service.enqueue(ana, Mode.AMISTAD)
            w.service.cancel(ana)
            transaction(db) { UsageEvents.selectAll().count() shouldBe 0L }
            w.service.enqueue(ana, Mode.AMISTAD)
            w.clock.now = w.clock.now.plus(Duration.ofHours(24)).plusSeconds(1)
            w.service.ticket(ana).status shouldBe TicketStatus.EXPIRED
            w.mirror.last("uid-ana")!!.status shouldBe TicketStatus.EXPIRED
            transaction(db) { UsageEvents.selectAll().count() shouldBe 0L }
            // Volver a entrar reemplaza el ticket vencido, sin chocar con la clave primaria.
            w.service.enqueue(beto, Mode.AMISTAD).status shouldBe TicketStatus.WAITING
            w.service.enqueue(ana, Mode.AMISTAD).status shouldBe TicketStatus.MATCHED
        }
    }

    test("Postgres: elección SIMULATION crea una simulación RANDOM sin cupo semanal; SCENARIO crea el chat con escenario del modo").config(enabled = TestDatabase.enabled) {
        withDb { db ->
            val w = World(db, flags = MatchingFlags(demoUsersEnabled = true, scenarioModeEnabled = true))
            val ana = db.user("ana")
            val beto = db.user("beto")
            w.service.enqueue(ana, Mode.AMISTAD)
            val matchId = w.service.enqueue(beto, Mode.AMISTAD).matchId!!
            w.service.choose(ana, matchId, MatchChoice.SCENARIO)
            w.service.choose(beto, matchId, MatchChoice.SCENARIO).status shouldBe MatchStatus.SCENARIO
            transaction(db) {
                val chat = Chats.selectAll().single()
                chat[Chats.type] shouldBe ChatType.SCENARIO
                chat[Chats.mode] shouldBe Mode.AMISTAD
                chat[Chats.scenarioId].shouldNotBeNull()
                chat[Chats.endsAt].shouldNotBeNull()
                ChatMembers.selectAll().map { it[ChatMembers.userId] } shouldContainExactlyInAnyOrder listOf(ana, beto)
                Matches.selectAll().single()[Matches.chatId] shouldBe chat[Chats.id]
            }

            val caro = db.user("caro")
            val dani = db.user("dani")
            w.service.enqueue(caro, Mode.ESTUDIO)
            val second = w.service.enqueue(dani, Mode.ESTUDIO).matchId!!
            w.service.choose(caro, second, MatchChoice.SIMULATION).status shouldBe MatchStatus.SIMULATION
            transaction(db) {
                val sim = Simulations.selectAll().single()
                sim[Simulations.origin] shouldBe SimulationOrigin.RANDOM
                Matches.selectAll().where { Matches.id eq UUID.fromString(second) }.single()[Matches.simulationId] shouldBe sim[Simulations.id]
                UsageEvents.selectAll().count { it[UsageEvents.kind] == me.santiagoreina.parcheu.contract.QuotaKind.SIMULATION } shouldBe 0
            }
        }
    }

    test("Postgres: un bloqueo posterior al match impide el chat SCENARIO y cierra el match; la versión del ticket crece").config(enabled = TestDatabase.enabled) {
        withDb { db ->
            val w = World(db, flags = MatchingFlags(demoUsersEnabled = true, scenarioModeEnabled = true))
            val ana = db.user("ana")
            val beto = db.user("beto")
            w.service.enqueue(ana, Mode.AMISTAD)
            val waitingVersion = transaction(db) { MatchQueue.selectAll().where { MatchQueue.userId eq ana }.single()[MatchQueue.version] }
            val matchId = w.service.enqueue(beto, Mode.AMISTAD).matchId!!
            transaction(db) { MatchQueue.selectAll().where { MatchQueue.userId eq ana }.single()[MatchQueue.version] } shouldBeGreaterThan waitingVersion
            w.service.choose(ana, matchId, MatchChoice.SCENARIO)
            transaction(db) {
                Blocks.insert {
                    it[blockerId] = ana
                    it[blockedId] = beto
                    it[createdAt] = OffsetDateTime.now()
                }
            }
            shouldThrow<NotFound> { w.service.choose(beto, matchId, MatchChoice.SCENARIO) }
            transaction(db) {
                Chats.selectAll().count() shouldBe 0L
                Matches.selectAll().single()[Matches.status] shouldBe MatchStatus.EXPIRED
            }
            // Y el repositorio, dentro de su transacción, tampoco crea el chat si el bloqueo llega tarde.
            val repo = ExposedMatchingRepository(db)
            transaction(db) { Matches.update({ Matches.id eq UUID.fromString(matchId) }) { it[status] = MatchStatus.SCENARIO } }
            repo.createScenarioChat(UUID.fromString(matchId), java.time.Instant.now(), allowPremium = false) shouldBe ScenarioChatOutcome.Unavailable(setOf(ana))
            transaction(db) { Chats.selectAll().count() shouldBe 0L }
        }
    }

    test("Postgres: sin elección el match vence a los 10 minutos y elegir tarde es 409").config(enabled = TestDatabase.enabled) {
        withDb { db ->
            val w = World(db)
            val ana = db.user("ana")
            val beto = db.user("beto")
            w.service.enqueue(ana, Mode.AMISTAD)
            val matchId = w.service.enqueue(beto, Mode.AMISTAD).matchId!!
            w.clock.now = w.clock.now.plus(MatchingRules.DECIDE_WINDOW).plusSeconds(1)
            runCatching { runBlocking { w.service.choose(ana, matchId, MatchChoice.SIMULATION) } }.exceptionOrNull()
                .shouldNotBeNull().javaClass.simpleName shouldBe "Conflict"
            w.service.match(ana, matchId).status shouldBe MatchStatus.EXPIRED
        }
    }
})
