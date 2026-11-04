package me.santiagoreina.parcheu.api.ai

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import me.santiagoreina.parcheu.api.platform.BogotaZone
import me.santiagoreina.parcheu.api.platform.errors.AiBudgetExceeded
import me.santiagoreina.parcheu.contract.ErrorCode
import java.math.BigDecimal

/** Criterio de E0.9: guard escalonado (ADR-0010). Presupuesto de prueba: 10 USD = 10.000.000 micros. */
class DailyBudgetGuardTest : FunSpec({
    val config = testAiConfig()
    val budget = 10_000_000L

    fun setup(spentMicros: Long): Triple<FakeLlmClient, InMemoryAiUsageStore, AiGateway> {
        val fake = FakeLlmClient()
        val usage = InMemoryAiUsageStore().apply { seedSpent(AI_DAY, spentMicros) }
        return Triple(fake, usage, testGateway(fake, usage, config))
    }

    test("con 69 % de gasto usa el modelo configurado para el propósito") {
        val (fake, usage, gateway) = setup(budget * 69 / 100)
        gateway.text(AiCall(AiPurpose.SIMULATION_TURN, "hola")).model shouldBe config.models.chat
        gateway.text(AiCall(AiPurpose.SIMULATION_JUDGE, "juzga")).model shouldBe config.models.judge
        fake.calls shouldBe 2
        usage.entries.map { it.model } shouldBe listOf(config.models.chat, config.models.judge)
    }

    test("justo por debajo del 70 % sigue en modo normal") {
        val (_, _, gateway) = setup(budget * 70 / 100 - 1)
        gateway.guard.check("chat", "lite").level shouldBe DailyBudgetGuard.Level.NORMAL
    }

    test("con 70 % de gasto todo pasa al modelo lite") {
        val (fake, usage, gateway) = setup(budget * 70 / 100)
        gateway.text(AiCall(AiPurpose.SIMULATION_TURN, "hola")).model shouldBe config.models.lite
        gateway.json(AiCall(AiPurpose.SIMULATION_JUDGE, "juzga"), JUDGE_SCHEMA).model shouldBe config.models.lite
        fake.lastRequest?.model shouldBe config.models.lite
        usage.entries.map { it.model }.toSet() shouldBe setOf(config.models.lite)
    }

    test("con 99,99 % sigue degradado y no bloquea") {
        val (_, _, gateway) = setup(budget - 1)
        gateway.guard.check("chat", "lite").level shouldBe DailyBudgetGuard.Level.DEGRADED
    }

    test("con 100 % lanza AiBudgetExceeded sin llamar al proveedor ni registrar uso") {
        val (fake, usage, gateway) = setup(budget)
        val e = shouldThrow<AiBudgetExceeded> { gateway.text(AiCall(AiPurpose.PERSONA, "hola")) }
        e.code shouldBe ErrorCode.AI_BUDGET
        e.code.httpStatus shouldBe 503
        // retryAt: medianoche del día siguiente en Bogotá.
        e.retryAt?.epochSeconds shouldBe AI_DAY.plusDays(1).atStartOfDay(BogotaZone).toEpochSecond()
        shouldThrow<AiBudgetExceeded> { gateway.json(AiCall(AiPurpose.SIMULATION_JUDGE, "x"), JUDGE_SCHEMA) }
        fake.calls shouldBe 0
        usage.entries shouldBe emptyList()
    }

    test("el gasto acumulado por las propias llamadas cuenta para el guard") {
        val fake = FakeLlmClient(textResponder = { "x".repeat(4_000) })
        val usage = InMemoryAiUsageStore()
        // 4.000 micros: cabe la reserva de la primera llamada (≈ 2.800 con lite y 1.024 tokens de salida), pero
        // con su costo real (≈ 2.750) ya no cabe la segunda.
        val gateway = AiGateway(fake, testAiConfig(mapOf("AI_DAILY_BUDGET_USD" to "0.004")), usage, AI_CLOCK)
        gateway.text(AiCall(AiPurpose.PERSONA, "hola"))
        shouldThrow<AiBudgetExceeded> { gateway.text(AiCall(AiPurpose.PERSONA, "otra")) }
        fake.calls shouldBe 1
    }

    test("el presupuesto se convierte a micro-USD sin perder decimales") {
        DailyBudgetGuard(InMemoryAiUsageStore(), BigDecimal("2.5"), AI_CLOCK).budgetMicros shouldBe 2_500_000L
    }
})
