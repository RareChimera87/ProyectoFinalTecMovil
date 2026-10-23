package me.santiagoreina.parcheu.api.identity.profile

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import me.santiagoreina.parcheu.api.identity.UserRecord
import me.santiagoreina.parcheu.contract.Visibility
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class AgeRulesTest : FunSpec({
    test("ageRange para 18, 20, 22, 23 y 26 años da 18-19, 20-22, 20-22, 23-25 y 26+") {
        listOf(18, 20, 22, 23, 26).map(AgeRules::ageRange) shouldBe listOf("18-19", "20-22", "20-22", "23-25", "26+")
    }

    test("los bordes de cada rango y edades altas") {
        AgeRules.ageRange(19) shouldBe "18-19"
        AgeRules.ageRange(25) shouldBe "23-25"
        AgeRules.ageRange(64) shouldBe "26+"
    }

    test("un menor de edad no tiene rango") {
        shouldThrow<IllegalArgumentException> { AgeRules.ageRange(17) }
    }

    test("la fecha de hoy se toma en Bogotá aunque en UTC ya sea mañana") {
        // 2026-10-24T04:30Z son las 23:30 del 23 de octubre en Bogotá (UTC-5).
        val clock = Clock.fixed(Instant.parse("2026-10-24T04:30:00Z"), ZoneOffset.UTC)
        AgeRules.todayInBogota(clock) shouldBe LocalDate.parse("2026-10-23")
    }

    test("edad cumplida: el día del cumpleaños cuenta, el día anterior no") {
        val birth = LocalDate.parse("2008-10-23")
        AgeRules.ageOn(birth, LocalDate.parse("2026-10-23")) shouldBe 18
        AgeRules.ageOn(birth, LocalDate.parse("2026-10-22")) shouldBe 17
        AgeRules.isAdult(birth, LocalDate.parse("2026-10-22")) shouldBe false
    }

    test("nacido un 29 de febrero cumple el 1 de marzo en años no bisiestos") {
        val birth = LocalDate.parse("2008-02-29")
        AgeRules.ageOn(birth, LocalDate.parse("2026-02-28")) shouldBe 17
        AgeRules.ageOn(birth, LocalDate.parse("2026-03-01")) shouldBe 18
    }

    test("AnonProfile lleva rango, universidad e intereses y nunca nombre, carrera ni fecha") {
        val today = LocalDate.parse("2026-10-23")
        val user = user(birthDate = LocalDate.parse("2005-01-10"), verified = true)
        val anon = AnonProfiles.of(user, anonId = "a1", label = "Alguien de la UIS", universityShortName = "UIS", today = today)!!
        anon.ageRange shouldBe "20-22"
        anon.university shouldBe "UIS"
        anon.interests shouldBe listOf("musica", "cine")
        anon.toString().contains("Lucía") shouldBe false
        anon.toString().contains("Ingeniería") shouldBe false
        anon.toString().contains("2005") shouldBe false
    }

    test("sin edad verificada o con cuenta de menor no hay AnonProfile") {
        val today = LocalDate.parse("2026-10-23")
        AnonProfiles.of(user(birthDate = null, verified = false), "a", "l", null, today) shouldBe null
        AnonProfiles.of(user(birthDate = LocalDate.parse("2010-01-01"), verified = false, underage = true), "a", "l", null, today) shouldBe null
    }
})

private fun user(birthDate: LocalDate?, verified: Boolean, underage: Boolean = false) = UserRecord(
    id = UUID.randomUUID(), firebaseUid = "lucia", email = null, displayName = "Lucía", photoUrl = null,
    career = "Ingeniería Industrial", universityId = 1, interests = listOf("musica", "cine"), birthDate = birthDate,
    ageVerifiedAt = if (verified) Instant.EPOCH else null, underageAt = if (underage) Instant.EPOCH else null,
    parejaConfirmedAt = null, visibility = Visibility.ANONYMOUS, isDemo = false, suspendedUntil = null, deletedAt = null,
    createdAt = Instant.EPOCH,
)
