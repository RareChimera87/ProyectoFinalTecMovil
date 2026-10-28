package me.santiagoreina.parcheu.api.platform

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import me.santiagoreina.parcheu.api.identity.deletion.AccountDeletionService
import me.santiagoreina.parcheu.api.platform.auth.FirebaseTokenVerifier
import me.santiagoreina.parcheu.api.platform.auth.GoogleOidcVerifier
import me.santiagoreina.parcheu.api.platform.config.AppConfig
import me.santiagoreina.parcheu.api.safety.moderation.ModerationService
import me.santiagoreina.parcheu.api.simulation.SimulationService
import me.santiagoreina.parcheu.api.twin.TwinService
import me.santiagoreina.parcheu.api.twin.memories.MemoryService

class InlineWiringTest : FunSpec({
    val devVars = mapOf(
        "ENV" to "dev", "INTERNAL_OIDC_AUDIENCE" to "https://api.example.org", "AI_VERTEX_PROJECT" to "parcheu-dev", "FIREBASE_PROJECT_ID" to "parcheu-dev",
        "TASKS_SERVICE_ACCOUNT" to "sa-tasks@p.iam.gserviceaccount.com",
        "DB_NAME" to "parcheu", "DB_URL" to "jdbc:postgresql://localhost:1/parcheu",
    )

    test("ENV=dev + TASKS_MODE=inline: verificadores reales y los 5 handlers registrados, sin arrancar bucles") {
        val vars = devVars + ("TASKS_MODE" to "inline")
        val container = AppContainer.fromConfig(AppConfig.fromEnv(vars), vars)
        try {
            container.tokenVerifier.shouldBeInstanceOf<FirebaseTokenVerifier>()
            container.oidcVerifier.shouldBeInstanceOf<GoogleOidcVerifier>()
            val background = container.background.shouldNotBeNull()
            // Cobertura: exactamente las tareas reales de /internal/tasks (el resto son stubs 501).
            background.queue.registeredPaths shouldBe setOf(
                TwinService.BUILD_PERSONA_PATH, SimulationService.SIMULATE_PATH, AccountDeletionService.PURGE_PATH,
                MemoryService.TASK_PATH, ModerationService.TASK_PATH,
            )
        } finally {
            container.background?.close()
        }
    }

    test("sin TASKS_MODE=inline no hay trabajo en segundo plano") {
        val vars = mapOf("ENV" to "local", "DB_NAME" to "parcheu", "DB_URL" to "jdbc:postgresql://localhost:1/parcheu")
        AppContainer.fromConfig(AppConfig.fromEnv(vars), vars).background.shouldBeNull()
    }
})
