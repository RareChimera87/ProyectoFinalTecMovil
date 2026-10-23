package me.santiagoreina.parcheu.api.twin

import me.santiagoreina.parcheu.api.ai.AiCall
import me.santiagoreina.parcheu.api.ai.AiGateway
import me.santiagoreina.parcheu.api.ai.AiPurpose
import me.santiagoreina.parcheu.api.ai.LlmInvalidOutput
import me.santiagoreina.parcheu.contract.TwinAnswer
import java.util.UUID

/**
 * Arma el resumen de persona (unas 120 palabras, español colombiano coloquial) con el modelo lite.
 *
 * Privacidad por construcción (ADR-0032, requisito 17): [build] solo recibe un [PersonaInput], es
 * decir las preguntas con las respuestas. No recibe el usuario, ni su perfil, ni memorias, así que
 * el prompt no puede contener correo, nombre, fecha de nacimiento ni memorias. [userId] va solo a
 * la contabilidad de `ai_usage`, nunca al texto.
 */
class PersonaBuilder(private val ai: AiGateway) {
    suspend fun build(input: PersonaInput, userId: UUID): String {
        val result = ai.text(
            AiCall(
                purpose = AiPurpose.PERSONA,
                prompt = prompt(input),
                system = SYSTEM,
                userId = userId,
                maxOutputTokens = 1024,
                temperature = 0.7,
            ),
        )
        var text = result.text.trim()
        // Si el modelo cortó la respuesta, se queda con la última oración completa.
        if (result.truncated && '.' in text) text = text.substring(0, text.lastIndexOf('.') + 1)
        if (text.isBlank()) throw LlmInvalidOutput("El modelo devolvió una persona vacía", result.usage)
        return text
    }

    companion object {
        const val SYSTEM = "Eres un redactor que describe la forma de ser de un estudiante universitario colombiano a partir de sus " +
            "respuestas a 12 preguntas de preferencia. Escribe en español colombiano coloquial, en tercera persona, en un solo " +
            "párrafo de unas 120 palabras. Describe cómo socializa, cómo se organiza, cómo maneja los desacuerdos, su humor, " +
            "su ambición académica y su apertura a lo nuevo. No inventes datos que no estén en las respuestas. No incluyas " +
            "nombres, correos, teléfonos, universidad, edad ni fecha de nacimiento. Responde solo con el párrafo."

        fun prompt(input: PersonaInput): String = buildString {
            appendLine("Respuestas del estudiante (cada pregunta tiene dos opciones, A y B):")
            input.answers.forEachIndexed { i, (q, answer) ->
                appendLine()
                appendLine("${i + 1}. ${q.text}")
                when (answer) {
                    TwinAnswer.A -> appendLine("   Eligió: ${q.optionA}")
                    TwinAnswer.B -> appendLine("   Eligió: ${q.optionB}")
                    TwinAnswer.DEPENDS -> appendLine("   Dijo que depende (entre \"${q.optionA}\" y \"${q.optionB}\")")
                }
            }
        }
    }
}
