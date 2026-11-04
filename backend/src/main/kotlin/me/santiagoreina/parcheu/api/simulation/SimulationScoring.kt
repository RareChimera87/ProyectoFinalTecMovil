package me.santiagoreina.parcheu.api.simulation

import me.santiagoreina.parcheu.contract.Dimension
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/** Puntaje e informe (arquitectura.md §7.2, punto 7; ADR-0009). Funciones puras. */
object SimulationScoring {
    const val TRAIT_WEIGHT = 0.35
    const val JUDGE_WEIGHT = 0.65

    /** `100 · (1 − ‖tA − tB‖₂ / √6)`, en [0, 100]. */
    fun traitSimilarity(a: List<Double>, b: List<Double>): Double {
        require(a.size == b.size && a.isNotEmpty()) { "Los vectores de rasgos deben tener el mismo tamaño" }
        val distance = sqrt(a.zip(b).sumOf { (x, y) -> (x - y) * (x - y) })
        return (100 * (1 - distance / sqrt(a.size.toDouble()))).coerceIn(0.0, 100.0)
    }

    /** `round(0.35 · simRasgos + 0.65 · promedioJuez)`, sin acotar el rango más allá de [0, 100]. */
    fun score(traitSimilarity: Double, judgeAverage: Double): Int =
        (TRAIT_WEIGHT * traitSimilarity + JUDGE_WEIGHT * judgeAverage).roundToInt().coerceIn(0, 100)

    /**
     * Informe de la simulación. Sin rasgos de alguna de las dos personas (solo en el modo escenario, E4.8, si un
     * gemelo ya no existe) la similitud se iguala al promedio del juez: el puntaje es entonces el del juez.
     */
    fun report(traitsA: List<Double>?, traitsB: List<Double>?, verdicts: List<JudgeVerdict>): StoredReport {
        require(verdicts.isNotEmpty()) { "Sin veredictos no hay informe" }
        val judgeAverage = verdicts.map { it.affinity }.average()
        val similarity = if (traitsA != null && traitsB != null) traitSimilarity(traitsA, traitsB) else judgeAverage
        return StoredReport(
            score = score(similarity, judgeAverage),
            traitSimilarity = similarity,
            judgeAverage = judgeAverage,
            summary = summaryOf(verdicts, judgeAverage),
            reasons = interleave(verdicts.map { it.reasons }).take(3),
            frictions = interleave(verdicts.map { it.frictions }).take(2),
            dimensions = averageDimensions(verdicts),
            scenarioScores = verdicts.map { it.affinity },
        )
    }

    /**
     * Un solo resumen: el del escenario cuya afinidad está más cerca del promedio (el más
     * representativo); en empate, el primero. Unir los tres repetía la misma idea.
     */
    private fun summaryOf(verdicts: List<JudgeVerdict>, average: Double): String =
        verdicts.filter { it.summary.isNotBlank() }.minByOrNull { kotlin.math.abs(it.affinity - average) }?.summary.orEmpty()

    /** Toma por turnos de cada escenario (1.º de cada uno, luego 2.º...) sin repetir. */
    private fun interleave(lists: List<List<String>>): List<String> {
        val out = linkedSetOf<String>()
        val max = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until max) lists.forEach { l -> l.getOrNull(i)?.let { out += it } }
        return out.toList()
    }

    /** Promedio por dimensión (en el orden en que aparecen) con la primera nota no vacía. */
    private fun averageDimensions(verdicts: List<JudgeVerdict>): List<Dimension> =
        verdicts.flatMap { it.dimensions }.groupBy { it.name }.map { (name, ds) ->
            Dimension(name, ds.map { it.score }.average().roundToInt(), ds.firstNotNullOfOrNull { it.note })
        }

    /**
     * Elige [SCENARIOS_PER_SIMULATION] escenarios del modo: solo básicos si [plus] es false. El orden
     * sale de una semilla derivada del id de la simulación (arquitectura.md §7.2, punto 3).
     */
    fun pickScenarios(all: List<ScenarioDef>, plus: Boolean, simulationId: UUID): List<ScenarioDef> {
        val pool = all.filter { plus || it.isBasic }.sortedBy { it.id }
        check(pool.size >= SCENARIOS_PER_SIMULATION) { "Faltan escenarios: hay ${pool.size} y se necesitan $SCENARIOS_PER_SIMULATION" }
        val seed = simulationId.mostSignificantBits xor simulationId.leastSignificantBits
        return pool.shuffled(Random(seed)).take(SCENARIOS_PER_SIMULATION)
    }
}
