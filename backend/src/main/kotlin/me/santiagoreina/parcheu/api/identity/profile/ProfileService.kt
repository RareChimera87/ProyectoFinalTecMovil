package me.santiagoreina.parcheu.api.identity.profile

import me.santiagoreina.parcheu.api.identity.IdentityService
import me.santiagoreina.parcheu.api.platform.auth.FirebaseUser
import me.santiagoreina.parcheu.api.platform.errors.Conflict
import me.santiagoreina.parcheu.api.platform.errors.Underage
import me.santiagoreina.parcheu.api.platform.errors.ValidationFailed
import me.santiagoreina.parcheu.contract.AgeVerification
import me.santiagoreina.parcheu.contract.CatalogDto
import me.santiagoreina.parcheu.contract.MeDto
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Perfil básico de B0 (E2.6): catálogos y verificación de edad. La universidad, la carrera y los
 * intereses se guardan con `PATCH /v1/me` ([IdentityService.updateMe]).
 *
 * La fecha de nacimiento es inmutable: se fija una sola vez. Un menor de 18 queda con `underage_at` y,
 * desde ese momento, la puerta de identity responde 403 UNDERAGE en toda ruta salvo `GET` y `DELETE /v1/me`.
 */
class ProfileService(
    private val identity: IdentityService,
    private val repo: ProfileRepository,
    private val clock: Clock,
) {
    suspend fun catalog(firebase: FirebaseUser): CatalogDto {
        identity.requireUser(firebase)
        return repo.catalog()
    }

    suspend fun verifyAge(firebase: FirebaseUser, request: AgeVerification): MeDto {
        val user = identity.requireUser(firebase)
        val birthDate = parse(request.birthDate.value)
        val today = AgeRules.todayInBogota(clock)
        if (birthDate.isAfter(today) || birthDate.isBefore(AgeRules.MIN_BIRTH_DATE)) {
            throw ValidationFailed("birthDate debe estar entre ${AgeRules.MIN_BIRTH_DATE} y hoy")
        }

        val existing = user.birthDate
        if (existing != null) {
            // Repetir la misma fecha (reintento tras un corte de red) no es un cambio.
            if (existing == birthDate) return identity.me(firebase)
            throw Conflict("La fecha de nacimiento ya está registrada y no se puede cambiar")
        }

        val now = clock.instant()
        val adult = AgeRules.isAdult(birthDate, today)
        val saved = repo.setBirthDate(
            userId = user.id,
            birthDate = birthDate,
            ageVerifiedAt = if (adult) now else null,
            underageAt = if (adult) null else now,
            now = now,
        )
        if (!saved) {
            // Otra petición la fijó entre la lectura y el UPDATE: se responde como si llegara después.
            val current = identity.requireUser(firebase).birthDate
            if (current != birthDate) throw Conflict("La fecha de nacimiento ya está registrada y no se puede cambiar")
        }
        if (!adult) throw Underage()
        return identity.me(firebase)
    }

    private fun parse(value: String): LocalDate = try {
        LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        throw ValidationFailed("birthDate debe tener el formato yyyy-MM-dd")
    }
}
