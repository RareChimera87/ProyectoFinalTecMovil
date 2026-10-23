package me.santiagoreina.parcheu.api.twin

import me.santiagoreina.parcheu.contract.TraitKey
import me.santiagoreina.parcheu.contract.TwinAnswer
import kotlin.math.abs
import kotlin.math.round

/** Una pregunta con sus pesos por rasgo (`questions.weights`). Un peso negativo invierte los polos. */
data class WeightedQuestion(
    val id: Int,
    val ord: Int,
    val text: String,
    val optionA: String,
    val optionB: String,
    val weights: Map<TraitKey, Double>,
)

/**
 * Cálculo determinista de los 6 rasgos (arquitectura.md §7.1). Para cada rasgo, cada pregunta que
 * lo pondera aporta una masa `|peso|` repartida entre el polo alto (1) y el bajo (0):
 * - A con peso positivo (o B con peso negativo) manda toda la masa al polo alto;
 * - B con peso positivo (o A con peso negativo) la manda al polo bajo;
 * - DEPENDS reparte la mitad a cada polo.
 * El rasgo es `masa alta / masa total` redondeado a 4 decimales; sin preguntas que lo pondere vale 0,5.
 * Sin aleatoriedad ni reloj: las mismas respuestas dan siempre el mismo vector.
 */
object TraitEngine {
    const val DECIMALS = 10_000.0

    /** Devuelve los 6 valores en el orden de [TraitKey.entries]. */
    fun compute(questions: List<WeightedQuestion>, answers: Map<Int, TwinAnswer>): List<Double> {
        val high = DoubleArray(TraitKey.entries.size)
        val total = DoubleArray(TraitKey.entries.size)
        for (question in questions) {
            val answer = answers[question.id] ?: continue
            for ((trait, weight) in question.weights) {
                val mass = abs(weight)
                val i = trait.ordinal
                total[i] += mass
                high[i] += when (answer) {
                    TwinAnswer.DEPENDS -> mass / 2
                    TwinAnswer.A -> if (weight > 0) mass else 0.0
                    TwinAnswer.B -> if (weight < 0) mass else 0.0
                }
            }
        }
        return TraitKey.entries.map { trait ->
            val i = trait.ordinal
            if (total[i] == 0.0) 0.5 else round(high[i] / total[i] * DECIMALS) / DECIMALS
        }
    }
}
