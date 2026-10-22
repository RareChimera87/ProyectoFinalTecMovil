package me.santiagoreina.parcheu.api.matching

import com.google.cloud.Timestamp
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.TicketStatus
import java.time.Duration
import java.util.UUID

/**
 * El espejo real ([FirestoreMatchTicketMirror]) contra el emulador de Firestore: `matchTickets/{uid}`
 * refleja WAITING -> MATCHED con los campos de arquitectura.md §8.2 (los que leen las reglas de E0.8
 * y la UI de F1), vence a EXPIRED y se borra al cancelar.
 */
class MatchTicketFirestoreEmulatorTest : FunSpec({
    fun docOf(uid: String) = FirestoreEmulator.firestore.collection(FirestoreMatchTicketMirror.COLLECTION).document(uid).get().get()

    test("matchTickets/{uid} refleja WAITING -> MATCHED y los campos tienen los tipos esperados")
        .config(enabled = FirestoreEmulator.enabled) {
            val suffix = UUID.randomUUID().toString().take(8)
            val f = MatchFixture(mirrorOverride = FirestoreMatchTicketMirror { FirestoreEmulator.firestore })
            val ana = f.person("ana-$suffix")
            val beto = f.person("beto-$suffix")

            val waiting = f.service.enqueue(ana, Mode.AMISTAD)
            docOf("uid-ana-$suffix").run {
                exists() shouldBe true
                getString("status") shouldBe "WAITING"
                getString("mode") shouldBe "AMISTAD"
                getString("matchId").shouldBeNull()
                getTimestamp("expiresAt") shouldBe Timestamp.ofTimeSecondsAndNanos(
                    f.clock.now.plus(MatchingRules.TICKET_TTL).epochSecond, 0,
                )
                data!!.keys shouldBe setOf("status", "mode", "matchId", "expiresAt")
            }
            waiting.status shouldBe TicketStatus.WAITING

            val matched = f.service.enqueue(beto, Mode.AMISTAD)
            listOf("uid-ana-$suffix", "uid-beto-$suffix").forEach { uid ->
                docOf(uid).run {
                    getString("status") shouldBe "MATCHED"
                    getString("matchId") shouldBe matched.matchId
                }
            }
        }

    test("un ticket que vence queda EXPIRED y cancelar borra el documento")
        .config(enabled = FirestoreEmulator.enabled) {
            val suffix = UUID.randomUUID().toString().take(8)
            val f = MatchFixture(mirrorOverride = FirestoreMatchTicketMirror { FirestoreEmulator.firestore })
            val ana = f.person("ana-$suffix")
            val caro = f.person("caro-$suffix", modes = setOf(Mode.ESTUDIO))

            f.service.enqueue(ana, Mode.AMISTAD)
            f.clock.now = f.clock.now.plus(Duration.ofHours(24)).plusSeconds(1)
            f.service.ticket(ana).status shouldBe TicketStatus.EXPIRED
            docOf("uid-ana-$suffix").getString("status") shouldBe "EXPIRED"

            f.service.enqueue(caro, Mode.ESTUDIO)
            docOf("uid-caro-$suffix").exists() shouldBe true
            f.service.cancel(caro)
            docOf("uid-caro-$suffix").exists() shouldBe false
        }
})
