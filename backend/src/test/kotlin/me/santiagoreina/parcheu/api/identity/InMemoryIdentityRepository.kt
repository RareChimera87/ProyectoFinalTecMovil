package me.santiagoreina.parcheu.api.identity

import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.contract.ConsentKind
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Visibility
import java.time.Instant
import java.util.UUID

/** Repositorio en memoria para pruebas de rutas y servicio (sin Postgres). Replica la semántica del real. */
class InMemoryIdentityRepository : IdentityRepository {
    val users = mutableListOf<UserRecord>()
    val consents = mutableListOf<Pair<UUID, ConsentRecord>>()
    val fcmTokens = mutableMapOf<String, UUID>()
    val twins = mutableSetOf<UUID>()
    val enabledModes = mutableMapOf<UUID, MutableSet<Mode>>()
    var universities = setOf(1, 2, 99)
    var interestSlugs = setOf("musica", "futbol", "cine", "lectura", "gym", "series", "anime")
    private var consentSeq = 0L

    /** Cuántas filas de `consents` tiene el usuario (para comprobar que solo se agregan). */
    fun consentRows(userId: UUID) = consents.count { it.first == userId }

    fun user(uid: String) = users.first { it.firebaseUid == uid }

    fun replace(user: UserRecord) {
        users[users.indexOfFirst { it.id == user.id }] = user
    }

    /** Usuario con los términos vigentes aceptados (sin perfil, gemelo ni modos). */
    fun seedAcceptedUser(uid: String, legalVersion: String) {
        val user = newUser(uid, "$uid@fake.parcheu.test", uid)
        users += user
        listOf(ConsentKind.TERMS, ConsentKind.PRIVACY).forEach { addConsent(user.id, it, true, legalVersion) }
    }

    fun addConsent(userId: UUID, kind: ConsentKind, granted: Boolean, version: String) {
        consents += userId to ConsentRecord(++consentSeq, kind, granted, version, FIXED_CLOCK.instant())
    }

    private fun newUser(uid: String, email: String?, name: String?, now: Instant = FIXED_CLOCK.instant()) = UserRecord(
        id = UUID.randomUUID(), firebaseUid = uid, email = email, displayName = name, photoUrl = null, career = null,
        universityId = null, interests = emptyList(), birthDate = null, ageVerifiedAt = null, underageAt = null,
        parejaConfirmedAt = null, visibility = Visibility.ANONYMOUS, isDemo = false, suspendedUntil = null,
        deletedAt = null, createdAt = now,
    )

    override suspend fun findByFirebaseUid(uid: String) = users.firstOrNull { it.firebaseUid == uid }

    override suspend fun createIfAbsent(uid: String, email: String?, displayName: String?, now: Instant): UserRecord =
        findByFirebaseUid(uid) ?: newUser(uid, email, displayName, now).also { users += it }

    override suspend fun updateEmail(userId: UUID, email: String, now: Instant) {
        replace(users.first { it.id == userId }.copy(email = email))
    }

    override suspend fun updateProfile(userId: UUID, patch: ProfilePatch, now: Instant): UserRecord {
        var u = users.first { it.id == userId }
        patch.displayName?.let { u = u.copy(displayName = it) }
        if (patch.clearCareer) u = u.copy(career = null) else patch.career?.let { u = u.copy(career = it) }
        patch.universityId?.let { u = u.copy(universityId = it) }
        patch.interests?.let { u = u.copy(interests = it) }
        replace(u)
        return u
    }

    override suspend fun universityExists(id: Int) = id in universities

    override suspend fun unknownInterests(slugs: List<String>) = slugs.filterNot { it in interestSlugs }

    override suspend fun modes(userId: UUID) = Mode.entries.associateWith { it in enabledModes[userId].orEmpty() }

    /** Última escritura de modos: estado y si fijó `pareja_confirmed_at`. */
    override suspend fun saveModes(
        userId: UUID,
        modes: Map<Mode, Boolean>,
        parejaConfirmedAt: Instant?,
        now: Instant,
        consentRevocations: List<NewConsent>,
    ) {
        enabledModes[userId] = modes.filterValues { it }.keys.toMutableSet()
        if (parejaConfirmedAt != null) replace(users.first { it.id == userId }.copy(parejaConfirmedAt = parejaConfirmedAt))
        appendConsents(userId, consentRevocations, now)
    }

    override suspend fun hasTwin(userId: UUID) = userId in twins

    override suspend fun latestConsents(userId: UUID): List<ConsentRecord> =
        consents.filter { it.first == userId }.map { it.second }
            .sortedByDescending { it.id }.distinctBy { it.kind }.sortedBy { it.kind.ordinal }

    override suspend fun appendConsents(userId: UUID, entries: List<NewConsent>, now: Instant) {
        entries.forEach { consents += userId to ConsentRecord(++consentSeq, it.kind, it.granted, it.textVersion, now) }
    }

    override suspend fun upsertFcmToken(userId: UUID, token: String, now: Instant) {
        fcmTokens[token] = userId
    }

    override suspend fun deleteFcmToken(userId: UUID, token: String) {
        if (fcmTokens[token] == userId) fcmTokens.remove(token)
    }

    override suspend fun markDeleted(userId: UUID, now: Instant) {
        val user = users.first { it.id == userId }
        if (user.deletedAt == null) replace(user.copy(deletedAt = now))
        fcmTokens.entries.removeIf { it.value == userId }
    }
}
