package me.santiagoreina.parcheu.api.privacy

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import me.santiagoreina.parcheu.api.graph.InMemoryGraphRepository
import me.santiagoreina.parcheu.api.graph.PersonRow
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Visibility
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

/** Arista no dirigida del mundo generado (una fila de `edges`). */
data class WorldEdge(val a: UUID, val b: UUID, val mode: Mode, val state: EdgeState, val affinity: Int?) {
    fun involves(id: UUID) = a == id || b == id
}

/**
 * Un grafo social completo generado al azar: usuarios de las tres visibilidades (algunos
 * borrados), aristas de los tres modos y estados, y bloqueos en ambos sentidos. Las sondas de la
 * suite lo cargan en los repositorios en memoria de cada vista.
 */
data class PrivacyWorld(
    val people: List<PersonRow>,
    val edges: List<WorldEdge>,
    /** (bloqueador, bloqueado). */
    val blocks: Set<Pair<UUID, UUID>>,
) {
    private val byId = people.associateBy { it.id }

    fun person(id: UUID): PersonRow? = byId[id]

    /** Observadores posibles: todo usuario no borrado (uno borrado no puede iniciar sesión). */
    fun observers(): List<UUID> = people.filter { it.deletedAt == null }.map { it.id }

    fun blockedEitherWay(viewer: UUID): Set<UUID> =
        blocks.mapNotNull { (blocker, blocked) ->
            when (viewer) {
                blocker -> blocked
                blocked -> blocker
                else -> null
            }
        }.toSet()

    /** Si [a] y [b] tienen alguna arista CONNECTED (en cualquier modo). */
    fun connected(a: UUID, b: UUID): Boolean =
        edges.any { it.state == EdgeState.CONNECTED && it.involves(a) && it.involves(b) }

    fun toGraphRepository(): InMemoryGraphRepository = InMemoryGraphRepository().also { repo ->
        people.forEach(repo::addPerson)
        edges.forEach { repo.putEdge(it.a, it.b, it.mode, it.state, it.affinity) }
        blocks.forEach { (blocker, blocked) -> repo.block(blocker, blocked) }
    }

    /** Compacto, para que el caso mínimo que imprime Kotest se pueda leer. */
    override fun toString(): String {
        val names = people.withIndex().associate { (i, p) -> p.id to "u$i" }
        val users = people.withIndex().joinToString { (i, p) -> "u$i(${p.visibility}${if (p.deletedAt != null) ",borrado" else ""})" }
        val edgeText = edges.joinToString { "${names[it.a]}-${names[it.b]}:${it.mode}/${it.state}" }
        val blockText = blocks.joinToString { "${names[it.first]}⊘${names[it.second]}" }
        return "PrivacyWorld(usuarios=[$users], aristas=[$edgeText], bloqueos=[$blockText])"
    }

    companion object {
        private val UNIVERSITIES = listOf("UIS", "UNAB", "UPB", "Otra", null)
        private val DELETED_AT: Instant = Instant.parse("2026-10-01T00:00:00Z")

        /** Mundos de 2 a 12 usuarios; cada par tiene arista en cada modo con probabilidad 0,35. */
        fun arb(): Arb<PrivacyWorld> = arbitrary { rs -> generate(rs.random) }

        fun generate(random: Random): PrivacyWorld {
            val size = random.nextInt(2, 13)
            val people = List(size) { i ->
                PersonRow(
                    id = UUID(random.nextLong(), random.nextLong()),
                    displayName = if (random.nextInt(10) == 0) null else "Persona $i",
                    photoUrl = "https://example.test/$i.png",
                    universityShort = UNIVERSITIES.random(random),
                    visibility = Visibility.entries.random(random),
                    isDemo = random.nextInt(10) == 0,
                    deletedAt = if (random.nextInt(100) < 15) DELETED_AT else null,
                )
            }
            val edges = buildList {
                for (i in people.indices) {
                    for (j in i + 1 until people.size) {
                        for (mode in Mode.entries) {
                            if (random.nextInt(100) >= 35) continue
                            val affinity = if (random.nextInt(10) == 0) null else random.nextInt(0, 101)
                            add(WorldEdge(people[i].id, people[j].id, mode, EdgeState.entries.random(random), affinity))
                        }
                    }
                }
            }
            val blocks = buildSet {
                for (a in people) for (b in people) if (a.id != b.id && random.nextInt(100) < 6) add(a.id to b.id)
            }
            return PrivacyWorld(people, edges, blocks)
        }
    }
}
