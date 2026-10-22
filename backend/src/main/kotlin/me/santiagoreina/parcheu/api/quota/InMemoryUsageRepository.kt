package me.santiagoreina.parcheu.api.quota

import me.santiagoreina.parcheu.contract.QuotaKind
import java.time.Instant
import java.util.UUID

/** `usage_events` en memoria: pruebas y `ENV=local` sin base de datos. Seguro entre hilos. */
class InMemoryUsageRepository : UsageRepository {
    private data class Event(val userId: UUID, val kind: QuotaKind, val refId: String, var refunded: Boolean, val createdAt: Instant)

    private val events = mutableListOf<Event>()

    @Synchronized
    override fun charge(userId: UUID, kind: QuotaKind, refId: String, window: QuotaWindow, limit: Int, now: Instant): ChargeResult {
        events.firstOrNull { it.kind == kind && it.refId == refId }?.let {
            return ChargeResult.AlreadyCharged(it.userId, it.refunded)
        }
        if (countActive(userId, kind, window) >= limit) return ChargeResult.LimitReached
        events += Event(userId, kind, refId, refunded = false, createdAt = now)
        return ChargeResult.Charged
    }

    @Synchronized
    override fun countActive(userId: UUID, kind: QuotaKind, window: QuotaWindow): Int = events.count {
        it.userId == userId && it.kind == kind && !it.refunded && it.createdAt >= window.start && it.createdAt < window.end
    }

    @Synchronized
    override fun refund(kind: QuotaKind, refId: String): Boolean {
        val event = events.firstOrNull { it.kind == kind && it.refId == refId } ?: return false
        event.refunded = true
        return true
    }
}
