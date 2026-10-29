package me.santiagoreina.parcheu.api.platform

import me.santiagoreina.parcheu.api.platform.auth.FirebaseTokenVerifier
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import me.santiagoreina.parcheu.api.Mode
import me.santiagoreina.parcheu.api.platform.auth.FakeOidcVerifier
import me.santiagoreina.parcheu.api.platform.auth.FakeTokenVerifier
import me.santiagoreina.parcheu.api.platform.config.AppConfig
import me.santiagoreina.parcheu.api.platform.config.DbConfig
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
        val config = AppConfig.fromEnv(mapOf("ENV" to "local", "ALLOW_FAKE_AUTH" to "true"))
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

    test("ENV=local sin ALLOW_FAKE_AUTH=true no arranca, en ningún modo (B-A2)") {
        listOf(true, false).forEach { serving ->
            shouldThrow<IllegalArgumentException> {
                AppConfig.fromEnv(mapOf("ENV" to "local"), serving = serving)
            }.message shouldContain "ALLOW_FAKE_AUTH=true"
            shouldThrow<IllegalArgumentException> {
                AppConfig.fromEnv(mapOf("ENV" to "local", "ALLOW_FAKE_AUTH" to "1"), serving = serving)
            }
        }
    }

    test("ENV=local con ALLOW_FAKE_AUTH=true arranca con una base local y rechaza una remota") {
        val local = mapOf("ENV" to "local", "ALLOW_FAKE_AUTH" to "true", "DB_NAME" to "parcheu")
        listOf(
            "jdbc:postgresql://localhost:5432/parcheu",
            "jdbc:postgresql://127.0.0.1/parcheu",
            "jdbc:postgresql://[::1]:5432/parcheu",
            // Servicio de la red interna de compose (deploy/homelab/compose.yaml).
            "jdbc:postgresql://parcheu-db:5432/parcheu",
        ).forEach { url ->
            AppConfig.fromEnv(local + ("DB_URL" to url)).db?.jdbcUrl shouldBe url
        }
        listOf(
            "jdbc:postgresql://10.20.0.3:5432/parcheu",
            "jdbc:postgresql://192.168.0.10:5432/parcheu",
            "jdbc:postgresql://db.example.com/parcheu",
            "jdbc:postgresql://localhost,db.example.com/parcheu",
            "jdbc:postgresql://user@db.example.com:5432/parcheu",
            // Revisión del PR #103 (M1): pgjdbc redirige la conexión por parámetros de la URL.
            "jdbc:postgresql://localhost/parcheu?host=10.0.0.5",
            "jdbc:postgresql://localhost?PGHOST=db.example.com",
            "jdbc:postgresql://parcheu-db:5432/parcheu?socketFactory=com.google.cloud.sql.postgres.SocketFactory&cloudSqlInstance=p:r:i",
            "jdbc:postgresql://localhost/parcheu?service=prod",
            "jdbc:mysql://localhost/x",
            // M2: sin heurística de una sola etiqueta; solo la lista exacta.
            "jdbc:postgresql://homelab:5432/parcheu",
            "jdbc:postgresql://db/parcheu",
            "jdbc:postgresql://0x0a000005/parcheu",
            "jdbc:postgresql://localhost:abc/parcheu",
            "jdbc:postgresql://localhost.evil.com/parcheu",
            "jdbc:postgresql:///parcheu?host=/cloudsql/p:r:i",
        ).forEach { url ->
            shouldThrow<IllegalArgumentException> { AppConfig.fromEnv(local + ("DB_URL" to url)) }.message shouldContain "local"
        }
        shouldThrow<IllegalArgumentException> { AppConfig.fromEnv(local + ("CLOUD_SQL_INSTANCE" to "p:r:i")) }
        // Otro nombre solo con la lista explícita LOCAL_DB_HOSTS.
        val otro = local + ("DB_URL" to "jdbc:postgresql://mi-postgres:5432/parcheu")
        shouldThrow<IllegalArgumentException> { AppConfig.fromEnv(otro) }
        AppConfig.fromEnv(otro + ("LOCAL_DB_HOSTS" to "otro, Mi-Postgres")).db?.jdbcUrl shouldBe "jdbc:postgresql://mi-postgres:5432/parcheu"
    }

    test("ALLOW_FAKE_AUTH solo vale con el texto exacto true (B1)") {
        listOf("TRUE", "True", " true", "true ", "\"true\"", "yes").forEach { v ->
            shouldThrow<IllegalArgumentException> { AppConfig.fromEnv(mapOf("ENV" to "local", "ALLOW_FAKE_AUTH" to v)) }
        }
    }

    test("fromConfig no cablea los Fakes sin el consentimiento aunque AppConfig no venga de fromEnv (B2)") {
        val sinConsentimiento = AppConfig(
            env = Env.LOCAL, port = 0, version = "t", oidcAudience = "http://localhost",
            tasksServiceAccount = "sa@p.iam.gserviceaccount.com", firebaseProjectId = null,
            db = DbConfig("parcheu", "postgres", null, null, "jdbc:postgresql://localhost:1/parcheu"),
        )
        shouldThrow<IllegalStateException> { AppContainer.fromConfig(sinConsentimiento, mapOf("ENV" to "local")) }
            .message shouldContain "ALLOW_FAKE_AUTH=true"
        AppConfig.fromEnv(
            mapOf("ENV" to "local", "ALLOW_FAKE_AUTH" to "true", "DB_NAME" to "parcheu", "DB_URL" to "jdbc:postgresql://localhost:1/parcheu"),
        ).fakeAuthAllowed shouldBe true
    }

    test("dev y prod ignoran ALLOW_FAKE_AUTH: nunca cablean Fakes y admiten una base remota") {
        val dev = mapOf(
            "ENV" to "dev", "GCP_PROJECT" to "parcheu-dev", "INTERNAL_OIDC_AUDIENCE" to "https://api.parcheu-dev.internal",
            "TASKS_SERVICE_ACCOUNT" to "sa-tasks@parcheu-dev.iam.gserviceaccount.com",
            "DB_NAME" to "parcheu", "DB_URL" to "jdbc:postgresql://db.example.com:1/parcheu",
        )
        listOf(dev, dev + ("ENV" to "prod")).forEach { vars ->
            val sinBandera = AppConfig.fromEnv(vars)
            val conBandera = AppConfig.fromEnv(vars + ("ALLOW_FAKE_AUTH" to "true"))
            conBandera shouldBe sinBandera
            val c = AppContainer.fromConfig(conBandera, vars + ("ALLOW_FAKE_AUTH" to "true"))
            (c.tokenVerifier is FakeTokenVerifier) shouldBe false
            (c.oidcVerifier is FakeOidcVerifier) shouldBe false
        }
    }

    test("FakeOidcVerifier no acepta el token fijo antiguo; local usa FAKE_OIDC_TOKEN o uno aleatorio por arranque") {
        val legacy = FakeOidcVerifier.LEGACY_TOKEN
        legacy shouldBe "oidc-tasks-fake"
        val v = FakeOidcVerifier("sa@p.iam.gserviceaccount.com", "token-de-prueba-0123456789")
        runBlocking {
            v.verify(legacy) shouldBe null
            v.verify("token-de-prueba-0123456789")?.email shouldBe "sa@p.iam.gserviceaccount.com"
        }
        shouldThrow<IllegalArgumentException> { FakeOidcVerifier("sa@p.iam.gserviceaccount.com", legacy) }
        shouldThrow<IllegalArgumentException> { FakeOidcVerifier.tokenFromEnv(mapOf("FAKE_OIDC_TOKEN" to legacy)) }
        FakeOidcVerifier.tokenFromEnv(mapOf("FAKE_OIDC_TOKEN" to "fijo-de-pruebas-0123456789")) shouldBe "fijo-de-pruebas-0123456789"
        val a = FakeOidcVerifier.tokenFromEnv(emptyMap())
        val b = FakeOidcVerifier.tokenFromEnv(emptyMap())
        (a == b) shouldBe false
        a.length shouldBe 64

        // El cableado de ENV=local tampoco acepta el token antiguo.
        val vars = mapOf("ENV" to "local", "ALLOW_FAKE_AUTH" to "true", "DB_NAME" to "parcheu", "DB_URL" to "jdbc:postgresql://localhost:1/parcheu")
        val c = AppContainer.fromConfig(AppConfig.fromEnv(vars), vars)
        val oidc = c.oidcVerifier as FakeOidcVerifier
        runBlocking { oidc.verify(legacy) shouldBe null }
        val fijo = AppContainer.fromConfig(AppConfig.fromEnv(vars), vars + ("FAKE_OIDC_TOKEN" to "fijo-de-pruebas-0123456789"))
        runBlocking { fijo.oidcVerifier.verify("fijo-de-pruebas-0123456789").shouldNotBeNull() }
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

    test("fromConfig sin base de datos falla con un mensaje claro") {
        val config = AppConfig.fromEnv(mapOf("ENV" to "local", "ALLOW_FAKE_AUTH" to "true"))
        shouldThrow<IllegalArgumentException> { AppContainer.fromConfig(config) }.message shouldContain "DB_NAME"
    }

    test("LEGAL_TEXT_VERSION se lee del entorno y tiene un valor por defecto") {
        AppConfig.fromEnv(mapOf("ENV" to "local", "ALLOW_FAKE_AUTH" to "true")).legalTextVersion shouldBe AppConfig.DEFAULT_LEGAL_TEXT_VERSION
        AppConfig.fromEnv(mapOf("ENV" to "local", "ALLOW_FAKE_AUTH" to "true", "LEGAL_TEXT_VERSION" to "2027-01-01")).legalTextVersion shouldBe "2027-01-01"
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
