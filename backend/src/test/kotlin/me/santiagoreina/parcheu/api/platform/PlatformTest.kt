package me.santiagoreina.parcheu.api.platform

import me.santiagoreina.parcheu.api.platform.auth.FirebaseTokenVerifier
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import me.santiagoreina.parcheu.api.Mode
import me.santiagoreina.parcheu.api.platform.auth.FakeOidcVerifier
import me.santiagoreina.parcheu.api.platform.auth.FakeTokenVerifier
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

    test("AppConfig usa valores por defecto locales salvo ENV, que es obligatorio") {
        val config = AppConfig.fromEnv(mapOf("ENV" to "local"))
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

    test("con las variables de E0.5 el servicio arranca: audiencia custom y GCP_PROJECT") {
        val config = AppConfig.fromEnv(
            mapOf(
                "ENV" to "dev", "K_SERVICE" to "api", "GCP_PROJECT" to "parcheu-dev",
                "INTERNAL_OIDC_AUDIENCE" to "https://api.parcheu-dev.internal",
                "SERVICE_URL" to "https://api-xyz.a.run.app",
                "TASKS_SERVICE_ACCOUNT" to "sa-tasks@parcheu-dev.iam.gserviceaccount.com",
            ),
        )
        config.firebaseProjectId shouldBe "parcheu-dev"
        config.oidcAudience shouldBe "https://api.parcheu-dev.internal"
    }

    test("SERVICE_URL sirve de respaldo de la audiencia y FIREBASE_PROJECT_ID gana sobre GCP_PROJECT") {
        val config = AppConfig.fromEnv(
            mapOf(
                "ENV" to "prod", "GCP_PROJECT" to "otro", "FIREBASE_PROJECT_ID" to "parcheu-prod",
                "SERVICE_URL" to "https://api.example.run.app",
                "TASKS_SERVICE_ACCOUNT" to "sa-tasks@p.iam.gserviceaccount.com",
            ),
        )
        config.firebaseProjectId shouldBe "parcheu-prod"
        config.oidcAudience shouldBe "https://api.example.run.app"
    }

    test("serve en dev exige audiencia https y cuenta de tareas explícita") {
        val base = mapOf("ENV" to "dev", "GCP_PROJECT" to "parcheu-dev")
        shouldThrow<IllegalArgumentException> { AppConfig.fromEnv(base) }
        shouldThrow<IllegalArgumentException> {
            AppConfig.fromEnv(base + ("INTERNAL_OIDC_AUDIENCE" to "https://api.parcheu-dev.internal"))
        }
    }

    test("los jobs migrate, seed-demo y purge-demo no exigen Firebase ni OIDC (variables de E0.5)") {
        val config = AppConfig.fromEnv(
            mapOf(
                "ENV" to "dev", "CLOUD_RUN_JOB" to "migrate", "DB_NAME" to "parcheu",
                "CLOUD_SQL_INSTANCE" to "p:r:i",
            ),
            serving = false,
        )
        config.db?.name shouldBe "parcheu"
        // ENV sigue siendo obligatorio también en los jobs.
        shouldThrow<IllegalStateException> { AppConfig.fromEnv(emptyMap(), serving = false) }
    }

    test("sin ENV no arranca ni hay Fakes, con o sin K_SERVICE") {
        shouldThrow<IllegalStateException> { AppConfig.fromEnv(emptyMap()) }
        shouldThrow<IllegalStateException> { AppConfig.fromEnv(mapOf("ENV" to " ")) }
        shouldThrow<IllegalStateException> { AppConfig.fromEnv(mapOf("K_SERVICE" to "api")) }
        shouldThrow<IllegalStateException> { AppConfig.fromEnv(mapOf("CLOUD_RUN_JOB" to "migrate")) }
    }

    test("ENV=local se rechaza en Cloud Run") {
        shouldThrow<IllegalArgumentException> { AppConfig.fromEnv(mapOf("ENV" to "local", "K_SERVICE" to "api")) }
        shouldThrow<IllegalArgumentException> { AppConfig.fromEnv(mapOf("ENV" to "LOCAL", "CLOUD_RUN_JOB" to "migrate")) }
    }

    test("fromConfig cablea verificadores reales fuera de local") {
        val config = AppConfig.fromEnv(
            mapOf(
                "ENV" to "prod", "K_SERVICE" to "api", "SERVICE_URL" to "https://api.example.run.app",
                "FIREBASE_PROJECT_ID" to "parcheu-prod", "TASKS_SERVICE_ACCOUNT" to "sa-tasks@p.iam.gserviceaccount.com",
                // E0.9: la pasarela de IA registra el uso en Postgres (conexión perezosa: aquí no se abre).
                "DB_NAME" to "parcheu", "DB_URL" to "jdbc:postgresql://localhost:1/parcheu",
            ),
        )
        val c = AppContainer.fromConfig(config, mapOf("GCP_PROJECT" to "parcheu-prod"))
        (c.tokenVerifier is FakeTokenVerifier) shouldBe false
        (c.oidcVerifier is FakeOidcVerifier) shouldBe false
        // E4.1: en prod no existe la concesión de Plus de desarrollo.
        c.quota.devPlus shouldBe null
    }

    test("FirebaseTokenVerifier no resuelve credenciales al construirse (ADC diferido)") {
        var llamadas = 0
        FirebaseTokenVerifier("parcheu-prod") {
            llamadas++
            error("sin ADC")
        }
        llamadas shouldBe 0
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
