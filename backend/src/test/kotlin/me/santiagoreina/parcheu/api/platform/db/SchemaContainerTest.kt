package me.santiagoreina.parcheu.api.platform.db

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.santiagoreina.parcheu.api.platform.db.tables.Consents
import me.santiagoreina.parcheu.api.platform.db.tables.Edges
import me.santiagoreina.parcheu.api.platform.db.tables.EdgesView
import me.santiagoreina.parcheu.api.platform.db.tables.Interests
import me.santiagoreina.parcheu.api.platform.db.tables.Questions
import me.santiagoreina.parcheu.api.platform.db.tables.Scenarios
import me.santiagoreina.parcheu.api.platform.db.tables.Schema
import me.santiagoreina.parcheu.api.platform.db.tables.Twins
import me.santiagoreina.parcheu.api.platform.db.tables.Universities
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.contract.ConsentKind
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.TraitKey
import me.santiagoreina.parcheu.contract.Visibility
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.SQLException
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

/**
 * Aplica V1 y V2 sobre un Postgres 16 limpio y verifica el esquema, las restricciones y las
 * semillas (criterios de E0.4). Necesita Docker o `PARCHEU_TEST_DB_URL`: ver [TestDatabase].
 */
class SchemaContainerTest : FunSpec({
    val now = OffsetDateTime.parse("2026-10-25T07:00:00-05:00")

    fun newUser(db: Database, uid: String): UUID = transaction(db) {
        Users.insert {
            it[firebaseUid] = uid
            it[createdAt] = now
            it[updatedAt] = now
        }[Users.id]
    }

    fun sqlState(block: () -> Unit): String = shouldThrow<Exception> { block() }.let { e ->
        generateSequence<Throwable>(e) { it.cause }.filterIsInstance<SQLException>().first().sqlState
    }

    test("V1 y V2 se aplican sobre Postgres 16 limpio y el esquema coincide con las tablas Exposed")
        .config(enabled = TestDatabase.enabled) {
            TestDatabase.withCleanDatabase { ds ->
                runMigrations(ds) shouldBe 2
                runMigrations(ds) shouldBe 0
                val applied = Flyway.configure().dataSource(ds).locations(MIGRATIONS_LOCATION).load().info().applied()
                applied.map { it.version.version } shouldContainExactly listOf("1", "2")
                ds.connection.use { c ->
                    c.metaData.databaseProductVersion.substringBefore('.') shouldBe "16"
                }

                val columnsInDb = columnsByTable(ds)
                (Schema.tables + Schema.views).forEach { table ->
                    val expected = table.columns.map { it.name }
                    withClue(table.tableName) { columnsInDb[table.tableName].orEmpty() shouldContainExactlyInAnyOrder expected }
                }
                // Ninguna tabla de la base queda sin su objeto Exposed.
                columnsInDb.keys.filterNot { it == "flyway_schema_history" } shouldContainExactlyInAnyOrder
                    (Schema.tables + Schema.views).map { it.tableName }

                // El mapeo de tipos de Exposed funciona contra cada tabla (lectura vacía o con semillas).
                val db = connectExposed(ds)
                transaction(db) { (Schema.tables + Schema.views).forEach { it.selectAll().limit(1).toList() } }
            }
        }

    test("una migración con versión menor que la ya aplicada se aplica por defecto (outOfOrder en todos los entornos)")
        .config(enabled = TestDatabase.enabled) {
            TestDatabase.withCleanDatabase { ds ->
                val dir = java.nio.file.Files.createTempDirectory("migraciones-orden")
                try {
                    val loc = "filesystem:$dir"
                    dir.resolve("V20261030_1015__a.sql").toFile().writeText("create table orden_a (id int);")
                    runMigrations(ds, location = loc) shouldBe 1
                    // Llega después una migración con versión menor (PR paralelo mezclado en orden inverso).
                    dir.resolve("V20261029_0900__b.sql").toFile().writeText("create table orden_b (id int);")
                    runMigrations(ds, location = loc) shouldBe 1
                    // Con outOfOrder=false la misma situación falla la validación de Flyway.
                    dir.resolve("V20261028_0800__c.sql").toFile().writeText("create table orden_c (id int);")
                    shouldThrow<Exception> { runMigrations(ds, outOfOrder = false, location = loc) }
                } finally {
                    dir.toFile().deleteRecursively()
                }
            }
        }

    test("una arista con user_low >= user_high viola el CHECK; orderedPair coincide con el orden de Postgres")
        .config(enabled = TestDatabase.enabled) {
            TestDatabase.withCleanDatabase { ds ->
                runMigrations(ds)
                val db = connectExposed(ds)
                // UUIDs con el bit alto encendido: con signo serían negativos y UUID.compareTo los ordenaría mal.
                val a = UUID.fromString("00000000-0000-4000-8000-000000000001")
                val b = UUID.fromString("f0000000-0000-4000-8000-000000000001")
                transaction(db) {
                    listOf(a to "uid-a", b to "uid-b").forEach { (id, uid) ->
                        Users.insert {
                            it[Users.id] = id
                            it[firebaseUid] = uid
                            it[createdAt] = now
                            it[updatedAt] = now
                        }
                    }
                }
                fun insertEdge(low: UUID, high: UUID) = transaction(db) {
                    Edges.insert {
                        it[userLow] = low
                        it[userHigh] = high
                        it[mode] = Mode.AMISTAD
                        it[state] = EdgeState.SIMULATED
                        it[affinity] = 80
                        it[createdAt] = now
                        it[updatedAt] = now
                    }
                }
                sqlState { insertEdge(b, a) } shouldBe "23514"
                sqlState { insertEdge(a, a) } shouldBe "23514"

                val (low, high) = Edges.orderedPair(b, a)
                low shouldBe a
                insertEdge(low, high)

                transaction(db) {
                    val rows = EdgesView.selectAll().map { it[EdgesView.me] to it[EdgesView.other] }
                    rows shouldContainExactlyInAnyOrder listOf(a to b, b to a)
                }
            }
        }

    test("las semillas tienen 12 preguntas, universidades, intereses y escenarios con rúbrica")
        .config(enabled = TestDatabase.enabled) {
            TestDatabase.withCleanDatabase { ds ->
                runMigrations(ds)
                val db = connectExposed(ds)
                transaction(db) {
                    val questions = Questions.selectAll().toList()
                    questions.size shouldBe 12
                    questions.map { it[Questions.ord].toInt() }.sorted() shouldContainExactly (1..12).toList()
                    val traitKeys = TraitKey.entries.map { it.name }.toSet()
                    val uses = questions.flatMap { it[Questions.weights].jsonObject.keys }
                    uses.forEach { traitKeys shouldContain it }
                    TraitKey.entries.forEach { key ->
                        withClue(key) { uses.count { it == key.name } shouldBeGreaterThanOrEqualTo 2 }
                    }

                    val universities = Universities.selectAll().map { it[Universities.shortName] }
                    (universities - "Otra").size shouldBeGreaterThanOrEqualTo 8
                    universities shouldContain "Otra"

                    Interests.selectAll().count() shouldBeGreaterThanOrEqualTo 20L

                    Mode.entries.forEach { m ->
                        val scenarios = Scenarios.selectAll().where { Scenarios.mode eq m }.toList()
                        withClue("$m básicos") { scenarios.count { it[Scenarios.isBasic] } shouldBeGreaterThanOrEqualTo 3 }
                        withClue("$m premium") { scenarios.count { !it[Scenarios.isBasic] } shouldBeGreaterThanOrEqualTo 2 }
                        scenarios.forEach { s ->
                            val rubric = s[Scenarios.rubric] as JsonArray
                            withClue(s[Scenarios.slug]) {
                                rubric.size shouldBeGreaterThanOrEqualTo 4
                                rubric.forEach { d ->
                                    (d as JsonObject).keys shouldContainExactlyInAnyOrder listOf("name", "weight", "description")
                                }
                                rubric.sumOf { it.jsonObject["weight"]!!.jsonPrimitive.double } shouldBe (1.0 plusOrMinus 1e-9)
                            }
                        }
                    }
                }
            }
        }

    test("consents solo admite INSERT, salvo la purga por retención explícita")
        .config(enabled = TestDatabase.enabled) {
            TestDatabase.withCleanDatabase { ds ->
                runMigrations(ds)
                val db = connectExposed(ds)
                val user = newUser(db, "uid-consent")
                transaction(db) {
                    Consents.insert {
                        it[userId] = user
                        it[kind] = ConsentKind.TERMS
                        it[granted] = true
                        it[textVersion] = "2026-10-22"
                        it[createdAt] = now
                    }
                }
                shouldThrow<Exception> { transaction(db) { exec("update consents set granted = false") } }
                    .message shouldContain "consents solo admite INSERT"
                shouldThrow<Exception> { transaction(db) { exec("delete from consents") } }
                shouldThrow<Exception> { transaction(db) { exec("truncate consents") } }
                transaction(db) { Consents.selectAll().count() } shouldBe 1L

                transaction(db) {
                    exec("set local parcheu.consents_purge = 'on'")
                    exec("delete from consents")
                }
                transaction(db) { Consents.selectAll().count() } shouldBe 0L
            }
        }

    test("las columnas text[], real[], enum y jsonb hacen ida y vuelta con Exposed")
        .config(enabled = TestDatabase.enabled) {
            TestDatabase.withCleanDatabase { ds ->
                runMigrations(ds)
                val db = connectExposed(ds)
                val user = transaction(db) {
                    Users.insert {
                        it[firebaseUid] = "uid-roundtrip"
                        it[interests] = listOf("musica", "cafe")
                        it[visibility] = Visibility.NAME
                        it[universityId] = 1
                        it[createdAt] = now
                        it[updatedAt] = now
                    }[Users.id]
                }
                transaction(db) {
                    Twins.insert {
                        it[userId] = user
                        it[traits] = listOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f)
                        it[updatedAt] = now
                    }
                    val row = Users.selectAll().where { Users.id eq user }.single()
                    row[Users.interests] shouldContainExactly listOf("musica", "cafe")
                    row[Users.visibility] shouldBe Visibility.NAME
                    row[Users.isDemo] shouldBe false
                    Twins.selectAll().single()[Twins.traits] shouldBe listOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f)
                }
                // CHECK de intereses (máximo 5) y de cardinalidad de rasgos (6).
                sqlState {
                    transaction(db) { exec("update users set interests = '{a,b,c,d,e,f}' where id = '$user'") }
                } shouldBe "23514"
                sqlState {
                    transaction(db) { exec("update twins set traits = '{0.1,0.2}' where user_id = '$user'") }
                } shouldBe "23514"
            }
        }
})

private fun columnsByTable(ds: DataSource): Map<String, List<String>> = ds.connection.use { c ->
    c.createStatement().executeQuery(
        "select table_name, column_name from information_schema.columns where table_schema = 'public'",
    ).use { rs ->
        buildList { while (rs.next()) add(rs.getString(1) to rs.getString(2)) }
    }.groupBy({ it.first }, { it.second })
}
