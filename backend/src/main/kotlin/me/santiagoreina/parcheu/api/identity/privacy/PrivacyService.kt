package me.santiagoreina.parcheu.api.identity.privacy

import me.santiagoreina.parcheu.api.identity.IdentityService
import me.santiagoreina.parcheu.api.identity.NewConsent
import me.santiagoreina.parcheu.api.identity.UserRecord
import me.santiagoreina.parcheu.api.identity.consent.LearningConsentPolicy
import me.santiagoreina.parcheu.api.platform.auth.FirebaseUser
import me.santiagoreina.parcheu.api.platform.errors.ValidationFailed
import me.santiagoreina.parcheu.contract.ConsentKind
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.PrivacyDto
import me.santiagoreina.parcheu.contract.UpdatePrivacy

/**
 * `GET/PATCH /v1/me/privacy` (E8.2, H3). Un solo documento con lo que H3 muestra: visibilidad en el grafo
 * de otras personas, los modos activos (solo lectura: se cambian con `PUT /v1/me/modes`) y si el gemelo
 * aprende de los chats de cada modo.
 *
 * Reglas del PATCH (solo se cambia lo presente):
 * - `visibility`: NAME / ANONYMOUS / HIDDEN.
 * - `learning[modo] = true`: concede `CHAT_LEARNING_<modo>` con la versión vigente del texto legal. Para
 *   Pareja exige Pareja activo y +18 confirmado ([LearningConsentPolicy.checkGrant], 403 AGE_REQUIRED).
 * - `learning[modo] = false`: agrega una fila de revocación a `consents` (si estaba concedido) y borra las
 *   memorias `PENDING` de ese modo, todo en una transacción.
 * El aprendizaje de un modo que no está en el mapa no se toca.
 */
class PrivacyService(private val identity: IdentityService) {

    suspend fun get(firebase: FirebaseUser): PrivacyDto = view(identity.requireUser(firebase))

    suspend fun update(firebase: FirebaseUser, request: UpdatePrivacy): PrivacyDto {
        val user = identity.requireUser(firebase)
        val now = identity.clock.instant()
        val learning = request.learning.orEmpty()
        val current = identity.repo.latestConsents(user.id).associateBy { it.kind }
        val modes = identity.repo.modes(user.id)

        val entries = mutableListOf<NewConsent>()
        val discard = mutableSetOf<Mode>()
        learning.forEach { (mode, enabled) ->
            val kind = learningKind(mode)
            val state = current[kind]
            if (enabled) {
                LearningConsentPolicy.checkGrant(kind, true, user, modes, now)
                if (state?.granted != true) entries += NewConsent(kind, granted = true, textVersion = identity.legalTextVersion)
            } else {
                if (state?.granted == true) entries += NewConsent(kind, granted = false, textVersion = state.textVersion)
                discard += mode
            }
        }
        if (request.visibility == null && learning.isEmpty()) {
            throw ValidationFailed("Indica visibility o learning")
        }
        if (request.visibility != null || entries.isNotEmpty() || discard.isNotEmpty()) {
            identity.repo.savePrivacy(user.id, request.visibility, entries, discard, now)
        }
        return view(identity.requireUser(firebase))
    }

    private suspend fun view(user: UserRecord): PrivacyDto {
        val consents = identity.repo.latestConsents(user.id).associateBy { it.kind }
        return PrivacyDto(
            visibility = user.visibility,
            modes = identity.repo.modes(user.id),
            learning = Mode.entries.associateWith { consents[learningKind(it)]?.granted == true },
        )
    }

    private fun learningKind(mode: Mode): ConsentKind = when (mode) {
        Mode.AMISTAD -> ConsentKind.CHAT_LEARNING_AMISTAD
        Mode.PAREJA -> ConsentKind.CHAT_LEARNING_PAREJA
        Mode.ESTUDIO -> ConsentKind.CHAT_LEARNING_ESTUDIO
    }
}
