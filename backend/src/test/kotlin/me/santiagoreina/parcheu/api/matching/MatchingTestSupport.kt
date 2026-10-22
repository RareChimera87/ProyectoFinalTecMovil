package me.santiagoreina.parcheu.api.matching

import me.santiagoreina.parcheu.api.ai.testGateway
import me.santiagoreina.parcheu.api.graph.InMemoryGraphRepository
import me.santiagoreina.parcheu.api.graph.PersonRow
import me.santiagoreina.parcheu.api.graph.inMemoryGraphModule
import me.santiagoreina.parcheu.api.platform.notifier.FakeNotifier
import me.santiagoreina.parcheu.api.platform.tasks.FakeTaskQueue
import me.santiagoreina.parcheu.api.quota.InMemoryDevEntitlementService
import me.santiagoreina.parcheu.api.quota.MovableClock
import me.santiagoreina.parcheu.api.quota.QuotaLimits
import me.santiagoreina.parcheu.api.quota.inMemoryQuotaModule
import me.santiagoreina.parcheu.api.simulation.InMemorySimulationRepository
import me.santiagoreina.parcheu.api.simulation.Participant
import me.santiagoreina.parcheu.api.simulation.RecordingUsageRepository
import me.santiagoreina.parcheu.api.simulation.TwinProfile
import me.santiagoreina.parcheu.api.simulation.inMemorySimulationModule
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Visibility
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Lunes 26 oct 2026, 10:00 en Bogotá. */
val MATCH_START: Instant = Instant.parse("2026-10-26T15:00:00Z")

/**
 * Mundo en memoria para la fila de match: reloj movible, cupos con `refunded` visible, motor de
 * simulación con la tarea sin ejecutar (se inspecciona el registro creado), espejo y avisos que registran.
 */
class MatchFixture(
    val flags: MatchingFlags = MatchingFlags(demoUsersEnabled = true, scenarioModeEnabled = false),
    val clock: MovableClock = MovableClock(MATCH_START),
    /** Espejo real (emulador de Firestore); por defecto se usa [mirror], que solo registra. */
    mirrorOverride: MatchTicketMirror? = null,
    limits: QuotaLimits = QuotaLimits(),
    val idFor: (String) -> UUID = { UUID.nameUUIDFromBytes("match:$it".toByteArray()) },
) {
    val simRepo = InMemorySimulationRepository()
    val graphRepo = InMemoryGraphRepository()
    val usage = RecordingUsageRepository()
    val entitlements = InMemoryDevEntitlementService()
    val quota = inMemoryQuotaModule(clock, limits = limits, usage = usage, entitlements = entitlements)
    val graph = inMemoryGraphModule(clock, graphRepo)
    val ai = testGateway()
    val simulation = inMemorySimulationModule(clock, ai, quota, graph, simRepo, FakeTaskQueue(), runInline = false)
    val matchRepo = InMemoryMatchingRepository()
    val mirror = RecordingMatchTicketMirror()
    val notifier = FakeNotifier()
    val module = inMemoryMatchingModule(clock, quota, simulation, graph, notifier, matchRepo, mirrorOverride ?: mirror, flags)
    val service get() = module.service

    /** Persona con gemelo listo, modos activos y datos de B0; devuelve su `users.id`. */
    fun person(
        name: String,
        modes: Set<Mode> = Mode.entries.toSet(),
        isDemo: Boolean = false,
        ageVerified: Boolean = true,
        birthDate: LocalDate = LocalDate.of(2004, 3, 14),
        university: String? = "UIS",
        interests: List<String> = listOf("musica", "cine"),
        id: UUID = idFor(name),
    ): UUID {
        simRepo.putParticipant(Participant(id, "uid-$name", modes, ageVerified, ageVerified, isDemo = isDemo, deleted = false))
        simRepo.putTwin(TwinProfile(id, 1, List(6) { 0.5 }, "Persona de $name", personaReady = true))
        graphRepo.addPerson(PersonRow(id, "Nombre Secreto de $name", "https://fotos.example/$name.png", university ?: "Otra", Visibility.NAME, isDemo, null))
        matchRepo.addPerson(
            MatchPerson(
                id = id, firebaseUid = "uid-$name", activeModes = modes, ageVerified = ageVerified, underage = false,
                parejaConfirmed = ageVerified, isDemo = isDemo, deleted = false, birthDate = birthDate,
                universityShort = university, interests = interests,
            ),
        )
        return id
    }

    fun plus(userId: UUID) = entitlements.grantPlus(userId, 30, clock.instant())
}
