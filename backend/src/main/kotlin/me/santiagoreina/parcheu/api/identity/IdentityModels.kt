package me.santiagoreina.parcheu.api.identity

import me.santiagoreina.parcheu.contract.ConsentKind
import me.santiagoreina.parcheu.contract.Visibility
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Fila de `users` tal como la usa el dominio (sin tipos de Exposed). */
data class UserRecord(
    val id: UUID,
    val firebaseUid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
    val career: String?,
    val universityId: Int?,
    val interests: List<String>,
    val birthDate: LocalDate?,
    val ageVerifiedAt: Instant?,
    val underageAt: Instant?,
    val parejaConfirmedAt: Instant?,
    val visibility: Visibility,
    val isDemo: Boolean,
    val suspendedUntil: Instant?,
    val deletedAt: Instant?,
    val createdAt: Instant,
)

/** Fila de `consents`. El estado vigente de cada tipo es la fila con el id más alto. */
data class ConsentRecord(
    val id: Long,
    val kind: ConsentKind,
    val granted: Boolean,
    val textVersion: String,
    val createdAt: Instant,
)

/** Fila nueva para `consents` (la tabla solo admite INSERT). */
data class NewConsent(val kind: ConsentKind, val granted: Boolean, val textVersion: String)

/** Cambios de PATCH /v1/me ya validados; `null` = no se toca. [career] vacío borra el valor. */
data class ProfilePatch(
    val displayName: String? = null,
    val career: String? = null,
    val clearCareer: Boolean = false,
    val universityId: Int? = null,
    val interests: List<String>? = null,
)
