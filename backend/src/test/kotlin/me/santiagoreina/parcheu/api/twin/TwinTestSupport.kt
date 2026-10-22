package me.santiagoreina.parcheu.api.twin

import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.ai.FakeLlmClient
import me.santiagoreina.parcheu.api.ai.testGateway
import me.santiagoreina.parcheu.api.platform.tasks.FakeTaskQueue
import me.santiagoreina.parcheu.contract.Answer
import me.santiagoreina.parcheu.contract.Answers
import me.santiagoreina.parcheu.contract.TwinAnswer

/** Las 12 respuestas de [text] ('A', 'B' o 'D' por pregunta). */
fun answersOf(text: String = "ABDABADBAABD") = Answers(
    text.mapIndexed { i, c -> Answer(i + 1, when (c) { 'A' -> TwinAnswer.A; 'B' -> TwinAnswer.B; else -> TwinAnswer.DEPENDS }) },
)

const val PERSONA_TEXT = "Persona simulada de unas ciento veinte palabras."

/** Gemelo en memoria con Fake de IA y cola Fake que NO ejecuta en línea (los tests disparan la tarea). */
class TwinFixture(
    val fake: FakeLlmClient = FakeLlmClient(textResponder = { PERSONA_TEXT }),
    val repo: InMemoryTwinRepository = InMemoryTwinRepository(),
    val queue: FakeTaskQueue = FakeTaskQueue(),
) {
    val gateway = testGateway(fake)
    val module = inMemoryTwinModule(FIXED_CLOCK, gateway, repo, queue, runInline = false)
}
