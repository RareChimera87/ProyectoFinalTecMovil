package me.santiagoreina.parcheu.api.identity.retention

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/** Plazos y aislamiento de fallos del job de retención (E8.8) con Fakes y el reloj fijo. */
class RetentionServiceTest : FunSpec({
    val now = FIXED_CLOCK.instant()

    class FakeRepo : RetentionRepository {
        val cutoffs = mutableMapOf<String, Instant>()
        val counts = mutableMapOf<String, Int>()
        val chats = CopyOnWriteArrayList<UUID>()
        var failNotifications = false

        private fun hit(rule: String, cutoff: Instant): Int {
            cutoffs[rule] = cutoff
            return counts[rule] ?: 0
        }

        override suspend fun deleteNotificationsBefore(cutoff: Instant): Int {
            if (failNotifications) error("Postgres caído")
            return hit("notifications", cutoff)
        }

        override suspend fun anonymizeAiUsageBefore(cutoff: Instant) = hit("ai_usage", cutoff)

        override suspend fun clearReportSnapshotsResolvedBefore(cutoff: Instant) = hit("snapshots", cutoff)

        override suspend fun clearTranscriptsBefore(cutoff: Instant) = hit("transcripts", cutoff)

        override suspend fun deleteMatchQueueExpiredBefore(cutoff: Instant) = hit("match_queue", cutoff)

        override suspend fun deleteExpiredMatchesBefore(cutoff: Instant) = hit("matches", cutoff)

        override suspend fun closedScenarioChats(cutoff: Instant, limit: Int): List<UUID> {
            cutoffs["chats"] = cutoff
            return chats.toList()
        }

        override suspend fun deleteChats(ids: List<UUID>): Int {
            chats.removeAll(ids.toSet())
            return ids.size
        }
    }

    test("cada regla usa su plazo contra el reloj inyectado") {
        val repo = FakeRepo()
        val mirror = RecordingRetentionMirror()
        RetentionService(repo, mirror, { 0 }, FIXED_CLOCK).run()
        repo.cutoffs["notifications"] shouldBe now.minusSeconds(90 * 86400)
        repo.cutoffs["ai_usage"] shouldBe now.minusSeconds(90 * 86400)
        repo.cutoffs["snapshots"] shouldBe now.minusSeconds(180 * 86400)
        repo.cutoffs["chats"] shouldBe now.minusSeconds(30 * 86400)
        repo.cutoffs["match_queue"] shouldBe now.minusSeconds(7 * 86400)
        repo.cutoffs["matches"] shouldBe now.minusSeconds(7 * 86400)
        // 12 meses calendario en Bogotá (UTC-5): 2025-10-25 12:00Z.
        repo.cutoffs["transcripts"] shouldBe Instant.parse("2025-10-25T12:00:00Z")
        mirror.simulationCutoffs shouldContainExactly listOf(now.minusSeconds(30 * 86400))
        mirror.ticketCutoffs shouldContainExactly listOf(now.minusSeconds(7 * 86400))
    }

    test("el límite de transcripts son 12 meses de calendario, no 365 días") {
        // 2028 es bisiesto: de 2028-03-01 a 2027-03-01 hay 366 días.
        val cutoff = RetentionPolicy.transcriptCutoff(Instant.parse("2028-03-01T12:00:00Z"), ZoneOffset.UTC)
        cutoff shouldBe Instant.parse("2027-03-01T12:00:00Z")
    }

    test("purga los chats por tandas, primero en Firestore y luego en Postgres") {
        val repo = FakeRepo()
        val mirror = RecordingRetentionMirror()
        val ids = List(3) { UUID.randomUUID() }
        repo.chats += ids
        val report = RetentionService(repo, mirror, { 0 }, FIXED_CLOCK).run()
        report.purged[RetentionRule.SCENARIO_CHATS] shouldBe 3
        mirror.deletedChats shouldContainExactly ids
        repo.chats.size shouldBe 0
    }

    test("cuenta las reglas en retention_purged_total y reencola las purgas atascadas") {
        val repo = FakeRepo().apply { counts["notifications"] = 4; counts["matches"] = 2 }
        val service = RetentionService(repo, RecordingRetentionMirror(), { 3 }, FIXED_CLOCK)
        val report = service.run()
        report.purged[RetentionRule.NOTIFICATIONS] shouldBe 4
        report.purged[RetentionRule.STALLED_ACCOUNTS] shouldBe 3
        report.total shouldBe 9
        service.purgedTotal(RetentionRule.NOTIFICATIONS) shouldBe 4
        service.purgedTotal(RetentionRule.AI_USAGE) shouldBe 0
        service.run()
        service.purgedTotal(RetentionRule.NOTIFICATIONS) shouldBe 8
    }

    test("una regla que falla no impide las demás y el job reporta el error") {
        val repo = FakeRepo().apply { failNotifications = true; counts["matches"] = 5 }
        val mirror = RecordingRetentionMirror()
        val service = RetentionService(repo, mirror, { 0 }, FIXED_CLOCK)
        val error = shouldThrow<RetentionFailed> { service.run() }
        error.report.failed.keys shouldContainExactly listOf(RetentionRule.NOTIFICATIONS)
        error.report.purged[RetentionRule.MATCHES] shouldBe 5
        // Los límites de las reglas posteriores sí se aplicaron.
        mirror.simulationCutoffs.size shouldBe 1
    }

    test("si Firestore falla, los chats siguen en Postgres para el siguiente ciclo") {
        val repo = FakeRepo()
        val mirror = RecordingRetentionMirror().apply { failWith = IllegalStateException("Firestore caído") }
        repo.chats += UUID.randomUUID()
        shouldThrow<RetentionFailed> { RetentionService(repo, mirror, { 0 }, FIXED_CLOCK).run() }
        repo.chats.size shouldBe 1
    }
})
