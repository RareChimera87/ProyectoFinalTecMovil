package me.santiagoreina.parcheu.api.platform.tasks

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import me.santiagoreina.parcheu.api.platform.config.AppConfig

class CloudTasksQueueTest : FunSpec({
    val config = AppConfig.fromEnv(mapOf("ENV" to "local"))
    val base = mapOf("SERVICE_URL" to "https://api.example.run.app")
    val full = "projects/parcheu-dev/locations/us-central1/queues/ai-light"

    fun queues(vars: Map<String, String>) = CloudTasksQueue.fromEnv(config, vars).shouldBeInstanceOf<CloudTasksQueue>().queues

    test("nombre corto de la cola se completa con GCP_PROJECT y GCP_REGION") {
        val vars = base + mapOf("TASKS_QUEUE_AI_LIGHT" to "ai-light", "GCP_PROJECT" to "parcheu-dev", "GCP_REGION" to "us-central1")
        queues(vars)[QueueKind.AI_LIGHT] shouldBe full
    }

    test("una ruta completa se respeta tal cual") {
        queues(base + ("TASKS_QUEUE_AI_LIGHT" to full))[QueueKind.AI_LIGHT] shouldBe full
    }

    test("proyecto y región se infieren de CLOUD_SQL_INSTANCE si faltan") {
        val vars = base + mapOf("TASKS_QUEUE_AI_LIGHT" to "ai-light", "CLOUD_SQL_INSTANCE" to "parcheu-dev:us-central1:db")
        queues(vars)[QueueKind.AI_LIGHT] shouldBe full
    }

    test("nombre corto sin proyecto ni región deja la cola sin configurar") {
        CloudTasksQueue.fromEnv(config, base + ("TASKS_QUEUE_AI_LIGHT" to "ai-light")).shouldBeInstanceOf<UnconfiguredTaskQueue>()
    }
})
