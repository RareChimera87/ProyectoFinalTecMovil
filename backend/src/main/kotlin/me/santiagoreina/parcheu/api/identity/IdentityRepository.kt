package me.santiagoreina.parcheu.api.identity

import me.santiagoreina.parcheu.contract.Mode
import java.time.Instant
import java.util.UUID

/**
 * Acceso a datos del dominio identity. La implementación real es [ExposedIdentityRepository];
 * los tests de rutas usan una en memoria y los de esquema, Postgres de verdad.
 */
interface IdentityRepository {
    suspend fun findByFirebaseUid(uid: String): UserRecord?

    /** Alta idempotente por `firebase_uid`: dos llamadas (incluso concurrentes) dejan un solo usuario. */
    suspend fun createIfAbsent(uid: String, email: String?, displayName: String?, now: Instant): UserRecord

    suspend fun updateEmail(userId: UUID, email: String, now: Instant)

    suspend fun updateProfile(userId: UUID, patch: ProfilePatch, now: Instant): UserRecord

    suspend fun universityExists(id: Int): Boolean

    /** Devuelve los slugs que no están en el catálogo de intereses. */
    suspend fun unknownInterests(slugs: List<String>): List<String>

    /** Estado de los tres modos (los que no tienen fila cuentan como desactivados). */
    suspend fun modes(userId: UUID): Map<Mode, Boolean>

    suspend fun hasTwin(userId: UUID): Boolean

    /** Última fila de cada tipo de consentimiento del usuario. */
    suspend fun latestConsents(userId: UUID): List<ConsentRecord>

    /** Agrega filas a `consents` en una sola transacción. Nunca actualiza ni borra. */
    suspend fun appendConsents(userId: UUID, entries: List<NewConsent>, now: Instant)

    /** Registra el token; si ya pertenecía a otro usuario (mismo dispositivo) pasa a este. */
    suspend fun upsertFcmToken(userId: UUID, token: String, now: Instant)

    /** Borra el token solo si es de este usuario. */
    suspend fun deleteFcmToken(userId: UUID, token: String)
}
