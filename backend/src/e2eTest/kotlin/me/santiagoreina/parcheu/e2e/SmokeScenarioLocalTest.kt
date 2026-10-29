package me.santiagoreina.parcheu.e2e

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import me.santiagoreina.parcheu.api.TEST_OIDC_TOKEN
import me.santiagoreina.parcheu.api.module
import me.santiagoreina.parcheu.api.platform.AppContainer
import me.santiagoreina.parcheu.api.platform.config.AppConfig
import me.santiagoreina.parcheu.api.platform.config.DbConfig
import me.santiagoreina.parcheu.api.platform.config.Env
import me.santiagoreina.parcheu.api.platform.db.TestDatabase
import me.santiagoreina.parcheu.api.platform.db.runMigrations
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.contract.ChatSummary
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Duration

/**
 * El mismo [SmokeScenario] que corre en dev, contra la API real con `ENV=local` sobre Postgres 16 (Testcontainers) y
 * el LLM Fake. En local nadie ejecuta Cloud Tasks, así que los ganchos llaman a las rutas internas de tareas con el
 * token OIDC Fake, igual que lo haría la cola. Verifica el recorrido completo, que las cuentas queden marcadas
 * `is_test` y que la limpieza las borre. Se omite con `-PsinContenedores`.
 */
class SmokeScenarioLocalTest : FunSpec({
    test("el smoke completo pasa contra la API local y limpia sus cuentas is_test").config(enabled = TestDatabase.enabled) {
        TestDatabase.withCleanDatabase { ds ->
            runMigrations(ds)
            val config = AppConfig(
                env = Env.LOCAL, port = 0, version = "e2e-local", oidcAudience = "http://localhost",
                tasksServiceAccount = "sa-tasks@parcheu-local.iam.gserviceaccount.com", firebaseProjectId = null,
                db = DbConfig("test", ds.username, ds.password, null, ds.jdbcUrl),
            )
            val container = AppContainer.fromConfig(config, mapOf("ENV" to "local", "FAKE_OIDC_TOKEN" to TEST_OIDC_TOKEN))
            val server: EmbeddedServer<*, *> = embeddedServer(Netty, port = 0) { module(container) }.start(wait = false)
            try {
                val port = runBlocking { server.engine.resolvedConnectors().first().port }
                val api = ApiClient("http://127.0.0.1:$port")
                val reader = object : ChatReader {
                    override fun visibleTexts(chatId: String, idToken: String): List<String> =
                        api.get<List<ChatSummary>>("/v1/chats", idToken).filter { it.id == chatId }.mapNotNull { it.lastMessage?.text }
                }
                fun pump(path: String, id: String) {
                    api.expectOk("POST", "/internal$path", TEST_OIDC_TOKEN, """{"id":"$id"}""")
                }
                val hooks = object : SmokeHooks {
                    override fun personaQueued(userId: String) = pump("/tasks/build-persona", userId)
                    override fun simulationQueued(simulationId: String) = pump("/tasks/simulate", simulationId)
                    override fun accountDeleted(userId: String) = pump("/tasks/purge-user", userId)
                }
                val scenario = SmokeScenario(
                    api, FakeTestAuth, reader, hooks,
                    SmokeBudget(interval = Duration.ofMillis(100), pairing = Duration.ofSeconds(10), persona = Duration.ofSeconds(10), simulation = Duration.ofSeconds(60)),
                    runId = "local1",
                )
                val db = Database.connect(ds)
                var marked = 0L
                try {
                    val result = scenario.run()
                    (result.score in 0..100) shouldBe true
                    // Las dos cuentas quedaron marcadas como de prueba al crearse.
                    marked = transaction(db) { Users.selectAll().where { Users.isTest eq true }.count() }
                    marked shouldBe 2L
                } finally {
                    scenario.cleanup()
                }
                // La limpieza purgó a las dos sin dejar nada: ni lápidas ni consentimientos, simulaciones, chats o mensajes.
                transaction(db) {
                    val sql = connection.connection as java.sql.Connection
                    fun count(query: String): Long = sql.createStatement().use { st ->
                        st.executeQuery(query).use { rs ->
                            rs.next()
                            rs.getLong(1)
                        }
                    }
                    Users.selectAll().where { Users.isTest eq true }.count() shouldBe 0L
                    count("select count(*) from users where firebase_uid like 'deleted:%' or firebase_uid like 'e2e-%'") shouldBe 0L
                    listOf("simulations", "chats", "chat_members", "chat_messages", "consents", "matches", "report_unlocks", "connection_decisions")
                        .forEach { table -> count("select count(*) from $table") shouldBe 0L }
                }
            } finally {
                server.stop(0, 0)
            }
        }
    }
})
