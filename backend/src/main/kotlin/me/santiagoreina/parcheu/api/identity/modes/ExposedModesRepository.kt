package me.santiagoreina.parcheu.api.identity.modes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.santiagoreina.parcheu.api.identity.NewConsent
import me.santiagoreina.parcheu.api.platform.db.tables.Consents
import me.santiagoreina.parcheu.api.platform.db.tables.UserModes
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.contract.Mode
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/** [ModesRepository] sobre Postgres: una fila de `user_modes` por modo (upsert) y `users.pareja_confirmed_at`. */
class ExposedModesRepository(private val db: Database) : ModesRepository {

    override suspend fun saveModes(
        userId: UUID,
        modes: Map<Mode, Boolean>,
        parejaConfirmedAt: Instant?,
        now: Instant,
        consentRevocations: List<NewConsent>,
    ) {
        withContext(Dispatchers.IO) {
            transaction(db) {
                val enabledSince = UserModes.selectAll()
                    .where { UserModes.userId eq userId }
                    .associate { it[UserModes.mode] to (if (it[UserModes.enabled]) it[UserModes.enabledAt] else null) }
                Mode.entries.forEach { mode ->
                    val enabled = modes[mode] ?: false
                    UserModes.upsert(UserModes.userId, UserModes.mode) {
                        it[UserModes.userId] = userId
                        it[UserModes.mode] = mode
                        it[UserModes.enabled] = enabled
                        // enabled_at = desde cuándo está activo: se conserva si ya lo estaba y se borra al apagarlo.
                        it[UserModes.enabledAt] = if (enabled) enabledSince[mode] ?: now.odt() else null
                    }
                }
                Users.update({ Users.id eq userId }) {
                    if (parejaConfirmedAt != null) it[Users.parejaConfirmedAt] = parejaConfirmedAt.odt()
                    it[updatedAt] = now.odt()
                }
                consentRevocations.forEach { e ->
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
    }
}

private fun Instant.odt(): OffsetDateTime = OffsetDateTime.ofInstant(this, ZoneOffset.UTC)
