package me.santiagoreina.parcheu.core.error

import me.santiagoreina.parcheu.feature.chat.errorMessage
import me.santiagoreina.parcheu.feature.conocer.scenario.ScenarioStrings
import me.santiagoreina.parcheu.feature.evolution.evolutionMessage
import me.santiagoreina.parcheu.feature.traintwin.message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** El 503 AI_BUDGET llega con texto propio a las pantallas que antes lo mostraban como 'Algo salió mal'. */
class AiBudgetMessagesTest {
    private val budget = AppError.AiBudget(retryAt = null)

    @Test
    fun theMapperProducesAiBudgetFor503AiBudget() {
        val body = """{"code":"AI_BUDGET","message":"x"}"""
        assertEquals(AppError.AiBudget(null), ApiErrorMapper().map(503, body))
    }

    @Test
    fun theMessageIsClearAndSpecific() {
        assertEquals("La IA llegó a su límite por hoy. Intenta más tarde.", AI_BUDGET_MESSAGE)
    }

    @Test
    fun chatUsesTheAiBudgetMessage() {
        assertEquals(AI_BUDGET_MESSAGE, errorMessage(budget))
    }

    @Test
    fun scenarioUsesTheAiBudgetMessage() {
        assertEquals(AI_BUDGET_MESSAGE, ScenarioStrings.error(budget))
    }

    @Test
    fun evolutionUsesTheAiBudgetMessage() {
        assertEquals(AI_BUDGET_MESSAGE, budget.evolutionMessage())
    }

    @Test
    fun trainTwinUsesTheAiBudgetMessage() {
        assertEquals(AI_BUDGET_MESSAGE, budget.message())
    }

    @Test
    fun otherErrorsKeepTheirGenericText() {
        assertNotEquals(AI_BUDGET_MESSAGE, AppError.Unknown().message())
    }
}
