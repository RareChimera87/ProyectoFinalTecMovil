package me.santiagoreina.parcheu.api.chat

import com.google.cloud.Timestamp
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.graph.PersonRow
import me.santiagoreina.parcheu.api.graph.inMemoryGraphModule
import me.santiagoreina.parcheu.api.identity.UserRecord
import me.santiagoreina.parcheu.api.platform.notifier.FakeNotifier
import me.santiagoreina.parcheu.contract.ChatType
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.SendMessage
import me.santiagoreina.parcheu.contract.Visibility
import java.util.UUID

/**
 * El espejo real ([FirestoreChatMirror]) contra el emulador de Firestore: los documentos tienen los
 * campos y tipos de arquitectura.md §8.2 (los que leen las reglas de E0.8 y la UI de E5.2) y un
 * `clientId` repetido no crea un segundo documento.
 */
class ChatFirestoreEmulatorTest : FunSpec({
    fun user(uid: String) = UserRecord(
        id = UUID.randomUUID(), firebaseUid = uid, email = null, displayName = uid, photoUrl = null, career = null,
        universityId = null, interests = emptyList(), birthDate = null, ageVerifiedAt = null, underageAt = null,
        parejaConfirmedAt = null, visibility = Visibility.ANONYMOUS, isDemo = false, suspendedUntil = null,
        deletedAt = null, createdAt = FIXED_CLOCK.instant(),
    )

    test("mensaje con senderUid, system=false, text, createdAt y hidden=false; sin duplicados al repetir clientId")
        .config(enabled = FirestoreEmulator.enabled) {
            val ana = user("ana-${UUID.randomUUID()}")
            val beto = user("beto-${UUID.randomUUID()}")
            val repo = InMemoryChatRepository().apply {
                listOf(ana, beto).forEach { addUser(PersonRow(it.id, it.displayName, null, "UIS", Visibility.ANONYMOUS, false, null), it.firebaseUid) }
            }
            val mirror = FirestoreChatMirror { FirestoreEmulator.firestore }
            val service = inMemoryChatModule(FIXED_CLOCK, inMemoryGraphModule(FIXED_CLOCK).policy, FakeNotifier(), repo, mirror).service

            val chat = service.createDirect(ana.id, beto.id, Mode.PAREJA)
            val first = service.send(ana, chat.id, SendMessage("c-1", "hola"))
            val second = service.send(ana, chat.id, SendMessage("c-1", "hola"))
            second shouldBe first

            val chatRef = FirestoreEmulator.firestore.collection(FirestoreChatMirror.CHATS).document(chat.id.toString())
            val chatDoc = chatRef.get().get()
            @Suppress("UNCHECKED_CAST")
            (chatDoc.get("members") as List<String>).toSet() shouldBe setOf(ana.firebaseUid, beto.firebaseUid)
            chatDoc.getString("type") shouldBe ChatType.DIRECT.name
            chatDoc.getString("mode") shouldBe Mode.PAREJA.name
            chatDoc.getString("status") shouldBe "OPEN"

            val docs = chatRef.collection(FirestoreChatMirror.MESSAGES).get().get().documents
            docs.size shouldBe 1
            val doc = docs.single()
            doc.id shouldBe first.id
            doc.data.keys shouldBe setOf("senderUid", "system", "text", "createdAt", "hidden")
            doc.getString("senderUid") shouldBe ana.firebaseUid
            doc.getBoolean("system") shouldBe false
            doc.getString("text") shouldBe "hola"
            (doc.get("createdAt") as Timestamp).toDate().toInstant() shouldBe FIXED_CLOCK.instant()
            doc.getBoolean("hidden") shouldBe false
            repo.allMessages.size shouldBe 1
        }
})
