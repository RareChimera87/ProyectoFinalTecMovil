package me.santiagoreina.parcheu.api.social.invites

import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.RedeemStatus
import java.time.Instant
import java.util.UUID

/** Un canje: [redeemer] pidió conectar con [owner] (dueño del código) en [mode]. */
data class Redemption(
    val owner: UUID,
    val redeemer: UUID,
    val mode: Mode,
    val status: RedeemStatus,
    val createdAt: Instant,
)

/**
 * Persistencia de las invitaciones (tablas `invites` e `invite_redemptions`). [ExposedInviteRepository]
 * es la real; [InMemoryInviteRepository] replica su semántica para pruebas y `ENV=local`.
 */
interface InviteRepository {
    suspend fun codeOf(userId: UUID): String?

    suspend fun ownerOf(code: String): UUID?

    /**
     * Guarda [code] como el código de [userId] si ni el código ni el usuario tienen uno. Devuelve false
     * si chocó (otro usuario ya tiene ese código, o [userId] ya tenía uno por una petición concurrente).
     */
    suspend fun claim(code: String, userId: UUID): Boolean

    suspend fun redemption(owner: UUID, redeemer: UUID, mode: Mode): Redemption?

    /** Registra un canje PENDING; false si ya existía (el canje es idempotente). */
    suspend fun addRedemption(owner: UUID, redeemer: UUID, mode: Mode, now: Instant): Boolean

    /** Canjes PENDING del código de [owner], los más recientes primero. */
    suspend fun pendingFor(owner: UUID): List<Redemption>

    /** Marca como CONNECTED el canje (si existe), con la fecha de aceptación. */
    suspend fun markConnected(owner: UUID, redeemer: UUID, mode: Mode, now: Instant)

    /** Borra el canje PENDING (si existe): su modo dejó de estar activo para alguno de los dos. */
    suspend fun removeRedemption(owner: UUID, redeemer: UUID, mode: Mode)

    /**
     * Registra un intento de canje de [userId] si lleva menos de [limit] desde [since] (exclusivo) y
     * devuelve true; si ya llegó al límite no registra nada y devuelve false. Borra sus intentos
     * anteriores a [since], que ya no cuentan.
     */
    suspend fun tryRecordAttempt(userId: UUID, since: Instant, limit: Int, now: Instant): Boolean
}

/** Invitaciones en memoria con la misma semántica que Postgres (unicidad de código y de usuario). */
class InMemoryInviteRepository : InviteRepository {
    private val lock = Any()
    private val codes = mutableMapOf<String, UUID>()
    private val byUser = mutableMapOf<UUID, String>()
    private val redemptions = mutableMapOf<Triple<UUID, UUID, Mode>, Redemption>()
    private val attempts = mutableMapOf<UUID, MutableList<Instant>>()

    override suspend fun codeOf(userId: UUID): String? = synchronized(lock) { byUser[userId] }

    override suspend fun ownerOf(code: String): UUID? = synchronized(lock) { codes[code] }

    override suspend fun claim(code: String, userId: UUID): Boolean = synchronized(lock) {
        if (code in codes || userId in byUser) return false
        codes[code] = userId
        byUser[userId] = code
        true
    }

    override suspend fun redemption(owner: UUID, redeemer: UUID, mode: Mode): Redemption? =
        synchronized(lock) { redemptions[Triple(owner, redeemer, mode)] }

    override suspend fun addRedemption(owner: UUID, redeemer: UUID, mode: Mode, now: Instant): Boolean = synchronized(lock) {
        val key = Triple(owner, redeemer, mode)
        if (key in redemptions) return false
        redemptions[key] = Redemption(owner, redeemer, mode, RedeemStatus.PENDING, now)
        true
    }

    override suspend fun pendingFor(owner: UUID): List<Redemption> = synchronized(lock) {
        redemptions.values.filter { it.owner == owner && it.status == RedeemStatus.PENDING }.sortedByDescending { it.createdAt }
    }

    override suspend fun markConnected(owner: UUID, redeemer: UUID, mode: Mode, now: Instant) {
        synchronized(lock) {
            val key = Triple(owner, redeemer, mode)
            redemptions[key]?.let { redemptions[key] = it.copy(status = RedeemStatus.CONNECTED) }
        }
    }

    override suspend fun removeRedemption(owner: UUID, redeemer: UUID, mode: Mode) {
        synchronized(lock) {
            val key = Triple(owner, redeemer, mode)
            if (redemptions[key]?.status == RedeemStatus.PENDING) redemptions.remove(key)
        }
    }

    override suspend fun tryRecordAttempt(userId: UUID, since: Instant, limit: Int, now: Instant): Boolean = synchronized(lock) {
        val mine = attempts.getOrPut(userId) { mutableListOf() }
        mine.removeAll { !it.isAfter(since) }
        if (mine.size >= limit) return false
        mine += now
        true
    }

    /** Borra a [userId] como lo haría la purga (on delete cascade). */
    fun purge(userId: UUID) = synchronized(lock) {
        byUser.remove(userId)?.let(codes::remove)
        attempts.remove(userId)
        redemptions.keys.removeAll { it.first == userId || it.second == userId }
    }
}
