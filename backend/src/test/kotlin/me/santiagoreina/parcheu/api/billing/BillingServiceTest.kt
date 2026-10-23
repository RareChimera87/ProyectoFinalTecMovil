package me.santiagoreina.parcheu.api.billing

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import me.santiagoreina.parcheu.api.platform.errors.Forbidden
import me.santiagoreina.parcheu.api.platform.errors.ValidationFailed
import me.santiagoreina.parcheu.api.quota.MovableClock
import me.santiagoreina.parcheu.contract.EntitlementState
import me.santiagoreina.parcheu.contract.VerifyPurchase
import java.time.Instant
import java.util.Base64
import java.util.UUID

/** Reloj de los tests (el mismo que `FIXED_CLOCK`); las respuestas grabadas tienen fechas relativas a él. */
private val NOW: Instant = Instant.parse("2026-10-25T12:00:00Z")

/** Mundo de prueba: servicio real sobre almacén en memoria y el Fake HTTP de Play. */
private class World {
    val clock = MovableClock(NOW)
    val play = FakePlayApi()
    val store = InMemoryBillingStore()

    /** Lo que usa el servicio: el mismo [store], pero su `save` puede fallar a pedido. */
    val flaky = FlakyBillingStore(store)
    val service = BillingService(flaky, play.client, clock)
    val entitlements = StoredEntitlementService(store)
    val ana: UUID = UUID.fromString("3f0e8c1a-6b2d-4e9f-9a7c-1d2e3f4a5b6c")
    val beto: UUID = UUID.fromString("0b7f1d2e-3c4a-4b5d-8e6f-7a8b9c0d1e2f")

    fun verify(user: UUID, token: String) = service.verify(user, VerifyPurchase(token, BillingService.PLUS_PRODUCT_ID))
}

/** Sobre de Pub/Sub con una `subscriptionNotification` de Play, como lo manda la suscripción push. */
internal fun rtdnBody(
    messageId: String,
    token: String,
    type: Int,
    packageName: String = BillingService.DEFAULT_PACKAGE_NAME,
): String {
    val notification = buildJsonObject {
        put("version", "1.0")
        put("packageName", packageName)
        put("eventTimeMillis", "1793102400000")
        putJsonObject("subscriptionNotification") {
            put("version", "1.0")
            put("notificationType", type)
            put("purchaseToken", token)
            put("subscriptionId", BillingService.PLUS_PRODUCT_ID)
        }
    }
    val data = Base64.getEncoder().encodeToString(notification.toString().toByteArray())
    return """{"message":{"data":"$data","messageId":"$messageId","message_id":"$messageId","publishTime":"2026-10-25T12:00:00Z"},""" +
        """"subscription":"projects/parcheu-prod/subscriptions/play-rtdn-push"}"""
}

private fun World.rtdn(messageId: String, token: String, type: Int) = service.handleRtdn(decodeRtdn(rtdnBody(messageId, token, type)))

