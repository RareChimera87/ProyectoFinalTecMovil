package me.santiagoreina.parcheu.api.identity.profile

import me.santiagoreina.parcheu.api.identity.UserRecord
import me.santiagoreina.parcheu.contract.AnonProfile
import java.time.LocalDate

/**
 * Arma el [AnonProfile] del match (F2) con los datos de B0: rango de edad, universidad (nombre corto)
 * e intereses. Nunca lleva nombre, foto, carrera, fecha exacta ni el id interno (ADR-0031): [anonId] y
 * [label] los decide quien llama (E4.2).
 */
object AnonProfiles {
    /** `null` si el usuario aún no tiene edad verificada o es menor: no puede entrar a un match. */
    fun of(
        user: UserRecord,
        anonId: String,
        label: String,
        universityShortName: String?,
        today: LocalDate,
    ): AnonProfile? {
        val birthDate = user.birthDate ?: return null
        if (user.ageVerifiedAt == null || user.underageAt != null || !AgeRules.isAdult(birthDate, today)) return null
        return AnonProfile(
            anonId = anonId,
            label = label,
            ageRange = AgeRules.ageRange(birthDate, today),
            university = universityShortName,
            interests = user.interests,
            isDemo = user.isDemo,
        )
    }
}
