package me.santiagoreina.parcheu.api.platform.tasks

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import me.santiagoreina.parcheu.api.platform.config.Env

class TasksModeTest : FunSpec({
    test("vacío: FAKE en local y CLOUD en el resto") {
        TasksMode.fromEnv(Env.LOCAL, emptyMap()) shouldBe TasksMode.FAKE
        TasksMode.fromEnv(Env.DEV, emptyMap()) shouldBe TasksMode.CLOUD
        TasksMode.fromEnv(Env.PROD, mapOf("TASKS_MODE" to " ")) shouldBe TasksMode.CLOUD
    }

    test("inline se admite en dev y local sin Cloud Run") {
        TasksMode.fromEnv(Env.DEV, mapOf("TASKS_MODE" to "inline")) shouldBe TasksMode.INLINE
        TasksMode.fromEnv(Env.LOCAL, mapOf("TASKS_MODE" to "INLINE")) shouldBe TasksMode.INLINE
    }

    test("inline falla en prod y en Cloud Run") {
        shouldThrow<IllegalArgumentException> { TasksMode.fromEnv(Env.PROD, mapOf("TASKS_MODE" to "inline")) }
        shouldThrow<IllegalArgumentException> { TasksMode.fromEnv(Env.DEV, mapOf("TASKS_MODE" to "inline", "K_SERVICE" to "api")) }
        shouldThrow<IllegalArgumentException> { TasksMode.fromEnv(Env.DEV, mapOf("TASKS_MODE" to "inline", "CLOUD_RUN_JOB" to "j")) }
    }

    test("fake solo en local; valores desconocidos fallan") {
        TasksMode.fromEnv(Env.LOCAL, mapOf("TASKS_MODE" to "fake")) shouldBe TasksMode.FAKE
        shouldThrow<IllegalArgumentException> { TasksMode.fromEnv(Env.DEV, mapOf("TASKS_MODE" to "fake")) }
        shouldThrow<IllegalArgumentException> { TasksMode.fromEnv(Env.DEV, mapOf("TASKS_MODE" to "rabbit")) }
    }
})
