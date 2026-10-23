package me.santiagoreina.parcheu.api.social.invites

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.graph.AnonIdSigner
import me.santiagoreina.parcheu.api.graph.ExposedGraphRepository
import me.santiagoreina.parcheu.api.graph.GraphModule
import me.santiagoreina.parcheu.api.platform.db.TestDatabase
import me.santiagoreina.parcheu.api.platform.db.connectExposed
import me.santiagoreina.parcheu.api.platform.db.runMigrations
import me.santiagoreina.parcheu.api.platform.db.tables.Blocks
import me.santiagoreina.parcheu.api.platform.db.tables.Edges
import me.santiagoreina.parcheu.api.platform.db.tables.InviteRedeemAttempts
import me.santiagoreina.parcheu.api.platform.db.tables.InviteRedemptions
import me.santiagoreina.parcheu.api.platform.db.tables.Invites
import me.santiagoreina.parcheu.api.platform.db.tables.UserModes
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.api.platform.errors.Blocked
import me.santiagoreina.parcheu.api.platform.errors.NotFound
import me.santiagoreina.parcheu.api.platform.errors.ValidationFailed
import me.santiagoreina.parcheu.api.platform.notifier.FakeNotifier
import me.santiagoreina.parcheu.api.simulation.ExposedSimulationRepository
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.NotificationKinds
import me.santiagoreina.parcheu.contract.Redeem
import me.santiagoreina.parcheu.contract.RedeemStatus
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Criterios de E6.1 contra Postgres 16 real (migración `invite_redemptions`, unicidad de `invites`,
 * aristas por `EdgeService` y bloqueos de la tabla `blocks`). Necesita Docker o `PARCHEU_TEST_DB_URL`.
 */
