package me.santiagoreina.parcheu.api.twin

import me.santiagoreina.parcheu.contract.TwinAnswer
import java.time.Instant
import java.util.UUID

/** Persistencia del gemelo (arquitectura.md §4): preguntas, respuestas, rasgos, persona y precisión. */
interface TwinRepository {
    /** Las preguntas ordenadas por `ord`. */
    fun questions(): List<WeightedQuestion>

    /**
     * Guarda las respuestas y los [traits] calculados en una sola transacción. Primera vez: crea el
     * gemelo (versión 1) y suma el XP de las respuestas. Si ya había respuestas: sube la versión,
     * deja `persona_ready = false` y no vuelve a sumar XP. No toca `persona_summary` ni las memorias.
     */
    fun saveAnswers(userId: UUID, answers: Map<Int, TwinAnswer>, traits: List<Double>, now: Instant): SavedAnswers

    /** Estado actual, o null si el usuario aún no responde las preguntas. */
    fun state(userId: UUID): TwinState?

    /** Puntos de `twin_accuracy` ordenados por semana ascendente. */
    fun accuracyPoints(userId: UUID): List<AccuracyPoint>

    /** Entrada de la persona (solo respuestas), o null si no hay gemelo con respuestas. */
    fun personaInput(userId: UUID): PersonaInput?

    /**
     * Guarda [summary] como persona de la [version] indicada y marca `persona_ready`. Es idempotente:
     * si esa versión ya está lista, o el gemelo ya pasó a una versión posterior, no hace nada y
     * devuelve false. Escribe `twin_versions(user, version)` exactamente una vez.
     */
    fun savePersona(userId: UUID, version: Int, summary: String, now: Instant): Boolean

    /** Inserta un punto de `twin_accuracy` (la usa el job semanal y las pruebas). */
    fun addAccuracyPoint(userId: UUID, point: AccuracyPoint)
}
