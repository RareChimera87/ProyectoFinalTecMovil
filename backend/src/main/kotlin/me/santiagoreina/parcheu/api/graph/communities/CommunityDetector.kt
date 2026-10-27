package me.santiagoreina.parcheu.api.graph.communities

import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Visibility
import java.time.LocalDate
import java.util.UUID

/**
 * Detección semanal de comunidades (arquitectura.md §6.3): función pura sobre las aristas.
 *
 * Solo cuentan las aristas CONNECTED de Amistad y Estudio (la **Pareja nunca** entra, aunque llegue en
 * la lista) entre personas que no son HIDDEN ni están borradas: segunda barrera tras el SQL del
 * repositorio. El peso de un par es la suma de las afinidades de sus aristas (50 si falta) y las
 * comunidades son los grupos de la propagación de etiquetas con al menos [MIN_MEMBERS] personas.
 */
object CommunityDetector {
    const val MIN_MEMBERS = 3
    const val JACCARD_MIN = 0.5

    /** Semilla fija: las mismas aristas dan siempre las mismas comunidades. */
    const val SEED = 6_4_2026L

    /** Afinidad que se supone cuando una arista aún no la tiene. */
    const val NEUTRAL_AFFINITY = 50

    /** Comunidades como conjuntos de `users.id`, de mayor a menor y desempatadas por el menor id. */
    fun detect(edges: List<CommunityEdge>, seed: Long = SEED): List<Set<UUID>> {
        val weights = HashMap<UUID, MutableMap<UUID, Double>>()
        for (edge in edges) {
            if (edge.mode == Mode.PAREJA) continue
            if (!eligible(edge.a) || !eligible(edge.b) || edge.a.id == edge.b.id) continue
            val weight = (edge.affinity ?: NEUTRAL_AFFINITY).coerceAtLeast(1).toDouble()
            weights.getOrPut(edge.a.id) { HashMap() }.merge(edge.b.id, weight, Double::plus)
            weights.getOrPut(edge.b.id) { HashMap() }.merge(edge.a.id, weight, Double::plus)
        }
        val labels = LabelPropagation.run(weights, seed)
        return labels.entries.groupBy({ it.value }, { it.key })
            .values
            .map { it.toSet() }
            .filter { it.size >= MIN_MEMBERS }
            .sortedWith(compareByDescending<Set<UUID>> { it.size }.thenBy { it.min() })
    }

    private fun eligible(person: me.santiagoreina.parcheu.api.graph.PersonRow) =
        person.deletedAt == null && person.visibility != Visibility.HIDDEN

    fun jaccard(a: Set<UUID>, b: Set<UUID>): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        val shared = a.count { it in b }
        return shared.toDouble() / (a.size + b.size - shared)
    }

    /**
     * IDs estables: cada comunidad nueva se empareja con la existente de mayor Jaccard (>= [JACCARD_MIN]),
     * una a una de mayor a menor similitud (desempate por id existente y por posición). La emparejada
     * conserva id, nombre y `stable_key`; las demás se crean con [PENDING_NAME] y las existentes sin
     * pareja se eliminan.
     */
    fun plan(week: LocalDate, detected: List<Set<UUID>>, existing: List<StoredCommunity>): CommunityPlan {
        val pairs = buildList {
            detected.forEachIndexed { i, members ->
                existing.forEach { old ->
                    val j = jaccard(members, old.members)
                    if (j >= JACCARD_MIN) add(Triple(i, old, j))
                }
            }
        }.sortedWith(compareByDescending<Triple<Int, StoredCommunity, Double>> { it.third }.thenBy { it.second.id }.thenBy { it.first })
        val matchOf = HashMap<Int, StoredCommunity>()
        val used = HashSet<UUID>()
        for ((i, old, _) in pairs) {
            if (i in matchOf || old.id in used) continue
            matchOf[i] = old
            used += old.id
        }
        val kept = mutableListOf<StoredCommunity>()
        val created = mutableListOf<StoredCommunity>()
        detected.forEachIndexed { i, members ->
            val old = matchOf[i]
            if (old != null) {
                kept += old.copy(week = week, members = members)
            } else {
                val id = newId(week, members)
                created += StoredCommunity(id, week, PENDING_NAME, id.toString(), members)
            }
        }
        return CommunityPlan(kept, created, existing.map { it.id }.filter { it !in used }.toSet())
    }

    /** Id determinista de una comunidad nueva (semana + miembros): reintentar el job no crea duplicados. */
    private fun newId(week: LocalDate, members: Set<UUID>): UUID =
        UUID.nameUUIDFromBytes("community:$week:${members.sorted().joinToString(",")}".toByteArray())

    /** Nombre mientras la tarea `name-community` no lo reemplaza. */
    const val PENDING_NAME = "Comunidad nueva"
}
