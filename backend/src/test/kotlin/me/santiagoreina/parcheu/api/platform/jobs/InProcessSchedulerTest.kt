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
})
