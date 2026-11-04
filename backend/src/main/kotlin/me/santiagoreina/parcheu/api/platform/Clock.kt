package me.santiagoreina.parcheu.api.platform

import java.time.Clock
import java.time.ZoneId
import kotlin.time.Instant

/** Zona de negocio: cuotas, edad y agrupaciones se calculan en hora de Bogotá. */
val BogotaZone: ZoneId = ZoneId.of("America/Bogota")

/** Reloj de producción. Los tests inyectan `Clock.fixed(...)`. */
fun systemClock(): Clock = Clock.system(BogotaZone)

fun Clock.nowInstant(): Instant = Instant.fromEpochMilliseconds(millis())
