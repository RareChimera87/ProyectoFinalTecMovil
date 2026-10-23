package me.santiagoreina.parcheu.api.quota

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import me.santiagoreina.parcheu.api.platform.errors.Conflict
import me.santiagoreina.parcheu.api.platform.errors.QuotaExceeded
import me.santiagoreina.parcheu.api.platform.errors.ValidationFailed
import me.santiagoreina.parcheu.contract.EntitlementState
import me.santiagoreina.parcheu.contract.QuotaKind
import java.util.UUID
import kotlin.time.Instant as KInstant

class QuotaServiceTest : FunSpec({
    val ana = UUID.fromString("00000000-0000-0000-0000-00000000000a")
    val beto = UUID.fromString("00000000-0000-0000-0000-00000000000b")

    fun setup(start: String, limits: QuotaLimits = QuotaLimits()): Triple<QuotaService, MovableClock, InMemoryDevEntitlementService> {
        val clock = MovableClock(bogota(start))
        val entitlements = InMemoryDevEntitlementService()
        return Triple(QuotaService(InMemoryUsageRepository(), entitlements, limits, clock), clock, entitlements)
    }

    fun kotlin(text: String) = KInstant.parse(text)

    test("con reloj fijo, la 6.a simulación de la semana da QUOTA_EXCEEDED con resetsAt el próximo lunes 00:00 -05:00") {
        // Miércoles 28 de octubre de 2026, 15:00 en Bogotá.
        val (quota, _) = setup("2026-10-28T15:00-05:00")
        repeat(5) { quota.consume(ana, QuotaKind.SIMULATION, "sim-$it") shouldBe QuotaService.Outcome.CHARGED }
        val e = shouldThrow<QuotaExceeded> { quota.consume(ana, QuotaKind.SIMULATION, "sim-5") }
        e.code.httpStatus shouldBe 402
        e.kind shouldBe "SIMULATION"
        e.resetsAt shouldBe kotlin("2026-11-02T00:00:00-05:00")
        shouldThrow<QuotaExceeded> { quota.ensureAvailable(ana, QuotaKind.SIMULATION) }
        // El cupo es por usuario.
        quota.consume(beto, QuotaKind.SIMULATION, "sim-beto") shouldBe QuotaService.Outcome.CHARGED
    }

    test("la semana ISO corta el lunes 00:00 de Bogotá, no a medianoche UTC") {
        val (quota, clock) = setup("2026-11-01T23:30-05:00") // domingo
        repeat(5) { quota.consume(ana, QuotaKind.SIMULATION, "dom-$it") }
        // Domingo 23:30 en Bogotá ya es lunes 04:30 UTC, pero sigue siendo la misma semana de cupo.
        clock.now = bogota("2026-11-01T23:59:59-05:00")
        shouldThrow<QuotaExceeded> { quota.consume(ana, QuotaKind.SIMULATION, "dom-5") }
        clock.now = bogota("2026-11-02T00:00-05:00")
        quota.consume(ana, QuotaKind.SIMULATION, "lun-0") shouldBe QuotaService.Outcome.CHARGED
        quota.status(ana).simulations.used shouldBe 1
    }

    test("devolver un uso restaura el cupo y repetir el mismo ref_id no cobra dos veces") {
        val (quota, _) = setup("2026-10-28T15:00-05:00")
        repeat(5) { quota.consume(ana, QuotaKind.SIMULATION, "sim-$it") }
        // Repetir un ref_id ya cobrado no cobra ni choca con el límite.
        quota.consume(ana, QuotaKind.SIMULATION, "sim-4") shouldBe QuotaService.Outcome.ALREADY_CHARGED
        quota.status(ana).simulations.used shouldBe 5
        shouldThrow<QuotaExceeded> { quota.consume(ana, QuotaKind.SIMULATION, "sim-nueva") }

        quota.refund(QuotaKind.SIMULATION, "sim-4") shouldBe true
        quota.refund(QuotaKind.SIMULATION, "sim-4") shouldBe true // idempotente
        quota.status(ana).simulations.used shouldBe 4
        // Un reintento del uso devuelto vuelve a cobrar (no sale gratis) porque hay cupo.
        quota.consume(ana, QuotaKind.SIMULATION, "sim-4") shouldBe QuotaService.Outcome.CHARGED
        quota.status(ana).simulations.used shouldBe 5
        quota.consume(ana, QuotaKind.SIMULATION, "sim-4") shouldBe QuotaService.Outcome.ALREADY_CHARGED
        // Sin cupo, reintentar un uso devuelto da QUOTA_EXCEEDED en vez de correr gratis.
        quota.refund(QuotaKind.SIMULATION, "sim-4")
        quota.consume(ana, QuotaKind.SIMULATION, "sim-nueva") shouldBe QuotaService.Outcome.CHARGED
        shouldThrow<QuotaExceeded> { quota.consume(ana, QuotaKind.SIMULATION, "sim-4") }
        quota.status(ana).simulations.used shouldBe 5
        // Otra persona no puede reactivar un uso devuelto ajeno.
        shouldThrow<Conflict> { quota.consume(beto, QuotaKind.SIMULATION, "sim-4") }
        quota.refund(QuotaKind.SIMULATION, "no-existe") shouldBe false
    }

    test("un ref_id es único por tipo: otra persona no puede reutilizarlo y el mismo id en otro tipo es otro uso") {
        val (quota, _) = setup("2026-10-28T15:00-05:00")
        quota.consume(ana, QuotaKind.SIMULATION, "x") shouldBe QuotaService.Outcome.CHARGED
        shouldThrow<Conflict> { quota.consume(beto, QuotaKind.SIMULATION, "x") }
        quota.consume(ana, QuotaKind.FULL_REPORT, "x") shouldBe QuotaService.Outcome.CHARGED
        shouldThrow<ValidationFailed> { quota.consume(ana, QuotaKind.SIMULATION, " ") }
    }

    test("un usuario Plus no tiene límite semanal pero sí el uso justo diario") {
        val limits = QuotaLimits(plusDailySimulations = 8)
        val (quota, clock, entitlements) = setup("2026-10-26T08:00-05:00", limits) // lunes
        entitlements.grantPlus(ana, days = 30, now = clock.instant())
        // Lunes: 8 simulaciones (más que las 5 semanales de gratis).
        repeat(8) { quota.consume(ana, QuotaKind.SIMULATION, "lun-$it") shouldBe QuotaService.Outcome.CHARGED }
        val e = shouldThrow<QuotaExceeded> { quota.consume(ana, QuotaKind.SIMULATION, "lun-8") }
        e.resetsAt shouldBe kotlin("2026-10-27T00:00:00-05:00")
        // Martes: el uso justo se reinicia aunque en la semana ya van 8.
        clock.now = bogota("2026-10-27T00:00-05:00")
        repeat(8) { quota.consume(ana, QuotaKind.SIMULATION, "mar-$it") shouldBe QuotaService.Outcome.CHARGED }
        val status = quota.status(ana)
        status.plus shouldBe true
        status.simulations.limit shouldBe null
        status.simulations.used shouldBe 8
        status.simulations.resetsAt shouldBe kotlin("2026-10-28T00:00:00-05:00")
        // Plus también tiene uso justo en matches e informes, no 1 al día ni 1 al mes.
        repeat(3) { quota.consume(ana, QuotaKind.FULL_REPORT, "inf-$it") }
        repeat(3) { quota.consume(ana, QuotaKind.RANDOM_MATCH, "match-$it") }
    }

    test("al vencer Plus vuelve el límite semanal gratis contando lo ya usado en la semana") {
        val (quota, clock, entitlements) = setup("2026-10-26T08:00-05:00")
        entitlements.grantPlus(ana, days = 1, now = clock.instant())
        repeat(6) { quota.consume(ana, QuotaKind.SIMULATION, "s-$it") }
        clock.now = bogota("2026-10-27T09:00-05:00")
        quota.isPlus(ana) shouldBe false
        shouldThrow<QuotaExceeded> { quota.consume(ana, QuotaKind.SIMULATION, "s-6") }
    }

    test("el límite mensual del informe se reinicia el día 1 a las 00:00 hora de Bogotá") {
        val (quota, clock) = setup("2026-10-05T10:00-05:00")
        quota.consume(ana, QuotaKind.FULL_REPORT, "inf-oct") shouldBe QuotaService.Outcome.CHARGED
        val e = shouldThrow<QuotaExceeded> { quota.consume(ana, QuotaKind.FULL_REPORT, "inf-oct-2") }
        e.kind shouldBe "FULL_REPORT"
        e.resetsAt shouldBe kotlin("2026-11-01T00:00:00-05:00")
        // 1 de noviembre 02:00 UTC: en Bogotá aún es 31 de octubre, sigue agotado.
        clock.now = java.time.Instant.parse("2026-11-01T02:00:00Z")
        shouldThrow<QuotaExceeded> { quota.consume(ana, QuotaKind.FULL_REPORT, "inf-oct-3") }
        clock.now = bogota("2026-10-31T23:59:59-05:00")
        shouldThrow<QuotaExceeded> { quota.consume(ana, QuotaKind.FULL_REPORT, "inf-oct-4") }
        clock.now = bogota("2026-11-01T00:00-05:00")
        quota.consume(ana, QuotaKind.FULL_REPORT, "inf-nov") shouldBe QuotaService.Outcome.CHARGED
        quota.status(ana).fullReport.resetsAt shouldBe kotlin("2026-12-01T00:00:00-05:00")
    }

    test("un match al azar por día para gratis, con corte a medianoche de Bogotá") {
        val (quota, clock) = setup("2026-10-28T23:00-05:00")
        quota.consume(ana, QuotaKind.RANDOM_MATCH, "m1:ana")
        val e = shouldThrow<QuotaExceeded> { quota.consume(ana, QuotaKind.RANDOM_MATCH, "m2:ana") }
        e.kind shouldBe "RANDOM_MATCH"
        e.resetsAt shouldBe kotlin("2026-10-29T00:00:00-05:00")
        clock.now = bogota("2026-10-29T00:00-05:00")
        quota.consume(ana, QuotaKind.RANDOM_MATCH, "m2:ana") shouldBe QuotaService.Outcome.CHARGED
    }

    test("el estado gratis muestra usados, límite y fin de cada ventana") {
        val (quota, _) = setup("2026-10-28T15:00-05:00")
        quota.consume(ana, QuotaKind.SIMULATION, "s1")
        quota.consume(ana, QuotaKind.SIMULATION, "s2")
        val status = quota.status(ana)
        status.plus shouldBe false
        status.simulations.used shouldBe 2
        status.simulations.limit shouldBe 5
        status.simulations.resetsAt shouldBe kotlin("2026-11-02T00:00:00-05:00")
        status.randomMatch.limit shouldBe 1
        status.randomMatch.resetsAt shouldBe kotlin("2026-10-29T00:00:00-05:00")
        status.fullReport.limit shouldBe 1
        status.fullReport.resetsAt shouldBe kotlin("2026-11-01T00:00:00-05:00")
    }

    test("las ventanas se calculan en Bogotá aunque el instante caiga en otro día en UTC") {
        val now = java.time.Instant.parse("2026-11-02T03:00:00Z") // domingo 1 nov, 22:00 en Bogotá
        windowFor(QuotaPeriod.ISO_WEEK, now) shouldBe QuotaWindow(bogota("2026-10-26T00:00-05:00"), bogota("2026-11-02T00:00-05:00"))
        windowFor(QuotaPeriod.DAY, now) shouldBe QuotaWindow(bogota("2026-11-01T00:00-05:00"), bogota("2026-11-02T00:00-05:00"))
        windowFor(QuotaPeriod.MONTH, now) shouldBe QuotaWindow(bogota("2026-11-01T00:00-05:00"), bogota("2026-12-01T00:00-05:00"))
        // Cambio de año: la semana ISO del 1 de enero de 2027 (viernes) empieza el lunes 28 de diciembre.
        windowFor(QuotaPeriod.ISO_WEEK, bogota("2027-01-01T12:00-05:00")).start shouldBe bogota("2026-12-28T00:00-05:00")
    }

    test("el uso justo de Plus se configura por entorno y rechaza valores inválidos") {
        val limits = QuotaLimits.fromEnv(mapOf("QUOTA_PLUS_DAILY_SIMULATIONS" to "12", "QUOTA_PLUS_DAILY_RANDOM_MATCHES" to " 4 "))
        limits.plusDailySimulations shouldBe 12
        limits.plusDailyRandomMatches shouldBe 4
        limits.plusDailyFullReports shouldBe QuotaLimits().plusDailyFullReports
        limits.freeWeeklySimulations shouldBe 5
        shouldThrow<IllegalStateException> { QuotaLimits.fromEnv(mapOf("QUOTA_PLUS_DAILY_FULL_REPORTS" to "-1")) }
        shouldThrow<IllegalStateException> { QuotaLimits.fromEnv(mapOf("QUOTA_PLUS_DAILY_SIMULATIONS" to "muchas")) }
    }

    test("grant-plus valida los días") {
        val (_, clock, entitlements) = setup("2026-10-28T15:00-05:00")
        shouldThrow<ValidationFailed> { entitlements.grantPlus(ana, 0, clock.instant()) }
        shouldThrow<ValidationFailed> { entitlements.grantPlus(ana, 366, clock.instant()) }
    }

    test("grantsPlus: CANCELED da Plus solo antes de expires_at; ON_HOLD, PAUSED, EXPIRED y REVOKED no") {
        val at = java.time.Instant.parse("2026-10-28T20:00:00Z")
        val future = at.plusSeconds(86_400)
        val past = at.minusSeconds(1)
        grantsPlus(EntitlementState.CANCELED, future, at) shouldBe true
        grantsPlus(EntitlementState.CANCELED, past, at) shouldBe false
        grantsPlus(EntitlementState.CANCELED, null, at) shouldBe false
        grantsPlus(EntitlementState.ACTIVE, future, at) shouldBe true
        grantsPlus(EntitlementState.ACTIVE, past, at) shouldBe false
        grantsPlus(EntitlementState.IN_GRACE_PERIOD, future, at) shouldBe true
        // Margen de 1 h solo para ACTIVE con renovación automática (B2).
        grantsPlus(EntitlementState.ACTIVE, at.minusSeconds(3_599), at, autoRenewing = true) shouldBe true
        grantsPlus(EntitlementState.ACTIVE, at.minusSeconds(3_600), at, autoRenewing = true) shouldBe false
        grantsPlus(EntitlementState.IN_GRACE_PERIOD, past, at, autoRenewing = true) shouldBe false
        grantsPlus(EntitlementState.CANCELED, past, at, autoRenewing = true) shouldBe false
        listOf(EntitlementState.ON_HOLD, EntitlementState.PAUSED, EntitlementState.EXPIRED, EntitlementState.REVOKED)
            .forEach { grantsPlus(it, future, at) shouldBe false }
    }
})
