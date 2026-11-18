package me.santiagoreina.parcheu.api.billing

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.santiagoreina.parcheu.api.quota.MovableClock
import me.santiagoreina.parcheu.api.quota.QuotaLimits
import me.santiagoreina.parcheu.api.quota.QuotaService
import me.santiagoreina.parcheu.api.quota.InMemoryUsageRepository
import me.santiagoreina.parcheu.contract.QuotaKind
import me.santiagoreina.parcheu.contract.EntitlementState
import me.santiagoreina.parcheu.contract.VerifyPurchase
import java.time.Duration
import java.time.Instant
import java.util.UUID

private val NOW: Instant = Instant.parse("2026-10-25T12:00:00Z")
private const val ANA_EMAIL = "ana@example.com"

/** Mundo del Plus manual: servicio real sobre el almacén en memoria, usuarios a mano y el Fake de Play. */
private class ManualWorld {
    val clock = MovableClock(NOW)
    val store = InMemoryBillingStore()
    val play = FakePlayApi()
    val billing = BillingService(store, play.client, clock)
    val entitlements = StoredEntitlementService(store)
    val ana: UUID = UUID.fromString("3f0e8c1a-6b2d-4e9f-9a7c-1d2e3f4a5b6c")
    val people = mutableMapOf(
        "ana" to Person(ManualPlusUser(ana, isDemo = false, deleted = false, suspendedUntil = null), ANA_EMAIL),
    )
    val manual = ManualPlusService(
        store,
        { sel ->
            people.values.filter {
                when (sel) {
                    is UserSelector.ByEmail -> it.email.equals(sel.email, ignoreCase = true)
                    is UserSelector.ByFirebaseUid -> it.uid == sel.uid
                }
            }.map { it.user }
        },
        clock,
    )

    data class Person(val user: ManualPlusUser, val email: String, val uid: String = "uid-${user.id}")

    fun add(name: String, user: ManualPlusUser, email: String = "$name@example.com") {
        people[name] = Person(user, email)
    }

    fun grant(days: Int = 7, email: String = ANA_EMAIL) = manual.grant(UserSelector.ByEmail(email), days)
    fun plus() = entitlements.isPlus(ana, clock.instant())
    fun manualEvents() = store.eventIds.filter { it.startsWith("manual-grant:") }
    fun verify(token: String) = billing.verify(ana, VerifyPurchase(token, BillingService.PLUS_PRODUCT_ID))
    fun rtdn(messageId: String, token: String, type: Int) = billing.handleRtdn(decodeRtdn(rtdnBody(messageId, token, type)))
}

