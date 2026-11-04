package me.santiagoreina.parcheu.api.twin

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import me.santiagoreina.parcheu.contract.TraitKey
import me.santiagoreina.parcheu.contract.TwinAnswer

/**
 * Vectores dorados de `TraitEngine` con las 12 preguntas de las semillas (V2). El orden es el de
 * `TraitKey`: SOCIAL_ENERGY, PLANNING, CONFLICT_HANDLING, HUMOR, ACADEMIC_AMBITION, OPENNESS.
 * Los esperados se calcularon aparte a mano con la regla de arquitectura.md §7.1.
 */
class TraitEngineTest : FunSpec({
    fun answers(text: String): Map<Int, TwinAnswer> = text.mapIndexed { i, c ->
        (i + 1) to when (c) {
            'A' -> TwinAnswer.A
            'B' -> TwinAnswer.B
            else -> TwinAnswer.DEPENDS
        }
    }.toMap()

    fun compute(text: String) = TraitEngine.compute(SeedQuestions.all, answers(text))

    test("dorado 1: todo A; los pesos negativos invierten el polo en planificación y apertura") {
        compute("AAAAAAAAAAAA") shouldBe listOf(1.0, 0.8846, 1.0, 1.0, 1.0, 0.8846)
    }

    test("dorado 2: todo B es el espejo exacto") {
        compute("BBBBBBBBBBBB") shouldBe listOf(0.0, 0.1154, 0.0, 0.0, 0.0, 0.1154)
    }

    test("dorado 3: todo DEPENDE deja los 6 rasgos en 0,5") {
        compute("DDDDDDDDDDDD") shouldBe List(6) { 0.5 }
    }

    test("dorado 4: mezcla de A, B y DEPENDE") {
        compute("ABDABADBAABD") shouldBe listOf(0.6818, 0.4231, 0.2826, 0.8333, 0.4, 0.5577)
    }

    test("es determinista y entrega 6 valores en [0, 1]") {
        val first = compute("ABDABADBAABD")
        compute("ABDABADBAABD") shouldBe first
        first.size shouldBe TraitKey.entries.size
        first.forEach { (it in 0.0..1.0) shouldBe true }
    }

    test("un rasgo que ninguna pregunta pondera vale 0,5") {
        val only = listOf(WeightedQuestion(1, 1, "p", "a", "b", mapOf(TraitKey.HUMOR to 1.0)))
        val traits = TraitEngine.compute(only, mapOf(1 to TwinAnswer.A))
        traits[TraitKey.HUMOR.ordinal] shouldBe 1.0
        traits[TraitKey.OPENNESS.ordinal] shouldBe (0.5 plusOrMinus 0.0)
    }
})