class BillingServiceTest : FunSpec({
    test("obfuscatedAccountId es sha256(userId) en hex minúscula (mismo vector que el cliente, E7.2)") {
        obfuscatedAccountId(UUID.fromString("3f0e8c1a-6b2d-4e9f-9a7c-1d2e3f4a5b6c")) shouldBe
            "53d33fd873210cc1f92da498b6ac2ef40b5088984ad30370081d475f1614917f"
    }

    context("respuestas grabadas de subscriptionsv2.get → Plus según arquitectura.md §9") {
        data class Case(val fixture: String, val state: EntitlementState, val plus: Boolean)
        withData(
            nameFn = { "${it.fixture} → ${it.state}, plus=${it.plus}" },
            Case("active", EntitlementState.ACTIVE, plus = true),
            Case("in-grace-period", EntitlementState.IN_GRACE_PERIOD, plus = true),
            Case("on-hold", EntitlementState.ON_HOLD, plus = false),
            Case("paused", EntitlementState.PAUSED, plus = false),
            Case("canceled", EntitlementState.CANCELED, plus = true),
            Case("expired", EntitlementState.EXPIRED, plus = false),
        ) { case ->
            val w = World()
            w.play.purchase("tok-${case.fixture}", case.fixture, owner = w.ana)
            val dto = w.verify(w.ana, "tok-${case.fixture}")
            dto.state shouldBe case.state
            dto.plus shouldBe case.plus
            w.entitlements.isPlus(w.ana, NOW) shouldBe case.plus
            w.service.entitlement(w.ana) shouldBe dto
        }
    }

    test("ACTIVE guarda plan base, vencimiento y renovación de la respuesta de Play") {
        val w = World()
        w.play.purchase("tok-anual", "active-anual-acknowledged", owner = w.ana)
        val dto = w.verify(w.ana, "tok-anual")
        dto.basePlan shouldBe "anual"
        dto.autoRenewing shouldBe true
        dto.expiresAt shouldBe kotlin.time.Instant.parse("2027-10-01T14:20:43.655Z")
        w.store.entitlement(w.ana)!!.purchaseToken shouldBe "tok-anual"
    }

    test("CANCELED da Plus hasta el vencimiento y después es gratis (EXPIRED en el DTO)") {
        val w = World()
        w.play.purchase("tok-c", "canceled", owner = w.ana)
        w.verify(w.ana, "tok-c").plus shouldBe true
        w.clock.now = Instant.parse("2026-11-10T18:40:13Z")
        w.entitlements.isPlus(w.ana, w.clock.instant()) shouldBe false
        val dto = w.service.entitlement(w.ana)
        dto.plus shouldBe false
        dto.state shouldBe EntitlementState.EXPIRED
    }

    test("sin compra: NONE y gratis") {
        val w = World()
        val dto = w.service.entitlement(w.ana)
        dto.state shouldBe EntitlementState.NONE
        dto.plus shouldBe false
    }

    test("obfuscatedAccountId distinto → 403 y no se guarda nada") {
        val w = World()
        w.play.purchase("tok-ajeno", "active", owner = w.beto)
        shouldThrow<Forbidden> { w.verify(w.ana, "tok-ajeno") }
        w.store.entitlement(w.ana).shouldBeNull()
        w.play.acknowledgeCalls("tok-ajeno") shouldBe 0
    }

    test("el mismo purchaseToken verificado desde otra cuenta → 403 y no cambia el derecho de la primera") {
        val w = World()
        w.play.purchase("tok-ana", "active", owner = w.ana)
        val primera = w.verify(w.ana, "tok-ana")
        primera.plus shouldBe true
        val antes = w.store.entitlement(w.ana)

        shouldThrow<Forbidden> { w.verify(w.beto, "tok-ana") }

        w.store.entitlement(w.ana) shouldBe antes
        w.store.entitlement(w.beto).shouldBeNull()
        w.service.entitlement(w.beto).plus shouldBe false
        w.service.entitlement(w.ana) shouldBe primera
        w.play.acknowledgeCalls("tok-ana") shouldBe 1
    }

    test("acknowledgementState=PENDING llama a acknowledge en el servidor exactamente una vez") {
        val w = World()
        w.play.purchase("tok-nuevo", "active", owner = w.ana)
        w.verify(w.ana, "tok-nuevo").plus shouldBe true
        w.play.acknowledgeCalls("tok-nuevo") shouldBe 1
        // Reintentar verify (o 'Restaurar') no la vuelve a reconocer: Play ya la tiene reconocida.
        w.verify(w.ana, "tok-nuevo").plus shouldBe true
        w.rtdn("m-compra", "tok-nuevo", RtdnTypes.SUBSCRIPTION_PURCHASED)
        w.play.acknowledgeCalls("tok-nuevo") shouldBe 1
    }

    test("una compra ya reconocida no llama a acknowledge") {
        val w = World()
        w.play.purchase("tok-ack", "active-anual-acknowledged", owner = w.ana)
        w.verify(w.ana, "tok-ack").plus shouldBe true
        w.play.acknowledgeCalls("tok-ack") shouldBe 0
    }

    test("compra con pago pendiente: sin Plus, sin fila y sin acknowledge (Play no deja reconocerla)") {
        val w = World()
        w.play.purchase("tok-pend", "pending", owner = w.ana)
        val dto = w.verify(w.ana, "tok-pend")
        dto.state shouldBe EntitlementState.NONE
        dto.plus shouldBe false
        w.store.entitlement(w.ana).shouldBeNull()
        w.play.acknowledgeCalls("tok-pend") shouldBe 0
        // Cuando Play confirma el pago, el siguiente verify la activa y la reconoce.
        w.play.update("tok-pend", "active", owner = w.ana)
        w.verify(w.ana, "tok-pend").plus shouldBe true
        w.play.acknowledgeCalls("tok-pend") shouldBe 1
    }

    test("si acknowledge falla en verify, el derecho se guarda igual y la RTDN lo reintenta") {
        val w = World()
        w.play.purchase("tok-x", "active", owner = w.ana)
        // Falla solo el acknowledge: el get va a Play y el reconocimiento lanza.
        val service = BillingService(w.store, object : PlaySubscriptions by w.play.client {
            override fun acknowledge(productId: String, purchaseToken: String) = error("Play caído")
        }, w.clock)
        service.verify(w.ana, VerifyPurchase("tok-x", BillingService.PLUS_PRODUCT_ID)).plus shouldBe true
        w.play.acknowledgeCalls("tok-x") shouldBe 0
        w.rtdn("m-x", "tok-x", RtdnTypes.SUBSCRIPTION_PURCHASED) shouldBe BillingService.RtdnOutcome.PROCESSED
        w.play.acknowledgeCalls("tok-x") shouldBe 1
    }

    test("producto distinto o token que Play no conoce → VALIDATION") {
        val w = World()
        shouldThrow<ValidationFailed> { w.service.verify(w.ana, VerifyPurchase("tok", "otro_producto")) }
        shouldThrow<ValidationFailed> { w.service.verify(w.ana, VerifyPurchase("  ", BillingService.PLUS_PRODUCT_ID)) }
        shouldThrow<ValidationFailed> { w.verify(w.ana, "tok-desconocido") }
        w.store.entitlement(w.ana).shouldBeNull()
    }

    test("'Restaurar' con una compra vieja vencida no pisa la vigente") {
        val w = World()
        w.play.purchase("tok-vigente", "active-anual-acknowledged", owner = w.ana)
        w.play.purchase("tok-vieja", "expired", owner = w.ana)
        w.verify(w.ana, "tok-vigente").plus shouldBe true
        w.verify(w.ana, "tok-vieja").plus shouldBe true
        w.store.entitlement(w.ana)!!.purchaseToken shouldBe "tok-vigente"
    }

    test("verify reemplaza una concesión de dev vencida por la compra real") {
        val w = World()
        w.entitlements.grantPlus(w.ana, 1, NOW.minusSeconds(3 * 86_400))
        w.play.purchase("tok-real", "active", owner = w.ana)
        w.verify(w.ana, "tok-real").plus shouldBe true
        w.store.entitlement(w.ana)!!.productId shouldBe BillingService.PLUS_PRODUCT_ID
    }

    context("RTDN") {
        test("la misma RTDN dos veces se procesa una sola vez") {
            val w = World()
            w.play.purchase("tok-r", "active-anual-acknowledged", owner = w.ana)
            w.verify(w.ana, "tok-r")
            val antes = w.play.getCalls("tok-r")
            w.play.update("tok-r", "in-grace-period", owner = w.ana)

            w.rtdn("m-1", "tok-r", 6) shouldBe BillingService.RtdnOutcome.PROCESSED
            w.rtdn("m-1", "tok-r", 6) shouldBe BillingService.RtdnOutcome.DUPLICATE

            w.play.getCalls("tok-r") shouldBe antes + 1
            w.store.eventIds shouldBe setOf("m-1")
            w.store.entitlement(w.ana)!!.state shouldBe EntitlementState.IN_GRACE_PERIOD
        }

        test("siempre reconsulta Play: el estado guardado es el de Play, no el que sugiere la notificación") {
            val w = World()
            w.play.purchase("tok-r", "active-anual-acknowledged", owner = w.ana)
            w.verify(w.ana, "tok-r")
            w.play.update("tok-r", "on-hold", owner = w.ana)
            // Tipo 2 = SUBSCRIPTION_RENEWED, pero Play dice ON_HOLD: manda Play.
            w.rtdn("m-2", "tok-r", 2)
            w.store.entitlement(w.ana)!!.state shouldBe EntitlementState.ON_HOLD
            w.entitlements.isPlus(w.ana, NOW) shouldBe false
            // Y vuelve a ACTIVE cuando Play lo recupera (SUBSCRIPTION_RECOVERED).
            w.play.update("tok-r", "active-anual-acknowledged", owner = w.ana)
            w.rtdn("m-3", "tok-r", 1)
            w.entitlements.isPlus(w.ana, NOW) shouldBe true
        }

        test("SUBSCRIPTION_REVOKED (reembolso) → REVOKED y sin Plus de inmediato") {
            val w = World()
            w.play.purchase("tok-rev", "active-anual-acknowledged", owner = w.ana)
            w.verify(w.ana, "tok-rev").plus shouldBe true

            // Play aún devuelve el periodo pagado, pero el reembolso quita Plus ya.
            w.rtdn("m-rev", "tok-rev", RtdnTypes.SUBSCRIPTION_REVOKED) shouldBe BillingService.RtdnOutcome.PROCESSED

            w.entitlements.isPlus(w.ana, NOW) shouldBe false
            val dto = w.service.entitlement(w.ana)
            dto.state shouldBe EntitlementState.REVOKED
            dto.plus shouldBe false
            dto.autoRenewing shouldBe false
            // Un verify posterior del mismo token (p. ej. 'Restaurar') no la revive.
            w.verify(w.ana, "tok-rev").state shouldBe EntitlementState.REVOKED
            w.play.update("tok-rev", "expired", owner = w.ana)
            w.rtdn("m-exp", "tok-rev", 13)
            w.service.entitlement(w.ana).state shouldBe EntitlementState.REVOKED
        }

        test("si Play falla, el reclamo se deshace y el reintento de Pub/Sub lo procesa") {
            val w = World()
            w.play.purchase("tok-f", "active-anual-acknowledged", owner = w.ana)
            w.verify(w.ana, "tok-f")
            w.play.update("tok-f", "expired", owner = w.ana)
            w.play.failNextWith = 503
            shouldThrow<Exception> { w.rtdn("m-f", "tok-f", 13) }
            w.store.eventIds shouldBe emptySet()
            w.rtdn("m-f", "tok-f", 13) shouldBe BillingService.RtdnOutcome.PROCESSED
            w.store.entitlement(w.ana)!!.state shouldBe EntitlementState.EXPIRED
        }

        test("si la base falla al guardar una renovación, no queda reclamo ni escritura y el reintento la procesa") {
            val w = World()
            w.play.purchase("tok-ren", "active", owner = w.ana)
            w.verify(w.ana, "tok-ren")
            val antes = w.store.entitlement(w.ana)!!
            w.play.update("tok-ren", "active-anual-acknowledged", owner = w.ana)
            w.flaky.failSaves = true

            shouldThrow<IllegalStateException> { w.rtdn("m-ren", "tok-ren", RtdnTypes.SUBSCRIPTION_RENEWED) }

            w.store.eventIds shouldBe emptySet()
            w.store.entitlement(w.ana) shouldBe antes
            w.flaky.failSaves = false
            w.rtdn("m-ren", "tok-ren", RtdnTypes.SUBSCRIPTION_RENEWED) shouldBe BillingService.RtdnOutcome.PROCESSED
            w.store.entitlement(w.ana)!!.expiresAt shouldBe Instant.parse("2027-10-01T14:20:43.655Z")
            w.store.eventIds shouldBe setOf("m-ren")
        }

        test("si la base falla al guardar una revocación, el reintento la procesa y quita Plus") {
            val w = World()
            w.play.purchase("tok-rv", "active-anual-acknowledged", owner = w.ana)
            w.verify(w.ana, "tok-rv").plus shouldBe true
            w.flaky.failSaves = true

            shouldThrow<IllegalStateException> { w.rtdn("m-rv", "tok-rv", RtdnTypes.SUBSCRIPTION_REVOKED) }

            w.entitlements.isPlus(w.ana, NOW) shouldBe true
            w.flaky.failSaves = false
            w.rtdn("m-rv", "tok-rv", RtdnTypes.SUBSCRIPTION_REVOKED) shouldBe BillingService.RtdnOutcome.PROCESSED
            w.store.entitlement(w.ana)!!.state shouldBe EntitlementState.REVOKED
            w.entitlements.isPlus(w.ana, NOW) shouldBe false
        }

        test("una revocación que se guarda mientras verify consulta a Play no se pisa con el ACTIVE de Play") {
            val w = World()
            w.play.purchase("tok-race", "active-anual-acknowledged", owner = w.ana)
            w.verify(w.ana, "tok-race").plus shouldBe true
            var armed = true
            val racing = object : PlaySubscriptions by w.play.client {
                override fun get(purchaseToken: String): PlaySubscription? {
                    val sub = w.play.client.get(purchaseToken) // Play aún responde ACTIVE (periodo pagado)
                    if (armed) {
                        armed = false
                        w.rtdn("m-race", "tok-race", RtdnTypes.SUBSCRIPTION_REVOKED) // llega y guarda REVOKED
                    }
                    return sub
                }
            }

            val dto = BillingService(w.store, racing, w.clock).verify(w.ana, VerifyPurchase("tok-race", BillingService.PLUS_PRODUCT_ID))

            dto.state shouldBe EntitlementState.REVOKED
            dto.plus shouldBe false
            w.store.entitlement(w.ana)!!.state shouldBe EntitlementState.REVOKED
        }

        test("compra aún sin verificar: se registra y se ignora (la app la verificará)") {
            val w = World()
            w.play.purchase("tok-sola", "active", owner = w.ana)
            w.rtdn("m-s", "tok-sola", RtdnTypes.SUBSCRIPTION_PURCHASED) shouldBe BillingService.RtdnOutcome.IGNORED
            w.store.entitlement(w.ana).shouldBeNull()
            w.play.acknowledgeCalls("tok-sola") shouldBe 0
            w.store.eventIds shouldBe setOf("m-s")
        }

        test("cambio de plan: el token nuevo con linkedPurchaseToken pasa al mismo dueño") {
            val w = World()
            w.play.purchase("tok-mensual", "active-anual-acknowledged", owner = w.ana)
            w.verify(w.ana, "tok-mensual")
            val linked = FakePlayApi.recorded("active").replace(
                "\"lineItems\"",
                "\"linkedPurchaseToken\": \"tok-mensual\",\n  \"lineItems\"",
            )
            w.play.purchaseRaw("tok-anual-nuevo", linked, owner = w.ana)
            w.rtdn("m-l", "tok-anual-nuevo", RtdnTypes.SUBSCRIPTION_PURCHASED) shouldBe BillingService.RtdnOutcome.PROCESSED
            w.store.entitlement(w.ana)!!.purchaseToken shouldBe "tok-anual-nuevo"
            w.play.acknowledgeCalls("tok-anual-nuevo") shouldBe 1
        }

        test("notificación de prueba, de otra app o ilegible: 'IGNORED' y queda registrada") {
            val w = World()
            val test = """{"version":"1.0","packageName":"${BillingService.DEFAULT_PACKAGE_NAME}","eventTimeMillis":"1","testNotification":{"version":"1.0"}}"""
            val data = Base64.getEncoder().encodeToString(test.toByteArray())
            w.service.handleRtdn(decodeRtdn("""{"message":{"data":"$data","messageId":"t-1"}}""")) shouldBe
                BillingService.RtdnOutcome.IGNORED
            w.service.handleRtdn(decodeRtdn(rtdnBody("t-2", "tok", 4, packageName = "otra.app"))) shouldBe
                BillingService.RtdnOutcome.IGNORED
            w.service.handleRtdn(decodeRtdn("""{"message":{"data":"no-es-base64!","messageId":"t-3"}}""")) shouldBe
                BillingService.RtdnOutcome.IGNORED
            w.store.eventIds shouldBe setOf("t-1", "t-2", "t-3")
        }

        test("un cuerpo que no es push de Pub/Sub falla al decodificar") {
            shouldThrow<IllegalArgumentException> { decodeRtdn("{}") }
            shouldThrow<IllegalArgumentException> { decodeRtdn("""{"message":{"data":"e30="}}""") }
            shouldThrow<IllegalArgumentException> { decodeRtdn("no es json") }
            decodeRtdn(rtdnBody("m", "t", 4)).raw shouldBe decodeRtdn(rtdnBody("m", "t", 4)).raw
            (decodeRtdn(rtdnBody("m", "t", 4)).raw as JsonObject)["packageName"] shouldBe
                JsonPrimitive(BillingService.DEFAULT_PACKAGE_NAME)
        }
    }
})
