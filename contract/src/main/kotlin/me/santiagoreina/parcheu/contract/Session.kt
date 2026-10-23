package me.santiagoreina.parcheu.contract

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/*
 * Sesión, cuenta, catálogo, edad, modos, consentimientos y privacidad (`/v1/session`,
 * `/v1/catalog`, `/v1/me/...`). Lo implementan E2.1, E2.6, E2.5 y E8.4.
 */

/** Siguiente paso del onboarding que decide el destino tras el login (arquitectura.md §2.5). */
@Serializable
public enum class OnboardingStep { NEEDS_TERMS, NEEDS_PROFILE, NEEDS_TWIN, NEEDS_MODES, DONE }

/** Tipos de consentimiento (tabla consents; Ley 1581). */
@Serializable
public enum class ConsentKind {
    TERMS,
    PRIVACY,
    CHAT_LEARNING_AMISTAD,
    CHAT_LEARNING_PAREJA,
    CHAT_LEARNING_ESTUDIO,
}

/** El usuario autenticado. */
@Serializable
public data class MeDto(
    val id: String,
    val email: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val career: String? = null,
    val universityId: Int? = null,
    val interests: List<String> = emptyList(),
    val birthDate: IsoDate? = null,
    val ageVerified: Boolean = false,
    val underage: Boolean = false,
    val parejaConfirmed: Boolean = false,
    val visibility: Visibility = Visibility.ANONYMOUS,
    val modes: Map<Mode, Boolean> = emptyMap(),
    val plus: Boolean = false,
    val isDemo: Boolean = false,
    val suspendedUntil: Instant? = null,
    val createdAt: Instant,
)

/** POST /v1/session */
@Serializable
public data class SessionDto(
    val me: MeDto,
    val onboarding: OnboardingStep,
    val legalTextVersion: String,
)

/** POST /v1/me/terms */
@Serializable
public data class AcceptTerms(
    val textVersion: String,
)

/** Estado vigente de un consentimiento (última fila de consents para ese tipo). */
@Serializable
public data class ConsentState(
    val kind: ConsentKind,
    val granted: Boolean,
    val textVersion: String,
    val updatedAt: Instant,
)

/** GET /v1/me/consents y respuesta de terms/consent. */
@Serializable
public data class ConsentsDto(
    val items: List<ConsentState> = emptyList(),
)

/** POST /v1/me/consent */
@Serializable
public data class ConsentChange(
    val kind: ConsentKind,
    val granted: Boolean,
    val textVersion: String,
)

@Serializable
public data class University(
    val id: Int,
    val shortName: String,
    val name: String,
)

@Serializable
public data class Interest(
    val slug: String,
    val label: String,
    val icon: String,
)

/** GET /v1/catalog */
@Serializable
public data class CatalogDto(
    val universities: List<University> = emptyList(),
    val interests: List<Interest> = emptyList(),
)

/** PATCH /v1/me: solo se cambian los campos presentes. */
@Serializable
public data class UpdateMe(
    val displayName: String? = null,
    val career: String? = null,
    val universityId: Int? = null,
    val interests: List<String>? = null,
)

/** PUT /v1/me/modes */
@Serializable
public data class UpdateModes(
    val modes: Map<Mode, Boolean>,
    val parejaConfirmed: Boolean = false,
)

/** POST /v1/me/age-verification (en B0, para todos). */
@Serializable
public data class AgeVerification(
    val birthDate: IsoDate,
)

/** PATCH /v1/me/privacy */
@Serializable
public data class UpdatePrivacy(
    val visibility: Visibility? = null,
)

/** GET/PATCH /v1/me/privacy */
@Serializable
public data class PrivacyDto(
    val visibility: Visibility,
    val modes: Map<Mode, Boolean> = emptyMap(),
    val learning: Map<Mode, Boolean> = emptyMap(),
)

/** POST/DELETE /v1/me/fcm-token */
@Serializable
public data class FcmToken(
    val token: String,
)
