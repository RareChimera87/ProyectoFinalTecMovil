package me.santiagoreina.parcheu.api.ai

import me.santiagoreina.parcheu.api.platform.BogotaZone
import me.santiagoreina.parcheu.api.platform.config.Env
import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime

/** 25-oct-2026 10:00 en Bogotá. */
val AI_CLOCK: Clock = Clock.fixed(ZonedDateTime.of(2026, 10, 25, 10, 0, 0, 0, BogotaZone).toInstant(), BogotaZone)
val AI_DAY: LocalDate = LocalDate.of(2026, 10, 25)

/** Config de tests: Gemini con los modelos del archivo y presupuesto de 10 USD (10.000.000 micros). */
fun testAiConfig(extra: Map<String, String> = emptyMap()): AiConfig = AiConfig.fromEnv(
    mapOf("AI_PROVIDER" to "gemini", "AI_VERTEX_PROJECT" to "parcheu-test", "AI_DAILY_BUDGET_USD" to "10") + extra,
    Env.DEV,
)

/** Pasarela con Fake: el proveedor real no se toca, pero modelos y precios son los configurados. */
fun testGateway(
    fake: FakeLlmClient = FakeLlmClient(),
    usage: AiUsageStore = InMemoryAiUsageStore(),
    config: AiConfig = testAiConfig(),
): AiGateway = AiGateway(fake, config, usage, AI_CLOCK)
