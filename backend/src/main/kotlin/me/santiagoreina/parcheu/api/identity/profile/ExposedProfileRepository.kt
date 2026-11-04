package me.santiagoreina.parcheu.api.identity.profile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.santiagoreina.parcheu.api.platform.db.tables.Interests
import me.santiagoreina.parcheu.api.platform.db.tables.Universities
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.contract.CatalogDto
import me.santiagoreina.parcheu.contract.Interest
import me.santiagoreina.parcheu.contract.University
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/** [ProfileRepository] sobre Postgres con Exposed. */
class ExposedProfileRepository(private val db: Database) : ProfileRepository {

    private suspend fun <T> tx(block: () -> T): T = withContext(Dispatchers.IO) { transaction(db) { block() } }

    override suspend fun catalog(): CatalogDto = tx {
        val universities = Universities.selectAll()
            .where { Universities.active eq true }
            .orderBy(Universities.id to SortOrder.ASC)
            .map { University(it[Universities.id].toInt(), it[Universities.shortName], it[Universities.name]) }
        val interests = Interests.selectAll()
            .orderBy(Interests.label to SortOrder.ASC)
            .map { Interest(it[Interests.slug], it[Interests.label], it[Interests.icon]) }
        CatalogDto(universities, interests)
    }

    override suspend fun setBirthDate(
        userId: UUID,
        birthDate: LocalDate,
        ageVerifiedAt: Instant?,
        underageAt: Instant?,
        now: Instant,
    ): Boolean = tx {
        Users.update({ (Users.id eq userId) and Users.birthDate.isNull() }) {
            it[Users.birthDate] = birthDate
            it[Users.ageVerifiedAt] = ageVerifiedAt?.odt()
            it[Users.underageAt] = underageAt?.odt()
            it[Users.updatedAt] = now.odt()
        } == 1
    }
}

private fun Instant.odt(): OffsetDateTime = OffsetDateTime.ofInstant(this, ZoneOffset.UTC)
