package me.santiagoreina.parcheu.api.graph.communities

import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.TraitKey

/**
 * Lo único que se sabe de una comunidad para describirla o nombrarla: tamaño, rasgos agregados y modos.
 * Por construcción no contiene nombres, correos ni ids de personas (el prompt del nombre se arma solo con
 * esto, igual que `PersonaBuilder` solo recibe respuestas).
 */
data class CommunityProfile(val size: Int, val traits: List<String>, val modes: List<String>) {
    companion object {
        /** Cuánto debe alejarse el promedio de 0,5 para considerarlo un rasgo de la comunidad. */
        const val THRESHOLD = 0.15
        const val MAX_TRAITS = 3

        private val HIGH = mapOf(
            TraitKey.SOCIAL_ENERGY to "muy sociables",
            TraitKey.PLANNING to "organizados",
            TraitKey.CONFLICT_HANDLING to "directos con los desacuerdos",
            TraitKey.HUMOR to "de mucho humor",
            TraitKey.ACADEMIC_AMBITION to "ambiciosos con la U",
            TraitKey.OPENNESS to "abiertos a lo nuevo",
        )
        private val LOW = mapOf(
            TraitKey.SOCIAL_ENERGY to "tranquilos y reservados",
            TraitKey.PLANNING to "espontáneos",
            TraitKey.CONFLICT_HANDLING to "conciliadores",
            TraitKey.HUMOR to "de humor serio",
            TraitKey.ACADEMIC_AMBITION to "relajados con la U",
            TraitKey.OPENNESS to "de gustos clásicos",
        )

        /** Rasgos de [members] (cada lista en el orden de [TraitKey]) y modos con más aristas internas. */
        fun of(size: Int, members: List<List<Double>>, internalModes: List<Mode>): CommunityProfile {
            val averages = TraitKey.entries.mapIndexedNotNull { i, key ->
                val values = members.mapNotNull { it.getOrNull(i) }
                if (values.isEmpty()) null else key to values.average()
            }
            val traits = averages
                .filter { (_, avg) -> kotlin.math.abs(avg - 0.5) >= THRESHOLD }
                .sortedByDescending { (_, avg) -> kotlin.math.abs(avg - 0.5) }
                .take(MAX_TRAITS)
                .map { (key, avg) -> (if (avg > 0.5) HIGH else LOW).getValue(key) }
            val modeCounts = internalModes.groupingBy { it }.eachCount()
            val modes = modeCounts.entries.sortedByDescending { it.value }.map { modeLabel(it.key) }.take(1)
            return CommunityProfile(size, traits, modes)
        }

        fun modeLabel(mode: Mode): String = when (mode) {
            Mode.AMISTAD -> "Amistad"
            Mode.ESTUDIO -> "Estudio"
            Mode.PAREJA -> "Pareja"
        }
    }
}
