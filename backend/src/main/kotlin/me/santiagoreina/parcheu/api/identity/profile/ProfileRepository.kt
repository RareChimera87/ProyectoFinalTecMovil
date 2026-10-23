package me.santiagoreina.parcheu.api.identity.profile

import me.santiagoreina.parcheu.contract.CatalogDto
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Datos del perfil básico (B0): catálogos y fecha de nacimiento. La implementación real es [ExposedProfileRepository]. */
interface ProfileRepository {
    /** Universidades activas (por id) e intereses (por etiqueta) del catálogo sembrado en V2. */
    suspend fun catalog(): CatalogDto

    /**
     * Guarda la fecha de nacimiento **solo si aún no hay una** (es inmutable) junto con
     * `age_verified_at` (adulto) o `underage_at` (menor). Devuelve `false` si el usuario ya tenía fecha;
     * la condición va en el mismo UPDATE, así que dos peticiones simultáneas no pueden fijar dos fechas.
     */
    suspend fun setBirthDate(userId: UUID, birthDate: LocalDate, ageVerifiedAt: Instant?, underageAt: Instant?, now: Instant): Boolean
}