class ManualPlusTest : FunSpec({
    context("argumentos del job") {
        test("sin parámetros falla (los argumentos por defecto del job son inocuos)") {
            shouldThrow<IllegalArgumentException> { ManualPlusJob.parse(revoke = false, emptyList()) }
            shouldThrow<IllegalArgumentException> { ManualPlusJob.parse(revoke = true, emptyList()) }
        }

        test("por correo con 7 días por defecto, por uid y --days=0 revoca") {
            ManualPlusJob.parse(false, listOf("--email=Ana@Example.com")) shouldBe
                ManualPlusJob.Request(UserSelector.ByEmail("Ana@Example.com"), 7)
            ManualPlusJob.parse(false, listOf("--uid=abc123", "--days=30")) shouldBe ManualPlusJob.Request(UserSelector.ByFirebaseUid("abc123"), 30)
            ManualPlusJob.parse(false, listOf("--uid=abc123", "--days=0")).days shouldBe 0
            ManualPlusJob.parse(true, listOf("--uid=abc123")).days shouldBe 0
        }

        test("rechaza días fuera de 1..30, correo y uid a la vez, comodines, argumentos desconocidos") {
            listOf(
                listOf("--email=a@b.co", "--days=31"),
                listOf("--email=a@b.co", "--days=-1"),
                listOf("--email=a@b.co", "--days=siempre"),
                listOf("--email=a@b.co", "--uid=x"),
                listOf("--email=%@b.co"),
                listOf("--email=*"),
                listOf("--email=sin-arroba"),
                listOf("--email=a@b.co", "--forever"),
                listOf("--email="),
            ).forEach { args -> shouldThrow<IllegalArgumentException> { ManualPlusJob.parse(false, args) } }
            shouldThrow<IllegalArgumentException> { ManualPlusJob.parse(true, listOf("--email=a@b.co", "--days=3")) }
        }

        test("el mensaje de error nunca repite el correo") {
            val e = shouldThrow<IllegalArgumentException> { ManualPlusJob.parse(false, listOf("--email=secreto@x.co", "--uid=y")) }
            e.message!! shouldNotContain "secreto"
        }

        test("códigos de salida: 0 hecho, 2 argumentos, 3 rechazo") {
            val w = ManualWorld()
            ManualPlusJob.execute(w.manual, ManualPlusJob.Request(UserSelector.ByEmail(ANA_EMAIL), 7)) shouldBe 0
            ManualPlusJob.execute(w.manual, ManualPlusJob.Request(UserSelector.ByEmail("nadie@example.com"), 7)) shouldBe 3
            ManualPlusJob.execute(w.manual, ManualPlusJob.Request(UserSelector.ByEmail(ANA_EMAIL), 0)) shouldBe 0
        }
    }

    context("conceder") {
        test("concede ACTIVE con origen manual, vencimiento a 7 días, auditoría sin correo y cuenta como Plus") {
            val w = ManualWorld()
            w.plus() shouldBe false
            val out = w.grant()
            out.expiresAt shouldBe NOW + Duration.ofDays(7)
            out.changed shouldBe true
            val row = w.store.entitlement(w.ana)!!
            row.productId shouldBe MANUAL_GRANT_PRODUCT
            row.purchaseToken shouldBe "manual-grant:${w.ana}"
            row.state shouldBe EntitlementState.ACTIVE
            row.autoRenewing shouldBe false
            row.basePlan shouldBe null
            w.plus() shouldBe true
            w.billing.entitlement(w.ana).plus shouldBe true

            val events = w.manualEvents()
            events shouldHaveSize 1
            val (token, user, payload) = w.store.eventOf(events.single())!!
            token shouldBe "manual-grant:${w.ana}"
            user shouldBe w.ana
            payload.jsonObject["source"]!!.jsonPrimitive.content shouldBe "MANUAL"
            payload.jsonObject["action"]!!.jsonPrimitive.content shouldBe "GRANT"
            payload.toString() shouldNotContain "@"
        }

        test("por uid de Firebase") {
            val w = ManualWorld()
            w.manual.grant(UserSelector.ByFirebaseUid("uid-${w.ana}"), 3).expiresAt shouldBe NOW + Duration.ofDays(3)
            w.plus() shouldBe true
        }

        test("el correo se compara exacto sin distinguir mayúsculas; un prefijo no basta") {
            val w = ManualWorld()
            w.grant(email = "ANA@example.com").userId shouldBe w.ana
            shouldThrow<ManualPlusRefused> { w.grant(email = "ana@example.co") }.reason shouldBe ManualPlusRefused.Reason.NOT_FOUND
        }

        test("vence solo: al pasar el vencimiento vuelve a gratis sin intervención (sin margen de renovación)") {
            val w = ManualWorld()
            w.grant(days = 1)
            w.clock.now = NOW + Duration.ofDays(1).minusSeconds(1)
            w.plus() shouldBe true
            w.clock.now = NOW + Duration.ofDays(1)
            w.plus() shouldBe false
            w.billing.entitlement(w.ana).let { it.plus shouldBe false; it.state shouldBe EntitlementState.EXPIRED }
        }

        test("idempotente: repetir no duplica la fila; extiende o deja igual, nunca acorta ni pasa de 30 días") {
            val w = ManualWorld()
            w.grant(days = 7)
            w.grant(days = 7).let { it.changed shouldBe false; it.expiresAt shouldBe NOW + Duration.ofDays(7) }
            w.grant(days = 2).let { it.changed shouldBe false; it.expiresAt shouldBe NOW + Duration.ofDays(7) }
            w.clock.now = NOW + Duration.ofDays(1)
            w.grant(days = 30).let {
                it.extended shouldBe true
                it.changed shouldBe true
                it.expiresAt shouldBe NOW + Duration.ofDays(31)
            }
            w.store.entitlement(w.ana)!!.expiresAt shouldBe NOW + Duration.ofDays(31)
            w.manualEvents() shouldHaveSize 4 // cada ejecución queda auditada
        }

        test("rechaza usuario inexistente, demo, borrado, suspendido y correo repetido") {
            val w = ManualWorld()
            fun reason(email: String) = shouldThrow<ManualPlusRefused> { w.grant(email = email) }.reason
            w.add("demo", ManualPlusUser(UUID.randomUUID(), isDemo = true, deleted = false, suspendedUntil = null))
            w.add("borrado", ManualPlusUser(UUID.randomUUID(), isDemo = false, deleted = true, suspendedUntil = null))
            w.add("susp", ManualPlusUser(UUID.randomUUID(), isDemo = false, deleted = false, suspendedUntil = NOW.plusSeconds(60)))
            w.add("susp-vencida", ManualPlusUser(UUID.randomUUID(), isDemo = false, deleted = false, suspendedUntil = NOW.minusSeconds(60)))
            w.add("dup1", ManualPlusUser(UUID.randomUUID(), false, false, null), email = "dup@example.com")
            w.add("dup2", ManualPlusUser(UUID.randomUUID(), false, false, null), email = "DUP@example.com")
            reason("nadie@example.com") shouldBe ManualPlusRefused.Reason.NOT_FOUND
            reason("demo@example.com") shouldBe ManualPlusRefused.Reason.DEMO
            reason("borrado@example.com") shouldBe ManualPlusRefused.Reason.DELETED
            reason("susp@example.com") shouldBe ManualPlusRefused.Reason.SUSPENDED
            reason("dup@example.com") shouldBe ManualPlusRefused.Reason.AMBIGUOUS
            w.grant(email = "susp-vencida@example.com").changed shouldBe true
            w.manualEvents() shouldHaveSize 1 // los rechazos no escriben nada
        }

        test("no pisa una suscripción de Play vigente ni una recuperable (ON_HOLD, PAUSED)") {
            listOf("active", "in-grace-period", "canceled", "on-hold", "paused").forEach { fixture ->
                val w = ManualWorld()
                w.play.purchase("tok-$fixture", fixture, owner = w.ana)
                w.verify("tok-$fixture")
                val before = w.store.entitlement(w.ana)
                shouldThrow<ManualPlusRefused> { w.grant() }.reason shouldBe ManualPlusRefused.Reason.PLAY_ACTIVE
                w.store.entitlement(w.ana) shouldBe before
                w.manualEvents() shouldHaveSize 0
            }
        }

        test("sobre una compra de Play ya vencida sí concede (y la purga del token sigue siendo de Play)") {
            val w = ManualWorld()
            w.play.purchase("tok-exp", "expired", owner = w.ana)
            w.verify("tok-exp").plus shouldBe false
            w.grant().changed shouldBe true
            w.plus() shouldBe true
        }
    }

    context("revocar") {
        test("revoke-plus deja la fila EXPIRED ya, auditada; repetir no hace nada") {
            val w = ManualWorld()
            w.grant()
            w.clock.now = NOW.plusSeconds(3600)
            w.manual.revoke(UserSelector.ByEmail(ANA_EMAIL)).shouldBeInstanceOf<ManualPlusOutcome.Revoked>()
            w.plus() shouldBe false
            w.store.entitlement(w.ana)!!.let { it.state shouldBe EntitlementState.EXPIRED; it.expiresAt shouldBe NOW.plusSeconds(3600) }
            w.manual.revoke(UserSelector.ByEmail(ANA_EMAIL)).shouldBeInstanceOf<ManualPlusOutcome.NothingToRevoke>()
            w.manualEvents() shouldHaveSize 2
            // Tras revocar se puede volver a conceder (otra demo).
            w.grant(days = 1).changed shouldBe true
            w.plus() shouldBe true
        }

        test("sin fila no hay nada que revocar; una suscripción de Play no se revoca por aquí") {
            val w = ManualWorld()
            w.manual.revoke(UserSelector.ByEmail(ANA_EMAIL)).shouldBeInstanceOf<ManualPlusOutcome.NothingToRevoke>()
            w.play.purchase("tok-a", "active", owner = w.ana)
            w.verify("tok-a")
            shouldThrow<ManualPlusRefused> { w.manual.revoke(UserSelector.ByEmail(ANA_EMAIL)) }.reason shouldBe ManualPlusRefused.Reason.NOT_MANUAL
            w.plus() shouldBe true
        }
    }

    context("convivencia con Play (verify y RTDN)") {
        test("verify de una compra vieja ya vencida ('Restaurar') no pisa el Plus manual") {
            val w = ManualWorld()
            w.grant()
            w.play.purchase("tok-vieja", "expired", owner = w.ana)
            w.verify("tok-vieja").plus shouldBe true
            w.store.entitlement(w.ana)!!.isManual() shouldBe true
        }

        test("una compra real que da Plus reemplaza al manual (Play manda) y revoke-plus ya no la toca") {
            val w = ManualWorld()
            w.grant()
            w.play.purchase("tok-real", "active", owner = w.ana)
            w.verify("tok-real").plus shouldBe true
            w.store.entitlement(w.ana)!!.purchaseToken shouldBe "tok-real"
            shouldThrow<ManualPlusRefused> { w.manual.revoke(UserSelector.ByEmail(ANA_EMAIL)) }
            w.plus() shouldBe true
        }

        test("RTDN de cualquier token de Play no toca ni revive el Plus manual") {
            val w = ManualWorld()
            w.grant()
            val row = w.store.entitlement(w.ana)
            w.play.purchase("tok-x", "active", owner = w.ana)
            w.rtdn("m-1", "tok-x", RtdnTypes.SUBSCRIPTION_RENEWED) shouldBe BillingService.RtdnOutcome.IGNORED
            w.rtdn("m-2", "tok-x", RtdnTypes.SUBSCRIPTION_REVOKED) shouldBe BillingService.RtdnOutcome.IGNORED
            w.store.entitlement(w.ana) shouldBe row

            w.manual.revoke(UserSelector.ByEmail(ANA_EMAIL))
            val revoked = w.store.entitlement(w.ana)
            w.rtdn("m-3", "tok-x", RtdnTypes.SUBSCRIPTION_RENEWED)
            w.store.entitlement(w.ana) shouldBe revoked
            w.plus() shouldBe false
        }

        test("tras revocar, verify de una compra vencida no revive Plus") {
            val w = ManualWorld()
            w.grant()
            w.manual.revoke(UserSelector.ByEmail(ANA_EMAIL))
            w.play.purchase("tok-v", "expired", owner = w.ana)
            w.verify("tok-v").plus shouldBe false
            w.plus() shouldBe false
        }
    }

    test("cuotas: con Plus manual no hay límite visible y se pasa del cupo gratis; al vencer vuelve el gratis") {
        val w = ManualWorld()
        val limits = QuotaLimits()
        val quota = QuotaService(InMemoryUsageRepository(), w.entitlements, limits, w.clock)
        quota.status(w.ana).let { it.plus shouldBe false; it.simulations.limit shouldBe limits.freeWeeklySimulations }
        w.grant(days = 1)
        quota.status(w.ana).let {
            it.plus shouldBe true
            it.simulations.limit shouldBe null
            it.randomMatch.limit shouldBe null
            it.fullReport.limit shouldBe null
        }
        repeat(limits.freeWeeklySimulations + 1) { quota.consume(w.ana, QuotaKind.SIMULATION, "sim-$it") }
        w.clock.now = NOW + Duration.ofDays(2)
        quota.status(w.ana).let { it.plus shouldBe false; it.simulations.limit shouldBe limits.freeWeeklySimulations }
    }
})
