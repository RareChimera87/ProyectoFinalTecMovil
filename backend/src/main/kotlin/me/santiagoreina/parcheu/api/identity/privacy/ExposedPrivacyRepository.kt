package me.santiagoreina.parcheu.api.identity.privacy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.santiagoreina.parcheu.api.identity.NewConsent
import me.santiagoreina.parcheu.api.platform.db.tables.Consents
import me.santiagoreina.parcheu.api.platform.db.tables.TwinMemories
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.contract.MemoryStatus
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Visibility
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/** [PrivacyRepository] sobre Postgres: `users.visibility`, filas nuevas de `consents` y `twin_memories` PENDING. */
class ExposedPrivacyRepository(private val db: Database) : PrivacyRepository {

    override suspend fun savePrivacy(
        userId: UUID,
        visibility: Visibility?,
        consents: List<NewConsent>,
        discardPendingModes: Set<Mode>,
        now: Instant,
    ) {
        withContext(Dispatchers.IO) {
            transaction(db) {
                // Bloquea la fila del usuario como `saveExtraction` (E9.2): una extracción en curso termina antes
                // de este DELETE o relee la revocación, así no quedan memorias PENDING de un modo revocado.
                if (discardPendingModes.isNotEmpty()) Users.select(Users.id).where { Users.id eq userId }.forUpdate().empty()
                if (visibility != null) {
                    Users.update({ Users.id eq userId }) {
                        it[Users.visibility] = visibility
                        it[updatedAt] = OffsetDateTime.ofInstant(now, ZoneOffset.UTC)
                    }
                }
                consents.forEach { e ->
                    Consents.insert {
                        it[Consents.userId] = userId
                        it[kind] = e.kind
                        it[granted] = e.granted
                        it[textVersion] = e.textVersion
                        it[createdAt] = OffsetDateTime.ofInstant(now, ZoneOffset.UTC)
                    }
                }
                if (discardPendingModes.isNotEmpty()) {
                    TwinMemories.deleteWhere {
                        (TwinMemories.userId eq userId) and
                            (TwinMemories.mode inList discardPendingModes) and
                            (TwinMemories.status eq MemoryStatus.PENDING)
                    }
                }
            }
        }
    }
}
