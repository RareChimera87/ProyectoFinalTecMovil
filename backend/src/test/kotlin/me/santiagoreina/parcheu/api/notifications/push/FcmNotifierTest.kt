package me.santiagoreina.parcheu.api.notifications.push

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.platform.notifier.Notification
import me.santiagoreina.parcheu.contract.NotificationKinds
import java.util.UUID

class FcmNotifierTest : FunSpec({
    val ana = UUID.randomUUID()
    val beto = UUID.randomUUID()

    fun build(results: Map<String, FcmResult> = emptyMap()): Triple<FcmNotifier, InMemoryPushStore, FakeFcmClient> {
        val store = InMemoryPushStore()
        val fcm = FakeFcmClient(results)
        return Triple(FcmNotifier(store, fcm, FIXED_CLOCK), store, fcm)
    }

    test("notify inserta una fila en notifications y llama al cliente FCM") {
        val (notifier, store, fcm) = build()
        store.tokens["tok-ana"] = ana
        notifier.notify(
            ana,
            Notification(NotificationKinds.SIMULATION_READY, "Tu simulación está lista", "Mira cómo les fue", "parcheu://results/sim-1", actorId = beto),
        )

        store.notifications shouldHaveSize 1
        val row = store.notifications.single()
        row.userId shouldBe ana
        row.kind shouldBe NotificationKinds.SIMULATION_READY
        row.createdAt shouldBe FIXED_CLOCK.instant()
        row.payload["title"]!!.jsonPrimitive.content shouldBe "Tu simulación está lista"
        row.payload["route"]!!.jsonPrimitive.content shouldBe "parcheu://results/sim-1"
        row.payload["actorId"]!!.jsonPrimitive.content shouldBe beto.toString()
        row.payload["locked"]!!.jsonPrimitive.boolean shouldBe false

        fcm.calls shouldHaveSize 1
        fcm.calls.single().token shouldBe "tok-ana"
        fcm.calls.single().data shouldBe mapOf(
            "id" to row.id.toString(),
            "type" to "SIMULATION_READY",
            "channel" to "resultados",
            "title" to "Tu simulación está lista",
            "body" to "Mira cómo les fue",
            "route" to "parcheu://results/sim-1",
        )
    }

    test("un token UNREGISTERED se borra y los demás se conservan") {
        val (notifier, store, fcm) = build(mapOf("tok-viejo" to FcmResult.Unregistered, "tok-falla" to FcmResult.Failed("UNAVAILABLE")))
        store.tokens["tok-viejo"] = ana
        store.tokens["tok-vivo"] = ana
        store.tokens["tok-falla"] = ana
        store.tokens["tok-beto"] = beto

        notifier.notify(ana, Notification(NotificationKinds.CHAT_MESSAGE, "Beto", "Hola", "parcheu://chat/c1"))

        fcm.calls.map { it.token } shouldContainExactlyInAnyOrder listOf("tok-viejo", "tok-vivo", "tok-falla")
        store.tokens.keys shouldContainExactlyInAnyOrder listOf("tok-vivo", "tok-falla", "tok-beto")
        store.notifications shouldHaveSize 1
    }

    test("sin tokens igual se guarda el aviso para C2 y no se llama a FCM") {
        val (notifier, store, fcm) = build()
        notifier.notify(ana, Notification(NotificationKinds.ACCOUNT, "Bienvenida", "Ya puedes entrar"))
        store.notifications shouldHaveSize 1
        store.notifications.single().payload["route"] shouldBe JsonNull
        fcm.calls shouldHaveSize 0
    }

    test("el mensaje de datos sin ruta no incluye la clave route") {
        val (notifier, store, fcm) = build()
        store.tokens["t"] = ana
        notifier.notify(ana, Notification(NotificationKinds.ACCOUNT, "Hola", "Texto"))
        fcm.calls.single().data.containsKey("route") shouldBe false
    }

    test("cada tipo de aviso cae en el canal correcto") {
        val esperado = mapOf(
            NotificationKinds.CHAT_MESSAGE to "chat",
            NotificationKinds.SIMULATION_READY to "resultados",
            NotificationKinds.SIMULATION_FAILED to "resultados",
            NotificationKinds.MATCH_FOUND to "resultados",
            NotificationKinds.MATCH_EXPIRED to "resultados",
            NotificationKinds.CONNECT_REQUEST to "social",
            NotificationKinds.NEW_CONNECTION to "social",
            NotificationKinds.INTRODUCTION_PROPOSAL to "social",
            NotificationKinds.INTRODUCTION_READY to "social",
            NotificationKinds.INTRODUCTION_REQUEST to "social",
            NotificationKinds.INVITE_PENDING to "social",
            NotificationKinds.COMMUNITY to "social",
            NotificationKinds.TWIN_MEMORIES to "cuenta",
            NotificationKinds.TWIN_FEEDBACK to "cuenta",
            NotificationKinds.ACCOUNT to "cuenta",
            "TIPO_NUEVO" to "cuenta",
        )
        esperado.forEach { (kind, canal) -> PushChannel.forKind(kind).id shouldBe canal }
        PushChannel.entries.map { it.id } shouldContainExactly listOf("chat", "resultados", "social", "cuenta")
    }
})
