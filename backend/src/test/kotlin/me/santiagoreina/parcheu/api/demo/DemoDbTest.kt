package me.santiagoreina.parcheu.api.demo

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import me.santiagoreina.parcheu.api.ai.testGateway
import me.santiagoreina.parcheu.api.chat.ChatModule
import me.santiagoreina.parcheu.api.chat.ExposedChatRepository
import me.santiagoreina.parcheu.api.chat.InMemoryChatMirror
import me.santiagoreina.parcheu.api.graph.AnonIdSigner
import me.santiagoreina.parcheu.api.graph.EdgeEvent
import me.santiagoreina.parcheu.api.graph.EdgeSource
import me.santiagoreina.parcheu.api.graph.ExposedGraphRepository
import me.santiagoreina.parcheu.api.graph.GraphModule
import me.santiagoreina.parcheu.api.identity.ExposedIdentityRepository
import me.santiagoreina.parcheu.api.matching.MatchingFlags
import me.santiagoreina.parcheu.api.matching.matchingModule
import me.santiagoreina.parcheu.api.platform.config.Env
import me.santiagoreina.parcheu.api.platform.db.TestDatabase
import me.santiagoreina.parcheu.api.platform.db.connectExposed
import me.santiagoreina.parcheu.api.platform.db.runMigrations
import me.santiagoreina.parcheu.api.platform.db.tables.MatchQueue
import me.santiagoreina.parcheu.api.platform.db.tables.Matches
import me.santiagoreina.parcheu.api.platform.db.tables.TwinAnswers
import me.santiagoreina.parcheu.api.platform.db.tables.TwinMemories
import me.santiagoreina.parcheu.api.platform.db.tables.Twins
import me.santiagoreina.parcheu.api.platform.db.tables.UserModes
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.api.platform.notifier.FakeNotifier
import me.santiagoreina.parcheu.api.platform.notifier.NoopNotifier
import me.santiagoreina.parcheu.api.platform.tasks.FakeTaskQueue
import me.santiagoreina.parcheu.api.quota.MovableClock
import me.santiagoreina.parcheu.api.quota.quotaModule
import me.santiagoreina.parcheu.api.simulation.NoopSimulationNotifier
import me.santiagoreina.parcheu.api.simulation.simulationModule
import me.santiagoreina.parcheu.contract.MatchChoice
import me.santiagoreina.parcheu.contract.MatchStatus
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.SendMessage
import me.santiagoreina.parcheu.contract.TicketStatus
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/**
 * `seed-demo` y `purge-demo` contra Postgres 16 real (esquema y semillas de Flyway). Necesita Docker o
 * `PARCHEU_TEST_DB_URL`; con `-PsinContenedores` se omite (ver [TestDatabase]).
 */
