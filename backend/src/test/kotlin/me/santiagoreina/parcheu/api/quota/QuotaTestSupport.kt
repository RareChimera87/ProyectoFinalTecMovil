package me.santiagoreina.parcheu.api.quota

import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Reloj fijo que la prueba puede mover. */
class MovableClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = now
}

/** Instante a partir de una hora de Bogotá escrita con su offset, p. ej. `2026-10-28T15:00-05:00`. */
fun bogota(text: String): Instant = OffsetDateTime.parse(text).toInstant()
