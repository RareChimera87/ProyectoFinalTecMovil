package me.santiagoreina.parcheu.api.graph.communities

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.platform.db.TestDatabase
import me.santiagoreina.parcheu.api.platform.db.connectExposed
import me.santiagoreina.parcheu.api.platform.db.runMigrations
import me.santiagoreina.parcheu.api.platform.db.tables.Blocks
import me.santiagoreina.parcheu.api.platform.db.tables.Edges
import me.santiagoreina.parcheu.api.platform.db.tables.Twins
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.api.privacy.PrivacyWorld
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Visibility
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.random.Random

/**
 * Comunidades contra Postgres 16 real (E6.4): la consulta de aristas con sus filtros, guardar, conservar
 * y eliminar comunidades, y paridad con [InMemoryCommunityRepository]. Necesita Docker o `PARCHEU_TEST_DB_URL`.
 */
class CommunityDbTest : FunSpec({
    val now = OffsetDateTime.ofInstant(FIXED_CLOCK.instant(), ZoneOffset.UTC)

    fun Database.user(name: String?, visibility: Visibility = Visibility.NAME, id: UUID? = null, isDemo: Boolean = false): UUID =
        transaction(this) {
            Users.insert {
                if (id != null) it[Users.id] = id
                it[firebaseUid] = "uid-$name-${UUID.randomUUID()}"
                it[displayName] = name
                it[universityId] = 1
                it[Users.visibility] = visibility
                it[Users.isDemo] = isDemo
                it[createdAt] = now
                it[updatedAt] = now
            }[Users.id]
        }

    fun Database.edge(a: UUID, b: UUID, mode: Mode, state: EdgeState = EdgeState.CONNECTED, affinity: Int? = 80) = transaction(this) {
        val (low, high) = Edges.orderedPair(a, b)
        Edges.insert {
            it[userLow] = low
            it[userHigh] = high
            it[Edges.mode] = mode
            it[Edges.state] = state
            it[Edges.affinity] = affinity?.toShort()
            it[createdAt] = now
            it[updatedAt] = now
            it[connectedAt] = if (state == EdgeState.CONNECTED) now else null
        }
    }

    fun Database.block(blocker: UUID, blocked: UUID) = transaction(this) {
        Blocks.insert {
            it[blockerId] = blocker
            it[blockedId] = blocked
            it[createdAt] = now
        }
    }

    fun Database.delete(user: UUID) = transaction(this) { Users.update({ Users.id eq user }) { it[deletedAt] = now } }

    fun Database.traits(user: UUID, values: List<Double>) = transaction(this) {
        Twins.insert {
            it[userId] = user
            it[traits] = values.map(Double::toFloat)
            it[updatedAt] = now
        }
    }

    fun withDb(block: suspend (Database) -> Unit) = TestDatabase.withCleanDatabase { ds ->
        runMigrations(ds)
        kotlinx.coroutines.runBlocking { block(connectExposed(ds)) }
    }

    fun List<CommunityEdge>.normalized() =
        map { Triple(listOf(it.a.id, it.b.id).sorted(), it.mode, it.affinity) }.toSet()

    test("las aristas excluyen Pareja, SIMULATED/PENDING, HIDDEN, borrados y bloqueos")
        .config(enabled = TestDatabase.enabled) {
            withDb { db ->
                val ana = db.user("Ana")
                val beto = db.user("Beto")
                val caro = db.user("Caro")
                val hidden = db.user("Oculto", Visibility.HIDDEN)
                val gone = db.user("Borrado")
                val blockedA = db.user("Bloq A")
                val blockedB = db.user("Bloq B")
                val dani = db.user("Dani")
                db.edge(ana, beto, Mode.AMISTAD, affinity = 90)
                db.edge(ana, beto, Mode.ESTUDIO, affinity = 70)
                db.edge(ana, caro, Mode.ESTUDIO, affinity = null)
                db.edge(ana, dani, Mode.PAREJA)
                db.edge(beto, dani, Mode.AMISTAD, EdgeState.SIMULATED)
                db.edge(caro, dani, Mode.AMISTAD, EdgeState.PENDING)
                db.edge(ana, hidden, Mode.AMISTAD)
                db.edge(ana, gone, Mode.AMISTAD)
                db.delete(gone)
                db.edge(blockedA, blockedB, Mode.AMISTAD)
                db.block(blockedB, blockedA)

                val edges = ExposedCommunityRepository(db).connectedEdges()

                edges.normalized() shouldBe setOf(
                    Triple(listOf(ana, beto).sorted(), Mode.AMISTAD, 90),
                    Triple(listOf(ana, beto).sorted(), Mode.ESTUDIO, 70),
                    Triple(listOf(ana, caro).sorted(), Mode.ESTUDIO, null),
                )
            }
        }

    test("apply guarda, conserva y elimina comunidades; los miembros alimentan el filtro del grafo")
        .config(enabled = TestDatabase.enabled) {
            withDb { db ->
                val people = List(8) { db.user("Persona $it") }
                val repo = ExposedCommunityRepository(db)
                val week1 = LocalDate.parse("2026-10-19")
                val week2 = LocalDate.parse("2026-10-26")
                val a = people.take(4).toSet()
                val b = people.drop(4).take(3).toSet()

                repo.apply(week1, CommunityDetector.plan(week1, listOf(a, b), emptyList()))
                val first = repo.all()
                first.map { it.members } shouldContainExactlyInAnyOrder listOf(a, b)
                first.all { it.week == week1 && it.name == CommunityDetector.PENDING_NAME && it.stableKey == it.id.toString() } shouldBe true

                val idA = first.single { it.members == a }.id
                repo.rename(idA, "Los del tinto")
                repo.ofUser(people[0]).single().id shouldBe idA
                repo.ofUser(people[7]).shouldBeEmpty()

                // Semana 2: `a` cambia un miembro y conserva id y nombre; `b` desaparece.
                val a2 = a - people[0] + people[7]
                repo.apply(week2, CommunityDetector.plan(week2, listOf(a2), repo.all()))
                val second = repo.all().single()
                second.id shouldBe idA
                second.name shouldBe "Los del tinto"
                second.week shouldBe week2
                second.members shouldBe a2
                repo.find(idA)!!.members shouldBe a2
                repo.ofUser(people[0]).shouldBeEmpty()
            }
        }

    test("traitsOf devuelve solo los gemelos con rasgos")
        .config(enabled = TestDatabase.enabled) {
            withDb { db ->
                val a = db.user("A")
                val b = db.user("B")
                val c = db.user("C")
                db.traits(a, listOf(0.1, 0.2, 0.3, 0.4, 0.5, 0.6))
                db.traits(b, listOf(0.9, 0.8, 0.7, 0.6, 0.5, 0.4))
                val traits = ExposedCommunityRepository(db).traitsOf(listOf(a, b, c))
                traits.size shouldBe 2
                ExposedCommunityRepository(db).traitsOf(emptyList()).shouldBeEmpty()
            }
        }

    test("paridad: el SQL y el repositorio en memoria dan las mismas aristas y comunidades en 40 mundos")
        .config(enabled = TestDatabase.enabled) {
            withDb { db ->
                var compared = 0
                val sql = ExposedCommunityRepository(db)
                repeat(40) { seed ->
                    val world = PrivacyWorld.generate(Random(seed))
                    val ids = world.people.map { it.id }.toSet()
                    world.people.forEach { p ->
                        db.user(p.displayName, p.visibility, p.id, p.isDemo)
                        if (p.deletedAt != null) db.delete(p.id)
                    }
                    world.edges.forEach { db.edge(it.a, it.b, it.mode, it.state, it.affinity) }
                    world.blocks.forEach { (a, b) -> db.block(a, b) }
                    val memory = InMemoryCommunityRepository(world.toGraphRepository())
                    // La base acumula mundos: se compara solo lo de este (los ids no se repiten entre mundos).
                    val fromSql = sql.connectedEdges().filter { it.a.id in ids }
                    val fromMemory = memory.connectedEdges()
                    withClue("mundo $seed: $world") {
                        fromSql.normalized() shouldBe fromMemory.normalized()
                        CommunityDetector.detect(fromSql) shouldBe CommunityDetector.detect(fromMemory)
                    }
                    compared += fromMemory.size
                }
                compared shouldBeGreaterThan 0
            }
        }
})