class DemoDbTest : FunSpec({
    /** Lunes 26 oct 2026, 10:00 en Bogotá. */
    val start: Instant = Instant.parse("2026-10-26T15:00:00Z")
    val enabled = DemoSettings(enabled = true)
    val disabled = DemoSettings(enabled = false)

    fun withDb(block: suspend (Database) -> Unit) = TestDatabase.withCleanDatabase { ds ->
        runMigrations(ds)
        runBlocking { block(connectExposed(ds)) }
    }

    fun Database.human(name: String): UUID = transaction(this) {
        val at = start.atOffset(ZoneOffset.UTC)
        val id = Users.insert {
            it[firebaseUid] = "uid-$name"
            it[displayName] = "Nombre de $name"
            it[birthDate] = LocalDate.of(2004, 3, 14)
            it[universityId] = 1
            it[interests] = listOf("musica")
            it[ageVerifiedAt] = at
            it[createdAt] = at
            it[updatedAt] = at
        }[Users.id]
        Mode.entries.forEach { m ->
            UserModes.insert {
                it[userId] = id
                it[mode] = m
                it[UserModes.enabled] = true
                it[enabledAt] = at
            }
        }
        Twins.insert {
            it[userId] = id
            it[traits] = List(6) { 0.5f }
            it[personaSummary] = "Persona de $name"
            it[personaReady] = true
            it[updatedAt] = at
        }
        id
    }

    class World(val db: Database, flags: MatchingFlags, val clock: MovableClock = MovableClock(Instant.parse("2026-10-26T15:00:00Z"))) {
        val quota = quotaModule(Env.DEV, db, clock)
        val graph = GraphModule(ExposedGraphRepository(db), AnonIdSigner.forTests(), clock)
        val chat = ChatModule(ExposedChatRepository(db), InMemoryChatMirror(), NoopNotifier, graph.policy, clock)
        val simulation = simulationModule(
            Env.DEV, db, clock, testGateway(), quota, graph, FakeTaskQueue(), firebaseProjectId = null,
            notifier = NoopSimulationNotifier, chat = chat, push = NoopNotifier,
        )
        val matching = matchingModule(Env.DEV, db, clock, quota, simulation, graph, FakeNotifier(), null, flags).service
    }

    fun Database.demoIds(): List<UUID> = transaction(this) { Users.selectAll().where { Users.isDemo eq true }.map { it[Users.id] } }

    fun Database.waitingDemoTickets(): List<Short> = transaction(this) {
        MatchQueue.selectAll().where { MatchQueue.status eq TicketStatus.WAITING }
            .filter { it[MatchQueue.userId] in demoIds() }
            .map { it[MatchQueue.priority] }
    }

    test("Postgres: seed-demo es idempotente (dos ejecuciones → 20 demos) y ningún demo tiene Pareja activo").config(enabled = TestDatabase.enabled) {
        withDb { db ->
            val seeder = DemoSeeder(db, enabled)
            seeder.seed(start) shouldBe SeedResult.Seeded(created = 20, updated = 0, waiting = 20)
            seeder.seed(start.plusSeconds(60)) shouldBe SeedResult.Seeded(created = 0, updated = 20, waiting = 20)

            val ids = db.demoIds()
            ids.size shouldBe 20
            transaction(db) {
                Users.selectAll().where { Users.isDemo eq true }.map { it[Users.firebaseUid] } shouldContainExactlyInAnyOrder
                    DemoProfiles.all.map { it.firebaseUid }
                val modes = UserModes.selectAll().where { UserModes.enabled eq true }.filter { it[UserModes.userId] in ids }
                modes.none { it[UserModes.mode] == Mode.PAREJA } shouldBe true
                modes.size shouldBe 40
                Users.selectAll().where { (Users.isDemo eq true) and (Users.parejaConfirmedAt.isNotNull()) }.count() shouldBe 0
                Twins.selectAll().filter { it[Twins.userId] in ids }.count { it[Twins.personaReady] && it[Twins.traits]?.size == 6 } shouldBe 20
                TwinAnswers.selectAll().count { it[TwinAnswers.userId] in ids } shouldBe 20 * DemoProfiles.QUESTIONS
                // Los intereses sembrados existen en el catálogo (users.interests no tiene llave foránea).
                val catalog = mutableSetOf<String>()
                exec("select slug from interests") { rs -> while (rs.next()) catalog += rs.getString(1) }
                Users.selectAll().where { Users.isDemo eq true }.flatMap { it[Users.interests] }.all { it in catalog } shouldBe true
            }
            // Todos esperan en la fila con prioridad -1 (por debajo de cualquier persona real).
            db.waitingDemoTickets().toSet() shouldBe setOf(DemoSeeder.DEMO_PRIORITY)
            db.waitingDemoTickets().size shouldBe 20
        }
    }

    test("Postgres: con DEMO_USERS_ENABLED=false los demo no entran a la fila y nadie se empareja con ellos").config(enabled = TestDatabase.enabled) {
        withDb { db ->
            DemoSeeder(db, disabled).seed(start).shouldBeInstanceOf<SeedResult.Disabled>()
            db.demoIds().shouldBeEmpty()

            // Sembrados con la bandera encendida y luego apagada: sus tickets se vencen.
            DemoSeeder(db, enabled).seed(start)
            DemoSeeder(db, disabled).seed(start.plusSeconds(60)) shouldBe SeedResult.Disabled(expiredTickets = 20)
            db.waitingDemoTickets().shouldBeEmpty()
            DemoSeeder(db, disabled).refresh(start.plusSeconds(120)) shouldBe 0
            db.waitingDemoTickets().shouldBeEmpty()

            // Aunque un ticket demo quedara en WAITING, la fila con la bandera apagada no lo toma.
            DemoSeeder(db, enabled).refresh(start.plusSeconds(180)) shouldBe 20
            val w = World(db, MatchingFlags(demoUsersEnabled = false, scenarioModeEnabled = false))
            val ana = db.human("ana")
            w.matching.enqueue(ana, Mode.AMISTAD).status shouldBe TicketStatus.WAITING
            transaction(db) { Matches.selectAll().count() } shouldBe 0
        }
    }

    test("Postgres: una persona real que espera va antes que los demo; un demo emparejado no vuelve a la fila ese día").config(enabled = TestDatabase.enabled) {
        withDb { db ->
            DemoSeeder(db, enabled).seed(start.minusSeconds(3600))
            val w = World(db, MatchingFlags(demoUsersEnabled = true, scenarioModeEnabled = false))
            val ana = db.human("ana")
            val beto = db.human("beto")
            val caro = db.human("caro")
            // Ana entra primero: la emparejan con un demo (es lo único que hay).
            val anaTicket = w.matching.enqueue(ana, Mode.AMISTAD)
            anaTicket.status shouldBe TicketStatus.MATCHED
            val anaMatch = w.matching.match(ana, anaTicket.matchId.shouldNotBeNull())
            anaMatch.other.isDemo shouldBe true
            // Beto espera en Estudio... y Caro, que llega después, se empareja con Beto y no con un demo.
            w.matching.cancel(beto)
            transaction(db) {
                MatchQueue.insert {
                    it[userId] = beto
                    it[mode] = Mode.ESTUDIO
                    it[priority] = 0
                    it[enqueuedAt] = start.atOffset(ZoneOffset.UTC)
                    it[expiresAt] = start.plus(Duration.ofHours(24)).atOffset(ZoneOffset.UTC)
                }
            }
            val caroTicket = w.matching.enqueue(caro, Mode.ESTUDIO)
            caroTicket.status shouldBe TicketStatus.MATCHED
            w.matching.match(caro, caroTicket.matchId!!).other.isDemo shouldBe false

            // El demo de Ana tiene un match esperando elección y ya gastó su match del día: no vuelve a la fila.
            DemoSeeder(db, enabled).refresh(start.plusSeconds(60)) shouldBe 19
        }
    }

    test("Postgres: tras purge-demo ninguna columna de ninguna tabla referencia a un usuario is_demo").config(enabled = TestDatabase.enabled) {
        withDb { db ->
            DemoSeeder(db, enabled).seed(start.minusSeconds(3600))
            val w = World(db, MatchingFlags(demoUsersEnabled = true, scenarioModeEnabled = false))
            val ana = db.human("ana")
            val anaUser = ExposedIdentityRepository(db).findByFirebaseUid("uid-ana").shouldNotBeNull()

            // Flujo real: Ana se empareja con un demo, elige simulación y se crea la simulación.
            val ticket = w.matching.enqueue(ana, Mode.AMISTAD)
            val matchId = ticket.matchId.shouldNotBeNull()
            w.matching.choose(ana, matchId, MatchChoice.SIMULATION).status shouldBe MatchStatus.SIMULATION
            val (demo, simulationId) = transaction(db) {
                val row = Matches.selectAll().where { Matches.id eq UUID.fromString(matchId) }.single()
                val other = if (row[Matches.userA] == ana) row[Matches.userB] else row[Matches.userA]
                other to row[Matches.simulationId].shouldNotBeNull()
            }
            val otherDemo = db.demoIds().first { it != demo }

            // Lo que queda alrededor: decisiones, chat con mensajes del tester, arista, avisos, reportes, etc.
            w.graph.edges.apply(EdgeEvent.Connected(ana, demo, Mode.AMISTAD, EdgeSource.DECISION))
            val chat = w.chat.service.createDirect(ana, demo, Mode.AMISTAD)
            w.chat.service.send(anaUser, chat.id, SendMessage("ana-1", "hola"))
            transaction(db) {
                listOf(
                    "insert into connection_decisions (simulation_id, user_id, connect, decided_at) values ('$simulationId', '$demo', true, now()), ('$simulationId', '$ana', true, now())",
                    "insert into report_unlocks (user_id, simulation_id) values ('$ana', '$simulationId')",
                    "insert into twin_feedback (user_id, kind, simulation_id, turn_index, rating) values ('$ana', 'RATING', '$simulationId', 0, 1)",
                    "insert into twin_memories (user_id, mode, text, source_chat_id, status) values ('$ana', 'AMISTAD', 'Le gusta el café', '${chat.id}', 'ACCEPTED')",
                    "insert into notifications (user_id, kind, payload) values ('$ana', 'NEW_CONNECTION', '{\"actorId\": \"$demo\", \"route\": \"parcheu://chat/${chat.id}\"}')",
                    "insert into notifications (user_id, kind, payload) values ('$demo', 'MATCH_FOUND', '{}')",
                    "insert into consents (user_id, kind, granted, text_version) values ('$demo', 'TERMS', true, 'v1')",
                    "insert into reports (reporter_id, target_id, chat_id, reason, snapshot) values ('$ana', '$demo', '${chat.id}', 'OTHER', '{\"target\": \"$demo\"}')",
                    "insert into entitlements (user_id, product_id, purchase_token, state) values ('$otherDemo', 'parcheu_plus', 'tok-demo', 'ACTIVE')",
                    "insert into blocks (blocker_id, blocked_id) values ('$ana', '$otherDemo')",
                    "insert into ai_usage (day, purpose, provider, model, user_id) values ('2026-10-26', 'SIMULATION', 'fake', 'fake', '$demo')",
                    "insert into fcm_tokens (token, user_id, updated_at) values ('tok-fcm-demo', '$demo', now())",
                    "insert into invites (code, user_id) values ('DEMO-0001', '$demo')",
                ).forEach { exec(it) }
            }

            val demoIds = db.demoIds()
            val needles = demoIds.map { it.toString() } + DemoProfiles.all.map { it.firebaseUid }
            val result = DemoPurger(db).purge()
            result.users shouldBe 20
            result.chatIds shouldBe listOf(chat.id)
            result.simulationIds shouldBe listOf(simulationId)

            // Todas las columnas de todas las tablas, convertidas a texto: ninguna nombra a un demo.
            val hits = transaction(db) {
                val columns = mutableListOf<Pair<String, String>>()
                exec(
                    """
                    select c.table_name, c.column_name from information_schema.columns c
                    join information_schema.tables t on t.table_schema = c.table_schema and t.table_name = c.table_name
                    where c.table_schema = 'public' and t.table_type = 'BASE TABLE'
                    """.trimIndent(),
                ) { rs -> while (rs.next()) columns += rs.getString(1) to rs.getString(2) }
                columns.size shouldBe columns.toSet().size
                val patterns = needles.joinToString(",") { "'%$it%'" }
                columns.mapNotNull { (table, column) ->
                    var count = 0L
                    exec("select count(*) from \"$table\" where \"$column\"::text like any (array[$patterns])") { rs -> rs.next(); count = rs.getLong(1) }
                    "$table.$column".takeIf { count > 0 }
                }
            }
            hits.shouldBeEmpty()
            db.demoIds().shouldBeEmpty()

            // Lo del tester se queda: su usuario, su gemelo y su memoria (sin el chat de origen).
            transaction(db) {
                Users.selectAll().where { Users.id eq ana }.count() shouldBe 1
                Twins.selectAll().where { Twins.userId eq ana }.count() shouldBe 1
                TwinMemories.selectAll().where { TwinMemories.userId eq ana }.single()[TwinMemories.sourceChatId] shouldBe null
            }

            // Idempotente y reversible: purgar otra vez no hace nada y se puede volver a sembrar.
            DemoPurger(db).purge().users shouldBe 0
            DemoSeeder(db, enabled).seed(start.plus(Duration.ofDays(1))) shouldBe SeedResult.Seeded(created = 20, updated = 0, waiting = 20)
        }
    }
})
