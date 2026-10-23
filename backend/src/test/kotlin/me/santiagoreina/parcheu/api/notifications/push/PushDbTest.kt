package me.santiagoreina.parcheu.api.notifications.push

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonPrimitive
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.platform.db.TestDatabase
import me.santiagoreina.parcheu.api.platform.db.connectExposed
import me.santiagoreina.parcheu.api.platform.db.runMigrations
import me.santiagoreina.parcheu.api.platform.db.tables.FcmTokens
import me.santiagoreina.parcheu.api.platform.db.tables.Notifications
import me.santiagoreina.parcheu.api.identity.ExposedIdentityRepository
import me.santiagoreina.parcheu.api.platform.notifier.Notification
import me.santiagoreina.parcheu.contract.NotificationKinds
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/** `FcmNotifier` contra Postgres real (tabla `notifications`, FK a `users`, borrado de `fcm_tokens`). Necesita Docker o `PARCHEU_TEST_DB_URL`. */
class PushDbTest : FunSpec({
    test("notify guarda la fila con su payload jsonb y borra el token UNREGISTERED en Postgres")
        .config(enabled = TestDatabase.enabled) {
            TestDatabase.withCleanDatabase { ds -> runBlocking {
                runMigrations(ds)
                val db = connectExposed(ds)
                val repo = ExposedIdentityRepository(db)
                val ana = repo.createIfAbsent("ana", "ana@example.com", "Ana", FIXED_CLOCK.instant())
                repo.upsertFcmToken(ana.id, "tok-viejo", FIXED_CLOCK.instant())
                repo.upsertFcmToken(ana.id, "tok-vivo", FIXED_CLOCK.instant())

                val fcm = FakeFcmClient(mapOf("tok-viejo" to FcmResult.Unregistered))
                val notifier = FcmNotifier(ExposedPushStore(db), fcm, FIXED_CLOCK)
                notifier.notify(ana.id, Notification(NotificationKinds.NEW_CONNECTION, "Nueva conexión", "Beto y tú ya son parche", "parcheu://parche"))

                transaction(db) {
                    val rows = Notifications.selectAll().toList()
                    rows.size shouldBe 1
                    rows.single()[Notifications.kind] shouldBe "NEW_CONNECTION"
                    rows.single()[Notifications.readAt] shouldBe null
                    rows.single()[Notifications.payload]!!.let { (it as kotlinx.serialization.json.JsonObject)["route"]!!.jsonPrimitive.content } shouldBe "parcheu://parche"
                    FcmTokens.selectAll().map { it[FcmTokens.token] } shouldBe listOf("tok-vivo")
                }
            } }
        }
})
