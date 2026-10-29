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
import me.santiagoreina.parcheu.api.platform.db.tables.DailyCounters
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.api.platform.errors.QuotaExceeded
import me.santiagoreina.parcheu.contract.ErrorCode
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.time.Instant as KInstant

/** B-M1: topes diarios internos (reenvíos del gemelo y moderaciones) por persona y día de Bogotá. */
class DailyLimiterTest : FunSpec({
    val ana = UUID.fromString("00000000-0000-0000-0000-00000000000a")
    val beto = UUID.fromString("00000000-0000-0000-0000-00000000000b")

    fun setup(start: String, limits: QuotaLimits = QuotaLimits()): Triple<DailyLimiter, MovableClock, InMemoryDevEntitlementService> {
        val clock = MovableClock(bogota(start))
        val entitlements = InMemoryDevEntitlementService()
        return Triple(DailyLimiter(InMemoryDailyCounterRepository(), entitlements, limits, clock), clock, entitlements)
    }

    test("gratis: el 11.º envío de respuestas del día da 402 QUOTA_EXCEEDED sin kind y con resetsAt a medianoche de Bogotá") {
        val (daily, _) = setup("2026-10-28T15:00-05:00")
        repeat(10) { daily.consume(ana, DailyKind.TWIN_ANSWERS) }
        val e = shouldThrow<QuotaExceeded> { daily.consume(ana, DailyKind.TWIN_ANSWERS) }
        e.code shouldBe ErrorCode.QUOTA_EXCEEDED
        e.code.httpStatus shouldBe 402
        // Sin kind: no es un cupo del contrato, así que la app no abre la hoja G2 de otro cupo.
        e.kind shouldBe null
        e.resetsAt shouldBe KInstant.parse("2026-10-29T00:00:00-05:00")
        // El tope es por persona.
        daily.consume(beto, DailyKind.TWIN_ANSWERS)
    }

    test("Plus tiene 30 envíos al día") {
        val (daily, clock, entitlements) = setup("2026-10-28T15:00-05:00")
        entitlements.grantPlus(ana, 30, clock.instant())
        repeat(30) { daily.consume(ana, DailyKind.TWIN_ANSWERS) }
        shouldThrow<QuotaExceeded> { daily.consume(ana, DailyKind.TWIN_ANSWERS) }
    }

    test("el tope se reinicia a las 00:00 de Bogotá, no a medianoche UTC") {
        val (daily, clock) = setup("2026-10-28T18:59-05:00") // 23:59 UTC
        repeat(10) { daily.consume(ana, DailyKind.TWIN_ANSWERS) }
        clock.now = bogota("2026-10-28T19:30-05:00") // ya es 29 en UTC, sigue siendo 28 en Bogotá
        daily.tryConsume(ana, DailyKind.TWIN_ANSWERS) shouldBe false
        clock.now = bogota("2026-10-29T00:00-05:00")
        daily.tryConsume(ana, DailyKind.TWIN_ANSWERS) shouldBe true
    }

    test("moderación: mismo tope para ambos planes y contadores separados por tipo") {
        val (daily, clock, entitlements) = setup("2026-10-28T15:00-05:00", QuotaLimits(dailyModerations = 3))
        entitlements.grantPlus(beto, 30, clock.instant())
        repeat(3) { daily.tryConsume(ana, DailyKind.MODERATION) shouldBe true }
        daily.tryConsume(ana, DailyKind.MODERATION) shouldBe false
        repeat(3) { daily.tryConsume(beto, DailyKind.MODERATION) shouldBe true }
        daily.tryConsume(beto, DailyKind.MODERATION) shouldBe false
        daily.tryConsume(ana, DailyKind.TWIN_ANSWERS) shouldBe true
    }

    test("un tope en 0 lo bloquea todo; las variables de entorno se leen y validan") {
        val (daily, _) = setup("2026-10-28T15:00-05:00", QuotaLimits(freeDailyTwinAnswers = 0))
        daily.tryConsume(ana, DailyKind.TWIN_ANSWERS) shouldBe false
        val limits = QuotaLimits.fromEnv(
            mapOf("QUOTA_FREE_DAILY_TWIN_ANSWERS" to "4", "QUOTA_PLUS_DAILY_TWIN_ANSWERS" to "8", "MODERATION_DAILY_PER_USER" to "50"),
        )
        limits.freeDailyTwinAnswers shouldBe 4
        limits.plusDailyTwinAnswers shouldBe 8
        limits.dailyModerations shouldBe 50
        QuotaLimits.fromEnv(emptyMap()).let {
            it.freeDailyTwinAnswers shouldBe 10
            it.plusDailyTwinAnswers shouldBe 30
            it.dailyModerations shouldBe 200
        }
        shouldThrow<IllegalStateException> { QuotaLimits.fromEnv(mapOf("MODERATION_DAILY_PER_USER" to "-1")) }
    }

    test("Postgres: 20 envíos simultáneos con tope 10 cuentan exactamente 10 y el contador es por día")
        .config(enabled = TestDatabase.enabled) {
            TestDatabase.withCleanDatabase { ds ->
                runMigrations(ds)
                val db = connectExposed(ds)
                val user = transaction(db) {
                    val now = OffsetDateTime.parse("2026-10-01T00:00:00-05:00")
                    Users.insert {
                        it[firebaseUid] = "ana"
                        it[createdAt] = now
                        it[updatedAt] = now
                    }[Users.id]
                }
                val clock = MovableClock(bogota("2026-10-28T15:00-05:00"))
                val daily = quotaModule(Env.DEV, db, clock, QuotaLimits()).daily
                val results = runBlocking(Dispatchers.IO) {
                    (1..20).map { async { daily.tryConsume(user, DailyKind.TWIN_ANSWERS) } }.awaitAll()
                }
                results.count { it } shouldBe 10
                shouldThrow<QuotaExceeded> { daily.consume(user, DailyKind.TWIN_ANSWERS) }
                clock.now = bogota("2026-10-29T08:00-05:00")
                daily.tryConsume(user, DailyKind.TWIN_ANSWERS) shouldBe true
                transaction(db) {
                    DailyCounters.selectAll().map { it[DailyCounters.count] }.sorted() shouldBe listOf(1, 10)
                }
            }
        }
})
