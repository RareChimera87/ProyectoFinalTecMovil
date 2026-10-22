package me.santiagoreina.parcheu.api.quota

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import me.santiagoreina.parcheu.api.platform.config.Env
import me.santiagoreina.parcheu.api.platform.db.TestDatabase
import me.santiagoreina.parcheu.api.platform.db.connectExposed
import me.santiagoreina.parcheu.api.platform.db.runMigrations
import me.santiagoreina.parcheu.api.platform.db.tables.Entitlements
import me.santiagoreina.parcheu.api.platform.db.tables.UsageEvents
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.api.platform.errors.NotFound
import me.santiagoreina.parcheu.api.platform.errors.QuotaExceeded
import me.santiagoreina.parcheu.contract.QuotaKind
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.time.Instant as KInstant

/**
 * Las mismas reglas contra Postgres 16 (`usage_events` y `entitlements` reales). Necesita Docker
 * o `PARCHEU_TEST_DB_URL`: con `-PsinContenedores` se omiten (ver [TestDatabase]).
 */
class QuotaDbTest : FunSpec({
    fun newUser(db: Database, uid: String): UUID = transaction(db) {
        val now = OffsetDateTime.parse("2026-10-01T00:00:00-05:00")
        Users.insert {
            it[firebaseUid] = uid
            it[createdAt] = now
            it[updatedAt] = now
        }[Users.id]
    }

    fun withQuota(env: Env, start: String, block: (QuotaModule, MovableClock, Database) -> Unit) =
        TestDatabase.withCleanDatabase { ds ->
            runMigrations(ds)
            val db = connectExposed(ds)
            val clock = MovableClock(bogota(start))
            block(quotaModule(env, db, clock, QuotaLimits(plusDailySimulations = 7)), clock, db)
        }

    test("Postgres: la 6.a simulación de la semana da 402 con resetsAt el lunes; devolver restaura el cupo; el ref_id no cobra dos veces")
        .config(enabled = TestDatabase.enabled) {
            withQuota(Env.DEV, "2026-10-28T15:00-05:00") { quota, _, db ->
                val ana = newUser(db, "ana")
                val users = quota.users
                users.userIdFor("ana") shouldBe ana
                users.userIdFor("nadie") shouldBe null
                val service = quota.service
                repeat(5) { service.consume(ana, QuotaKind.SIMULATION, "sim-$it") shouldBe QuotaService.Outcome.CHARGED }
                service.consume(ana, QuotaKind.SIMULATION, "sim-0") shouldBe QuotaService.Outcome.ALREADY_CHARGED
                val e = shouldThrow<QuotaExceeded> { service.consume(ana, QuotaKind.SIMULATION, "sim-5") }
                e.resetsAt shouldBe KInstant.parse("2026-11-02T00:00:00-05:00")
                transaction(db) { UsageEvents.selectAll().count() } shouldBe 5L

                service.refund(QuotaKind.SIMULATION, "sim-2") shouldBe true
                service.status(ana).simulations.used shouldBe 4
                service.consume(ana, QuotaKind.SIMULATION, "sim-2") shouldBe QuotaService.Outcome.ALREADY_CHARGED
                service.consume(ana, QuotaKind.SIMULATION, "sim-5") shouldBe QuotaService.Outcome.CHARGED
                transaction(db) {
                    UsageEvents.selectAll().where { UsageEvents.refId eq "sim-2" }.single()[UsageEvents.refunded]
                } shouldBe true
            }
        }

    test("Postgres: 10 cobros simultáneos del mismo usuario dejan exactamente 5")
        .config(enabled = TestDatabase.enabled) {
            withQuota(Env.DEV, "2026-10-28T15:00-05:00") { quota, _, db ->
                val ana = newUser(db, "ana")
                val results = runBlocking {
                    (0 until 10).map { i ->
                        async(Dispatchers.IO) { runCatching { quota.service.consume(ana, QuotaKind.SIMULATION, "par-$i") } }
                    }.awaitAll()
                }
                results.count { it.isSuccess } shouldBe 5
                results.count { it.exceptionOrNull() is QuotaExceeded } shouldBe 5
                transaction(db) { UsageEvents.selectAll().count() } shouldBe 5L
            }
        }

    test("Postgres: grant-plus en dev da uso justo diario sin límite semanal; al vencer vuelve el gratis")
        .config(enabled = TestDatabase.enabled) {
            withQuota(Env.DEV, "2026-10-26T08:00-05:00") { quota, clock, db ->
                val ana = newUser(db, "ana")
                quota.devPlus!!.grantPlus(ana, 2, clock.instant())
                transaction(db) { Entitlements.selectAll().single()[Entitlements.productId] } shouldBe DEV_GRANT_PRODUCT
                repeat(7) { quota.service.consume(ana, QuotaKind.SIMULATION, "lun-$it") }
                val e = shouldThrow<QuotaExceeded> { quota.service.consume(ana, QuotaKind.SIMULATION, "lun-7") }
                e.resetsAt shouldBe KInstant.parse("2026-10-27T00:00:00-05:00")
                quota.service.status(ana).plus shouldBe true
                // Vence a las 08:00 del miércoles; después vuelve el límite semanal (ya van 7).
                clock.now = bogota("2026-10-28T09:00-05:00")
                quota.service.status(ana).plus shouldBe false
                shouldThrow<QuotaExceeded> { quota.service.consume(ana, QuotaKind.SIMULATION, "mie-0") }
                shouldThrow<NotFound> { quota.devPlus!!.grantPlus(UUID.randomUUID(), 2, clock.instant()) }
            }
        }

    test("Postgres: el informe mensual se reinicia el día 1 a las 00:00 de Bogotá y prod no tiene grant-plus")
        .config(enabled = TestDatabase.enabled) {
            withQuota(Env.PROD, "2026-10-31T20:00-05:00") { quota, clock, db ->
                quota.devPlus shouldBe null
                val ana = newUser(db, "ana")
                quota.service.consume(ana, QuotaKind.FULL_REPORT, "inf-1")
                shouldThrow<QuotaExceeded> { quota.service.consume(ana, QuotaKind.FULL_REPORT, "inf-2") }
                clock.now = bogota("2026-11-01T00:00-05:00")
                quota.service.consume(ana, QuotaKind.FULL_REPORT, "inf-2") shouldBe QuotaService.Outcome.CHARGED
                // Un usuario borrado ya no resuelve su UID.
                transaction(db) { Users.update({ Users.id eq ana }) { it[deletedAt] = OffsetDateTime.now() } }
                quota.users.userIdFor("ana") shouldBe null
            }
        }
})