class InviteDbTest : FunSpec({
    val now = OffsetDateTime.ofInstant(FIXED_CLOCK.instant(), ZoneOffset.UTC)

    fun Database.user(name: String, modes: Set<Mode> = Mode.entries.toSet()): UUID = transaction(this) {
        val id = Users.insert {
            it[firebaseUid] = "uid-$name-${UUID.randomUUID()}"
            it[displayName] = name
            it[universityId] = 1
            it[createdAt] = now
            it[updatedAt] = now
        }[Users.id]
        modes.forEach { m ->
            UserModes.insert {
                it[userId] = id
                it[mode] = m
                it[enabled] = true
                it[enabledAt] = now
            }
        }
        id
    }

    fun Database.edgeState(a: UUID, b: UUID, mode: Mode): EdgeState? = transaction(this) {
        val (low, high) = Edges.orderedPair(a, b)
        Edges.selectAll().where { (Edges.userLow eq low) and (Edges.userHigh eq high) and (Edges.mode eq mode) }
            .singleOrNull()?.get(Edges.state)
    }

    class Ctx(val db: Database, val service: InviteService, val notifier: FakeNotifier, val repo: ExposedInviteRepository)

    fun withService(block: suspend Ctx.() -> Unit) = TestDatabase.withCleanDatabase { ds ->
        runMigrations(ds)
        val db = connectExposed(ds)
        val graph = GraphModule(ExposedGraphRepository(db), AnonIdSigner.forTests(), FIXED_CLOCK)
        val sims = ExposedSimulationRepository(db)
        val notifier = FakeNotifier()
        val repo = ExposedInviteRepository(db)
        val service = InviteService(
            repo, graph.repository, graph.edges, graph.anonIds,
            { id -> sims.participant(id)?.let { InviteParticipant(it.id, it.activeModes, it.deleted) } },
            notifier, InviteLinks(InviteLinks.LOCAL_BASE), FIXED_CLOCK,
        )
        kotlinx.coroutines.runBlocking { Ctx(db, service, notifier, repo).block() }
    }

    test("el código cumple el formato, es único (pk y user_id unique) y se crea una sola vez aunque haya concurrencia")
        .config(enabled = TestDatabase.enabled) {
            withService {
                val people = List(30) { db.user("Mariana $it") }
                val codes = coroutineScope { people.map { p -> async(Dispatchers.IO) { service.codeFor(p) } }.awaitAll() }
                codes.forEach { it shouldMatch "^[A-Z]{4}-[0-9]{4}$" }
                codes.toSet() shouldHaveSize people.size
                // Muchas peticiones simultáneas de la misma persona: un solo código.
                val solo = db.user("Solo")
                val same = coroutineScope { List(8) { async(Dispatchers.IO) { service.codeFor(solo) } }.awaitAll() }
                same.toSet() shouldHaveSize 1
                transaction(db) { Invites.selectAll().count() } shouldBe (people.size + 1).toLong()
                // La base rechaza repetir código o dar un segundo código a la misma persona.
                repo.claim(codes[0], db.user("Otra")) shouldBe false
                repo.claim("ZZZZ-0001", people[0]) shouldBe false
            }
        }

    test("canje PENDING idempotente con aviso, aceptación CONNECTED, propio código 400, bloqueo 403 e inexistente 404")
        .config(enabled = TestDatabase.enabled) {
            withService {
                val ana = db.user("Ana")
                val beto = db.user("Beto")
                val caro = db.user("Caro")
                val code = service.codeFor(ana)

                shouldThrow<ValidationFailed> { service.redeem(ana, Redeem(code, Mode.AMISTAD)) }

                val first = service.redeem(beto, Redeem(code, Mode.ESTUDIO))
                first.status shouldBe RedeemStatus.PENDING
                service.redeem(beto, Redeem(code, Mode.ESTUDIO)) shouldBe first
                db.edgeState(ana, beto, Mode.ESTUDIO) shouldBe EdgeState.PENDING
                transaction(db) { InviteRedemptions.selectAll().count() } shouldBe 1
                notifier.sent.filter { it.notification.kind == NotificationKinds.INVITE_PENDING }.map { it.userId } shouldBe listOf(ana)

                shouldThrow<NotFound> { service.accept(ana, UUID.randomUUID().toString()) }
                shouldThrow<NotFound> { service.accept(beto, ana.toString()) }
                val pending = service.me(ana).pending.single()
                val accepted = service.accept(ana, pending.id)
                accepted.status shouldBe RedeemStatus.CONNECTED
                accepted.other.id shouldBe beto.toString()
                db.edgeState(ana, beto, Mode.ESTUDIO) shouldBe EdgeState.CONNECTED
                db.edgeState(ana, beto, Mode.AMISTAD) shouldBe null
                service.me(ana).pending.shouldBeEmpty()
                transaction(db) {
                    InviteRedemptions.selectAll().single()[InviteRedemptions.status]
                } shouldBe RedeemStatus.CONNECTED

                transaction(db) {
                    Blocks.insert {
                        it[blockerId] = caro
                        it[blockedId] = ana
                        it[createdAt] = now
                    }
                }
                shouldThrow<Blocked> { service.redeem(caro, Redeem(code, Mode.AMISTAD)) }
                db.edgeState(ana, caro, Mode.AMISTAD) shouldBe null
                shouldThrow<NotFound> { service.redeem(caro, Redeem("ZZZZ-9999", Mode.AMISTAD)) }
            }
        }

    test("aceptar un modo deja los demás canjes de la misma persona PENDING")
        .config(enabled = TestDatabase.enabled) {
            withService {
                val ana = db.user("Ana")
                val beto = db.user("Beto")
                val code = service.codeFor(ana)
                service.redeem(beto, Redeem(code, Mode.AMISTAD))
                service.redeem(beto, Redeem(code, Mode.PAREJA))
                val amistad = service.me(ana).pending.single { it.mode == Mode.AMISTAD }
                shouldThrow<ValidationFailed> { service.accept(ana, beto.toString()) }
                service.accept(ana, amistad.id).status shouldBe RedeemStatus.CONNECTED
                db.edgeState(ana, beto, Mode.AMISTAD) shouldBe EdgeState.CONNECTED
                db.edgeState(ana, beto, Mode.PAREJA) shouldBe EdgeState.PENDING
                service.me(ana).pending.single().mode shouldBe Mode.PAREJA
            }
        }

    test("un canje cuyo modo se apagó después (user_modes) no se muestra ni se acepta, y se descarta")
        .config(enabled = TestDatabase.enabled) {
            withService {
                val ana = db.user("Ana")
                val beto = db.user("Beto")
                val code = service.codeFor(ana)
                service.redeem(beto, Redeem(code, Mode.PAREJA))
                val pareja = service.me(ana).pending.single()
                db.edgeState(ana, beto, Mode.PAREJA) shouldBe EdgeState.PENDING

                // Beto apaga Pareja como lo hace PUT /v1/me/modes.
                transaction(db) {
                    UserModes.update({ (UserModes.userId eq beto) and (UserModes.mode eq Mode.PAREJA) }) { it[enabled] = false }
                }
                service.me(ana).pending.shouldBeEmpty()
                shouldThrow<NotFound> { service.accept(ana, pareja.id) }
                db.edgeState(ana, beto, Mode.PAREJA) shouldBe null
                transaction(db) { InviteRedemptions.selectAll().count() } shouldBe 0
                notifier.sent.filter { it.notification.kind == NotificationKinds.NEW_CONNECTION }.shouldBeEmpty()
            }
        }

    test("el límite diario de canjes es atómico: intentos simultáneos nunca pasan de MAX_REDEEMS_PER_DAY")
        .config(enabled = TestDatabase.enabled) {
            withService {
                val beto = db.user("Beto")
                val since = FIXED_CLOCK.instant().minus(InviteService.ATTEMPT_WINDOW)
                val limit = InviteService.MAX_REDEEMS_PER_DAY
                val results = coroutineScope {
                    List(limit + 10) { async(Dispatchers.IO) { repo.tryRecordAttempt(beto, since, limit, FIXED_CLOCK.instant()) } }.awaitAll()
                }
                results.count { it } shouldBe limit
                transaction(db) { InviteRedeemAttempts.selectAll().count() } shouldBe limit.toLong()
                // Al día siguiente los viejos se borran y vuelve a haber cupo.
                val tomorrow = FIXED_CLOCK.instant().plus(InviteService.ATTEMPT_WINDOW)
                repo.tryRecordAttempt(beto, tomorrow.minus(InviteService.ATTEMPT_WINDOW), limit, tomorrow) shouldBe true
                transaction(db) { InviteRedeemAttempts.selectAll().count() } shouldBe 1
                transaction(db) { Users.deleteWhere { Users.id eq beto } }
                transaction(db) { InviteRedeemAttempts.selectAll().count() } shouldBe 0
            }
        }

    test("al purgar la cuenta su código y sus canjes se van (on delete cascade)")
        .config(enabled = TestDatabase.enabled) {
            withService {
                val ana = db.user("Ana")
                val beto = db.user("Beto")
                service.redeem(beto, Redeem(service.codeFor(ana), Mode.AMISTAD))
                transaction(db) { Users.deleteWhere { Users.id eq beto } }
                transaction(db) { InviteRedemptions.selectAll().count() } shouldBe 0
                transaction(db) { Users.deleteWhere { Users.id eq ana } }
                transaction(db) { Invites.selectAll().count() } shouldBe 0
            }
        }
})
