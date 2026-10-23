package me.santiagoreina.parcheu.api.identity.export

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.runBlocking
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.graph.AnonIdSigner
import me.santiagoreina.parcheu.api.identity.ExposedIdentityRepository
import me.santiagoreina.parcheu.api.identity.IdentityService
import me.santiagoreina.parcheu.api.platform.auth.FirebaseUser
import me.santiagoreina.parcheu.api.platform.db.TestDatabase
import me.santiagoreina.parcheu.api.platform.db.connectExposed
import me.santiagoreina.parcheu.api.platform.db.runMigrations
import me.santiagoreina.parcheu.api.twin.ExposedTwinRepository
import me.santiagoreina.parcheu.contract.ContractJson
import me.santiagoreina.parcheu.contract.ExportDto
import java.sql.Connection
import java.sql.Timestamp
import java.util.UUID

/**
 * Criterio de E8.3 contra Postgres real: la exportación trae los datos propios (perfil, respuestas,
 * memorias, consentimientos, conexiones y mensajes propios) y nada privado de otra persona: ni su
 * uuid, ni su nombre, correo, foto o uid de Firebase, ni sus mensajes. Solo su `anonId`.
 */
class ExportDbTest : FunSpec({
    fun Connection.exec(sql: String, vararg params: Any?) {
        prepareStatement(sql).use { st ->
            params.forEachIndexed { i, v -> st.setObject(i + 1, v) }
            st.execute()
        }
    }

    test("la exportación no incluye datos privados de otros usuarios (solo su id anónimo)").config(enabled = TestDatabase.enabled) {
        TestDatabase.withCleanDatabase { ds ->
            runMigrations(ds)
            val db = connectExposed(ds)
            val now = Timestamp.from(FIXED_CLOCK.instant())
            val ana = UUID.randomUUID()
            val anaUid = "uid-ana-${UUID.randomUUID()}"
            val laura = UUID.randomUUID()
            val lauraUid = "uid-laura-${UUID.randomUUID()}"
            val lauraSecret = "secreto-de-laura-${UUID.randomUUID()}"
            val chat = UUID.randomUUID()
            val sim = UUID.randomUUID()
            val mari = UUID.randomUUID()
            val introSim = UUID.randomUUID()
            ds.connection.use { c ->
                c.exec(
                    "insert into users (id, firebase_uid, email, display_name, photo_url, visibility, created_at, updated_at) " +
                        "values (?, ?, 'ana@example.com', 'Ana Gómez', null, 'NAME', ?, ?)",
                    ana, anaUid, now, now,
                )
                c.exec(
                    "insert into users (id, firebase_uid, email, display_name, photo_url, career, visibility, created_at, updated_at) " +
                        "values (?, ?, 'laura.privada@example.com', 'Laura Pinzón', 'https://photos/laura', 'Medicina', 'NAME', ?, ?)",
                    laura, lauraUid, now, now,
                )
                listOf("TERMS", "PRIVACY").forEach {
                    c.exec("insert into consents (user_id, kind, granted, text_version, created_at) values (?, ?, true, '2026-10-29', ?)", ana, it, now)
                }
                c.exec("insert into twin_answers (user_id, question_id, answer, answered_at) values (?, 1, 'B', ?)", ana, now)
                c.exec("insert into twin_memories (id, user_id, mode, text, status, created_at) values (?, ?, 'AMISTAD', 'Ama el café', 'ACCEPTED', ?)", UUID.randomUUID(), ana, now)
                c.exec(
                    "insert into simulations (id, mode, user_a, user_b, requested_by, origin, status, score, transcript, created_at) " +
                        "values (?, 'AMISTAD', ?, ?, ?, 'PARCHE', 'DONE', 81, ?::jsonb, ?)",
                    sim, laura, ana, laura, """{"turns":[{"text":"$lauraSecret"}]}""", now,
                )
                c.exec(
                    "insert into users (id, firebase_uid, display_name, created_at, updated_at) values (?, ?, 'Mari', ?, ?)",
                    mari, "uid-mari-${UUID.randomUUID()}", now, now,
                )
                // Mari presentó a Ana y Laura y la simulación terminó: Ana no debe verla en su exportación.
                c.exec(
                    "insert into simulations (id, mode, user_a, user_b, requested_by, origin, status, score, created_at) " +
                        "values (?, 'AMISTAD', ?, ?, ?, 'INTRODUCTION', 'DONE', 87, ?)",
                    introSim, ana, laura, mari, now,
                )
                c.exec(
                    "insert into edges (user_low, user_high, mode, state, affinity, created_at, updated_at, connected_at) " +
                        "values (least(?::uuid, ?::uuid), greatest(?::uuid, ?::uuid), 'AMISTAD', 'CONNECTED', 81, ?, ?, ?)",
                    ana, laura, ana, laura, now, now, now,
                )
                c.exec("insert into chats (id, type, mode, created_at) values (?, 'DIRECT', 'AMISTAD', ?)", chat, now)
                listOf(ana, laura).forEach { c.exec("insert into chat_members (chat_id, user_id) values (?, ?)", chat, it) }
                c.exec("insert into chat_messages (id, chat_id, sender_id, text, learnable, created_at) values (?, ?, ?, 'Nos vemos a las 3', true, ?)", UUID.randomUUID(), chat, ana, now)
                c.exec("insert into chat_messages (id, chat_id, sender_id, text, learnable, created_at) values (?, ?, ?, ?, true, ?)", UUID.randomUUID(), chat, laura, lauraSecret, now)
                c.exec("insert into blocks (blocker_id, blocked_id, created_at) values (?, ?, ?)", ana, laura, now)
                c.exec(
                    "insert into notifications (id, user_id, kind, payload, created_at) values (?, ?, 'CHAT_MESSAGE', ?::jsonb, ?)",
                    UUID.randomUUID(), ana, """{"title":"Laura Pinzón","body":"$lauraSecret","actorId":"$laura"}""", now,
                )
            }

            val signer = AnonIdSigner.forTests()
            val identity = IdentityService(ExposedIdentityRepository(db), FIXED_CLOCK, "2026-10-29")
            val twins = ExposedTwinRepository(db)
            val service = AccountExportService(identity, ExposedExportRepository(db), signer, twins::state, FIXED_CLOCK)
            val dto = runBlocking { service.export(FirebaseUser(anaUid, "ana@example.com", "Ana Gómez")) }
            val json = ContractJson.encodeToString(ExportDto.serializer(), dto)

            // Lo propio sí está.
            dto.me.displayName shouldBe "Ana Gómez"
            dto.consents.map { it.kind.name } shouldContainExactly listOf("TERMS", "PRIVACY")
            dto.twinAnswers.single().questionId shouldBe 1
            dto.memories.single().text shouldBe "Ama el café"
            dto.chats.single().messages.map { it.text } shouldContainExactly listOf("Nos vemos a las 3")
            dto.simulations.single().score shouldBe 81
            json shouldNotContain introSim.toString()
            json shouldNotContain "INTRODUCTION"
            dto.connections.size shouldBe 1

            // De Laura, solo su anonId.
            val anon = signer.anonId(ana, laura)
            json shouldContain anon
            listOf(laura.toString(), lauraUid, "Laura", "Pinzón", "laura.privada@example.com", "https://photos/laura", "Medicina", lauraSecret)
                .forEach { json shouldNotContain it }
        }
    }
})
