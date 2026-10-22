package me.santiagoreina.parcheu.api.identity

import me.santiagoreina.parcheu.api.platform.auth.FirebaseUser
import me.santiagoreina.parcheu.api.platform.errors.NotFound
import me.santiagoreina.parcheu.api.platform.errors.Suspended
import me.santiagoreina.parcheu.api.platform.errors.TermsRequired
import me.santiagoreina.parcheu.api.platform.errors.Unauthenticated
import me.santiagoreina.parcheu.api.platform.errors.Underage
import me.santiagoreina.parcheu.api.platform.errors.ValidationFailed
import me.santiagoreina.parcheu.contract.AcceptTerms
import me.santiagoreina.parcheu.contract.ConsentChange
import me.santiagoreina.parcheu.contract.ConsentKind
import me.santiagoreina.parcheu.contract.ConsentState
import me.santiagoreina.parcheu.contract.ConsentsDto
import me.santiagoreina.parcheu.contract.FcmToken
import me.santiagoreina.parcheu.contract.IsoDate
import me.santiagoreina.parcheu.contract.MeDto
import me.santiagoreina.parcheu.contract.OnboardingStep
import me.santiagoreina.parcheu.contract.SessionDto
import me.santiagoreina.parcheu.contract.UpdateMe
import java.time.Clock
import java.time.Instant

/**
 * Sesión, cuenta, términos, consentimientos y tokens FCM (E2.1). Los errores salen como
 * excepciones de dominio; las rutas no capturan nada.
 *
 * [legalTextVersion] es `LEGAL_TEXT_VERSION`: el texto vigente de Términos y Política. Un usuario
 * está al día solo si la última fila de TERMS y de PRIVACY está concedida con esa versión.
 */
