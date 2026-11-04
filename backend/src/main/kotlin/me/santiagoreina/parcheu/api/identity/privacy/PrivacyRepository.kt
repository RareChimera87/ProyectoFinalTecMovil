package me.santiagoreina.parcheu.api.identity.privacy

import me.santiagoreina.parcheu.api.identity.NewConsent
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Visibility
import java.time.Instant
import java.util.UUID

/**
 * Escritura de los ajustes de privacidad (E8.2, H3). La lectura sale de `IdentityRepository`
 * (`users.visibility`, `modes`, `latestConsents`); esta interfaz la extiende para que el contenedor siga
 * recibiendo un solo repositorio de identity (mismo patrón que `ModesRepository`).
 */
interface PrivacyRepository {
    /**
     * Aplica en una sola transacción: la [visibility] nueva (si no es nula), las filas de [consents]
     * (`consents` solo admite INSERT) y el borrado de las memorias `PENDING` de los modos en
     * [discardPendingModes]. Apagar el aprendizaje de un modo y descartar lo que su gemelo aún no
     * confirmó son atómicos: no queda un recuerdo por aceptar de un chat que ya no se aprende.
     */
    suspend fun savePrivacy(
        userId: UUID,
        visibility: Visibility?,
        consents: List<NewConsent>,
        discardPendingModes: Set<Mode>,
        now: Instant,
    )
}
