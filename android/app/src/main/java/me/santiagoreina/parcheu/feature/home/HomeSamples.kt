package me.santiagoreina.parcheu.feature.home

import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.HomeDto
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.PersonRef
import me.santiagoreina.parcheu.contract.QuotaDto
import me.santiagoreina.parcheu.contract.SimulationStatus
import me.santiagoreina.parcheu.contract.SimulationSummary
import me.santiagoreina.parcheu.contract.Suggestion
import me.santiagoreina.parcheu.contract.Usage

/** Datos de ejemplo de C1 (previews, pruebas y capturas Roborazzi), con los del mockup 04. */
object HomeSamples {
    /** "Ahora" de las capturas: martes 3 de noviembre de 2026, 10:00 en Bogotá. */
    val now: Instant = Instant.parse("2026-11-03T15:00:00Z")

    /** Lunes 9 de noviembre, 00:00 en Bogotá: fin de la semana ISO. */
    private val weekEnd = Instant.parse("2026-11-09T05:00:00Z")
    private val dayEnd = Instant.parse("2026-11-04T05:00:00Z")

    fun quota(used: Int, plus: Boolean = false, randomUsed: Int = 0) = QuotaDto(
        simulations = Usage(used, if (plus) null else 5, if (plus) dayEnd else weekEnd),
        randomMatch = Usage(randomUsed, if (plus) null else 1, dayEnd),
        fullReport = Usage(0, if (plus) null else 1, Instant.parse("2026-12-01T05:00:00Z")),
        plus = plus
    )

    val running =
        SimulationSummary(
            id = "sim-laura",
            status = SimulationStatus.RUNNING,
            mode = Mode.ESTUDIO,
            other = PersonRef(id = "u-laura", label = "Laura", initials = "L"),
            createdAt = Instant.parse("2026-11-03T14:40:00Z")
        )

    val ready =
        SimulationSummary(
            id = "sim-daniela",
            status = SimulationStatus.DONE,
            mode = Mode.ESTUDIO,
            other = PersonRef(id = "u-daniela", label = "Daniela", initials = "D"),
            createdAt = Instant.parse("2026-11-03T14:00:00Z"),
            score = 78
        )

    fun suggestions(plus: Boolean) = listOf(
        Suggestion(
            other = PersonRef(id = "u-sebas", label = "Sebas", initials = "S"),
            mode = Mode.AMISTAD,
            estimatedAffinity = 72,
            reason = "La última vez salieron 72 % afines",
            mutualCount = if (plus) 2 else null
        )
    )

    private fun home(
        used: Int,
        plus: Boolean = false,
        running: SimulationSummary? = null,
        ready: List<SimulationSummary> = emptyList(),
        suggestions: List<Suggestion> = suggestions(plus),
        unread: Int = 2
    ) = HomeDto(quota(used, plus), running, ready, suggestions, unread, plus)

    private fun loaded(home: HomeDto, progress: RunningProgress? = null) =
        HomeUiState.Loaded(home = home, firstName = "Mari", progress = progress)

    /** Gratis, 3 de 5, solo sugerencias. */
    val freeThreeOfFive = loaded(home(used = 3))

    /** Simulación en curso (60 %, escenario 2 de 3). */
    val inProgress =
        loaded(home(used = 3, running = running), RunningProgress(percent = 60, scenario = 2))

    /** Resultado listo (con la simulación en curso del mockup). */
    val resultReady =
        loaded(
            home(used = 3, running = running, ready = listOf(ready)),
            RunningProgress(percent = 60, scenario = 2)
        )

    /** Vacío: sin simulaciones ni sugerencias. */
    val empty = loaded(home(used = 0, suggestions = emptyList(), unread = 0))

    /** 0 restantes: las 5 de la semana usadas y el match del día también. */
    val noneLeft =
        loaded(home(used = 5, ready = listOf(ready)).copy(quota = quota(used = 5, randomUsed = 1)))

    /**
     * Perfiles demo (ADR-0027, prueba cerrada): en curso con un demo con nombre, resultado listo con un demo
     * anónimo ("Alguien de la UIS", sin sufijo) y sugerencia de un demo; los tres llevan la insignia.
     */
    val demo =
        loaded(
            home(
                used = 2,
                running = running.copy(
                    other = PersonRef(
                        id = "u-valen",
                        label = "Valentina (demo)",
                        initials = "V",
                        isDemo = true
                    )
                ),
                ready = listOf(
                    ready.copy(
                        other = PersonRef(
                            id = null,
                            label = "Alguien de la UIS",
                            initials = "?",
                            isDemo = true
                        )
                    )
                ),
                suggestions = listOf(
                    Suggestion(
                        other = PersonRef(
                            id = "u-tomas",
                            label = "Tomás (demo)",
                            initials = "T",
                            isDemo = true
                        ),
                        mode = Mode.AMISTAD,
                        estimatedAffinity = 70,
                        reason = "La última vez salieron 70 % afines"
                    )
                )
            ),
            RunningProgress(percent = 60, scenario = 2)
        )

    /** ParcheU+: sin límite visible y con amigos en común. */
    val plus =
        loaded(
            home(used = 4, plus = true, running = running, ready = listOf(ready)),
            RunningProgress(60, 2)
        )
}
