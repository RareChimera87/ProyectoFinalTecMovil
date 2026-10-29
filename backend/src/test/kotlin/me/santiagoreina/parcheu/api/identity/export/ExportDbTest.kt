package me.santiagoreina.parcheu.api.identity.export

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
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
import me.santiagoreina.parcheu.contract.ExportIntroRole
import me.santiagoreina.parcheu.contract.ExportInviteRole
import me.santiagoreina.parcheu.contract.IntroductionStatus
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

    test("la exportación completa trae avisos, matches, reportes, presentaciones, compras y más, sin terceros ni tokens").config(enabled = TestDatabase.enabled) {
        TestDatabase.withCleanDatabase { ds ->
            runMigrations(ds)
            val db = connectExposed(ds)
            val now = Timestamp.from(FIXED_CLOCK.instant())
            val later = Timestamp.from(FIXED_CLOCK.instant().plusSeconds(86_400))
            val ana = UUID.randomUUID()
            val anaUid = "uid-ana-${UUID.randomUUID()}"
            val laura = UUID.randomUUID()
            val mari = UUID.randomUUID()
            val pedro = UUID.randomUUID()
            val secret = "secreto-de-terceros-${UUID.randomUUID()}"
            val playToken = "play-token-${UUID.randomUUID()}"
            val reportId = UUID.randomUUID()
            val introMade = UUID.randomUUID()
            val introReceived = UUID.randomUUID()
            val introMadeDeclined = UUID.randomUUID()
            val introHiddenSimulating = UUID.randomUUID()
            val introHiddenDeclined = UUID.randomUUID()
            val matchId = UUID.randomUUID()
            val chat = UUID.randomUUID()
            ds.connection.use { c ->
                fun user(id: UUID, uid: String, name: String) = c.exec(
                    "insert into users (id, firebase_uid, email, display_name, photo_url, created_at, updated_at) " +
                        "values (?, ?, ?, ?, ?, ?, ?)",
                    id, uid, "$uid@example.com", name, "https://photos/${if (id == ana) "ana" else "otro"}", now, now,
                )
                user(ana, anaUid, "Ana Gómez")
                user(laura, "uid-laura-${UUID.randomUUID()}", "Laura Pinzón")
                user(mari, "uid-mari-${UUID.randomUUID()}", "Mariana Ruiz")
                user(pedro, "uid-pedro-${UUID.randomUUID()}", "Pedro Cárdenas")
                c.exec("insert into twins (user_id, persona_summary, updated_at) values (?, 'Persona curiosa que ama el café.', ?)", ana, now)
                // Avisos: uno propio (texto conservado) y otros que citan a terceros (texto reemplazado).
                c.exec(
                    "insert into notifications (id, user_id, kind, payload, created_at) values (?, ?, 'SIMULATION_FAILED', ?::jsonb, ?)",
                    UUID.randomUUID(), ana, """{"title":"La simulación no se pudo completar","body":"No se gastó tu cupo.","route":"parcheu://home"}""", now,
                )
                c.exec(
                    "insert into notifications (id, user_id, kind, payload, created_at, read_at) values (?, ?, 'CHAT_MESSAGE', ?::jsonb, ?, ?)",
                    UUID.randomUUID(), ana, """{"title":"Laura Pinzón","body":"$secret","route":"parcheu://chat/$chat","actorId":"$laura"}""", now, now,
                )
                // Reporte hecho por Ana a Laura (con instantánea de mensajes de Laura) y otro de Laura contra Ana.
                c.exec(
                    "insert into reports (id, reporter_id, target_id, kind, reason, details, snapshot, source, status, resolution, resolved_by, created_at) " +
                        "values (?, ?, ?, 'USER', 'HARASSMENT', 'Me insistió demasiado', ?::jsonb, 'USER', 'RESOLVED', 'nota-interna-moderacion', ?, ?)",
                    reportId, ana, laura, """{"messages":[{"text":"$secret"}]}""", mari, now,
                )
                c.exec(
                    "insert into reports (id, reporter_id, target_id, kind, reason, details, source, status, created_at) " +
                        "values (?, ?, ?, 'USER', 'OTHER', 'Detalle-de-laura-contra-ana', 'USER', 'OPEN', ?)",
                    UUID.randomUUID(), laura, ana, now,
                )
                // Presentación hecha por Ana (Laura-Pedro) y recibida (Mari presenta a Ana con Laura; Laura ya declinó).
                c.exec(
                    "insert into introductions (id, introducer_id, mode, user_a, user_b, status, created_at) values (?, ?, 'AMISTAD', ?, ?, 'CONNECTED', ?)",
                    introMade, ana, laura, pedro, now,
                )
                c.exec(
                    "insert into introductions (id, introducer_id, mode, user_a, user_b, a_response, b_response, status, created_at) " +
                        "values (?, ?, 'ESTUDIO', ?, ?, 'INTERESTED', 'DECLINED', 'DECLINED', ?)",
                    introReceived, mari, ana, laura, now,
                )
                // Ana presentó a Laura y Mari, y Mari dijo 'No, gracias': a Ana la app le muestra PROPOSED.
                c.exec(
                    "insert into introductions (id, introducer_id, mode, user_a, user_b, b_response, status, created_at) " +
                        "values (?, ?, 'AMISTAD', ?, ?, 'DECLINED', 'DECLINED', ?)",
                    introMadeDeclined, ana, laura, mari, now,
                )
                // Presentaciones a Ana que ella nunca vio: una aún simulándose y otra que Pedro descartó ('No por ahora').
                c.exec(
                    "insert into introductions (id, introducer_id, mode, user_a, user_b, status, created_at) values (?, ?, 'AMISTAD', ?, ?, 'SIMULATING', ?)",
                    introHiddenSimulating, pedro, ana, laura, now,
                )
                c.exec(
                    "insert into introductions (id, introducer_id, mode, user_a, user_b, status, created_at) values (?, ?, 'ESTUDIO', ?, ?, 'DECLINED', ?)",
                    introHiddenDeclined, pedro, laura, ana, now,
                )
                // Match con Laura (la elección de Laura no debe salir) y turno en la fila.
                c.exec(
                    "insert into matches (id, mode, user_a, user_b, choice_a, choice_b, status, created_at, decide_by) " +
                        "values (?, 'AMISTAD', ?, ?, 'SIMULATION', 'SCENARIO', 'PENDING_CHOICE', ?, ?)",
                    matchId, laura, ana, now, later,
                )
                c.exec("insert into match_queue (user_id, mode, enqueued_at, expires_at) values (?, 'ESTUDIO', ?, ?)", ana, now, later)
                c.exec(
                    "insert into twin_feedback (id, user_id, kind, question_id, predicted, actual, rating, created_at) values (?, ?, 'CONTROL', 1, 'A', 'B', 4, ?)",
                    UUID.randomUUID(), ana, now,
                )
                c.exec("insert into invites (code, user_id) values ('ABCD-1234', ?)", ana)
                c.exec(
                    "insert into invite_redemptions (owner_id, redeemer_id, mode, status, created_at, accepted_at) values (?, ?, 'AMISTAD', 'CONNECTED', ?, ?)",
                    ana, pedro, now, now,
                )
                c.exec("insert into invite_redemptions (owner_id, redeemer_id, mode, status, created_at) values (?, ?, 'ESTUDIO', 'PENDING', ?)", laura, ana, now)
                c.exec("insert into invite_redeem_attempts (id, user_id, created_at) values (?, ?, ?)", UUID.randomUUID(), ana, now)
                c.exec(
                    "insert into purchase_events (message_id, purchase_token, payload, user_id, received_at) values ('m1', ?, ?::jsonb, ?, ?)",
                    playToken, """{"subscriptionNotification":{"notificationType":4,"purchaseToken":"$playToken","subscriptionId":"plus"}}""", ana, now,
                )
                c.exec("insert into purchase_events (message_id, purchase_token, payload, received_at) values ('m-ajeno', 'otro', '{}'::jsonb, ?)", now)
            }

            val signer = AnonIdSigner.forTests()
            val identity = IdentityService(ExposedIdentityRepository(db), FIXED_CLOCK, "2026-10-29")
            val service = AccountExportService(identity, ExposedExportRepository(db), signer, ExposedTwinRepository(db)::state, FIXED_CLOCK)
            val dto = runBlocking { service.export(FirebaseUser(anaUid, "ana@example.com", "Ana Gómez")) }
            val json = ContractJson.encodeToString(ExportDto.serializer(), dto)

            // Avisos: ambos, el propio con su texto y el de Laura reemplazado, con actor anónimo.
            dto.notifications.size shouldBe 2
            dto.notifications.single { it.kind == "SIMULATION_FAILED" }.body shouldBe "No se gastó tu cupo."
            val chatNotice = dto.notifications.single { it.kind == "CHAT_MESSAGE" }
            chatNotice.read shouldBe true
            chatNotice.actor?.anonId shouldBe signer.anonId(ana, laura)
            chatNotice.route shouldBe null

            dto.twinPersonaSummary shouldBe "Persona curiosa que ama el café."

            val report = dto.reportsMade.single()
            report.id shouldBe reportId.toString()
            report.details shouldBe "Me insistió demasiado"
            report.reportedAnonId shouldBe signer.anonId(ana, laura)

            // Las dos presentaciones que Ana nunca vio (SIMULATING y 'No por ahora') no salen.
            dto.introductions.size shouldBe 3
            dto.introductions.map { it.id } shouldNotContain introHiddenSimulating.toString()
            dto.introductions.map { it.id } shouldNotContain introHiddenDeclined.toString()
            // El 'No, gracias' de Mari no se revela a quien presenta: sale PROPOSED, no DECLINED.
            dto.introductions.single { it.id == introMadeDeclined.toString() }.status shouldBe IntroductionStatus.PROPOSED
            val made = dto.introductions.single { it.id == introMade.toString() }
            made.people shouldContainExactly listOf(signer.anonId(ana, laura), signer.anonId(ana, pedro))
            val received = dto.introductions.single { it.role == ExportIntroRole.RECEIVED }
            received.introducerAnonId shouldBe signer.anonId(ana, mari)
            received.people shouldContainExactly listOf(signer.anonId(ana, laura))
            received.myResponse shouldBe "INTERESTED"
            received.status shouldBe null // el estado revelaría que Laura declinó

            val match = dto.matches.single()
            match.myChoice?.name shouldBe "SCENARIO" // Ana es user_b: la elección de Laura (SIMULATION) no sale
            match.otherAnonId shouldBe signer.anonId(ana, laura)
            dto.queueTicket?.mode?.name shouldBe "ESTUDIO"

            dto.twinFeedback.single().rating shouldBe 4
            dto.twinFeedback.single().actual shouldBe "B"

            val invites = dto.invites!!
            invites.code shouldBe "ABCD-1234"
            invites.redemptions.map { it.role } shouldContainExactly listOf(ExportInviteRole.OWNER, ExportInviteRole.REDEEMER)
            invites.redeemAttempts.size shouldBe 1

            dto.purchases.single().notificationType shouldBe 4

            listOf(
                laura.toString(), mari.toString(), pedro.toString(), "Laura", "Pinzón", "Mariana", "Pedro", "Cárdenas",
                "photos/otro", "uid-laura", "uid-mari", "uid-pedro", secret, playToken, "nota-interna-moderacion", "Detalle-de-laura-contra-ana",
                "purchaseToken", "m-ajeno", "otro",
            ).forEach { json shouldNotContain it }
        }
    }
})
