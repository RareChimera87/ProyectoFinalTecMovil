package me.santiagoreina.parcheu.api.identity.modes

import me.santiagoreina.parcheu.api.identity.IdentityService
import me.santiagoreina.parcheu.api.platform.auth.FirebaseUser
import me.santiagoreina.parcheu.api.platform.errors.AgeRequired
import me.santiagoreina.parcheu.api.platform.errors.ValidationFailed
import me.santiagoreina.parcheu.contract.MeDto
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.UpdateModes

/**
 * `PUT /v1/me/modes` (E2.5, B2/B3 y H3). Reemplaza el estado de los tres modos: un modo ausente en
 * el mapa queda apagado. Reglas:
 * - Debe quedar al menos un modo activo (400 VALIDATION), igual que el botón Continuar de B2.
 * - Encender Pareja (pasar de apagado a encendido) exige `age_verified_at` (lo fija E2.6 en B0;
 *   si falta, 403 AGE_REQUIRED) y la confirmación explícita de B3 en la misma petición
 *   (`parejaConfirmed = true`; si falta, 400 VALIDATION). La confirmación queda en
 *   `users.pareja_confirmed_at`. Mantener Pareja encendido no vuelve a pedirla.
 */
class ModesService(private val identity: IdentityService, private val repo: ModesRepository) {

    suspend fun update(firebase: FirebaseUser, request: UpdateModes): MeDto {
        val user = identity.requireUser(firebase)
        val target = Mode.entries.associateWith { request.modes[it] ?: false }
        if (target.values.none { it }) throw ValidationFailed("Activa al menos un modo")

        val current = identity.repo.modes(user.id)
        val now = identity.clock.instant()
        val turningOnPareja = target.getValue(Mode.PAREJA) && current[Mode.PAREJA] != true
        if (turningOnPareja) {
            if (user.ageVerifiedAt == null || user.underageAt != null) {
                throw AgeRequired("Verifica tu fecha de nacimiento antes de activar Pareja")
            }
            if (!request.parejaConfirmed) {
                throw ValidationFailed("Confirma que eres mayor de 18 años para activar Pareja")
            }
        }
        repo.saveModes(user.id, target, parejaConfirmedAt = if (turningOnPareja) now else null, now = now)
        return identity.me(firebase)
    }
}
