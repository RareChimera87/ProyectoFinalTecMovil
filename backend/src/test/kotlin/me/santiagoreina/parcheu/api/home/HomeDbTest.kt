package me.santiagoreina.parcheu.api.home

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import me.santiagoreina.parcheu.api.platform.db.TestDatabase
import me.santiagoreina.parcheu.api.platform.db.connectExposed
import me.santiagoreina.parcheu.api.platform.db.runMigrations
import me.santiagoreina.parcheu.api.platform.db.tables.Notifications
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlinx.coroutines.runBlocking
import java.time.OffsetDateTime
import java.util.UUID

/**
 * [ExposedUnreadCounter] contra Postgres 16. Necesita Docker o `PARCHEU_TEST_DB_URL`: con
 * `-PsinContenedores` se omite (ver [TestDatabase]).
 */
class HomeDbTest : FunSpec({
    val now = OffsetDateTime.parse("2026-10-25T07:00:00-05:00")

    fun newUser(db: Database, uid: String): UUID = transaction(db) {
        Users.insert {
            it[firebaseUid] = uid
            it[createdAt] = now
            it[updatedAt] = now
        }[Users.id]
    }

    fun notify(db: Database, user: UUID, read: Boolean) = transaction(db) {
        Notifications.insert {
            it[userId] = user
            it[kind] = "SIMULATION_DONE"
            it[createdAt] = now
            it[readAt] = if (read) now else null
        }
    }

    test("Postgres: cuenta solo los avisos sin read_at del usuario").config(enabled = TestDatabase.enabled) {
        TestDatabase.withCleanDatabase { ds ->
            runMigrations(ds)
            val db = connectExposed(ds)
            val ana = newUser(db, "ana")
            val beto = newUser(db, "beto")
            repeat(3) { notify(db, ana, read = false) }
            notify(db, ana, read = true)
            notify(db, beto, read = false)

            val counter = ExposedUnreadCounter(db)
            runBlocking {
                counter.unread(ana) shouldBe 3
                counter.unread(beto) shouldBe 1
                counter.unread(UUID.randomUUID()) shouldBe 0
            }
        }
    }
})
