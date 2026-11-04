package me.santiagoreina.parcheu.api.twin.feedback

import me.santiagoreina.parcheu.api.twin.TraitEngine
import me.santiagoreina.parcheu.api.twin.WeightedQuestion
import me.santiagoreina.parcheu.contract.TwinAnswer
import kotlin.math.roundToInt

/**
 * 'Qué tanto te conoce' (arquitectura.md §7.1, punto 5): 50 % proporción de calificaciones positivas
 * de respuestas simuladas + 50 % aciertos del gemelo en la pregunta de control.
 */
object AccuracyMetric {
    /**
     * Porcentaje (0-100) con las dos mitades al 50 %. Si solo una tiene datos esa semana, vale por sí
     * sola (no se castiga con un 0 una mitad que no se pudo medir). Sin ningún dato devuelve null: no
     * se guarda punto.
     */
    fun percent(stats: WeekStats): Double? {
        val parts = buildList {
            if (stats.ratings > 0) add(stats.positive.toDouble() / stats.ratings)
            if (stats.predictions > 0) add(stats.correct.toDouble() / stats.predictions)
        }
        if (parts.isEmpty()) return null
        return parts.average() * 100
    }

    /** Valor que se guarda en `twin_accuracy.accuracy` (smallint): 77,5 % se guarda como 78. */
    fun stored(stats: WeekStats): Int? = percent(stats)?.roundToInt()
}

/**
 * Predicción del gemelo para la pregunta de control, determinista y sin llamar a la IA: calcula los
 * rasgos **sin** la respuesta a esa pregunta y mira hacia qué polo la inclinan los pesos de la
 * pregunta. Si la inclinación es casi nula predice DEPENDS.
 */
object ControlPredictor {
    private const val NEUTRAL_BAND = 0.02

    fun predict(questions: List<WeightedQuestion>, answers: Map<Int, TwinAnswer>, target: WeightedQuestion): TwinAnswer {
        val traits = TraitEngine.compute(questions, answers - target.id)
        val lean = target.weights.entries.sumOf { (trait, weight) -> weight * (traits[trait.ordinal] - 0.5) }
        return when {
            lean > NEUTRAL_BAND -> TwinAnswer.A
            lean < -NEUTRAL_BAND -> TwinAnswer.B
            else -> TwinAnswer.DEPENDS
        }
    }
}
