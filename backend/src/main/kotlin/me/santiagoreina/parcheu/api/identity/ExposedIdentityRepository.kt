package me.santiagoreina.parcheu.api.identity

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.santiagoreina.parcheu.api.identity.modes.ExposedModesRepository
import me.santiagoreina.parcheu.api.identity.modes.ModesRepository
import me.santiagoreina.parcheu.api.platform.db.tables.Consents
import me.santiagoreina.parcheu.api.platform.db.tables.FcmTokens
import me.santiagoreina.parcheu.api.platform.db.tables.Interests
import me.santiagoreina.parcheu.api.platform.db.tables.Twins
import me.santiagoreina.parcheu.api.platform.db.tables.Universities
import me.santiagoreina.parcheu.api.platform.db.tables.UserModes
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.contract.Mode
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/** Repositorio de identity sobre Postgres con Exposed. Cada operación abre su transacción (arquitectura.md §3.2). */
class ExposedIdentityRepository(private val db: Database) :
    IdentityRepository,
    ModesRepository by ExposedModesRepository(db) {

    private suspend fun <T> tx(block: () -> T): T = withContext(Dispatchers.IO) { transaction(db) { block() } }

    override suspend fun findByFirebaseUid(uid: String): UserRecord? = tx {
        Users.selectAll().where { Users.firebaseUid eq uid }.singleOrNull()?.toUser()
    }

    override suspend fun createIfAbsent(uid: String, email: String?, displayName: String?, now: Instant): UserRecord = tx {
        // ON CONFLICT DO NOTHING sobre firebase_uid único: seguro ante dos sesiones simultáneas.
        Users.insertIgnore {
            it[firebaseUid] = uid
            it[Users.email] = email
            it[Users.displayName] = displayName
            it[createdAt] = now.odt()
            it[updatedAt] = now.odt()
        }
        Users.selectAll().where { Users.firebaseUid eq uid }.single().toUser()
    }

    override suspend fun updateEmail(userId: UUID, email: String, now: Instant) {
        tx {
            Users.update({ Users.id eq userId }) {
                it[Users.email] = email
                it[updatedAt] = now.odt()
            }
        }
    }

    override suspend fun updateProfile(userId: UUID, patch: ProfilePatch, now: Instant): UserRecord = tx {
        Users.update({ Users.id eq userId }) {
            patch.displayName?.let { v -> it[displayName] = v }
            if (patch.clearCareer) it[career] = null else patch.career?.let { v -> it[career] = v }
            patch.universityId?.let { v -> it[universityId] = v.toShort() }
            patch.interests?.let { v -> it[interests] = v }
            it[updatedAt] = now.odt()
        }
        Users.selectAll().where { Users.id eq userId }.single().toUser()
    }

    override suspend fun universityExists(id: Int): Boolean = tx {
        Universities.selectAll().where { (Universities.id eq id.toShort()) and (Universities.active eq true) }.any()
    }

    override suspend fun unknownInterests(slugs: List<String>): List<String> = tx {
        if (slugs.isEmpty()) return@tx emptyList()
        val known = Interests.selectAll().where { Interests.slug inList slugs }.map { it[Interests.slug] }.toSet()
        slugs.filterNot { it in known }
    }

    override suspend fun modes(userId: UUID): Map<Mode, Boolean> = tx {
        val rows = UserModes.selectAll().where { UserModes.userId eq userId }.associate { it[UserModes.mode] to it[UserModes.enabled] }
        Mode.entries.associateWith { rows[it] ?: false }
    }

    override suspend fun hasTwin(userId: UUID): Boolean = tx {
        Twins.selectAll().where { Twins.userId eq userId }.any()
    }

    override suspend fun latestConsents(userId: UUID): List<ConsentRecord> = tx {
        Consents.selectAll()
            .where { Consents.userId eq userId }
            .orderBy(Consents.id to SortOrder.DESC)
            .map {
                ConsentRecord(it[Consents.id], it[Consents.kind], it[Consents.granted], it[Consents.textVersion], it[Consents.createdAt].toInstant())
            }
            .distinctBy { it.kind }
            .sortedBy { it.kind.ordinal }
    }

    override suspend fun appendConsents(userId: UUID, entries: List<NewConsent>, now: Instant) {
        tx {
            entries.forEach { e ->
                Consents.insert {
                    it[Consents.userId] = userId
                    it[kind] = e.kind
                    it[granted] = e.granted
                    it[textVersion] = e.textVersion
                    it[createdAt] = now.odt()
                }
            }
        }
    }

    override suspend fun upsertFcmToken(userId: UUID, token: String, now: Instant) {
        tx {
            FcmTokens.upsert(FcmTokens.token) {
                it[FcmTokens.token] = token
                it[FcmTokens.userId] = userId
                it[updatedAt] = now.odt()
            }
        }
    }

    override suspend fun deleteFcmToken(userId: UUID, token: String) {
        tx { FcmTokens.deleteWhere { (FcmTokens.token eq token) and (FcmTokens.userId eq userId) } }
    }

    override suspend fun markDeleted(userId: UUID, now: Instant) {
        tx {
            Users.update({ (Users.id eq userId) and Users.deletedAt.isNull() }) {
                it[deletedAt] = now.odt()
                it[updatedAt] = now.odt()
            }
            FcmTokens.deleteWhere { FcmTokens.userId eq userId }
        }
    }

    override suspend fun suspendUntil(userId: UUID, until: Instant, now: Instant): Instant? = tx {
        val current = Users.selectAll().where { Users.id eq userId }.forUpdate().singleOrNull() ?: return@tx null
        val effective = current[Users.suspendedUntil]?.toInstant()?.takeIf { it.isAfter(until) } ?: until
        Users.update({ Users.id eq userId }) {
            it[suspendedUntil] = effective.odt()
            it[updatedAt] = now.odt()
        }
        effective
    }
}

private fun Instant.odt(): OffsetDateTime = OffsetDateTime.ofInstant(this, ZoneOffset.UTC)

private fun ResultRow.toUser() = UserRecord(
    id = this[Users.id],
    firebaseUid = this[Users.firebaseUid],
    email = this[Users.email],
    displayName = this[Users.displayName],
    photoUrl = this[Users.photoUrl],
    career = this[Users.career],
    universityId = this[Users.universityId]?.toInt(),
    interests = this[Users.interests],
    birthDate = this[Users.birthDate],
    ageVerifiedAt = this[Users.ageVerifiedAt]?.toInstant(),
    underageAt = this[Users.underageAt]?.toInstant(),
    parejaConfirmedAt = this[Users.parejaConfirmedAt]?.toInstant(),
    visibility = this[Users.visibility],
    isDemo = this[Users.isDemo],
    suspendedUntil = this[Users.suspendedUntil]?.toInstant(),
    deletedAt = this[Users.deletedAt]?.toInstant(),
    createdAt = this[Users.createdAt].toInstant(),
)
