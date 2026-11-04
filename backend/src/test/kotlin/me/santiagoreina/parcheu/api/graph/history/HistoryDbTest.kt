package me.santiagoreina.parcheu.api.graph.history

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.graph.AnonIdSigner
import me.santiagoreina.parcheu.api.graph.ExposedGraphRepository
import me.santiagoreina.parcheu.api.graph.GraphVisibilityPolicy
import me.santiagoreina.parcheu.api.platform.db.TestDatabase
import me.santiagoreina.parcheu.api.platform.db.connectExposed
import me.santiagoreina.parcheu.api.platform.db.runMigrations
import me.santiagoreina.parcheu.api.platform.db.tables.Communities
import me.santiagoreina.parcheu.api.platform.db.tables.CommunityMembers
import me.santiagoreina.parcheu.api.platform.db.tables.Edges
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.MomentKind
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/** El historial contra Postgres 16 real (E6.5): `created_at`, `connected_at` y la primera comunidad. Necesita Docker o `PARCHEU_TEST_DB_URL`. */
class HistoryDbTest : FunSpec({
    val now = OffsetDateTime.ofInstant(FIXED_CLOCK.instant(), ZoneOffset.UTC)

    fun at(iso: String) = OffsetDateTime.parse(iso)

    fun Database.user(name: String): UUID = transaction(this) {
        Users.insert {
            it[firebaseUid] = "uid-$name-${UUID.randomUUID()}"
            it[displayName] = name
            it[universityId] = 1
            it[createdAt] = now
            it[updatedAt] = now
        }[Users.id]
    }

    fun Database.edge(a: UUID, b: UUID, created: OffsetDateTime) = transaction(this) {
        val (low, high) = Edges.orderedPair(a, b)
        Edges.insert {
            it[userLow] = low
            it[userHigh] = high
            it[mode] = Mode.AMISTAD
            it[state] = EdgeState.CONNECTED
            it[affinity] = 80
            it[createdAt] = created
            it[updatedAt] = created
            it[connectedAt] = created
        }
    }

    fun withDb(block: suspend (Database) -> Unit) = TestDatabase.withCleanDatabase { ds ->
        runMigrations(ds)
        kotlinx.coroutines.runBlocking { block(connectExposed(ds)) }
    }

    test("tres meses de aristas en Postgres dan tres meses y la primera comunidad más antigua")
        .config(enabled = TestDatabase.enabled) {
            withDb { db ->
                val me = db.user("Ana")
                val a = db.user("Beto")
                val b = db.user("Caro")
                val c = db.user("Dani")
                db.edge(me, a, at("2026-08-10T15:00:00Z"))
                db.edge(me, b, at("2026-09-10T15:00:00Z"))
                db.edge(me, c, at("2026-10-10T15:00:00Z"))
                transaction(db) {
                    val late = Communities.insert {
                        it[week] = LocalDate.of(2026, 10, 19)
                        it[name] = "Tardía"
                        it[stableKey] = "k2"
                        it[createdAt] = at("2026-10-20T08:00:00Z")
                    }[Communities.id]
                    val early = Communities.insert {
                        it[week] = LocalDate.of(2026, 9, 28)
                        it[name] = "Temprana"
                        it[stableKey] = "k1"
                        it[createdAt] = at("2026-09-29T08:00:00Z")
                    }[Communities.id]
                    listOf(late, early).forEach { id ->
                        CommunityMembers.insert {
                            it[communityId] = id
                            it[userId] = me
                        }
                    }
                }
                val repo = ExposedGraphRepository(db)
                repo.firstCommunityAt(me)?.toString() shouldBe "2026-09-29T08:00:00Z"
                repo.firstCommunityAt(a).shouldBeNull()

                val service = HistoryService(repo, GraphVisibilityPolicy(AnonIdSigner.forTests()), { true }, FIXED_CLOCK)
                val dto = service.history(me)
                dto.months.map { it.month } shouldContainExactly listOf("2026-08", "2026-09", "2026-10")
                dto.months.map { it.connections } shouldContainExactly listOf(1, 2, 3)
                dto.months.map { it.newConnections } shouldContainExactly listOf(1, 1, 1)
                dto.moments.map { it.kind }.contains(MomentKind.FIRST_COMMUNITY) shouldBe true
            }
        }
})
