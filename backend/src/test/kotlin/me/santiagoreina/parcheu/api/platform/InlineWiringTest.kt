package me.santiagoreina.parcheu.api.platform

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldBeInstanceOf
import me.santiagoreina.parcheu.api.identity.deletion.AccountDeletionService
import me.santiagoreina.parcheu.api.platform.auth.FirebaseTokenVerifier
import me.santiagoreina.parcheu.api.platform.auth.GoogleOidcVerifier
import me.santiagoreina.parcheu.api.platform.config.AppConfig
import me.santiagoreina.parcheu.api.safety.moderation.ModerationService
import me.santiagoreina.parcheu.api.platform.jobs.InProcessScheduler
import me.santiagoreina.parcheu.api.scenario.ScenarioRoutes
import me.santiagoreina.parcheu.api.scenario.ScenarioService
import me.santiagoreina.parcheu.api.simulation.SimulationService
import me.santiagoreina.parcheu.api.twin.TwinService
import me.santiagoreina.parcheu.api.twin.memories.MemoryService
import java.time.Duration

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
                ScenarioService.JUDGE_PATH,
                MemoryService.TASK_PATH, ModerationService.TASK_PATH,
            )
            // E4.8: rutas, gancho del chat y juez comparten el módulo real, que encola en la cola en proceso
            // (no en la FakeTaskQueue del valor por defecto, que ejecutaría el juez dentro de la petición HTTP).
            container.scenario.service.tasks shouldBeSameInstanceAs background.queue
            container.chat.service.scenarioEnded shouldBeSameInstanceAs container.scenario.service.endedHook
            container.domains.filterIsInstance<ScenarioRoutes>().size shouldBe 1
        } finally {
            container.background?.close()
        }
    }

    test("TASKS_MODE=inline programa los jobs memories y retention a su hora local (sin lanzar bucles)") {
        val vars = devVars + ("TASKS_MODE" to "inline")
        val container = AppContainer.fromConfig(AppConfig.fromEnv(vars), vars)
        try {
            val initialDelays = mutableMapOf<String, Duration>()
            val recorder = object : InProcessScheduler() {
                override fun every(name: String, period: Duration, initialDelay: Duration, block: suspend () -> Unit) {
                    initialDelays[name] = initialDelay
                }
            }
            container.background.shouldNotBeNull().scheduleOn(recorder)
            recorder.close()
            initialDelays.keys.shouldContainAll("memories", "retention", "purge-sweep", "match-sweep", "twin-accuracy")
            // Cada job diario queda anclado a su hora: un cambio de reloj o de constante rompe este test.
            // El reloj es el del sistema: se tolera el segundo que pasa entre las dos lecturas.
            listOf("retention" to AppContainer.RETENTION_AT, "memories" to AppContainer.MEMORIES_AT).forEach { (job, at) ->
                val expected = InProcessScheduler.delayUntil(at, container.clock)
                (initialDelays.getValue(job) - expected).abs().seconds shouldBeLessThan 5L
            }
            // E9.3: `twin-accuracy` queda anclado a los domingos a las 23:00 de Bogotá.
            val weekly = InProcessScheduler.delayUntilWeekly(AppContainer.TWIN_ACCURACY_DAY, AppContainer.TWIN_ACCURACY_AT, container.clock)
            (initialDelays.getValue("twin-accuracy") - weekly).abs().seconds shouldBeLessThan 5L
        } finally {
            container.background?.close()
        }
    }

    test("sin TASKS_MODE=inline no hay trabajo en segundo plano") {
        val vars = mapOf("ENV" to "local", "DB_NAME" to "parcheu", "DB_URL" to "jdbc:postgresql://localhost:1/parcheu")
        AppContainer.fromConfig(AppConfig.fromEnv(vars), vars).background.shouldBeNull()
    }
})
