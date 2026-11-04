package me.santiagoreina.parcheu.api.demo

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import me.santiagoreina.parcheu.api.chat.FirestoreChatMirror
import me.santiagoreina.parcheu.api.chat.FirestoreEmulator
import me.santiagoreina.parcheu.api.matching.FirestoreMatchTicketMirror
import me.santiagoreina.parcheu.api.simulation.FirestoreSimulationMirror
import java.util.UUID

/**
 * [FirestoreDemoMirrorCleaner] contra el emulador de Firestore: tras `purge-demo` desaparecen
 * `matchTickets/{uid}` de los demo, sus `chats/{id}` (con los mensajes) y sus `simulations/{id}`; lo demás se queda.
 */
class DemoFirestoreEmulatorTest : FunSpec({
    test("purge-demo borra los espejos de los demo y nada más").config(enabled = FirestoreEmulator.enabled) {
        val db = FirestoreEmulator.firestore
        val demoUid = "demo-${UUID.randomUUID()}"
        val humanUid = "ana-${UUID.randomUUID()}"
        val demoChat = UUID.randomUUID()
        val otherChat = UUID.randomUUID()
        val demoSim = UUID.randomUUID()
        val tickets = db.collection(FirestoreMatchTicketMirror.COLLECTION)
        val chats = db.collection(FirestoreChatMirror.CHATS)
        val sims = db.collection(FirestoreSimulationMirror.COLLECTION)
        tickets.document(demoUid).set(mapOf("status" to "WAITING")).get()
        tickets.document(humanUid).set(mapOf("status" to "WAITING")).get()
        chats.document(demoChat.toString()).set(mapOf("members" to listOf(humanUid, demoUid))).get()
        chats.document(demoChat.toString()).collection(FirestoreChatMirror.MESSAGES).document("m1").set(mapOf("text" to "hola")).get()
        chats.document(otherChat.toString()).set(mapOf("members" to listOf(humanUid))).get()
        sims.document(demoSim.toString()).set(mapOf("status" to "DONE")).get()

        FirestoreDemoMirrorCleaner { db }.clean(PurgeResult(1, listOf(demoUid), listOf(demoChat), listOf(demoSim)))

        tickets.document(demoUid).get().get().exists() shouldBe false
        chats.document(demoChat.toString()).get().get().exists() shouldBe false
        chats.document(demoChat.toString()).collection(FirestoreChatMirror.MESSAGES).get().get().isEmpty shouldBe true
        sims.document(demoSim.toString()).get().get().exists() shouldBe false
        tickets.document(humanUid).get().get().exists() shouldBe true
        chats.document(otherChat.toString()).get().get().exists() shouldBe true
    }
})
