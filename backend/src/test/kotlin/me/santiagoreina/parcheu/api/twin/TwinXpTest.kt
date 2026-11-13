package me.santiagoreina.parcheu.api.twin

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.ai.testGateway
import java.util.UUID

/** Regla de XP y nivel (arquitectura.md §7.1, punto 6) sin base: el conteo de eventos lo da el repositorio. */
class TwinXpTest : FunSpec({
    test("cada evento suma su XP y el nivel sube cada 500") {
        TwinActivity.NONE.xp shouldBe 0
        TwinActivity(acceptedMemories = 2, ratings = 3, controlAnswers = 1, doneSimulations = 4).xp shouldBe
            2 * XP_PER_ACCEPTED_MEMORY + 3 * XP_PER_RATING + XP_PER_CONTROL_ANSWER + 4 * XP_PER_DONE_SIMULATION

        val base = TwinState(traits = null, personaReady = false, version = 1, level = 1, xp = 120, accuracy = null)
        base.withActivity(TwinActivity.NONE) shouldBe base
        val subido = base.withActivity(TwinActivity(doneSimulations = 19))
        subido.xp shouldBe 500
        subido.level shouldBe 2
    }

    test("GET del gemelo suma la actividad del repositorio sin acumularla entre lecturas") {
        val repo = InMemoryTwinRepository()
        val module = inMemoryTwinModule(FIXED_CLOCK, testGateway(), repo = repo)
        val ana = UUID.randomUUID()
        runBlocking { module.service.submitAnswers(ana, answersOf()) }.xp shouldBe 120
        repo.activities[ana] = TwinActivity(acceptedMemories = 1, ratings = 2)
        val esperado = 120 + XP_PER_ACCEPTED_MEMORY + 2 * XP_PER_RATING
        runBlocking { module.service.twin(ana) }.xp shouldBe esperado
        runBlocking { module.service.twin(ana) }.xp shouldBe esperado
        module.service.progress(ana)?.xp shouldBe esperado
    }
})
