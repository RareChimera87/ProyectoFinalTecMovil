package me.santiagoreina.parcheu.feature.twinquestions

import androidx.annotation.DrawableRes
import me.santiagoreina.parcheu.R

/** Fotos de las dos opciones de una pregunta de B1: [a] ilustra la opción A y [b] la B. */
data class OptionImages(@param:DrawableRes val a: Int, @param:DrawableRes val b: Int)

/**
 * Fotos decorativas de las 12 preguntas de la semilla (backend V2__semillas.sql, ids 1 a 12). Créditos y
 * licencias en docs/creditos-imagenes.md. Son referencias directas a `R.drawable` a propósito: con
 * `getIdentifier` el reductor de recursos de R8 las quitaría del APK minificado. Una pregunta nueva del
 * servidor que no esté aquí se muestra sin foto.
 */
object TwinQuestionImages {
    private val byQuestionId: Map<Int, OptionImages> =
        mapOf(
            1 to OptionImages(R.drawable.twin_q01_a, R.drawable.twin_q01_b),
            2 to OptionImages(R.drawable.twin_q02_a, R.drawable.twin_q02_b),
            3 to OptionImages(R.drawable.twin_q03_a, R.drawable.twin_q03_b),
            4 to OptionImages(R.drawable.twin_q04_a, R.drawable.twin_q04_b),
            5 to OptionImages(R.drawable.twin_q05_a, R.drawable.twin_q05_b),
            6 to OptionImages(R.drawable.twin_q06_a, R.drawable.twin_q06_b),
            7 to OptionImages(R.drawable.twin_q07_a, R.drawable.twin_q07_b),
            8 to OptionImages(R.drawable.twin_q08_a, R.drawable.twin_q08_b),
            9 to OptionImages(R.drawable.twin_q09_a, R.drawable.twin_q09_b),
            10 to OptionImages(R.drawable.twin_q10_a, R.drawable.twin_q10_b),
            11 to OptionImages(R.drawable.twin_q11_a, R.drawable.twin_q11_b),
            12 to OptionImages(R.drawable.twin_q12_a, R.drawable.twin_q12_b)
        )

    /** Fotos de la pregunta [questionId], o null si no tiene (la tarjeta queda solo con texto). */
    fun forQuestion(questionId: Int): OptionImages? = byQuestionId[questionId]
}
