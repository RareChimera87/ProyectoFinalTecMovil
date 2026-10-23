package me.santiagoreina.parcheu.api.graph.fof

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.graph.AnonIdSigner
import me.santiagoreina.parcheu.api.graph.ExposedGraphRepository
import me.santiagoreina.parcheu.api.graph.GraphVisibilityPolicy
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
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.random.Random

/**
 * La consulta de 2 saltos de arquitectura.md §6.2 contra Postgres 16 real (E6.3). Los filtros de
 * Pareja, HIDDEN, borrados, bloqueos y aristas existentes se comprueban llamando al repositorio sin la
 * policy, y una prueba de paridad compara el SQL con [InMemoryFofRepository] en mundos al azar.
 * Necesita Docker o `PARCHEU_TEST_DB_URL`.
 */
class FofDbTest : FunSpec({
    val now = OffsetDateTime.ofInstant(FIXED_CLOCK.instant(), ZoneOffset.UTC)

    fun Database.user(
        name: String?,
        visibility: Visibility = Visibility.ANONYMOUS,
        id: UUID? = null,
        isDemo: Boolean = false,
    ): UUID = transaction(this) {
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

    test("un amigo en común conectado en dos modos cuenta una sola vez (count DISTINCT en SQL)")
        .config(enabled = TestDatabase.enabled) {
            withDb { db ->
                val me = db.user("Ana")
                val beto = db.user("Beto")
                val caro = db.user("Caro")
                val dani = db.user("Dani")
                db.edge(me, beto, Mode.AMISTAD)
                db.edge(me, beto, Mode.ESTUDIO) // el mismo amigo en dos modos con el observador
                db.edge(me, caro, Mode.ESTUDIO)
                db.edge(beto, dani, Mode.AMISTAD)
                db.edge(beto, dani, Mode.ESTUDIO) // y en dos modos con el candidato
                db.edge(caro, dani, Mode.ESTUDIO)
                db.traits(me, List(6) { 0.5 })
                db.traits(dani, List(6) { 0.5 })
                val repo = ExposedFofRepository(db)

                val row = repo.candidates(me, 30).single()
                row.person.id shouldBe dani
                row.mutuals shouldBe 2
                row.links shouldContainExactlyInAnyOrder listOf(
                    FofLink(beto, Mode.AMISTAD),
                    FofLink(beto, Mode.ESTUDIO),
                    FofLink(caro, Mode.ESTUDIO),
                )
                row.traits shouldBe List(6) { 0.5 }
                repo.traits(me) shouldBe List(6) { 0.5 }
                repo.traits(beto) shouldBe null

                val service = FofService(repo, ExposedGraphRepository(db), GraphVisibilityPolicy(AnonIdSigner.forTests()), { true })
                val item = service.fof(me).items!!.single()
                item.mutuals shouldBe 2
                item.estimatedAffinity shouldBe 100
                item.mode shouldBe Mode.ESTUDIO
                // viaId: uno de los amigos en común con arista del modo del candidato (desempate por id).
                item.viaId shouldBe listOf(beto, caro).minBy { it.toString() }.toString()
            }
        }

    test("viaId sale de la arista CONNECTED sin Pareja de la misma fila, nunca de una Pareja ajena")
        .config(enabled = TestDatabase.enabled) {
            withDb { db ->
                // Caro tiene el id menor: si viaId saliera de cualquier amigo en común, ganaría ella.
                val me = db.user("Ana")
                val caro = db.user("Caro", id = UUID.fromString("00000000-0000-4000-8000-000000000001"))
                val eli = db.user("Eli", id = UUID.fromString("00000000-0000-4000-8000-000000000002"))
                val beto = db.user("Beto", id = UUID.fromString("00000000-0000-4000-8000-000000000003"))
                val dani = db.user("Dani")
                db.edge(me, beto, Mode.AMISTAD)
                db.edge(me, caro, Mode.AMISTAD)
                db.edge(me, eli, Mode.AMISTAD)
                db.edge(beto, dani, Mode.AMISTAD)
                db.edge(caro, dani, Mode.PAREJA) // Pareja ajena: no es un salto ni puede ser viaId
                db.edge(eli, dani, Mode.AMISTAD, EdgeState.SIMULATED, 70) // no CONNECTED

                val service = FofService(
                    ExposedFofRepository(db),
                    ExposedGraphRepository(db),
                    GraphVisibilityPolicy(AnonIdSigner.forTests()),
                    { true },
                )
                val item = service.fof(me).items!!.single()
                item.mutuals shouldBe 1
                item.viaId shouldBe beto.toString()
            }
        }

    test("Pareja, HIDDEN, borrados, bloqueos y aristas existentes se filtran ya en SQL")
        .config(enabled = TestDatabase.enabled) {
            withDb { db ->
                val me = db.user("Ana")
                val beto = db.user("Beto")
                val pareja = db.user("Pareja")
                val viaPareja = db.user("Solo por Pareja")
                val hidden = db.user("Oculta", Visibility.HIDDEN)
                val deleted = db.user("Borrada")
                val blocked = db.user("Bloqueada")
                val blocker = db.user("Bloqueadora")
                val simulated = db.user("Ya simularon")
                val hiddenFriend = db.user("Amiga oculta", Visibility.HIDDEN)
                val viaHidden = db.user("Solo por la oculta")
                val ok = db.user("Visible")
                db.edge(me, beto, Mode.AMISTAD)
                db.edge(me, pareja, Mode.PAREJA)
                db.edge(beto, pareja, Mode.AMISTAD) // la pareja del observador, amiga de Beto
                db.edge(beto, viaPareja, Mode.PAREJA) // Pareja ajena no es un salto
                listOf(hidden, deleted, blocked, blocker, simulated, ok).forEach { db.edge(beto, it, Mode.ESTUDIO) }
                db.delete(deleted)
                db.block(me, blocked)
                db.block(blocker, me)
                db.edge(me, simulated, Mode.AMISTAD, EdgeState.SIMULATED, 60)
                db.edge(me, hiddenFriend, Mode.AMISTAD)
                db.edge(hiddenFriend, viaHidden, Mode.AMISTAD)

                val repo = ExposedFofRepository(db)
                repo.candidates(me, 30).map { it.person.id } shouldBe listOf(ok)

                // Bloqueo entre el amigo en común y el candidato: tampoco cuenta.
                db.block(beto, ok)
                repo.candidates(me, 30).shouldBeEmpty()
            }
        }

    test("tras un bloqueo en cualquier sentido el bloqueado desaparece de fof del observador")
        .config(enabled = TestDatabase.enabled) {
            withDb { db ->
                val me = db.user("Ana")
                val beto = db.user("Beto")
                val dani = db.user("Dani")
                val eli = db.user("Eli")
                db.edge(me, beto, Mode.AMISTAD)
                db.edge(beto, dani, Mode.AMISTAD)
                db.edge(beto, eli, Mode.AMISTAD)
                val repo = ExposedFofRepository(db)
                repo.candidates(me, 30).map { it.person.id } shouldContainExactlyInAnyOrder listOf(dani, eli)
                db.block(me, dani)
                db.block(eli, me)
                repo.candidates(me, 30).shouldBeEmpty()
            }
        }

    test("paridad: el SQL y el repositorio en memoria devuelven lo mismo en 40 mundos al azar")
        .config(enabled = TestDatabase.enabled) {
            withDb { db ->
                var compared = 0
                repeat(40) { seed ->
                    val world = PrivacyWorld.generate(Random(seed))
                    val random = Random(seed)
                    val traits = world.people.filter { random.nextBoolean() }.associate { p -> p.id to List(6) { random.nextDouble().toFloat().toDouble() } }
                    world.people.forEach { p ->
                        db.user(p.displayName, p.visibility, p.id, p.isDemo)
                        if (p.deletedAt != null) db.delete(p.id)
                        traits[p.id]?.let { db.traits(p.id, it) }
                    }
                    world.edges.forEach { db.edge(it.a, it.b, it.mode, it.state, it.affinity) }
                    world.blocks.forEach { (a, b) -> db.block(a, b) }
                    val sql = ExposedFofRepository(db)
                    val memory = InMemoryFofRepository(world.toGraphRepository()) { traits[it] }
                    world.observers().forEach { viewer ->
                        fun List<FofRow>.normalized() = map { Triple(it.person.id, it.mutuals, it.links.toSet()) }
                        val expected = memory.candidates(viewer, 200).normalized()
                        withClue("mundo $seed, observador $viewer en $world") {
                            sql.candidates(viewer, 200).normalized() shouldBe expected
                        }
                        compared += expected.size
                        // viaId también coincide: sale solo de las aristas que la paridad ya compara.
                        val policy = GraphVisibilityPolicy(AnonIdSigner.forTests())
                        val viaMemory = FofService(memory, world.toGraphRepository(), policy, { true })
                            .fof(viewer).items.orEmpty().map { it.anonId to it.viaId }
                        val viaSql = FofService(sql, ExposedGraphRepository(db), policy, { true })
                            .fof(viewer).items.orEmpty().map { it.anonId to it.viaId }
                        withClue("viaId, mundo $seed, observador $viewer en $world") {
                            viaSql.toSet() shouldBe viaMemory.toSet()
                        }
                    }
                }
                // Que la paridad no sea trivial (mundos sin candidatos).
                compared shouldBeGreaterThan 20
            }
        }
})
