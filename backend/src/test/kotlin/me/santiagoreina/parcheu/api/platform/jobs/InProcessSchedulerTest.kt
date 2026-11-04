package me.santiagoreina.parcheu.api.platform.jobs

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import me.santiagoreina.parcheu.api.platform.AppContainer
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class InProcessSchedulerTest : FunSpec({
    // Bogotá es UTC-5 sin horario de verano: las 02:00 de Bogotá son las 07:00 UTC.
    fun at(utc: String) = Clock.fixed(Instant.parse(utc), ZoneOffset.UTC)

    test("antes de las 02:00 de Bogotá el job de memorias espera hasta hoy a esa hora") {
        InProcessScheduler.delayUntil(AppContainer.MEMORIES_AT, at("2026-10-27T05:30:00Z")) shouldBe Duration.ofMinutes(90)
    }

    test("después de las 02:00 de Bogotá espera hasta mañana") {
        InProcessScheduler.delayUntil(AppContainer.MEMORIES_AT, at("2026-10-27T08:00:00Z")) shouldBe Duration.ofHours(23)
    }

    test("justo a las 02:00 no corre dos veces: espera 24 h") {
        InProcessScheduler.delayUntil(AppContainer.MEMORIES_AT, at("2026-10-27T07:00:00Z")) shouldBe Duration.ofHours(24)
    }

    test("twin-accuracy: un viernes espera hasta el domingo a las 23:00 de Bogotá") {
        // Viernes 2026-10-23 12:00 UTC = 07:00 Bogotá; domingo 23:00 Bogotá está a 2 días y 16 horas.
        InProcessScheduler.delayUntilWeekly(AppContainer.TWIN_ACCURACY_DAY, AppContainer.TWIN_ACCURACY_AT, at("2026-10-23T12:00:00Z")) shouldBe
            Duration.ofHours(64)
    }

    test("twin-accuracy: justo el domingo a las 23:00 espera una semana") {
        // Domingo 2026-10-25 23:00 Bogotá = lunes 04:00 UTC.
        InProcessScheduler.delayUntilWeekly(AppContainer.TWIN_ACCURACY_DAY, AppContainer.TWIN_ACCURACY_AT, at("2026-10-26T04:00:00Z")) shouldBe
            Duration.ofDays(7)
    }

    test("twin-accuracy: el lunes siguiente espera hasta el domingo próximo") {
        // Lunes 2026-10-26 00:00 Bogotá: faltan 6 días y 23 horas.
        InProcessScheduler.delayUntilWeekly(AppContainer.TWIN_ACCURACY_DAY, AppContainer.TWIN_ACCURACY_AT, at("2026-10-26T05:00:00Z")) shouldBe
            Duration.ofHours(6 * 24 + 23)
    }
})
