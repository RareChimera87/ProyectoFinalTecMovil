package me.santiagoreina.parcheu.api.identity.retention

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.platform.BogotaZone
import me.santiagoreina.parcheu.api.platform.db.TestDatabase
import me.santiagoreina.parcheu.api.platform.db.connectExposed
import me.santiagoreina.parcheu.api.platform.db.runMigrations
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Criterios de E8.8 contra Postgres 16 real (Docker o `PARCHEU_TEST_DB_URL`): para cada regla, un registro
 * un segundo antes de cumplir el plazo se conserva y uno un segundo después se purga o anonimiza; una
 * segunda ejecución no cambia nada.
 */
class RetentionDbTest : FunSpec({
    val now = FIXED_CLOCK.instant()
    val day = 86400L

    fun Connection.exec(sql: String, vararg params: Any?) {
        prepareStatement(sql).use { st ->
            params.forEachIndexed { i, v -> if (v == null) st.setNull(i + 1, java.sql.Types.VARCHAR) else st.setObject(i + 1, v) }
            st.execute()
        }
    }

    fun Connection.count(sql: String, vararg params: Any?): Int =
        prepareStatement(sql).use { st ->
            params.forEachIndexed { i, v -> st.setObject(i + 1, v) }
            st.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }

    fun Connection.ids(sql: String): List<UUID> =
        prepareStatement(sql).use { st -> st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getObject(1, UUID::class.java)) } } }

    fun ts(instant: Instant) = Timestamp.from(instant)

    // Un segundo antes del plazo (todavía se conserva) y un segundo después (se purga).
    fun kept(cutoff: Instant) = ts(cutoff.plusSeconds(1))
    fun due(cutoff: Instant) = ts(cutoff.minusSeconds(1))

    test("cada regla conserva lo que está justo antes del plazo y purga lo que está justo después; repetir no cambia nada")
        .config(enabled = TestDatabase.enabled) {
            TestDatabase.withCleanDatabase { ds ->
                runMigrations(ds)
                val db = connectExposed(ds)
                ds.connection.use { c ->
                    fun user(): UUID {
                        val id = UUID.randomUUID()
                        c.exec(
                            "insert into users (id, firebase_uid, visibility, created_at, updated_at) values (?, ?, 'HIDDEN', ?, ?)",
                            id, "uid-$id", ts(now), ts(now),
                        )
                        return id
                    }
                    val (u1, u2, u3, u4) = List(4) { user() }

                    // 1. Notificaciones: 90 días.
                    val c90 = now.minusSeconds(90 * day)
                    c.exec("insert into notifications (user_id, kind, created_at) values (?, 'KEEP', ?)", u1, kept(c90))
                    c.exec("insert into notifications (user_id, kind, created_at) values (?, 'DROP', ?)", u1, due(c90))

                    // 2. ai_usage: user_id a null a los 90 días; el agregado diario se conserva.
                    c.exec("insert into ai_usage (day, purpose, provider, model, user_id, created_at) values ('2026-01-01', 'KEEP', 'p', 'm', ?, ?)", u1, kept(c90))
                    c.exec("insert into ai_usage (day, purpose, provider, model, user_id, created_at) values ('2026-01-01', 'DROP', 'p', 'm', ?, ?)", u1, due(c90))
                    c.exec("insert into ai_usage_daily (day, provider, model, tokens_in, tokens_out, cost_micros) values ('2026-01-01', 'p', 'm', 10, 20, 30)")

                    // 3. Snapshot de reportes: 180 días tras resolverse; uno abierto antiguo no se toca.
                    val c180 = now.minusSeconds(180 * day)
                    fun report(label: String, status: String, resolvedAt: Timestamp?): UUID {
                        val id = UUID.randomUUID()
                        c.exec(
                            "insert into reports (id, reporter_id, target_id, reason, details, snapshot, status, created_at, resolved_at) " +
                                "values (?, ?, ?, 'OTHER', ?, '[{\"text\":\"hola\"}]'::jsonb, ?, ?, ?::timestamptz)",
                            id, u1, u2, label, status, ts(now.minusSeconds(400 * day)), resolvedAt,
                        )
                        return id
                    }
                    val reportKept = report("KEEP", "RESOLVED", kept(c180))
                    val reportDue = report("DROP", "RESOLVED", due(c180))
                    val reportOpen = report("OPEN", "OPEN", null)

                    // 4. Transcript: 12 meses.
                    val c12 = RetentionPolicy.transcriptCutoff(now, BogotaZone)
                    fun sim(createdAt: Timestamp): UUID {
                        val id = UUID.randomUUID()
                        c.exec(
                            "insert into simulations (id, mode, user_a, user_b, origin, status, score, transcript, report, created_at) " +
                                "values (?, 'AMISTAD', ?, ?, 'PARCHE', 'DONE', 70, '{\"scenarios\":[]}'::jsonb, '{\"summary\":\"x\"}'::jsonb, ?)",
                            id, u1, u2, createdAt,
                        )
                        return id
                    }
                    val simKept = sim(kept(c12))
                    val simDue = sim(due(c12))

                    // 5. match_queue (una fila por persona) y matches: 7 días.
                    val c7 = now.minusSeconds(7 * day)
                    fun queue(user: UUID, expiresAt: Timestamp) = c.exec(
                        "insert into match_queue (user_id, mode, enqueued_at, expires_at, status) values (?, 'AMISTAD', ?, ?, 'EXPIRED')",
                        user, ts(now.minusSeconds(20 * day)), expiresAt,
                    )
                    queue(u1, kept(c7))
                    queue(u2, due(c7))
                    fun match(status: String, decideBy: Timestamp, chat: UUID? = null): UUID {
                        val id = UUID.randomUUID()
                        c.exec(
                            "insert into matches (id, mode, user_a, user_b, status, chat_id, created_at, decide_by) values (?, 'AMISTAD', ?, ?, ?, ?::uuid, ?, ?)",
                            id, u3, u4, status, chat, ts(now.minusSeconds(30 * day)), decideBy,
                        )
                        return id
                    }
                    val matchKept = match("EXPIRED", kept(c7))
                    val matchDue = match("EXPIRED", due(c7))
                    val matchSettled = match("SIMULATION", ts(now.minusSeconds(60 * day)))

                    // 6. Chats SCENARIO: 30 días tras cerrarse (`ends_at`).
                    val c30 = now.minusSeconds(30 * day)
                    fun chat(type: String, endsAt: Timestamp?, status: String = "CLOSED"): UUID {
                        val id = UUID.randomUUID()
                        c.exec(
                            "insert into chats (id, type, mode, scenario_id, ends_at, status, created_at) values (?, ?, 'AMISTAD', ?::smallint, ?::timestamptz, ?, ?)",
                            id, type, if (type == "SCENARIO") 1 else null, endsAt, status, ts(now.minusSeconds(100 * day)),
                        )
                        c.exec("insert into chat_members (chat_id, user_id) values (?, ?)", id, u1)
                        c.exec("insert into chat_messages (chat_id, sender_id, text, learnable, created_at) values (?, ?, 'hola', false, ?)", id, u1, ts(now.minusSeconds(100 * day)))
                        return id
                    }
                    val chatKept = chat("SCENARIO", kept(c30))
                    val chatDue = chat("SCENARIO", due(c30))
                    val chatDueOpen = chat("SCENARIO", due(c30), status = "OPEN")
                    val chatDirect = chat("DIRECT", null, status = "OPEN")
                    // Un match apunta al chat vencido: la referencia se suelta, el match se conserva.
                    val matchWithChat = match("SCENARIO", ts(now.plusSeconds(day)), chatDue)
                    c.exec("update reports set chat_id = ? where id = ?", chatDue, reportKept)

                    val mirror = RecordingRetentionMirror()
                    val stalled = intArrayOf(2)
                    val service = RetentionService(ExposedRetentionRepository(db), mirror, { stalled[0] }, FIXED_CLOCK)

                    val first = runBlocking { service.run() }

                    withClue("notificaciones") { c.ids("select id from notifications").size shouldBe 1 }
                    withClue("notificación conservada") { c.count("select count(*) from notifications where kind = 'KEEP'") shouldBe 1 }
                    withClue("ai_usage anonimizado") {
                        c.count("select count(*) from ai_usage where purpose = 'KEEP' and user_id = ?", u1) shouldBe 1
                        c.count("select count(*) from ai_usage where purpose = 'DROP' and user_id is null") shouldBe 1
                    }
                    withClue("agregados diarios") { c.count("select count(*) from ai_usage_daily where cost_micros = 30") shouldBe 1 }
                    withClue("snapshots") {
                        c.count("select count(*) from reports where id = ? and snapshot is not null", reportKept) shouldBe 1
                        c.count("select count(*) from reports where id = ? and snapshot is null", reportDue) shouldBe 1
                        c.count("select count(*) from reports where id = ? and snapshot is not null", reportOpen) shouldBe 1
                        // El reporte se conserva (sin snapshot): solo se anonimiza.
                        c.count("select count(*) from reports where id = ?", reportDue) shouldBe 1
                    }
                    withClue("transcripts") {
                        c.count("select count(*) from simulations where id = ? and transcript is not null", simKept) shouldBe 1
                        c.count("select count(*) from simulations where id = ? and transcript is null and report is not null", simDue) shouldBe 1
                    }
                    withClue("match_queue") {
                        c.count("select count(*) from match_queue where user_id = ?", u1) shouldBe 1
                        c.count("select count(*) from match_queue where user_id = ?", u2) shouldBe 0
                    }
                    withClue("matches") {
                        c.count("select count(*) from matches where id = ?", matchKept) shouldBe 1
                        c.count("select count(*) from matches where id = ?", matchDue) shouldBe 0
                        c.count("select count(*) from matches where id = ?", matchSettled) shouldBe 1
                    }
                    withClue("chats SCENARIO") {
                        c.ids("select id from chats").toSet() shouldBe setOf(chatKept, chatDirect)
                        c.count("select count(*) from chat_messages where chat_id in (?, ?)", chatDue, chatDueOpen) shouldBe 0
                        c.count("select count(*) from chat_messages where chat_id = ?", chatKept) shouldBe 1
                        c.count("select count(*) from matches where id = ? and chat_id is null", matchWithChat) shouldBe 1
                        c.count("select count(*) from reports where id = ? and chat_id is null", reportKept) shouldBe 1
                        mirror.deletedChats.toSet() shouldBe setOf(chatDue, chatDueOpen)
                    }
                    withClue("reporte") {
                        first.purged[RetentionRule.NOTIFICATIONS] shouldBe 1
                        first.purged[RetentionRule.AI_USAGE] shouldBe 1
                        first.purged[RetentionRule.REPORT_SNAPSHOTS] shouldBe 1
                        first.purged[RetentionRule.TRANSCRIPTS] shouldBe 1
                        first.purged[RetentionRule.MATCH_QUEUE] shouldBe 1
                        first.purged[RetentionRule.MATCHES] shouldBe 1
                        first.purged[RetentionRule.SCENARIO_CHATS] shouldBe 2
                        first.purged[RetentionRule.STALLED_ACCOUNTS] shouldBe 2
                    }

                    // Segunda ejecución: nada cambia (y no queda nada por contar salvo lo que reencola el barrido de cuentas).
                    stalled[0] = 0
                    val before = c.ids("select id from chats").toSet()
                    val second = runBlocking { service.run() }
                    second.total shouldBe 0
                    c.ids("select id from chats").toSet() shouldBe before
                    c.count("select count(*) from notifications") shouldBe 1
                    c.count("select count(*) from matches") shouldBe 3
                    c.count("select count(*) from simulations where transcript is not null") shouldBe 1
                }
            }
        }
})
