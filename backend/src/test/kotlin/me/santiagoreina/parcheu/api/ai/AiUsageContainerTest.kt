package me.santiagoreina.parcheu.api.ai

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import me.santiagoreina.parcheu.api.platform.db.TestDatabase
import me.santiagoreina.parcheu.api.platform.db.connectExposed
import me.santiagoreina.parcheu.api.platform.db.runMigrations
import me.santiagoreina.parcheu.api.platform.db.tables.AiUsage
import me.santiagoreina.parcheu.api.platform.db.tables.AiUsageDaily
import me.santiagoreina.parcheu.api.platform.errors.AiBudgetExceeded
import me.santiagoreina.parcheu.api.platform.errors.UserAiBudgetExceeded
import me.santiagoreina.parcheu.api.platform.db.tables.AiBudgetGlobal
import me.santiagoreina.parcheu.api.platform.db.tables.AiBudgetUser
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.time.OffsetDateTime
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Criterio de E0.9 contra Postgres real: cada llamada del Fake deja una fila en `ai_usage` con
 * el costo de la tabla de precios y suma en `ai_usage_daily`, que es lo que lee el guard.
 * Necesita Docker o `PARCHEU_TEST_DB_URL` (ver [TestDatabase]); en local sin Docker se omite.
 */
class AiUsageContainerTest : FunSpec({
    test("el Fake registra filas en ai_usage y agrega ai_usage_daily; el guard lee ese agregado")
        .config(enabled = TestDatabase.enabled) {
            TestDatabase.withCleanDatabase { ds ->
                runMigrations(ds)
                val db = connectExposed(ds)
                val store = ExposedAiUsageStore(db)
                val config = testAiConfig()
                val fake = FakeLlmClient(textResponder = { "a".repeat(400) })
                val gateway = AiGateway(fake, config, store, AI_CLOCK)
                val prompt = "b".repeat(2_000)

                runBlocking {
                    gateway.text(AiCall(AiPurpose.SIMULATION_TURN, prompt))
                    gateway.text(AiCall(AiPurpose.SIMULATION_TURN, prompt))
                    gateway.json(AiCall(AiPurpose.PERSONA, prompt), JUDGE_SCHEMA)
                }

                transaction(db) {
                    val rows = AiUsage.selectAll().toList()
                    rows.size shouldBe 3
                    val first = rows.first()
                    first[AiUsage.day] shouldBe AI_DAY
                    first[AiUsage.purpose] shouldBe "simulation_turn"
                    first[AiUsage.provider] shouldBe "fake"
                    first[AiUsage.model] shouldBe "gemini-3.8-flash"
                    first[AiUsage.tokensIn] shouldBe 500
                    first[AiUsage.tokensOut] shouldBe 100
                    first[AiUsage.costMicros] shouldBe 825L

                    val daily = AiUsageDaily.selectAll().associateBy { it[AiUsageDaily.model] }
                    daily.size shouldBe 2
                    val flash = daily.getValue("gemini-3.8-flash")
                    flash[AiUsageDaily.tokensIn] shouldBe 1_000L
                    flash[AiUsageDaily.tokensOut] shouldBe 200L
                    flash[AiUsageDaily.costMicros] shouldBe 1_650L
                    flash[AiUsageDaily.provider] shouldBe "fake"
                }
                val total = rowsCost(db)
                runBlocking { store.spentMicros(AI_DAY) } shouldBe total
                runBlocking { store.spentMicros(AI_DAY.minusDays(1)) } shouldBe 0L

                // Con un presupuesto por debajo del gasto registrado, el guard bloquea sin llamar.
                val calls = fake.calls
                val tight = AiGateway(fake, testAiConfig(mapOf("AI_DAILY_BUDGET_USD" to "0.001")), store, AI_CLOCK)
                shouldThrow<AiBudgetExceeded> { runBlocking { tight.text(AiCall(AiPurpose.PERSONA, "x")) } }
                fake.calls shouldBe calls
            }
        }

    test("Postgres: llamadas simultáneas no pasan el tope total ni el de la persona y la reserva cuadra con ai_usage")
        .config(enabled = TestDatabase.enabled) {
            TestDatabase.withCleanDatabase { ds ->
                runMigrations(ds)
                val db = connectExposed(ds)
                val ana = transaction(db) {
                    val now = OffsetDateTime.parse("2026-10-01T00:00:00-05:00")
                    Users.insert {
                        it[firebaseUid] = "ana"
                        it[createdAt] = now
                        it[updatedAt] = now
                    }[Users.id]
                }
                val config = testAiConfig()
                val call = AiCall(AiPurpose.SIMULATION_TURN, "x".repeat(300), maxOutputTokens = 200)
                val estimate = config.prices.costMicros(config.models.chat, AI_DAY, call.ceilingUsage())
                val inner = ExposedAiUsageStore(db)
                // Gasto previo de Ana registrado antes de que existan las filas de reserva: la semilla debe contarlo.
                runBlocking {
                    inner.record(
                        AiUsageEntry(AI_DAY, AiPurpose.PERSONA, AiProvider.FAKE, config.models.lite, ana, 10, 10, estimate, OffsetDateTime.now(AI_CLOCK)),
                    )
                }
                val store = PeakObservingStore(inner)
                val budget = estimate * 12
                val userBudget = estimate * 5
                val gateway = AiGateway(
                    SlowLlmClient(delayMs = 40),
                    testAiConfig(
                        mapOf(
                            "AI_DAILY_BUDGET_USD" to java.math.BigDecimal.valueOf(budget, 6).toPlainString(),
                            "AI_USER_DAILY_BUDGET_USD" to java.math.BigDecimal.valueOf(userBudget, 6).toPlainString(),
                        ),
                    ),
                    store,
                    AI_CLOCK,
                )
                val outcomes = runBlocking(Dispatchers.IO) {
                    (1..30).map { i ->
                        async {
                            try {
                                gateway.text(call.copy(userId = if (i % 2 == 0) ana else null))
                                "ok"
                            } catch (e: UserAiBudgetExceeded) {
                                "usuario"
                            } catch (e: AiBudgetExceeded) {
                                "total"
                            }
                        }
                    }.awaitAll()
                }
                outcomes.count { it == "usuario" } shouldBeGreaterThan 0
                outcomes.count { it == "total" } shouldBeGreaterThan 0
                store.peakGlobal.get() shouldBeLessThanOrEqual budget
                store.peakUser.get() shouldBeLessThanOrEqual userBudget
                transaction(db) {
                    val all = AiUsage.selectAll().sumOf { it[AiUsage.costMicros] }
                    val anas = AiUsage.selectAll().where { AiUsage.userId eq ana }.sumOf { it[AiUsage.costMicros] }
                    all shouldBeLessThanOrEqual budget
                    anas shouldBeLessThanOrEqual userBudget
                    // Sin llamadas en curso, lo reservado es exactamente lo registrado.
                    AiBudgetGlobal.selectAll().single()[AiBudgetGlobal.micros] shouldBe all
                    AiBudgetUser.selectAll().single()[AiBudgetUser.micros] shouldBe anas
                }
            }
        }
})

private fun rowsCost(db: org.jetbrains.exposed.v1.jdbc.Database): Long =
    transaction(db) { AiUsage.selectAll().sumOf { it[AiUsage.costMicros] } }
