package me.santiagoreina.parcheu.api.identity.retention

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.chat.FirestoreEmulator
import java.time.Instant
import java.util.Date
import java.util.UUID

/**
 * [FirestoreRetentionMirror] contra el emulador (solo con `FIRESTORE_EMULATOR_HOST`): borra los
 * `simulations/{id}` vencidos y conserva los recientes (y los que siguen en curso), borra los
 * `matchTickets` vencidos y los chats pedidos con sus mensajes. Repetir no cambia nada.
 */
class RetentionFirestoreEmulatorTest : FunSpec({
    val now = FIXED_CLOCK.instant()
    fun ago(days: Long, plusSeconds: Long = 0): Date = Date.from(now.minusSeconds(days * 86400).plusSeconds(plusSeconds))
    fun cutoff(days: Long): Instant = now.minusSeconds(days * 86400)

    test("simulations: borra las terminadas hace más de 30 días y conserva las recientes y las en curso")
        .config(enabled = FirestoreEmulator.enabled) {
            val fs = FirestoreEmulator.firestore
            val tag = UUID.randomUUID().toString()
            fun sim(status: String, updatedAt: Date): String {
                val id = UUID.randomUUID().toString()
                fs.collection("simulations").document(id).set(mapOf("status" to status, "updatedAt" to updatedAt, "tag" to tag)).get()
                return id
            }
            val dueDone = sim("DONE", ago(30, -1))
            val dueFailed = sim("FAILED", ago(31))
            val justBefore = sim("DONE", ago(30, +1))
            val recent = sim("DONE", ago(1))
            val stuck = sim("RUNNING", ago(40))

            val mirror = FirestoreRetentionMirror { fs }
            // Otros documentos del emulador (de otras pruebas) también pueden estar vencidos: se cuentan al menos los nuestros.
            (mirror.deleteSimulationsBefore(cutoff(30)) >= 2) shouldBe true
            mirror.deleteSimulationsBefore(cutoff(30)) shouldBe 0

            fun exists(id: String) = fs.collection("simulations").document(id).get().get().exists()
            exists(dueDone) shouldBe false
            exists(dueFailed) shouldBe false
            exists(justBefore) shouldBe true
            exists(recent) shouldBe true
            exists(stuck) shouldBe true
        }

    test("matchTickets: borra los vencidos hace más de 7 días y conserva el resto")
        .config(enabled = FirestoreEmulator.enabled) {
            val fs = FirestoreEmulator.firestore
            fun ticket(expiresAt: Date): String {
                val uid = "uid-${UUID.randomUUID()}"
                fs.collection("matchTickets").document(uid).set(mapOf("status" to "EXPIRED", "expiresAt" to expiresAt)).get()
                return uid
            }
            val due = ticket(ago(7, -1))
            val justBefore = ticket(ago(7, +1))
            val future = ticket(Date.from(now.plusSeconds(86400)))

            val mirror = FirestoreRetentionMirror { fs }
            (mirror.deleteMatchTicketsExpiredBefore(cutoff(7)) >= 1) shouldBe true
            mirror.deleteMatchTicketsExpiredBefore(cutoff(7)) shouldBe 0

            fun exists(uid: String) = fs.collection("matchTickets").document(uid).get().get().exists()
            exists(due) shouldBe false
            exists(justBefore) shouldBe true
            exists(future) shouldBe true
        }

    test("chats: borra el documento y sus mensajes, y repetir no falla")
        .config(enabled = FirestoreEmulator.enabled) {
            val fs = FirestoreEmulator.firestore
            val doomed = UUID.randomUUID()
            val other = UUID.randomUUID()
            for (id in listOf(doomed, other)) {
                val chat = fs.collection("chats").document(id.toString())
                chat.set(mapOf("members" to listOf("a", "b"), "type" to "SCENARIO")).get()
                chat.collection("messages").document(UUID.randomUUID().toString()).set(mapOf("text" to "hola")).get()
            }
            val mirror = FirestoreRetentionMirror { fs }
            mirror.deleteChats(listOf(doomed)) shouldBe 1
            mirror.deleteChats(listOf(doomed)) shouldBe 0

            fs.collection("chats").document(doomed.toString()).get().get().exists() shouldBe false
            fs.collection("chats").document(doomed.toString()).collection("messages").get().get().isEmpty shouldBe true
            fs.collection("chats").document(other.toString()).get().get().exists() shouldBe true
        }
})
