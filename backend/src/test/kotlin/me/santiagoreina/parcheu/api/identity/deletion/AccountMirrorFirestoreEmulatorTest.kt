package me.santiagoreina.parcheu.api.identity.deletion

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import me.santiagoreina.parcheu.api.chat.FirestoreEmulator
import java.util.UUID

/**
 * [FirestoreAccountMirrorCleaner] contra el emulador (solo con `FIRESTORE_EMULATOR_HOST`): borra
 * `simulations/{id}` y `matchTickets/{uid}`, saca el uid de `members` y deja sus mensajes sin autor; los
 * del otro siguen igual. Repetir la limpieza (reintento de la tarea) no falla ni cambia nada.
 */
class AccountMirrorFirestoreEmulatorTest : FunSpec({
    test("limpia simulaciones, ticket y chats de la cuenta; es idempotente").config(enabled = FirestoreEmulator.enabled) {
        val fs = FirestoreEmulator.firestore
        val me = "uid-me-${UUID.randomUUID()}"
        val other = "uid-other-${UUID.randomUUID()}"
        val sim = UUID.randomUUID()
        val chatId = UUID.randomUUID()
        val mine = UUID.randomUUID()
        val theirs = UUID.randomUUID()
        fs.collection("simulations").document(sim.toString()).set(mapOf("participants" to listOf(me, other), "score" to 70)).get()
        fs.collection("matchTickets").document(me).set(mapOf("status" to "WAITING")).get()
        val chat = fs.collection("chats").document(chatId.toString())
        chat.set(mapOf("members" to listOf(me, other), "type" to "SCENARIO")).get()
        chat.collection("messages").document(mine.toString()).set(mapOf("senderUid" to me, "system" to false, "text" to "mío")).get()
        chat.collection("messages").document(theirs.toString()).set(mapOf("senderUid" to other, "system" to false, "text" to "suyo")).get()
        // Un chat que Postgres ya no lista pero el espejo sí: también se limpia.
        val stray = fs.collection("chats").document(UUID.randomUUID().toString())
        stray.set(mapOf("members" to listOf(me, other))).get()

        val plan = PurgePlan(UUID.randomUUID(), me, "Yo", listOf(sim, UUID.randomUUID()), listOf(ChatPurge(chatId, listOf(mine))))
        val cleaner = FirestoreAccountMirrorCleaner { fs }
        cleaner.clean(plan)
        cleaner.clean(plan)

        fs.collection("simulations").document(sim.toString()).get().get().exists() shouldBe false
        fs.collection("matchTickets").document(me).get().get().exists() shouldBe false
        @Suppress("UNCHECKED_CAST")
        (chat.get().get().get("members") as List<String>) shouldContainExactly listOf(other)
        @Suppress("UNCHECKED_CAST")
        (stray.get().get().get("members") as List<String>) shouldContainExactly listOf(other)
        val own = chat.collection("messages").document(mine.toString()).get().get()
        own.getString("senderUid") shouldBe null
        own.getString("text") shouldBe "mío"
        chat.collection("messages").document(theirs.toString()).get().get().getString("senderUid") shouldBe other
    }
})
