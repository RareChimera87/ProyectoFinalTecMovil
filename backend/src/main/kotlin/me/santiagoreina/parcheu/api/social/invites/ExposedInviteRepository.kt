package me.santiagoreina.parcheu.api.social.invites

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.santiagoreina.parcheu.api.platform.db.tables.InviteRedeemAttempts
import me.santiagoreina.parcheu.api.platform.db.tables.InviteRedemptions
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.api.platform.db.tables.Invites
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.RedeemStatus
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/** Invitaciones sobre Postgres (`invites` de V1 e `invite_redemptions` de E6.1). */
class ExposedInviteRepository(private val db: Database) : InviteRepository {

    private suspend fun <T> tx(block: JdbcTransaction.() -> T): T = withContext(Dispatchers.IO) { transaction(db) { block() } }

    override suspend fun codeOf(userId: UUID): String? = tx {
        Invites.selectAll().where { Invites.userId eq userId }.singleOrNull()?.get(Invites.code)
    }

    override suspend fun ownerOf(code: String): UUID? = tx {
        Invites.selectAll().where { Invites.code eq code }.singleOrNull()?.get(Invites.userId)
    }

    /** `insert ... on conflict do nothing`: cubre el choque de código (pk) y el de usuario (unique). */
    override suspend fun claim(code: String, userId: UUID): Boolean = tx {
        Invites.insertIgnore {
            it[Invites.code] = code
            it[Invites.userId] = userId
        }.insertedCount == 1
    }

    override suspend fun redemption(owner: UUID, redeemer: UUID, mode: Mode): Redemption? = tx {
        InviteRedemptions.selectAll()
            .where { (InviteRedemptions.ownerId eq owner) and (InviteRedemptions.redeemerId eq redeemer) and (InviteRedemptions.mode eq mode) }
            .singleOrNull()
            ?.toRedemption()
    }

    override suspend fun addRedemption(owner: UUID, redeemer: UUID, mode: Mode, now: Instant): Boolean = tx {
        InviteRedemptions.insertIgnore {
            it[ownerId] = owner
            it[redeemerId] = redeemer
            it[InviteRedemptions.mode] = mode
            it[status] = RedeemStatus.PENDING
            it[createdAt] = now.odt()
        }.insertedCount == 1
    }

    override suspend fun pendingFor(owner: UUID): List<Redemption> = tx {
        InviteRedemptions.selectAll()
            .where { (InviteRedemptions.ownerId eq owner) and (InviteRedemptions.status eq RedeemStatus.PENDING) }
            .orderBy(InviteRedemptions.createdAt to SortOrder.DESC)
            .map { it.toRedemption() }
    }

    override suspend fun markConnected(owner: UUID, redeemer: UUID, mode: Mode, now: Instant) {
        tx {
            InviteRedemptions.update({
                (InviteRedemptions.ownerId eq owner) and (InviteRedemptions.redeemerId eq redeemer) and (InviteRedemptions.mode eq mode)
            }) {
                it[status] = RedeemStatus.CONNECTED
                it[acceptedAt] = now.odt()
            }
        }
    }

    override suspend fun removeRedemption(owner: UUID, redeemer: UUID, mode: Mode) {
        tx {
            InviteRedemptions.deleteWhere {
                (InviteRedemptions.ownerId eq owner) and (InviteRedemptions.redeemerId eq redeemer) and
                    (InviteRedemptions.mode eq mode)
            }
        }
    }

    /**
     * Bloquea la fila del usuario (`SELECT ... FOR UPDATE`, como `ExposedUsageRepository`) para que dos
     * canjes simultáneos de la misma persona no se pasen del límite.
     */
    override suspend fun tryRecordAttempt(userId: UUID, since: Instant, limit: Int, now: Instant): Boolean = tx {
        if (!Users.select(Users.id).where { Users.id eq userId }.forUpdate().any()) return@tx false
        InviteRedeemAttempts.deleteWhere { (InviteRedeemAttempts.userId eq userId) and (InviteRedeemAttempts.createdAt lessEq since.odt()) }
        val used = InviteRedeemAttempts.selectAll()
            .where { (InviteRedeemAttempts.userId eq userId) and (InviteRedeemAttempts.createdAt greater since.odt()) }
            .count()
        if (used >= limit) return@tx false
        InviteRedeemAttempts.insert {
            it[InviteRedeemAttempts.userId] = userId
            it[createdAt] = now.odt()
        }
        true
    }

    private fun ResultRow.toRedemption() = Redemption(
        owner = this[InviteRedemptions.ownerId],
        redeemer = this[InviteRedemptions.redeemerId],
        mode = this[InviteRedemptions.mode],
        status = this[InviteRedemptions.status],
        createdAt = this[InviteRedemptions.createdAt].toInstant(),
    )

    private fun Instant.odt(): OffsetDateTime = OffsetDateTime.ofInstant(this, ZoneOffset.UTC)
}
