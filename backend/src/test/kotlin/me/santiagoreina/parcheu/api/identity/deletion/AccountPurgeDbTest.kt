package me.santiagoreina.parcheu.api.identity.deletion

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.runBlocking
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.chat.FirestoreEmulator
import me.santiagoreina.parcheu.api.identity.ExposedIdentityRepository
import me.santiagoreina.parcheu.api.platform.auth.FirebaseUser
import me.santiagoreina.parcheu.api.platform.db.TestDatabase
import me.santiagoreina.parcheu.api.platform.db.connectExposed
import me.santiagoreina.parcheu.api.platform.db.runMigrations
import me.santiagoreina.parcheu.api.platform.db.tables.PersonalJsonPaths
import me.santiagoreina.parcheu.api.platform.tasks.FakeTaskQueue
import java.sql.Connection
import java.sql.Timestamp
import java.util.UUID
import javax.sql.DataSource

/**
 * Criterios de E8.3 contra Postgres 16 real (Docker en CI o `PARCHEU_TEST_DB_URL` en local) y, si corre
 * el emulador (`FIRESTORE_EMULATOR_HOST`, como en backend-ci), también contra Firestore.
 *
 * Se siembra una cuenta con datos en todas las tablas que la tocan, se borra con `DELETE /v1/me` (el
 * servicio) y se ejecuta `purge-user`. Las comprobaciones se generan desde el catálogo de Postgres, no
 * desde una lista escrita a mano: una tabla o columna nueva queda cubierta sin tocar esta prueba.
 */
