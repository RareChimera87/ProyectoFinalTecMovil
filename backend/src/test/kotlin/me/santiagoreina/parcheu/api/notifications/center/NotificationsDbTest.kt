package me.santiagoreina.parcheu.api.notifications.center

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.home.ExposedUnreadCounter
import me.santiagoreina.parcheu.api.identity.ExposedIdentityRepository
import me.santiagoreina.parcheu.api.notifications.push.ExposedPushStore
import me.santiagoreina.parcheu.api.platform.db.TestDatabase
import me.santiagoreina.parcheu.api.platform.db.connectExposed
import me.santiagoreina.parcheu.api.platform.db.runMigrations
import me.santiagoreina.parcheu.contract.NotificationKinds
import java.time.Instant

/** Bandeja C2 contra Postgres real (filas que escribe `ExposedPushStore`). Necesita Docker o `PARCHEU_TEST_DB_URL`. */
class NotificationsDbTest : FunSpec({
    test("recent ordena del más nuevo al más viejo y markRead marca solo las del usuario (con ids o todas)")
        .config(enabled = TestDatabase.enabled) {
            TestDatabase.withCleanDatabase { ds -> runBlocking {
                runMigrations(ds)
                val db = connectExposed(ds)
                val repo = ExposedIdentityRepository(db)
                val ana = repo.createIfAbsent("ana", "ana@example.com", "Ana", FIXED_CLOCK.instant())
                val beto = repo.createIfAbsent("beto", "beto@example.com", "Beto", FIXED_CLOCK.instant())
                val push = ExposedPushStore(db)
                val payload = JsonObject(mapOf("title" to JsonPrimitive("t"), "body" to JsonPrimitive("b"), "locked" to JsonPrimitive(true)))
                val viejo = push.saveNotification(ana.id, NotificationKinds.ACCOUNT, payload, Instant.parse("2026-10-20T10:00:00Z"))
                val nuevo = push.saveNotification(ana.id, NotificationKinds.CONNECT_REQUEST, payload, Instant.parse("2026-10-25T10:00:00Z"))
                val medio = push.saveNotification(ana.id, NotificationKinds.CHAT_MESSAGE, payload, Instant.parse("2026-10-24T10:00:00Z"))
                val deBeto = push.saveNotification(beto.id, NotificationKinds.ACCOUNT, payload, Instant.parse("2026-10-25T10:00:00Z"))

                val store = ExposedNotificationStore(db)
                val rows = store.recent(ana.id, limit = 10)
                rows.map { it.id } shouldContainExactly listOf(nuevo, medio, viejo)
                rows.first().payload!!["locked"] shouldBe JsonPrimitive(true)
                rows.first().createdAt shouldBe Instant.parse("2026-10-25T10:00:00Z")
                store.recent(ana.id, limit = 2).size shouldBe 2

                store.markRead(ana.id, listOf(medio, deBeto), FIXED_CLOCK.instant())
                store.unread(ana.id) shouldBe 2
                store.unread(beto.id) shouldBe 1
                store.recent(ana.id, 10).single { it.id == medio }.readAt shouldBe FIXED_CLOCK.instant()

                store.markRead(ana.id, null, FIXED_CLOCK.instant())
                store.unread(ana.id) shouldBe 0
                // La campana de Inicio (E4.7) cuenta sobre la misma tabla.
                ExposedUnreadCounter(db).unread(ana.id) shouldBe 0
                store.unread(beto.id) shouldBe 1
            } }
        }
})