class IdentityService(
    private val repo: IdentityRepository,
    private val clock: Clock,
    val legalTextVersion: String,
) {
    /** Alta o login idempotente por `firebase_uid`. No exige términos: es la puerta de entrada. */
    suspend fun session(firebase: FirebaseUser): SessionDto {
        val now = clock.instant()
        val existing = repo.findByFirebaseUid(firebase.uid)
        var user = existing ?: repo.createIfAbsent(
            uid = firebase.uid,
            email = firebase.email,
            displayName = firebase.name?.trim()?.take(MAX_NAME)?.takeIf { it.isNotEmpty() },
            now = now,
        )
        ensureActive(user)
        if (existing != null && firebase.email != null && firebase.email != user.email) {
            repo.updateEmail(user.id, firebase.email, now)
            user = user.copy(email = firebase.email)
        }
        return SessionDto(toMe(user), onboarding(user), legalTextVersion)
    }

    /** Usuario de la sesión actual; falla si nunca llamó a `POST /v1/session` o la cuenta se eliminó. */
    suspend fun requireUser(firebase: FirebaseUser): UserRecord {
        val user = repo.findByFirebaseUid(firebase.uid)
            ?: throw NotFound("Primero inicia sesión con POST /v1/session")
        ensureActive(user)
        return user
    }

    /**
     * Puerta de todas las rutas salvo las exentas: cuenta de menor de edad (403 UNDERAGE, E2.6),
     * suspensión y términos vigentes (403 TERMS_REQUIRED).
     */
    suspend fun requireFullAccess(firebase: FirebaseUser) {
        val user = requireUser(firebase)
        if (user.underageAt != null) throw Underage()
        val until = user.suspendedUntil
        if (until != null && until.isAfter(clock.instant())) throw Suspended()
        if (!termsAccepted(repo.latestConsents(user.id))) throw TermsRequired()
    }

    /**
     * Puerta de edad para las rutas exentas de términos (`session` y `terms`): una cuenta marcada como
     * menor de edad solo puede ver (`GET /v1/me`) o borrar (`DELETE /v1/me`) su cuenta. Si el usuario aún
     * no existe (primer `POST /v1/session`) no hay nada que bloquear.
     */
    suspend fun requireNotUnderage(firebase: FirebaseUser) {
        val user = repo.findByFirebaseUid(firebase.uid) ?: return
        if (user.deletedAt == null && user.underageAt != null) throw Underage()
    }

    suspend fun me(firebase: FirebaseUser): MeDto = toMe(requireUser(firebase))

    suspend fun updateMe(firebase: FirebaseUser, request: UpdateMe): MeDto {
        val user = requireUser(firebase)
        val patch = validate(request)
        if (patch == ProfilePatch()) return toMe(user)
        return toMe(repo.updateProfile(user.id, patch, clock.instant()))
    }

    /** Registra TERMS y PRIVACY con la versión vigente. Repetirlo en la misma versión no agrega filas. */
    suspend fun acceptTerms(firebase: FirebaseUser, request: AcceptTerms): ConsentsDto {
        val user = requireUser(firebase)
        if (request.textVersion != legalTextVersion) {
            throw ValidationFailed("La versión del texto legal vigente es $legalTextVersion")
        }
        val state = repo.latestConsents(user.id).associateBy { it.kind }
        val pending = LEGAL_KINDS
            .filterNot { state[it]?.granted == true && state[it]?.textVersion == legalTextVersion }
            .map { NewConsent(it, granted = true, textVersion = legalTextVersion) }
        if (pending.isNotEmpty()) repo.appendConsents(user.id, pending, clock.instant())
        return consents(user)
    }

    /** Concede o revoca un consentimiento de aprendizaje. Siempre agrega una fila; nunca edita las anteriores. */
    suspend fun changeConsent(firebase: FirebaseUser, change: ConsentChange): ConsentsDto {
        val user = requireUser(firebase)
        if (change.kind in LEGAL_KINDS) throw ValidationFailed("Los términos y la política se aceptan con POST /v1/me/terms")
        if (change.textVersion.isBlank()) throw ValidationFailed("textVersion es obligatorio")
        if (change.granted && change.textVersion != legalTextVersion) {
            throw ValidationFailed("La versión del texto vigente es $legalTextVersion")
        }
        val current = repo.latestConsents(user.id).firstOrNull { it.kind == change.kind }
        val unchanged = current != null && current.granted == change.granted && current.textVersion == change.textVersion
        if (!unchanged) repo.appendConsents(user.id, listOf(NewConsent(change.kind, change.granted, change.textVersion)), clock.instant())
        return consents(user)
    }

    suspend fun consents(firebase: FirebaseUser): ConsentsDto = consents(requireUser(firebase))

    suspend fun registerFcmToken(firebase: FirebaseUser, request: FcmToken) {
        val user = requireUser(firebase)
        repo.upsertFcmToken(user.id, validToken(request), clock.instant())
    }

    suspend fun removeFcmToken(firebase: FirebaseUser, request: FcmToken) {
        val user = requireUser(firebase)
        repo.deleteFcmToken(user.id, validToken(request))
    }

    // --- internos ---------------------------------------------------------------------------

    private suspend fun consents(user: UserRecord) = ConsentsDto(
        repo.latestConsents(user.id).map { ConsentState(it.kind, it.granted, it.textVersion, it.createdAt.toKotlin()) },
    )

    private fun termsAccepted(consents: List<ConsentRecord>): Boolean {
        val byKind = consents.associateBy { it.kind }
        return LEGAL_KINDS.all { byKind[it]?.granted == true && byKind[it]?.textVersion == legalTextVersion }
    }

    private suspend fun onboarding(user: UserRecord): OnboardingStep = when {
        !termsAccepted(repo.latestConsents(user.id)) -> OnboardingStep.NEEDS_TERMS
        // B0 (E2.6) exige fecha verificada y universidad; el nombre viene de Google y B0 lo pide si falta.
        user.displayName.isNullOrBlank() || user.ageVerifiedAt == null || user.universityId == null ->
            OnboardingStep.NEEDS_PROFILE
        !repo.hasTwin(user.id) -> OnboardingStep.NEEDS_TWIN
        repo.modes(user.id).none { it.value } -> OnboardingStep.NEEDS_MODES
        else -> OnboardingStep.DONE
    }

    private suspend fun toMe(user: UserRecord) = MeDto(
        id = user.id.toString(),
        email = user.email,
        displayName = user.displayName,
        photoUrl = user.photoUrl,
        career = user.career,
        universityId = user.universityId,
        interests = user.interests,
        birthDate = user.birthDate?.let { IsoDate(it.toString()) },
        ageVerified = user.ageVerifiedAt != null,
        underage = user.underageAt != null,
        parejaConfirmed = user.parejaConfirmedAt != null,
        visibility = user.visibility,
        modes = repo.modes(user.id),
        // El plan Plus llega con la facturación (E7.x); hasta entonces nadie lo tiene.
        plus = false,
        isDemo = user.isDemo,
        suspendedUntil = user.suspendedUntil?.toKotlin(),
        createdAt = user.createdAt.toKotlin(),
    )

    private suspend fun validate(request: UpdateMe): ProfilePatch {
        val name = request.displayName?.trim()
        if (name != null && (name.isEmpty() || name.codePointCount(0, name.length) > MAX_NAME)) {
            throw ValidationFailed("displayName debe tener entre 1 y $MAX_NAME caracteres")
        }
        val career = request.career?.trim()
        if (career != null && career.codePointCount(0, career.length) > MAX_CAREER) {
            throw ValidationFailed("career debe tener máximo $MAX_CAREER caracteres")
        }
        request.universityId?.let { id ->
            if (!repo.universityExists(id)) throw ValidationFailed("universityId no existe en el catálogo")
        }
        request.interests?.let { slugs ->
            if (slugs.size > MAX_INTERESTS) throw ValidationFailed("interests admite máximo $MAX_INTERESTS elementos")
            if (slugs.toSet().size != slugs.size) throw ValidationFailed("interests no admite repetidos")
            val unknown = repo.unknownInterests(slugs)
            if (unknown.isNotEmpty()) throw ValidationFailed("Intereses fuera del catálogo: ${unknown.joinToString()}")
        }
        return ProfilePatch(
            displayName = name,
            career = career?.takeIf { it.isNotEmpty() },
            clearCareer = career != null && career.isEmpty(),
            universityId = request.universityId,
            interests = request.interests,
        )
    }

    private fun validToken(request: FcmToken): String {
        val token = request.token.trim()
        if (token.isEmpty() || token.length > MAX_FCM_TOKEN) throw ValidationFailed("token FCM inválido")
        return token
    }

    private fun ensureActive(user: UserRecord) {
        if (user.deletedAt != null) throw Unauthenticated("La cuenta fue eliminada")
    }

    companion object {
        const val MAX_NAME = 40
        const val MAX_CAREER = 60
        const val MAX_INTERESTS = 5
        const val MAX_FCM_TOKEN = 4096
        val LEGAL_KINDS = listOf(ConsentKind.TERMS, ConsentKind.PRIVACY)
    }
}

internal fun Instant.toKotlin(): kotlin.time.Instant = kotlin.time.Instant.fromEpochSeconds(epochSecond, nano)
