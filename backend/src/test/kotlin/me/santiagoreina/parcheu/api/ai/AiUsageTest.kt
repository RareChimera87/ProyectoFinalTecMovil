package me.santiagoreina.parcheu.api.ai

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** Criterio de E0.9: cada llamada del Fake deja una fila de uso con el costo de la tabla de precios. */
class AiUsageTest : FunSpec({
    val config = testAiConfig()

    test("cada llamada del Fake registra una fila con propósito, modelo, tokens y costo calculado") {
        val fake = FakeLlmClient(textResponder = { "a".repeat(400) }) // 100 tokens de salida
        val usage = InMemoryAiUsageStore()
        val gateway = testGateway(fake, usage, config)
        val user = UUID.fromString("00000000-0000-0000-0000-000000000001")

        val prompt = "b".repeat(2_000) // 500 tokens de entrada
        gateway.text(AiCall(AiPurpose.SIMULATION_TURN, prompt, userId = user))
        gateway.json(AiCall(AiPurpose.SIMULATION_JUDGE, prompt), JUDGE_SCHEMA)
        gateway.text(AiCall(AiPurpose.PERSONA, prompt))

        usage.entries.size shouldBe 3
        val turn = usage.entries[0]
        turn.day shouldBe AI_DAY
        turn.purpose shouldBe AiPurpose.SIMULATION_TURN
        turn.provider shouldBe AiProvider.FAKE
        turn.model shouldBe "gemini-3.8-flash"
        turn.userId shouldBe user
        turn.tokensIn shouldBe 500
        turn.tokensOut shouldBe 100
        // gemini-3.8-flash en `us` hasta el 31-dic-2026: 0,825 entrada y 4,125 salida (USD/1M).
        // 500·0,825 + 100·4,125 = 412,5 + 412,5 = 825 micro-USD.
        turn.costMicros shouldBe 825L

        val persona = usage.entries[2]
        persona.model shouldBe "gemini-3.5-flash-lite"
        // 500·0,33 + 100·2,75 = 165 + 275 = 440.
        persona.costMicros shouldBe 440L

        usage.entries.forEach { it.costMicros shouldBe config.prices.costMicros(it.model, it.day, LlmUsage(it.tokensIn, it.tokensOut)) }
        usage.spentMicros(AI_DAY) shouldBe usage.entries.sumOf { it.costMicros }
    }

    test("una respuesta JSON que no cumple el esquema se registra igual (se cobró) y se relanza") {
        val fake = FakeLlmClient(jsonResponder = { _, _ -> buildJsonObject { put("afinidad", JsonPrimitive("alta")) } })
        val usage = InMemoryAiUsageStore()
        shouldThrow<LlmInvalidOutput> {
            testGateway(fake, usage, config).json(AiCall(AiPurpose.SIMULATION_JUDGE, "x"), JUDGE_SCHEMA)
        }
        usage.entries.size shouldBe 1
    }

    test("el Fake devuelve JSON que cumple el esquema") {
        val result = testGateway().json(AiCall(AiPurpose.SIMULATION_JUDGE, "x"), JUDGE_SCHEMA)
        result.json["afinidad"]!!.jsonPrimitive.int shouldBe 0
        JsonSchemaCheck.errors(result.json, JUDGE_SCHEMA) shouldBe emptyList()
    }

    test("la tabla de precios cambia el 1-ene-2027 (fin del precio introductorio de Flash)") {
        val prices = config.prices
        prices.priceOn("gemini-3.8-flash", LocalDate.of(2026, 12, 31)).inputPerMillionUsd shouldBe BigDecimal("0.825")
        prices.priceOn("gemini-3.8-flash", LocalDate.of(2027, 1, 1)).inputPerMillionUsd shouldBe BigDecimal("1.65")
        prices.costMicros("gemini-3.8-flash", LocalDate.of(2027, 1, 1), LlmUsage(1_000_000, 0)) shouldBe 1_650_000L
    }

    test("el costo se redondea hacia arriba para no subestimar el gasto") {
        // 1 token de entrada de flash-lite = 0,33 micro-USD → 1.
        config.prices.costMicros("gemini-3.5-flash-lite", AI_DAY, LlmUsage(1, 0)) shouldBe 1L
    }

    test("cada ubicación usa sus precios") {
        val global = PriceTable.fromProperties(AiConfig.loadDefaults(), "global")
        global.priceOn("gemini-3.8-flash", AI_DAY).outputPerMillionUsd shouldBe BigDecimal("3.75")
    }
})
