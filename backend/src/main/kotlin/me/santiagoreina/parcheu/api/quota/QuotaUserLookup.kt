package me.santiagoreina.parcheu.api.quota

import me.santiagoreina.parcheu.api.platform.db.tables.Users
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/**
 * Traduce el UID de Firebase del token al `users.id`. Es lo único que las cuotas necesitan de la
 * identidad; cuando E2.1 se mezcle puede implementarse con su repositorio.
 */
fun interface QuotaUserLookup {
    /** `users.id` del usuario vivo (no borrado) con ese UID, o null si aún no creó sesión. */
    fun userIdFor(firebaseUid: String): UUID?
}

class ExposedQuotaUserLookup(private val db: Database) : QuotaUserLookup {
    override fun userIdFor(firebaseUid: String): UUID? = transaction(db) {
        Users.select(Users.id)
            .where { (Users.firebaseUid eq firebaseUid) and Users.deletedAt.isNull() }
            .singleOrNull()
            ?.get(Users.id)
    }
}

/** `ENV=local` sin base y pruebas: cada UID tiene un id estable derivado de él. */
object DerivedQuotaUserLookup : QuotaUserLookup {
    override fun userIdFor(firebaseUid: String): UUID = UUID.nameUUIDFromBytes("parcheu:$firebaseUid".toByteArray())
}
