package me.santiagoreina.parcheu.api.simulation

import me.santiagoreina.parcheu.contract.MemoryStatus
import me.santiagoreina.parcheu.contract.Mode
import java.time.Instant
import java.util.UUID

/** Una fila de `twin_memories` tal como la necesita la selección (sin tipos de Exposed). */
data class CandidateMemory(
    val id: UUID,
    val mode: Mode,
    val text: String,
    val status: MemoryStatus,
    val createdAt: Instant,
)

/**
 * Costura única que decide qué memorias pueden entrar al prompt de un `TwinAgent` (ADR-0032):
 * solo ACEPTADAS **del modo de la simulación**, como máximo [MAX_MEMORIES] (las más recientes) y
 * saneadas como datos. El `TwinPromptBuilder` del motor de simulación (E4.3) es el dueño del
 * prompt y DEBE obtener sus memorias de [select]; así la suite de privacidad (E3.5) cubre al
 * builder real sin que este PR declare otro `TwinPromptBuilder` ni otro `TwinMemory`.
 */
object MemorySelection {
    const val MAX_MEMORIES = 10
    const val MAX_MEMORY_CHARS = 280

    const val OPEN = "<memorias>"
    const val CLOSE = "</memorias>"

    /** Memorias que pueden entrar a una simulación de modo [mode], de la más reciente a la más antigua. */
    fun select(mode: Mode, memories: List<CandidateMemory>): List<CandidateMemory> = memories
        .filter { it.status == MemoryStatus.ACCEPTED }
        .filter { it.mode == mode }
        .sortedWith(compareByDescending<CandidateMemory> { it.createdAt }.thenBy { it.id })
        .take(MAX_MEMORIES)

    /** Texto de una memoria listo para el prompt: sin delimitadores ni saltos de línea, con tope. */
    fun sanitize(text: String): String = text
        .replace(OPEN, "")
        .replace(CLOSE, "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(MAX_MEMORY_CHARS)
}
