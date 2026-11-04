package me.santiagoreina.parcheu.api.graph.communities

import java.util.UUID
import kotlin.random.Random

/**
 * Propagación de etiquetas ponderada (arquitectura.md §6.3), en Kotlin y sin librerías.
 *
 * Cada nodo empieza con su propio id como etiqueta y, en cada pasada, adopta la etiqueta con mayor peso
 * total entre sus vecinos (el peso es la afinidad de la arista). Es determinista:
 * - el orden de visita sale de barajar los nodos (ordenados por id) con una semilla fija;
 * - los vecinos se suman en orden de id y un empate se resuelve conservando la etiqueta actual si es
 *   una de las mejores y, si no, con la menor (por id).
 * Cada pasada es O(aristas); termina cuando nadie cambia o al llegar a [MAX_ITERATIONS].
 */
object LabelPropagation {
    const val MAX_ITERATIONS = 20

    /** Grafo no dirigido y ponderado: `weights[a][b] == weights[b][a]`. */
    fun run(
        weights: Map<UUID, Map<UUID, Double>>,
        seed: Long,
        maxIterations: Int = MAX_ITERATIONS,
    ): Map<UUID, UUID> {
        val nodes = weights.keys.sorted()
        val neighbors = nodes.associateWith { node -> weights.getValue(node).entries.sortedBy { it.key } }
        val labels = nodes.associateWithTo(HashMap()) { it }
        val order = nodes.shuffled(Random(seed))
        repeat(maxIterations) {
            var changed = false
            for (node in order) {
                val sums = HashMap<UUID, Double>()
                for ((other, weight) in neighbors.getValue(node)) {
                    val label = labels.getValue(other)
                    sums[label] = (sums[label] ?: 0.0) + weight
                }
                if (sums.isEmpty()) continue
                val best = sums.values.max()
                val winners = sums.filterValues { it == best }.keys
                val current = labels.getValue(node)
                val next = if (current in winners) current else winners.min()
                if (next != current) {
                    labels[node] = next
                    changed = true
                }
            }
            if (!changed) return labels
        }
        return labels
    }
}
