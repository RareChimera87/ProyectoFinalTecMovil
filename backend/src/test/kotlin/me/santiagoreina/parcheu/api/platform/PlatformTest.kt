package me.santiagoreina.parcheu.api.platform

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import me.santiagoreina.parcheu.api.Mode
import me.santiagoreina.parcheu.api.platform.config.AppConfig
import me.santiagoreina.parcheu.api.platform.config.Env
import me.santiagoreina.parcheu.api.platform.logging.parseTraceId

class PlatformTest : FunSpec({
    test("parseTraceId toma el traceId de X-Cloud-Trace-Context") {
        parseTraceId("105445aa7843bc8bf206b12000100000/1;o=1") shouldBe "105445aa7843bc8bf206b12000100000"
        parseTraceId("abc") shouldBe "abc"
        parseTraceId("") shouldBe null
        parseTraceId(null) shouldBe null
    }

    test("AppConfig usa valores por defecto locales") {
        val config = AppConfig.fromEnv(emptyMap())
        config.env shouldBe Env.LOCAL
        config.port shouldBe 8080
        config.version shouldBe "dev"
    }

    test("AppConfig lee las variables de entorno") {
        val config = AppConfig.fromEnv(
            mapOf(
                "ENV" to "dev", "PORT" to "9000", "APP_VERSION" to "deadbeef",
                "SERVICE_URL" to "https://api-dev.example.run.app", "FIREBASE_PROJECT_ID" to "parcheu-dev",
                "TASKS_SERVICE_ACCOUNT" to "sa-tasks@parcheu-dev.iam.gserviceaccount.com",
                "DB_NAME" to "parcheu", "CLOUD_SQL_INSTANCE" to "p:r:i",
            ),
        )
        config.env shouldBe Env.DEV
        config.port shouldBe 9000
        config.version shouldBe "deadbeef"
        config.db?.cloudSqlInstance shouldBe "p:r:i"
    }

    test("AppConfig falla al arrancar si dev/prod no tienen proyecto de Firebase") {
        shouldThrow<IllegalArgumentException> { AppConfig.fromEnv(mapOf("ENV" to "prod")) }
        shouldThrow<IllegalStateException> { AppConfig.fromEnv(mapOf("ENV" to "staging")) }
    }

    test("Mode.parse reconoce los cuatro modos y usa serve por defecto") {
        Mode.parse(null) shouldBe Mode.SERVE
        Mode.parse("serve") shouldBe Mode.SERVE
        Mode.parse("migrate") shouldBe Mode.MIGRATE
        Mode.parse("seed-demo") shouldBe Mode.SEED_DEMO
        Mode.parse("purge-demo") shouldBe Mode.PURGE_DEMO
        Mode.parse("otro") shouldBe null
    }
})