class AccountPurgeDbTest : FunSpec({
    val canary = "canario-${UUID.randomUUID()}"
    val name = "Zoraida Canaria Quintero"

    class Seed(
        val zoraida: UUID,
        val zoraidaUid: String,
        val bruno: UUID,
        val brunoUid: String,
        val carla: UUID,
        val shared: UUID,
        val unrelated: UUID,
        val chat: UUID,
        val zoraidaMessage: UUID,
        val brunoMessage: UUID,
        val reportAgainstZoraida: UUID,
        val reportUnrelated: UUID,
        val reportAiContent: UUID,
    )

    fun Connection.exec(sql: String, vararg params: Any?) {
        prepareStatement(sql).use { st ->
            params.forEachIndexed { i, v -> if (v == null) st.setNull(i + 1, java.sql.Types.VARCHAR) else st.setObject(i + 1, v) }
            st.execute()
        }
    }

    fun <T> Connection.one(sql: String, vararg params: Any?, read: (java.sql.ResultSet) -> T): T =
        prepareStatement(sql).use { st ->
            params.forEachIndexed { i, v -> st.setObject(i + 1, v) }
            st.executeQuery().use { rs ->
                check(rs.next()) { "Sin filas: $sql" }
                read(rs)
            }
        }

    fun Connection.count(sql: String, vararg params: Any?): Int = one(sql, *params) { it.getInt(1) }

    fun seed(c: Connection): Seed {
        val now = Timestamp.from(FIXED_CLOCK.instant())
        fun user(label: String, displayName: String): Pair<UUID, String> {
            val id = UUID.randomUUID()
            val uid = "uid-$label-${UUID.randomUUID()}"
            c.exec(
                "insert into users (id, firebase_uid, email, display_name, photo_url, career, university_id, interests, birth_date, " +
                    "age_verified_at, created_at, updated_at) values (?, ?, ?, ?, ?, 'Sistemas', 1, '{musica}', '2000-01-01', ?, ?, ?)",
                id, uid, "$label@example.com", displayName, "https://photos/$label", now, now, now,
            )
            return id to uid
        }
        val (z, zUid) = user("zoraida", name)
        val (b, bUid) = user("bruno", "Bruno Díaz")
        val (cId, _) = user("carla", "Carla Ruiz")

        c.exec("insert into user_modes (user_id, mode, enabled, enabled_at) values (?, 'AMISTAD', true, ?)", z, now)
        listOf("TERMS", "PRIVACY", "CHAT_LEARNING_AMISTAD").forEach { kind ->
            c.exec("insert into consents (user_id, kind, granted, text_version, created_at) values (?, ?, true, '2026-10-29', ?)", z, kind, now)
        }
        c.exec("insert into consents (user_id, kind, granted, text_version, created_at) values (?, 'TERMS', true, '2026-10-29', ?)", b, now)
        c.exec("insert into fcm_tokens (token, user_id, updated_at) values (?, ?, ?)", "tok-$z", z, now)
        c.exec("insert into twin_answers (user_id, question_id, answer, answered_at) values (?, 1, 'A', ?)", z, now)
        c.exec("insert into twins (user_id, traits, persona_summary, persona_ready, updated_at) values (?, '{0.1,0.2,0.3,0.4,0.5,0.6}', ?, true, ?)", z, "Persona de $name", now)
        c.exec("insert into twin_versions (user_id, version, persona_summary, created_at) values (?, 1, ?, ?)", z, "Persona de $name", now)
        c.exec("insert into twin_memories (id, user_id, mode, text, status, created_at) values (?, ?, 'AMISTAD', ?, 'ACCEPTED', ?)", UUID.randomUUID(), z, "Le gusta $canary", now)
        c.exec("insert into twin_learning_cursor (user_id, last_message_at) values (?, ?)", z, now)

        val shared = UUID.randomUUID()
        val unrelated = UUID.randomUUID()
        c.exec(
            "insert into simulations (id, mode, user_a, user_b, requested_by, origin, status, score, transcript, report, created_at, finished_at) " +
                "values (?, 'AMISTAD', ?, ?, ?, 'PARCHE', 'DONE', 77, ?::jsonb, ?::jsonb, ?, ?)",
            shared, z, b, z,
            """{"scenarios":[{"title":"Plan","turns":[{"speaker":"$z","text":"Soy $name y digo $canary"}]}]}""",
            """{"summary":"$name y Bruno conectan","reasons":["$canary"]}""",
            now, now,
        )
        c.exec(
            "insert into simulations (id, mode, user_a, user_b, origin, status, score, transcript, created_at) " +
                "values (?, 'AMISTAD', ?, ?, 'PARCHE', 'DONE', 50, '{\"scenarios\":[]}'::jsonb, ?)",
            unrelated, b, cId, now,
        )
        c.exec("insert into report_unlocks (user_id, simulation_id, created_at) values (?, ?, ?)", b, shared, now)
        listOf(z, b).forEach { c.exec("insert into connection_decisions (simulation_id, user_id, connect, decided_at) values (?, ?, true, ?)", shared, it, now) }
        c.exec(
            "insert into edges (user_low, user_high, mode, state, affinity, last_simulation_id, created_at, updated_at, connected_at) " +
                "values (least(?::uuid, ?::uuid), greatest(?::uuid, ?::uuid), 'AMISTAD', 'CONNECTED', 77, ?, ?, ?, ?)",
            z, b, z, b, shared, now, now, now,
        )

        val chat = UUID.randomUUID()
        c.exec("insert into chats (id, type, mode, created_at, last_message_at) values (?, 'DIRECT', 'AMISTAD', ?, ?)", chat, now, now)
        listOf(z, b).forEach { c.exec("insert into chat_members (chat_id, user_id, last_read_at) values (?, ?, ?)", chat, it, now) }
        val zMsg = UUID.randomUUID()
        val bMsg = UUID.randomUUID()
        c.exec("insert into chat_messages (id, chat_id, sender_id, text, learnable, client_id, created_at) values (?, ?, ?, ?, true, 'c-z', ?)", zMsg, chat, z, "Hola, $canary", now)
        c.exec("insert into chat_messages (id, chat_id, sender_id, text, learnable, client_id, created_at) values (?, ?, ?, 'Hola desde Bruno', true, 'c-b', ?)", bMsg, chat, b, now)

        val match = UUID.randomUUID()
        c.exec(
            "insert into matches (id, mode, user_a, user_b, status, simulation_id, chat_id, created_at, decide_by) " +
                "values (?, 'AMISTAD', ?, ?, 'SIMULATION', ?, ?, ?, ?)",
            match, z, b, shared, chat, now, now,
        )
        c.exec("insert into match_queue (user_id, mode, enqueued_at, expires_at, status, match_id) values (?, 'AMISTAD', ?, ?, 'MATCHED', ?)", z, now, now, match)

        // Avisos de Bruno que citan a Zoraida (actor, nombre o un mensaje suyo) y uno que no tiene nada que ver.
        c.exec(
            "insert into notifications (id, user_id, kind, payload, created_at) values (?, ?, 'CHAT_MESSAGE', ?::jsonb, ?)",
            UUID.randomUUID(), b, """{"title":"Alguien","body":"Hola, $canary","route":"parcheu://chat/$chat"}""", now,
        )
        c.exec(
            "insert into notifications (id, user_id, kind, payload, created_at) values (?, ?, 'NEW_CONNECTION', ?::jsonb, ?)",
            UUID.randomUUID(), b, """{"title":"Nueva conexión","body":"Ya estás conectado","actorId":"$z"}""", now,
        )
        c.exec(
            "insert into notifications (id, user_id, kind, payload, created_at) values (?, ?, 'CONNECT_REQUEST', ?::jsonb, ?)",
            UUID.randomUUID(), b, """{"title":"$name","body":"quiere conectar"}""", now,
        )
        c.exec(
            "insert into notifications (id, user_id, kind, payload, created_at) values (?, ?, 'SIMULATION_READY', ?::jsonb, ?)",
            UUID.randomUUID(), b, """{"title":"Tu simulación está lista","body":"Mira qué tal te fue"}""", now,
        )
        c.exec(
            "insert into notifications (id, user_id, kind, payload, created_at) values (?, ?, 'ACCOUNT', ?::jsonb, ?)",
            UUID.randomUUID(), z, """{"title":"Hola $name","body":"Bienvenida"}""", now,
        )
        c.exec("insert into usage_events (id, user_id, kind, ref_id, created_at) values (?, ?, 'SIMULATION', ?, ?)", UUID.randomUUID(), z, shared.toString(), now)
        c.exec("insert into blocks (blocker_id, blocked_id, created_at) values (?, ?, ?)", z, cId, now)

        val reportAgainst = UUID.randomUUID()
        c.exec(
            "insert into reports (id, reporter_id, target_id, chat_id, kind, reason, snapshot, status, created_at) " +
                "values (?, ?, ?, ?, 'USER', 'HARASSMENT', ?::jsonb, 'OPEN', ?)",
            reportAgainst, b, z, chat, """[{"id":"$zMsg","senderId":"$z","text":"Hola, $canary","hidden":false}]""", now,
        )
        c.exec(
            "insert into reports (id, reporter_id, target_id, kind, reason, snapshot, status, resolution, resolved_at, created_at) " +
                "values (?, ?, ?, 'USER', 'OTHER', '[]'::jsonb, 'RESOLVED', 'DISMISS', ?, ?)",
            UUID.randomUUID(), z, cId, now, now,
        )
        val reportUnrelated = UUID.randomUUID()
        c.exec(
            "insert into reports (id, reporter_id, target_id, kind, reason, snapshot, status, created_at) " +
                "values (?, ?, ?, 'USER', 'OTHER', '[{\"text\":\"de Carla\"}]'::jsonb, 'OPEN', ?)",
            reportUnrelated, cId, b, now,
        )
        // AI_CONTENT de Bruno sobre la simulación que comparte con Zoraida: sin target, reporter ajeno,
        // pero el texto copiado sale de las memorias de ella (E8.7).
        val reportAiContent = UUID.randomUUID()
        c.exec(
            "insert into reports (id, reporter_id, kind, simulation_id, turn_index, reason, snapshot, status, created_at) " +
                "values (?, ?, 'AI_CONTENT', ?, -1, 'AI_HARMFUL', '[{\"text\":\"informe copiado\"}]'::jsonb, 'OPEN', ?)",
            reportAiContent, b, shared, now,
        )

        val token = "dev-grant:$z"
        c.exec(
            "insert into entitlements (user_id, product_id, purchase_token, state, expires_at, auto_renewing, updated_at) " +
                "values (?, 'dev-grant', ?, 'ACTIVE', ?, false, ?)",
            z, token, now, now,
        )
        c.exec(
            "insert into purchase_events (message_id, purchase_token, payload, received_at) values ('m-1', ?, ?::jsonb, ?)",
            token,
            """{"eventTimeMillis":"1","subscriptionNotification":{"notificationType":4,"purchaseToken":"$token"},"obfuscatedExternalAccountId":"${sha256Hex(z.toString())}"}""",
            now,
        )
        // Un token histórico (la fila de entitlements ya pasó a otra compra): su evento ligado a la cuenta y otro
        // posterior que llegó sin dueño conocido. Un tercero con otro token conserva el suyo (E7.1, B4).
        c.exec("update purchase_events set user_id = ? where message_id = 'm-1'", z)
        c.exec(
            "insert into purchase_events (message_id, purchase_token, payload, received_at, user_id) values ('m-2', 'tok-historico', ?::jsonb, ?, ?)",
            """{"eventTimeMillis":"2","subscriptionNotification":{"notificationType":12,"purchaseToken":"tok-historico"}}""", now, z,
        )
        c.exec(
            "insert into purchase_events (message_id, purchase_token, payload, received_at) values ('m-3', 'tok-historico', ?::jsonb, ?)",
            """{"eventTimeMillis":"3","subscriptionNotification":{"notificationType":13,"purchaseToken":"tok-historico"}}""", now,
        )
        c.exec(
            "insert into purchase_events (message_id, purchase_token, payload, received_at) values ('m-ajeno', 'tok-de-otro', ?::jsonb, ?)",
            """{"eventTimeMillis":"4","subscriptionNotification":{"notificationType":4,"purchaseToken":"tok-de-otro"}}""", now,
        )
        c.exec("insert into ai_usage (day, purpose, provider, model, user_id, created_at) values ('2026-10-25', 'simulate', 'fake', 'fake', ?, ?)", z, now)
        c.exec("insert into invites (code, user_id) values ('ZORA-0001', ?)", z)
        c.exec("insert into introductions (id, introducer_id, mode, user_a, user_b) values (?, ?, 'AMISTAD', ?, ?)", UUID.randomUUID(), b, z, cId)
        c.exec("insert into intro_requests (id, requester_id, via_user_id, target_user_id) values (?, ?, ?, ?)", UUID.randomUUID(), z, b, cId)
        val community = UUID.randomUUID()
        c.exec("insert into communities (id, week, name, stable_key, created_at) values (?, '2026-10-19', 'Café', 'k1', ?)", community, now)
        c.exec("insert into community_members (community_id, user_id) values (?, ?)", community, z)

        return Seed(z, zUid, b, bUid, cId, shared, unrelated, chat, zMsg, bMsg, reportAgainst, reportUnrelated, reportAiContent)
    }

    /** (tabla, columna) de todas las tablas base del esquema público, salvo el historial de Flyway. */
    fun catalogColumns(c: Connection, dataType: String? = null): List<Pair<String, String>> {
        val sql = "select c.table_name, c.column_name from information_schema.columns c " +
            "join information_schema.tables t on t.table_schema = c.table_schema and t.table_name = c.table_name " +
            "where c.table_schema = 'public' and t.table_type = 'BASE TABLE' and c.table_name <> 'flyway_schema_history'" +
            (if (dataType != null) " and c.data_type = ?" else "") + " order by 1, 2"
        return c.prepareStatement(sql).use { st ->
            if (dataType != null) st.setString(1, dataType)
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getString(1) to rs.getString(2)) } }
        }
    }

    fun Connection.rowsContaining(table: String, column: String, needle: String): Int =
        count("select count(*) from \"$table\" where \"$column\"::text like ?", "%$needle%")

    fun withDb(block: suspend (DataSource) -> Unit) = TestDatabase.withCleanDatabase { ds ->
        runMigrations(ds)
        runBlocking { block(ds) }
    }

    fun seedFirestore(s: Seed) {
        val fs = FirestoreEmulator.firestore
        fs.collection("simulations").document(s.shared.toString()).set(mapOf("participants" to listOf(s.zoraidaUid, s.brunoUid), "score" to 77)).get()
        fs.collection("simulations").document(s.unrelated.toString()).set(mapOf("participants" to listOf(s.brunoUid), "score" to 50)).get()
        fs.collection("matchTickets").document(s.zoraidaUid).set(mapOf("status" to "MATCHED")).get()
        fs.collection("matchTickets").document(s.brunoUid).set(mapOf("status" to "MATCHED")).get()
        val chat = fs.collection("chats").document(s.chat.toString())
        chat.set(mapOf("members" to listOf(s.zoraidaUid, s.brunoUid), "type" to "DIRECT")).get()
        chat.collection("messages").document(s.zoraidaMessage.toString())
            .set(mapOf("senderUid" to s.zoraidaUid, "system" to false, "text" to "Hola, $canary")).get()
        chat.collection("messages").document(s.brunoMessage.toString())
            .set(mapOf("senderUid" to s.brunoUid, "system" to false, "text" to "Hola desde Bruno")).get()
    }

    test("tras la purga el id no aparece en ninguna tabla y los jsonb registrados no contienen id, nombre ni canario")
        .config(enabled = TestDatabase.enabled) {
            withDb { ds ->
                val db = connectExposed(ds)
                val s = ds.connection.use { seed(it) }
                if (FirestoreEmulator.enabled) seedFirestore(s)
                val identity = ExposedIdentityRepository(db)
                val mirror = if (FirestoreEmulator.enabled) FirestoreAccountMirrorCleaner { FirestoreEmulator.firestore } else RecordingAccountMirrorCleaner()
                val auth = FakeAuthAccountRemover()
                val tasks = FakeTaskQueue()
                val service = AccountDeletionService(identity, ExposedAccountPurgeRepository(db), mirror, auth, tasks, FIXED_CLOCK)

                service.requestDeletion(FirebaseUser(s.zoraidaUid, null, null))
                tasks.tasks.single().name shouldBe "purge-user-${s.zoraida}"
                service.purge(s.zoraida) shouldBe PurgeOutcome.PURGED
                auth.removed shouldContainExactly listOf(s.zoraidaUid)
                if (mirror is RecordingAccountMirrorCleaner) {
                    val plan = mirror.plans.single()
                    plan.simulationIds shouldContainExactly listOf(s.shared)
                    plan.chats.map { it.chatId } shouldContainExactly listOf(s.chat)
                    plan.chats.single().ownMessageIds shouldContainExactly listOf(s.zoraidaMessage)
                }

                ds.connection.use { c ->
                    // 1. Ninguna columna de ninguna tabla (consulta generada desde el catálogo) contiene su id ni su uid.
                    val columns = catalogColumns(c)
                    columns.size shouldNotBe 0
                    columns.forEach { (table, column) ->
                        withClue("$table.$column") {
                            c.rowsContaining(table, column, s.zoraida.toString()) shouldBe 0
                            c.rowsContaining(table, column, s.zoraidaUid) shouldBe 0
                        }
                    }
                    // 2. Las columnas jsonb del registro PersonalJsonPaths no contienen id, nombre ni el mensaje canario.
                    PersonalJsonPaths.personal.forEach { col ->
                        listOf(s.zoraida.toString(), name, canary).forEach { needle ->
                            withClue("${col.table.tableName}.${col.name} contiene '$needle'") {
                                c.rowsContaining(col.table.tableName, col.name, needle) shouldBe 0
                            }
                        }
                    }

                    // Se conserva lo que exige la ley, en una lápida sin datos de contacto.
                    val tombstone = c.one("select id from users where firebase_uid = ?", tombstoneUid(s.zoraidaUid)) { it.getObject(1, UUID::class.java) }
                    c.one("select email, display_name, photo_url, firebase_uid, deleted_at from users where id = ?", tombstone) { rs ->
                        rs.getString(1) shouldBe null
                        rs.getString(2) shouldBe null
                        rs.getString(3) shouldBe null
                        rs.getString(4) shouldStartWith "deleted:"
                        rs.getTimestamp(5) shouldNotBe null
                    }
                    c.count("select count(*) from consents where user_id = ?", tombstone) shouldBe 3
                    c.count("select count(*) from consents where user_id = ?", s.bruno) shouldBe 1
                    c.one("select product_id, purchase_token from entitlements where user_id = ?", tombstone) { rs ->
                        rs.getString(1) shouldBe "dev-grant"
                        rs.getString(2) shouldBe "dev-grant:$tombstone"
                    }
                    c.one("select payload::text from purchase_events where message_id = 'm-1'") { it.getString(1) } shouldBe
                        """{"redacted": true, "eventTimeMillis": "1", "notificationType": 4}"""
                    // E7.1 (B4): se redactan los eventos de TODOS sus tokens (también el histórico y el que llegó sin dueño),
                    // sin token ni cuenta; el de un tercero no se toca.
                    c.one("select payload::text, purchase_token, user_id from purchase_events where message_id = 'm-2'") { rs ->
                        rs.getString(1) shouldBe """{"redacted": true, "eventTimeMillis": "2", "notificationType": 12}"""
                        rs.getString(2) shouldBe null
                        rs.getObject(3) shouldBe null
                    }
                    c.one("select payload::text, purchase_token from purchase_events where message_id = 'm-3'") { rs ->
                        rs.getString(1) shouldBe """{"redacted": true, "eventTimeMillis": "3", "notificationType": 13}"""
                        rs.getString(2) shouldBe null
                    }
                    c.count("select count(*) from purchase_events where purchase_token is not null and message_id <> 'm-ajeno'") shouldBe 0
                    c.one("select purchase_token, payload->>'redacted' from purchase_events where message_id = 'm-ajeno'") { rs ->
                        rs.getString(1) shouldBe "tok-de-otro"
                        rs.getString(2) shouldBe null
                    }

                    // Reportes: el abierto contra ella se resuelve y pierde la instantánea; el ajeno no se toca.
                    c.one("select status, resolution, snapshot, target_id, reporter_id from reports where id = ?", s.reportAgainstZoraida) { rs ->
                        rs.getString(1) shouldBe "RESOLVED"
                        rs.getString(2) shouldBe ExposedAccountPurgeRepository.RESOLUTION
                        rs.getString(3) shouldBe null
                        rs.getObject(4, UUID::class.java) shouldBe tombstone
                        rs.getObject(5, UUID::class.java) shouldBe s.bruno
                    }
                    c.one("select status, snapshot::text from reports where id = ?", s.reportUnrelated) { rs ->
                        rs.getString(1) shouldBe "OPEN"
                        rs.getString(2) shouldBe """[{"text": "de Carla"}]"""
                    }

                    c.one("select status, snapshot from reports where id = ?", s.reportAiContent) { rs ->
                        rs.getString(1) shouldBe "OPEN"
                        rs.getString(2) shouldBe null
                    }

                    // Avisos de Bruno: se borran los que la citan; el que no tiene nada que ver sigue.
                    c.count("select count(*) from notifications where user_id = ?", s.bruno) shouldBe 1
                    c.one("select kind from notifications where user_id = ?", s.bruno) { it.getString(1) } shouldBe "SIMULATION_READY"
                    c.count("select count(*) from users where id in (?, ?)", s.bruno, s.carla) shouldBe 2
                    c.count("select count(*) from report_unlocks where user_id = ?", s.bruno) shouldBe 1
                }

                // Reintento de Cloud Tasks: la cuenta ya no existe.
                service.purge(s.zoraida) shouldBe PurgeOutcome.NOTHING_TO_DO
                auth.removed.size shouldBe 1
            }
        }

    test("una simulación compartida queda para el otro con puntaje y fecha, sin transcript ni report; Firestore sin sus documentos")
        .config(enabled = TestDatabase.enabled) {
            withDb { ds ->
                val db = connectExposed(ds)
                val s = ds.connection.use { seed(it) }
                if (FirestoreEmulator.enabled) seedFirestore(s)
                val mirror = if (FirestoreEmulator.enabled) FirestoreAccountMirrorCleaner { FirestoreEmulator.firestore } else RecordingAccountMirrorCleaner()
                val service = AccountDeletionService(
                    ExposedIdentityRepository(db), ExposedAccountPurgeRepository(db), mirror, FakeAuthAccountRemover(), FakeTaskQueue(), FIXED_CLOCK,
                )
                service.requestDeletion(FirebaseUser(s.zoraidaUid, null, null))
                service.purge(s.zoraida)

                ds.connection.use { c ->
                    c.one(
                        "select score, transcript, report, created_at, user_a, user_b, requested_by from simulations where id = ?",
                        s.shared,
                    ) { rs ->
                        rs.getInt(1) shouldBe 77
                        rs.getString(2) shouldBe null
                        rs.getString(3) shouldBe null
                        rs.getTimestamp(4).toInstant() shouldBe FIXED_CLOCK.instant()
                        val a = rs.getObject(5, UUID::class.java)
                        val b = rs.getObject(6, UUID::class.java)
                        b shouldBe s.bruno
                        a shouldNotBe s.zoraida
                        rs.getObject(7, UUID::class.java) shouldBe a
                        // El otro participante es 'Usuario eliminado': una cuenta borrada sin nombre.
                        c.one("select display_name, deleted_at from users where id = ?", a) { u ->
                            u.getString(1) shouldBe null
                            u.getTimestamp(2) shouldNotBe null
                        }
                    }
                    c.one("select transcript::text from simulations where id = ?", s.unrelated) { it.getString(1) } shouldBe """{"scenarios": []}"""
                }

                if (FirestoreEmulator.enabled) {
                    val fs = FirestoreEmulator.firestore
                    fs.collection("simulations").document(s.shared.toString()).get().get().exists() shouldBe false
                    fs.collection("matchTickets").document(s.zoraidaUid).get().get().exists() shouldBe false
                    fs.collection("simulations").document(s.unrelated.toString()).get().get().exists() shouldBe true
                    fs.collection("matchTickets").document(s.brunoUid).get().get().exists() shouldBe true
                } else {
                    (mirror as RecordingAccountMirrorCleaner).plans.single().simulationIds shouldContainExactly listOf(s.shared)
                }
            }
        }

    test("los mensajes de la persona en chats compartidos quedan anonimizados y los del otro intactos")
        .config(enabled = TestDatabase.enabled) {
            withDb { ds ->
                val db = connectExposed(ds)
                val s = ds.connection.use { seed(it) }
                if (FirestoreEmulator.enabled) seedFirestore(s)
                val mirror = if (FirestoreEmulator.enabled) FirestoreAccountMirrorCleaner { FirestoreEmulator.firestore } else RecordingAccountMirrorCleaner()
                val service = AccountDeletionService(
                    ExposedIdentityRepository(db), ExposedAccountPurgeRepository(db), mirror, FakeAuthAccountRemover(), FakeTaskQueue(), FIXED_CLOCK,
                )
                service.requestDeletion(FirebaseUser(s.zoraidaUid, null, null))
                service.purge(s.zoraida)

                ds.connection.use { c ->
                    c.one("select sender_id, client_id, learnable from chat_messages where id = ?", s.zoraidaMessage) { rs ->
                        rs.getObject(1) shouldBe null
                        rs.getString(2) shouldBe null
                        rs.getBoolean(3) shouldBe false
                    }
                    c.one("select sender_id, text, learnable, client_id from chat_messages where id = ?", s.brunoMessage) { rs ->
                        rs.getObject(1, UUID::class.java) shouldBe s.bruno
                        rs.getString(2) shouldBe "Hola desde Bruno"
                        rs.getBoolean(3) shouldBe true
                        rs.getString(4) shouldBe "c-b"
                    }
                    // El chat sigue para Bruno, que ahora es su único miembro.
                    c.count("select count(*) from chat_members where chat_id = ?", s.chat) shouldBe 1
                    c.count("select count(*) from chat_members where chat_id = ? and user_id = ?", s.chat, s.bruno) shouldBe 1
                }

                if (FirestoreEmulator.enabled) {
                    val chat = FirestoreEmulator.firestore.collection("chats").document(s.chat.toString())
                    @Suppress("UNCHECKED_CAST")
                    (chat.get().get().get("members") as List<String>) shouldContainExactly listOf(s.brunoUid)
                    val own = chat.collection("messages").document(s.zoraidaMessage.toString()).get().get()
                    own.exists() shouldBe true
                    own.getString("senderUid") shouldBe null
                    val other = chat.collection("messages").document(s.brunoMessage.toString()).get().get()
                    other.getString("senderUid") shouldBe s.brunoUid
                    other.getString("text") shouldBe "Hola desde Bruno"
                } else {
                    (mirror as RecordingAccountMirrorCleaner).plans.single().chats.single().chatId shouldBe s.chat
                }
            }
        }

    test("una cuenta is_test se purga sin lápida ni restos: sin consentimientos, simulaciones, chats ni mensajes")
        .config(enabled = TestDatabase.enabled) {
            withDb { ds ->
                val db = connectExposed(ds)
                val s = ds.connection.use { c ->
                    seed(c).also {
                        c.exec("update users set is_test = true where id in (?, ?)", it.zoraida, it.bruno)
                    }
                }
                if (FirestoreEmulator.enabled) seedFirestore(s)
                val mirror = if (FirestoreEmulator.enabled) FirestoreAccountMirrorCleaner { FirestoreEmulator.firestore } else RecordingAccountMirrorCleaner()
                val service = AccountDeletionService(
                    ExposedIdentityRepository(db), ExposedAccountPurgeRepository(db), mirror, FakeAuthAccountRemover(), FakeTaskQueue(), FIXED_CLOCK,
                )
                listOf(s.zoraida to s.zoraidaUid, s.bruno to s.brunoUid).forEach { (id, uid) ->
                    service.requestDeletion(FirebaseUser(uid, null, null))
                    service.purge(id)
                }

                ds.connection.use { c ->
                    c.count("select count(*) from users where id in (?, ?) or firebase_uid like 'deleted:%'", s.zoraida, s.bruno) shouldBe 0
                    c.count("select count(*) from simulations") shouldBe 0
                    c.count("select count(*) from report_unlocks where simulation_id = ?", s.shared) shouldBe 0
                    c.count("select count(*) from connection_decisions where simulation_id = ?", s.shared) shouldBe 0
                    c.count("select count(*) from chat_members where chat_id = ?", s.chat) shouldBe 0
                    // Los consentimientos no pasan a ninguna lápida.
                    c.count("select count(*) from consents") shouldBe 0
                    c.count("select count(*) from chats where id = ?", s.chat) shouldBe 0
                    c.count("select count(*) from chat_messages where chat_id = ?", s.chat) shouldBe 0
                }

                if (FirestoreEmulator.enabled) {
                    val chat = FirestoreEmulator.firestore.collection("chats").document(s.chat.toString())
                    chat.get().get().exists() shouldBe false
                    chat.collection("messages").get().get().isEmpty shouldBe true
                }
            }
        }

    test("arquitectura: toda columna jsonb del esquema real está clasificada en PersonalJsonPaths")
        .config(enabled = TestDatabase.enabled) {
            withDb { ds ->
                val catalog = ds.connection.use { c -> catalogColumns(c, "jsonb") }.map { (t, col) -> "$t.$col" }
                val classified = (PersonalJsonPaths.personal + PersonalJsonPaths.nonPersonal).map { "${it.table.tableName}.${it.name}" }
                catalog shouldContainExactlyInAnyOrder classified
            }
        }

    test("al borrar a quien presenta (E6.2) su simulación INTRODUCTION entra al plan y se borra su documento")
        .config(enabled = TestDatabase.enabled) {
            withDb { ds ->
                val db = connectExposed(ds)
                val s = ds.connection.use { seed(it) }
                val intro = UUID.randomUUID()
                val other = UUID.randomUUID()
                ds.connection.use { c ->
                    val now = Timestamp.from(FIXED_CLOCK.instant())
                    c.exec(
                        "insert into users (id, firebase_uid, display_name, created_at, updated_at) values (?, ?, 'Vale', ?, ?)",
                        other, "uid-vale-${UUID.randomUUID()}", now, now,
                    )
                    // Zoraida presenta a Bruno y a Vale: no es user_a ni user_b, solo requested_by.
                    c.exec(
                        "insert into simulations (id, mode, user_a, user_b, requested_by, origin, status, score, created_at, finished_at) " +
                            "values (?, 'AMISTAD', ?, ?, ?, 'INTRODUCTION', 'DONE', 80, ?, ?)",
                        intro, s.bruno, other, s.zoraida, now, now,
                    )
                }
                val mirror = RecordingAccountMirrorCleaner()
                val service = AccountDeletionService(
                    ExposedIdentityRepository(db), ExposedAccountPurgeRepository(db), mirror, FakeAuthAccountRemover(), FakeTaskQueue(), FIXED_CLOCK,
                )
                service.requestDeletion(FirebaseUser(s.zoraidaUid, null, null))
                service.purge(s.zoraida) shouldBe PurgeOutcome.PURGED

                mirror.plans.single().simulationIds.toSet() shouldBe setOf(s.shared, intro)
                mirror.removedSimulations.single().toSet() shouldBe setOf(s.shared, intro)
                ds.connection.use { c ->
                    // La fila sobrevive para las personas presentadas, pero ya sin el uuid de quien presentó.
                    c.count("select count(*) from simulations where requested_by = ?", s.zoraida) shouldBe 0
                }
            }
        }

    test("el aviso INTRODUCTION_REQUEST que cita a la persona candidata se borra al purgarla (E6.2), sin tocar otros")
        .config(enabled = TestDatabase.enabled) {
            withDb { ds ->
                val db = connectExposed(ds)
                val s = ds.connection.use { seed(it) }
                val now = Timestamp.from(FIXED_CLOCK.instant())
                val carla = UUID.randomUUID()
                val diego = UUID.randomUUID()
                val vale = UUID.randomUUID()
                val noticeAboutZoraida = UUID.randomUUID()
                val noticeAboutVale = UUID.randomUUID()
                ds.connection.use { c ->
                    listOf(carla to "Carla Ruiz", diego to "Diego", vale to "Vale").forEach { (id, n) ->
                        c.exec("insert into users (id, firebase_uid, display_name, created_at, updated_at) values (?, ?, ?, ?, ?)", id, "uid-$id", n, now, now)
                    }
                    // Carla (Plus) le pide a Bruno, amigo en común, que le presente a Zoraida: el aviso lleva su nombre.
                    c.exec(
                        "insert into intro_requests (id, requester_id, via_user_id, target_user_id, status, created_at) values (?, ?, ?, ?, 'PENDING', ?)",
                        UUID.randomUUID(), carla, s.bruno, s.zoraida, now,
                    )
                    c.exec(
                        "insert into notifications (id, user_id, kind, payload, created_at) values (?, ?, 'INTRODUCTION_REQUEST', ?::jsonb, ?)",
                        noticeAboutZoraida, s.bruno,
                        """{"title":"Te piden una presentación","body":"Carla Ruiz quiere conocer a $name. ¿Los presentas?","route":"parcheu://introduce","actorId":"$carla"}""", now,
                    )
                    // Control: otra solicitud de Diego, por Vale, no tiene relación con Zoraida.
                    c.exec(
                        "insert into intro_requests (id, requester_id, via_user_id, target_user_id, status, created_at) values (?, ?, ?, ?, 'PENDING', ?)",
                        UUID.randomUUID(), diego, s.bruno, vale, now,
                    )
                    c.exec(
                        "insert into notifications (id, user_id, kind, payload, created_at) values (?, ?, 'INTRODUCTION_REQUEST', ?::jsonb, ?)",
                        noticeAboutVale, s.bruno,
                        """{"title":"Te piden una presentación","body":"Diego quiere conocer a Vale. ¿Los presentas?","route":"parcheu://introduce","actorId":"$diego"}""", now,
                    )
                }
                val service = AccountDeletionService(
                    ExposedIdentityRepository(db), ExposedAccountPurgeRepository(db), RecordingAccountMirrorCleaner(), FakeAuthAccountRemover(), FakeTaskQueue(), FIXED_CLOCK,
                )
                service.requestDeletion(FirebaseUser(s.zoraidaUid, null, null))
                service.purge(s.zoraida) shouldBe PurgeOutcome.PURGED

                ds.connection.use { c ->
                    c.count("select count(*) from notifications where id = ?", noticeAboutZoraida) shouldBe 0
                    c.count("select count(*) from notifications where payload::text like ?", "%$name%") shouldBe 0
                    c.count("select count(*) from notifications where id = ?", noticeAboutVale) shouldBe 1
                }
            }
        }

    test("una simulación en curso queda FAILED con el cupo devuelto y el motor ya no puede guardar turnos en ella")
        .config(enabled = TestDatabase.enabled) {
            withDb { ds ->
                val db = connectExposed(ds)
                val s = ds.connection.use { seed(it) }
                val running = UUID.randomUUID()
                val queued = UUID.randomUUID()
                ds.connection.use { c ->
                    val now = Timestamp.from(FIXED_CLOCK.instant())
                    // Bruno pidió una simulación con Zoraida (va en el escenario 1, turno 2) y otra sigue en cola.
                    c.exec(
                        "insert into simulations (id, mode, user_a, user_b, requested_by, origin, status, transcript, created_at) " +
                            "values (?, 'AMISTAD', ?, ?, ?, 'PARCHE', 'RUNNING', ?::jsonb, ?)",
                        running, s.bruno, s.zoraida, s.bruno,
                        """{"scenarios":[{"scenarioId":1,"title":"Plan","turns":[{"speaker":"A","text":"Hola"},{"speaker":"B","text":"Soy $name"}]}]}""", now,
                    )
                    c.exec(
                        "insert into simulations (id, mode, user_a, user_b, requested_by, origin, status, created_at) " +
                            "values (?, 'AMISTAD', ?, ?, ?, 'PARCHE', 'QUEUED', ?)",
                        queued, s.zoraida, s.bruno, s.zoraida, now,
                    )
                    listOf(running to s.bruno, queued to s.zoraida).forEach { (sim, payer) ->
                        c.exec("insert into usage_events (id, user_id, kind, ref_id, created_at) values (?, ?, 'SIMULATION', ?, ?)", UUID.randomUUID(), payer, sim.toString(), now)
                    }
                }
                val mirror = RecordingAccountMirrorCleaner()
                val service = AccountDeletionService(
                    ExposedIdentityRepository(db), ExposedAccountPurgeRepository(db), mirror, FakeAuthAccountRemover(), FakeTaskQueue(), FIXED_CLOCK,
                )
                service.requestDeletion(FirebaseUser(s.zoraidaUid, null, null))
                service.purge(s.zoraida) shouldBe PurgeOutcome.PURGED

                ds.connection.use { c ->
                    listOf(running, queued).forEach { sim ->
                        c.one("select status, error, finished_at, transcript from simulations where id = ?", sim) { rs ->
                            rs.getString(1) shouldBe "FAILED"
                            rs.getString(2) shouldBe ExposedAccountPurgeRepository.SIMULATION_ERROR
                            rs.getTimestamp(3).toInstant() shouldBe FIXED_CLOCK.instant()
                            rs.getString(4) shouldBe null
                        }
                    }
                    // A Bruno se le devuelve el cupo; el uso de Zoraida se fue con su cuenta (ON DELETE CASCADE).
                    c.one("select refunded from usage_events where ref_id = ? and user_id = ?", running.toString(), s.bruno) { it.getBoolean(1) } shouldBe true
                    c.count("select count(*) from usage_events where ref_id = ?", queued.toString()) shouldBe 0
                }
                // El motor, que seguía corriendo con el transcript de 2 turnos, ya no puede guardar el tercero.
                val sims = me.santiagoreina.parcheu.api.simulation.ExposedSimulationRepository(db)
                sims.saveStep(
                    running, 2, me.santiagoreina.parcheu.api.simulation.StoredTranscript(),
                    me.santiagoreina.parcheu.api.simulation.initialProgress(), me.santiagoreina.parcheu.api.simulation.UsageTotals(),
                ) shouldBe false
                // Y se borra `simulations/{id}` una segunda vez, después de Postgres.
                mirror.removedSimulations.single().toSet() shouldBe mirror.plans.single().simulationIds.toSet()
                mirror.plans.single().simulationIds.toSet() shouldBe setOf(s.shared, running, queued)
            }
        }

    test("el barrido reencola solo las cuentas borradas hace más de una hora que siguen sin purgar")
        .config(enabled = TestDatabase.enabled) {
            withDb { ds ->
                val db = connectExposed(ds)
                val s = ds.connection.use { seed(it) }
                val purge = ExposedAccountPurgeRepository(db)
                val identity = ExposedIdentityRepository(db)
                AccountDeletionService(identity, purge, RecordingAccountMirrorCleaner(), FakeAuthAccountRemover(), FakeTaskQueue(), FIXED_CLOCK)
                    .requestDeletion(FirebaseUser(s.zoraidaUid, null, null))

                fun sweepAt(offset: java.time.Duration): FakeTaskQueue {
                    val tasks = FakeTaskQueue()
                    val clock = java.time.Clock.offset(FIXED_CLOCK, offset)
                    runBlocking { AccountDeletionService(identity, purge, RecordingAccountMirrorCleaner(), FakeAuthAccountRemover(), tasks, clock).sweepStalled() }
                    return tasks
                }
                // A los 30 minutos la primera tarea aún puede estar reintentando: no se toca.
                sweepAt(java.time.Duration.ofMinutes(30)).tasks.shouldBeEmpty()
                val later = FIXED_CLOCK.instant().plus(java.time.Duration.ofHours(2))
                sweepAt(java.time.Duration.ofHours(2)).tasks.map { it.name } shouldContainExactly
                    listOf("purge-user-${s.zoraida}-sweep-${later.epochSecond / 3600}")

                // Tras la purga solo queda la lápida (también con deleted_at): el barrido no la toma.
                AccountDeletionService(identity, purge, RecordingAccountMirrorCleaner(), FakeAuthAccountRemover(), FakeTaskQueue(), FIXED_CLOCK)
                    .purge(s.zoraida) shouldBe PurgeOutcome.PURGED
                sweepAt(java.time.Duration.ofHours(2)).tasks.shouldBeEmpty()
            }
        }

    test("la purga no toca una cuenta que no está marcada como borrada").config(enabled = TestDatabase.enabled) {
        withDb { ds ->
            val db = connectExposed(ds)
            val s = ds.connection.use { seed(it) }
            val auth = FakeAuthAccountRemover()
            val service = AccountDeletionService(
                ExposedIdentityRepository(db), ExposedAccountPurgeRepository(db), RecordingAccountMirrorCleaner(), auth, FakeTaskQueue(), FIXED_CLOCK,
            )
            service.purge(s.zoraida) shouldBe PurgeOutcome.NOTHING_TO_DO
            auth.removed shouldBe emptyList()
            ds.connection.use { c -> c.count("select count(*) from users where id = ?", s.zoraida) shouldBe 1 }
        }
    }

    test("el nombre visible solo borra avisos de sus contrapartes: un tercero con un amigo homónimo conserva los suyos")
        .config(enabled = TestDatabase.enabled) {
            withDb { ds ->
                val db = connectExposed(ds)
                val s = ds.connection.use { seed(it) }
                val now = Timestamp.from(FIXED_CLOCK.instant())
                val diego = UUID.randomUUID()
                val diegoChat = UUID.randomUUID()
                val diegoNotice = UUID.randomUUID()
                val brunoNotice = UUID.randomUUID()
                ds.connection.use { c ->
                    // Diego no tiene relación con Zoraida, pero su amiga (otra persona) se llama igual.
                    c.exec("insert into users (id, firebase_uid, display_name, created_at, updated_at) values (?, ?, 'Diego', ?, ?)", diego, "uid-diego-$diego", now, now)
                    c.exec(
                        "insert into notifications (id, user_id, kind, payload, created_at) values (?, ?, 'CHAT_MESSAGE', ?::jsonb, ?)",
                        diegoNotice, diego, """{"title":"$name","body":"¿Vamos al cine?","route":"parcheu://chat/$diegoChat"}""", now,
                    )
                    // Bruno sí la conoce: el aviso de mensaje con su nombre como título se borra.
                    c.exec(
                        "insert into notifications (id, user_id, kind, payload, created_at) values (?, ?, 'CHAT_MESSAGE', ?::jsonb, ?)",
                        brunoNotice, s.bruno, """{"title":"$name","body":"Hola","route":"parcheu://chat/${UUID.randomUUID()}"}""", now,
                    )
                }
                val service = AccountDeletionService(
                    ExposedIdentityRepository(db), ExposedAccountPurgeRepository(db), RecordingAccountMirrorCleaner(), FakeAuthAccountRemover(), FakeTaskQueue(), FIXED_CLOCK,
                )
                service.requestDeletion(FirebaseUser(s.zoraidaUid, null, null))
                service.purge(s.zoraida) shouldBe PurgeOutcome.PURGED

                ds.connection.use { c ->
                    c.count("select count(*) from notifications where id = ?", diegoNotice) shouldBe 1
                    c.count("select count(*) from notifications where id = ?", brunoNotice) shouldBe 0
                }
            }
        }

    test("un nombre visible igual al título de un aviso del sistema no borra esos avisos, ni siquiera a sus contrapartes")
        .config(enabled = TestDatabase.enabled) {
            withDb { ds ->
                val db = connectExposed(ds)
                val s = ds.connection.use { seed(it) }
                val now = Timestamp.from(FIXED_CLOCK.instant())
                val title = "Tu simulación está lista"
                val diego = UUID.randomUUID()
                ds.connection.use { c ->
                    c.exec("update users set display_name = ? where id = ?", title, s.zoraida)
                    c.exec("insert into users (id, firebase_uid, created_at, updated_at) values (?, ?, ?, ?)", diego, "uid-diego-$diego", now, now)
                    c.exec(
                        "insert into notifications (id, user_id, kind, payload, created_at) values (?, ?, 'SIMULATION_READY', ?::jsonb, ?)",
                        UUID.randomUUID(), diego, """{"title":"$title","body":"Mira cómo les fue."}""", now,
                    )
                }
                val service = AccountDeletionService(
                    ExposedIdentityRepository(db), ExposedAccountPurgeRepository(db), RecordingAccountMirrorCleaner(), FakeAuthAccountRemover(), FakeTaskQueue(), FIXED_CLOCK,
                )
                service.requestDeletion(FirebaseUser(s.zoraidaUid, null, null))
                service.purge(s.zoraida) shouldBe PurgeOutcome.PURGED

                ds.connection.use { c ->
                    c.count("select count(*) from notifications where kind = 'SIMULATION_READY' and user_id = ?", diego) shouldBe 1
                    c.count("select count(*) from notifications where kind = 'SIMULATION_READY' and user_id = ?", s.bruno) shouldBe 1
                }
            }
        }

    test("un match PENDING_CHOICE se cierra: la contraparte recupera el cupo, su ticket queda EXPIRED y se le avisa")
        .config(enabled = TestDatabase.enabled) {
            withDb { ds ->
                val db = connectExposed(ds)
                val s = ds.connection.use { seed(it) }
                val now = Timestamp.from(FIXED_CLOCK.instant())
                val pending = UUID.randomUUID()
                val old = UUID.randomUUID()
                ds.connection.use { c ->
                    // Zoraida y Carla emparejadas, esperando elegir; las dos pagaron RANDOM_MATCH.
                    c.exec(
                        "insert into matches (id, mode, user_a, user_b, status, created_at, decide_by) values (?, 'AMISTAD', ?, ?, 'PENDING_CHOICE', ?, ?)",
                        pending, s.zoraida, s.carla, now, Timestamp.from(FIXED_CLOCK.instant().plusSeconds(600)),
                    )
                    listOf(s.zoraida, s.carla).forEach { u ->
                        c.exec("insert into usage_events (id, user_id, kind, ref_id, created_at) values (?, ?, 'RANDOM_MATCH', ?, ?)", UUID.randomUUID(), u, "$pending:$u", now)
                    }
                    c.exec("insert into match_queue (user_id, mode, enqueued_at, expires_at, status, match_id) values (?, 'AMISTAD', ?, ?, 'MATCHED', ?)", s.carla, now, now, pending)
                    // Un match viejo con Bruno que ya se cerró (p. ej. por un bloqueo) y cuyo ticket seguía MATCHED.
                    c.exec(
                        "insert into matches (id, mode, user_a, user_b, status, created_at, decide_by) values (?, 'AMISTAD', ?, ?, 'EXPIRED', ?, ?)",
                        old, s.bruno, s.zoraida, now, now,
                    )
                    c.exec("insert into match_queue (user_id, mode, enqueued_at, expires_at, status, match_id) values (?, 'AMISTAD', ?, ?, 'MATCHED', ?)", s.bruno, now, now, old)
                }
                val closed = java.util.concurrent.CopyOnWriteArrayList<ClosedMatch>()
                val service = AccountDeletionService(
                    ExposedIdentityRepository(db), ExposedAccountPurgeRepository(db), RecordingAccountMirrorCleaner(), FakeAuthAccountRemover(),
                    FakeTaskQueue(), FIXED_CLOCK, closedMatches = { closed += it },
                )
                service.requestDeletion(FirebaseUser(s.zoraidaUid, null, null))
                service.purge(s.zoraida) shouldBe PurgeOutcome.PURGED

                closed shouldContainExactlyInAnyOrder listOf(ClosedMatch(pending, s.carla, notify = true), ClosedMatch(old, s.bruno, notify = false))
                ds.connection.use { c ->
                    c.one("select refunded from usage_events where ref_id = ?", "$pending:${s.carla}") { it.getBoolean(1) } shouldBe true
                    listOf(s.carla, s.bruno).forEach { u ->
                        c.one("select status, match_id from match_queue where user_id = ?", u) { rs ->
                            rs.getString(1) shouldBe "EXPIRED"
                            rs.getObject(2) shouldBe null
                        }
                    }
                }
            }
        }
})

