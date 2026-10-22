package me.santiagoreina.parcheu.api.graph

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.platform.db.TestDatabase
import me.santiagoreina.parcheu.api.platform.db.connectExposed
import me.santiagoreina.parcheu.api.platform.db.runMigrations
import me.santiagoreina.parcheu.api.platform.db.tables.Blocks
import me.santiagoreina.parcheu.api.platform.db.tables.Communities
import me.santiagoreina.parcheu.api.platform.db.tables.CommunityMembers
import me.santiagoreina.parcheu.api.platform.db.tables.Edges
import me.santiagoreina.parcheu.api.platform.db.tables.Simulations
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.SimulationOrigin
import me.santiagoreina.parcheu.contract.SimulationStatus
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Criterios de E3.1 contra Postgres 16 real: el filtro de bloqueados y borrados va en el SQL
 * (se comprueba llamando al repositorio sin la policy) y el servicio completo da el mismo
 * resultado. Necesita Docker o `PARCHEU_TEST_DB_URL`.
 */
class GraphDbTest : FunSpec({
    val now = OffsetDateTime.ofInstant(FIXED_CLOCK.instant(), ZoneOffset.UTC)

    fun Database.user(name: String, universityId: Short? = 1): UUID = transaction(this) {
        Users.insert {
            it[firebaseUid] = "uid-$name-${UUID.randomUUID()}"
            it[displayName] = name
            it[Users.universityId] = universityId
            it[createdAt] = now
            it[updatedAt] = now
        }[Users.id]
    }

    fun Database.edge(a: UUID, b: UUID, mode: Mode, state: EdgeState, affinity: Int?) = transaction(this) {
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

    fun withDb(block: suspend (Database) -> Unit) = TestDatabase.withCleanDatabase { ds ->
        runMigrations(ds)
        kotlinx.coroutines.runBlocking { block(connectExposed(ds)) }
    }

    test("Pareja propia visible; bloqueo en cualquier sentido y borrado se filtran ya en SQL")
        .config(enabled = TestDatabase.enabled) {
            withDb { db ->
                val me = db.user("Ana")
                val pareja = db.user("Pareja")
                val bloqueado = db.user("Bloqueado")
                val bloqueador = db.user("Bloqueador")
                val borrado = db.user("Borrado")
                db.edge(me, pareja, Mode.PAREJA, EdgeState.CONNECTED, 92)
                listOf(bloqueado, bloqueador, borrado).forEach { db.edge(me, it, Mode.AMISTAD, EdgeState.CONNECTED, 80) }
                db.block(me, bloqueado)
                db.block(bloqueador, me)
                db.delete(borrado)
                val repo = ExposedGraphRepository(db)

                // Solo SQL (sin policy): ya no aparecen.
                repo.egoEdges(me, GraphFilter()).map { it.other.id to it.mode } shouldBe listOf(pareja to Mode.PAREJA)

                val graph = graphService(repo).graph(me, GraphFilter())
                graph.nodes.map { it.id } shouldBe listOf(pareja.toString())
                graph.edges.single().mode shouldBe Mode.PAREJA
                graph.me.label shouldBe "Ana"

                // El otro extremo ve la misma arista de Pareja (es suya); un tercero no la ve en su ego-grafo.
                graphService(repo).graph(pareja, GraphFilter()).nodes.map { it.label } shouldBe listOf("Ana")
                graphService(repo).graph(bloqueado, GraphFilter()).nodes.shouldBeEmpty()
            }
        }

    test("stats.connections = 5 con 5 CONNECTED, 3 SIMULATED y 1 PENDING; SIMULATED sale como 'Alguien'")
        .config(enabled = TestDatabase.enabled) {
            withDb { db ->
                val me = db.user("Ana")
                val others = (1..9).map { db.user("Persona $it") }
                others.take(5).forEach { db.edge(me, it, Mode.AMISTAD, EdgeState.CONNECTED, 80) }
                others.drop(5).take(3).forEach { db.edge(me, it, Mode.AMISTAD, EdgeState.SIMULATED, 70) }
                db.edge(me, others[8], Mode.ESTUDIO, EdgeState.PENDING, null)
                val randomMatch = others[5]
                val simId = transaction(db) {
                    Simulations.insert {
                        it[mode] = Mode.AMISTAD
                        it[userA] = randomMatch
                        it[userB] = me
                        it[origin] = SimulationOrigin.RANDOM
                        it[status] = SimulationStatus.DONE
                        it[score] = 70
                        it[createdAt] = now
                    }[Simulations.id]
                }
                val service = graphService(ExposedGraphRepository(db))

                val graph = service.graph(me, GraphFilter())
                graph.stats.connections shouldBe 5
                graph.stats.simulated shouldBe 3
                graph.stats.pending shouldBe 1
                graph.stats.avgAffinity shouldBe 76 // (5*80 + 3*70) / 8 = 76.25; PENDING sin afinidad no cuenta
                graph.nodes.filter { it.state == EdgeState.SIMULATED }.forEach {
                    it.label shouldBe "Alguien de la UIS"
                    it.anonymous shouldBe true
                }
                (graph.nodes.map { it.id }.contains(randomMatch.toString())) shouldBe false

                val anonId = AnonIdSigner.forTests().anonId(me, randomMatch)
                val detail = service.node(me, anonId)
                detail.lastSimulation?.id shouldBe simId.toString()
                detail.lastSimulation?.other?.label shouldBe "Alguien de la UIS"
            }
        }

    test("filtros mode, minAffinity y community en SQL")
        .config(enabled = TestDatabase.enabled) {
            withDb { db ->
                val me = db.user("Ana")
                val a = db.user("A")
                val b = db.user("B")
                val c = db.user("C")
                db.edge(me, a, Mode.AMISTAD, EdgeState.CONNECTED, 90)
                db.edge(me, a, Mode.ESTUDIO, EdgeState.SIMULATED, 60)
                db.edge(me, b, Mode.AMISTAD, EdgeState.SIMULATED, 65)
                db.edge(me, c, Mode.PAREJA, EdgeState.PENDING, null)
                val community = transaction(db) {
                    val id = Communities.insert {
                        it[week] = LocalDate.of(2026, 10, 19)
                        it[name] = "Los del tinto"
                        it[stableKey] = "k1"
                        it[createdAt] = now
                    }[Communities.id]
                    listOf(me, a).forEach { u ->
                        CommunityMembers.insert {
                            it[communityId] = id
                            it[userId] = u
                        }
                    }
                    id
                }
                val repo = ExposedGraphRepository(db)

                repo.egoEdges(me, GraphFilter(mode = Mode.AMISTAD)).map { it.other.id } shouldContainExactlyInAnyOrder listOf(a, b)
                repo.egoEdges(me, GraphFilter(minAffinity = 65)).map { it.affinity } shouldContainExactlyInAnyOrder listOf(90, 65)
                repo.egoEdges(me, GraphFilter(mode = Mode.ESTUDIO, minAffinity = 61)).shouldBeEmpty()
                repo.egoEdges(me, GraphFilter(community = community)).map { it.mode } shouldContainExactlyInAnyOrder
                    listOf(Mode.AMISTAD, Mode.ESTUDIO)
                // b no es miembro: con la comunidad de otros no ve nada.
                repo.egoEdges(b, GraphFilter(community = community)).shouldBeEmpty()

                val filtered = graphService(repo).graph(me, GraphFilter(mode = Mode.AMISTAD, minAffinity = 70))
                filtered.nodes.map { it.id } shouldBe listOf(a.toString())
                filtered.stats.connections shouldBe 1
            }
        }

    test("EdgeService escribe en edges respetando user_low < user_high, bloqueos y concurrencia")
        .config(enabled = TestDatabase.enabled) {
            withDb { db ->
                val a = db.user("A")
                val b = db.user("B")
                val c = db.user("C")
                val repo = ExposedGraphRepository(db)
                val edges = EdgeService(repo, FIXED_CLOCK)
                val sim = transaction(db) {
                    Simulations.insert {
                        it[mode] = Mode.AMISTAD
                        it[userA] = b
                        it[userB] = a
                        it[origin] = SimulationOrigin.PARCHE
                        it[createdAt] = now
                    }[Simulations.id]
                }

                // Diez eventos simultáneos sobre la misma arista nueva dejan una sola fila.
                coroutineScope {
                    (1..10).map { async(Dispatchers.IO) { edges.apply(EdgeEvent.Simulated(b, a, Mode.AMISTAD, sim, 81)) } }.awaitAll()
                }
                edges.apply(EdgeEvent.InterestShown(a, b, Mode.AMISTAD, EdgeSource.DECISION))
                val connected = edges.apply(EdgeEvent.Connected(b, a, Mode.AMISTAD, EdgeSource.DECISION))
                connected?.state shouldBe EdgeState.CONNECTED
                connected?.affinity shouldBe 81
                connected?.lastSimulationId shouldBe sim

                val rows = transaction(db) { Edges.selectAll().toList() }
                rows.size shouldBe 1
                rows.single()[Edges.state] shouldBe EdgeState.CONNECTED
                rows.single()[Edges.connectedAt]?.toInstant() shouldBe FIXED_CLOCK.instant()

                db.block(c, a)
                edges.apply(EdgeEvent.Connected(a, c, Mode.ESTUDIO, EdgeSource.INVITE)).shouldBeNull()
                transaction(db) { Edges.selectAll().count() } shouldBe 1L
            }
        }
})
