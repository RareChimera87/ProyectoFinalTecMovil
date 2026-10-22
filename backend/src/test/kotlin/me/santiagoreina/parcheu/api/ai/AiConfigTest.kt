package me.santiagoreina.parcheu.api.ai

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import me.santiagoreina.parcheu.api.platform.AppContainer
import me.santiagoreina.parcheu.api.platform.config.AppConfig
import me.santiagoreina.parcheu.api.platform.config.Env
import java.math.BigDecimal

/** Criterio de E0.9: el proveedor se elige por configuración y un valor inválido no arranca. */
class AiConfigTest : FunSpec({
    val base = mapOf("AI_VERTEX_PROJECT" to "parcheu-test")

    test("AI_PROVIDER=claude instancia ClaudeVertexClient con los modelos de Claude") {
        val config = AiConfig.fromEnv(base + ("AI_PROVIDER" to "claude"), Env.DEV)
        config.provider shouldBe AiProvider.CLAUDE
        config.models.lite shouldBe "claude-haiku-5-5"
        AiModule.createClient(config).shouldBeInstanceOf<ClaudeVertexClient>()
    }

    test("AI_PROVIDER=gemini instancia GeminiVertexClient y Gemini es el predeterminado") {
        AiModule.createClient(AiConfig.fromEnv(base + ("AI_PROVIDER" to "gemini"), Env.PROD))
            .shouldBeInstanceOf<GeminiVertexClient>()
        val default = AiConfig.fromEnv(base, Env.DEV)
        default.provider shouldBe AiProvider.GEMINI
        AiModule.createClient(default).shouldBeInstanceOf<GeminiVertexClient>()
    }

    test("los IDs vienen de la configuración y las variables los sobrescriben") {
        val fromFile = AiConfig.fromEnv(base, Env.DEV)
        fromFile.models shouldBe AiModels("gemini-3.8-flash", "gemini-3.8-flash", "gemini-3.5-flash-lite")
        fromFile.vertexLocation shouldBe "us"
        val overridden = AiConfig.fromEnv(
            base + mapOf("AI_MODEL_LITE" to "gemini-3.1-flash-lite", "AI_DAILY_BUDGET_USD" to "7.5"),
            Env.DEV,
        )
        overridden.models.lite shouldBe "gemini-3.1-flash-lite"
        overridden.dailyBudgetUsd shouldBe BigDecimal("7.5")
    }

    test("un AI_PROVIDER inválido falla al arrancar el servicio") {
        val e = shouldThrow<IllegalArgumentException> {
            AiConfig.fromEnv(base + ("AI_PROVIDER" to "openai"), Env.DEV)
        }
        e.message shouldContain "AI_PROVIDER"
        // El mismo error corta AppContainer.fromConfig, que es lo que llama Main en modo serve.
        val appConfig = AppConfig.fromEnv(mapOf("ENV" to "local"))
        shouldThrow<IllegalArgumentException> {
            AppContainer.fromConfig(appConfig, mapOf("ENV" to "local", "AI_PROVIDER" to "chatgpt"))
        }
    }

    test("fake solo se admite con ENV=local y es el predeterminado ahí") {
        shouldThrow<IllegalArgumentException> { AiConfig.fromEnv(base + ("AI_PROVIDER" to "fake"), Env.PROD) }
        AiConfig.fromEnv(emptyMap(), Env.LOCAL).provider shouldBe AiProvider.FAKE
        // E2.1 exige base de datos también en local; el pool es perezoso, así que no se conecta.
        val localDb = mapOf("ENV" to "local", "DB_NAME" to "parcheu", "DB_URL" to "jdbc:postgresql://127.0.0.1:1/parcheu")
        val container = AppContainer.fromConfig(AppConfig.fromEnv(localDb), localDb)
        container.ai.provider shouldBe AiProvider.FAKE
    }

    test("sin proyecto de Vertex, con un modelo sin precio o con presupuesto inválido no arranca") {
        shouldThrow<IllegalArgumentException> { AiConfig.fromEnv(mapOf("AI_PROVIDER" to "gemini"), Env.DEV) }
        shouldThrow<IllegalArgumentException> {
            AiConfig.fromEnv(base + ("AI_MODEL_CHAT" to "gemini-9-ultra"), Env.DEV)
        }.message shouldContain "gemini-9-ultra"
        shouldThrow<IllegalArgumentException> { AiConfig.fromEnv(base + ("AI_DAILY_BUDGET_USD" to "0"), Env.DEV) }
        shouldThrow<IllegalArgumentException> { AiConfig.fromEnv(base + ("AI_DAILY_BUDGET_USD" to "mucho"), Env.DEV) }
    }

    test("GCP_PROJECT sirve de respaldo para el proyecto de Vertex") {
        AiConfig.fromEnv(mapOf("GCP_PROJECT" to "parcheu-dev"), Env.DEV).vertexProject shouldBe "parcheu-dev"
    }

    test("en dev/prod la pasarela exige base de datos para registrar el uso") {
        val appConfig = AppConfig.fromEnv(
            mapOf(
                "ENV" to "dev", "GCP_PROJECT" to "parcheu-dev",
                "INTERNAL_OIDC_AUDIENCE" to "https://api.parcheu-dev.internal",
                "TASKS_SERVICE_ACCOUNT" to "sa-tasks@parcheu-dev.iam.gserviceaccount.com",
            ),
        )
        shouldThrow<IllegalStateException> {
            AppContainer.aiGateway(appConfig, mapOf("GCP_PROJECT" to "parcheu-dev"), AI_CLOCK)
        }.message shouldContain "DB_NAME"
    }
})
