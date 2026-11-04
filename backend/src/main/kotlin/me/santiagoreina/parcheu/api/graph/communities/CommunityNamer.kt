package me.santiagoreina.parcheu.api.graph.communities

import me.santiagoreina.parcheu.api.ai.AiCall
import me.santiagoreina.parcheu.api.ai.AiGateway
import me.santiagoreina.parcheu.api.ai.AiPurpose

/**
 * Nombre creativo de una comunidad con el modelo lite (arquitectura.md §6.3).
 *
 * Privacidad por construcción: [name] solo recibe un [CommunityProfile] (tamaño, rasgos agregados y
 * modos), nunca personas, así que el prompt no puede llevar nombres ni correos.
 */
class CommunityNamer(private val ai: AiGateway) {
    suspend fun name(profile: CommunityProfile): String {
        val result = ai.text(
            AiCall(
                purpose = AiPurpose.COMMUNITY_LABEL,
                prompt = prompt(profile),
                system = SYSTEM,
                maxOutputTokens = 64,
                temperature = 0.9,
            ),
        )
        return clean(result.text) ?: fallback(profile)
    }

    companion object {
        const val MAX_LENGTH = 40

        const val SYSTEM = "Eres quien bautiza grupos de estudiantes universitarios colombianos. Inventa un nombre creativo, corto " +
            "(de 2 a 5 palabras) y en español colombiano coloquial, como \"Los del tinto de las 7\". Usa solo los rasgos que te " +
            "doy. No uses nombres de personas, apellidos, correos ni universidades. Responde solo con el nombre, sin comillas ni explicación."

        fun prompt(profile: CommunityProfile): String = buildString {
            appendLine("Grupo de ${profile.size} estudiantes.")
            appendLine("Rasgos en común: ${profile.traits.joinToString().ifEmpty { "equilibrados, sin un rasgo marcado" }}.")
            appendLine("Tipo de vínculo: ${profile.modes.joinToString().ifEmpty { "Amistad" }}.")
        }

        /** Primera línea sin comillas ni adornos y de a lo sumo [MAX_LENGTH] caracteres; null si queda vacía. */
        fun clean(raw: String): String? {
            val line = raw.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
            val text = line.trim('"', '\'', '“', '”', '«', '»', '*', '#', ' ').take(MAX_LENGTH).trim()
            return text.takeIf { it.isNotEmpty() }
        }

        /** Nombre determinista cuando el modelo no devuelve nada utilizable. */
        fun fallback(profile: CommunityProfile): String =
            profile.traits.firstOrNull()?.let { "Los $it".take(MAX_LENGTH) }
                ?: "Parche de ${profile.size}"
    }
}
