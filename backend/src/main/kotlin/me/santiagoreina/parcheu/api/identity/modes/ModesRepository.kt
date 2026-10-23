package me.santiagoreina.parcheu.api.identity.modes

import me.santiagoreina.parcheu.api.identity.NewConsent
import me.santiagoreina.parcheu.contract.Mode
import java.time.Instant
import java.util.UUID

/**
 * Escritura de los modos del usuario (E2.5). La lectura vive en `IdentityRepository.modes`, que
 * extiende esta interfaz para que el contenedor siga recibiendo un solo repositorio de identity.
 */
interface ModesRepository {
    /**
     * Guarda el estado de los tres modos en una transacción. Si [parejaConfirmedAt] no es nulo,
     * también fija `users.pareja_confirmed_at` (confirmación explícita de B3). Las [consentRevocations]
     * se agregan a `consents` en la misma transacción, para que apagar un modo y revocar su
     * aprendizaje sean atómicos.
     */
    suspend fun saveModes(
        userId: UUID,
        modes: Map<Mode, Boolean>,
        parejaConfirmedAt: Instant?,
        now: Instant,
        consentRevocations: List<NewConsent> = emptyList(),
    )
}
