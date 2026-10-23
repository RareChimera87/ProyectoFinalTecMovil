package me.santiagoreina.parcheu.api.billing

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.identity.ExposedIdentityRepository
import me.santiagoreina.parcheu.api.platform.config.Env
import me.santiagoreina.parcheu.api.platform.db.TestDatabase
import me.santiagoreina.parcheu.api.platform.db.connectExposed
import me.santiagoreina.parcheu.api.platform.db.runMigrations
import me.santiagoreina.parcheu.api.platform.db.tables.Entitlements
import me.santiagoreina.parcheu.api.platform.db.tables.PurchaseEvents
import me.santiagoreina.parcheu.api.platform.errors.Forbidden
import me.santiagoreina.parcheu.api.quota.QuotaLimits
import me.santiagoreina.parcheu.api.quota.quotaModule
import me.santiagoreina.parcheu.contract.EntitlementState
import me.santiagoreina.parcheu.contract.VerifyPurchase
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/**
 * Billing contra Postgres 16 (`entitlements` y `purchase_events` reales, con su `unique` y sus CHECK).
 * Necesita Docker o `PARCHEU_TEST_DB_URL`: con `-PsinContenedores` se omite (ver [TestDatabase]).
 */
class BillingDbTest : FunSpec({
    class Db(val db: Database, val ana: UUID, val beto: UUID, val play: FakePlayApi, val service: BillingService, val store: ExposedBillingStore)

    fun withBilling(block: suspend (Db) -> Unit) = TestDatabase.withCleanDatabase { ds ->
        runMigrations(ds)
        val db = connectExposed(ds)
        runBlocking {
            val repo = ExposedIdentityRepository(db)
            val ana = repo.createIfAbsent("ana", "ana@example.com", "Ana", FIXED_CLOCK.instant()).id
            val beto = repo.createIfAbsent("beto", "beto@example.com", "Beto", FIXED_CLOCK.instant()).id
            val play = FakePlayApi()
            val store = ExposedBillingStore(db)
            block(Db(db, ana, beto, play, BillingService(store, play.client, FIXED_CLOCK), store))
        }
    }

    fun Db.verify(user: UUID, token: String) = service.verify(user, VerifyPurchase(token, BillingService.PLUS_PRODUCT_ID))

    test("Postgres: verify guarda el derecho y en prod el cupo lo lee (EntitlementService real, sin grant-plus)")
        .config(enabled = TestDatabase.enabled) {
            withBilling { w ->
                w.play.purchase("tok-1", "active", owner = w.ana)
                w.verify(w.ana, "tok-1").plus shouldBe true
                w.play.acknowledgeCalls("tok-1") shouldBe 1

                val row = transaction(w.db) { Entitlements.selectAll().single() }
                row[Entitlements.userId] shouldBe w.ana
                row[Entitlements.state] shouldBe EntitlementState.ACTIVE
                row[Entitlements.basePlan] shouldBe "mensual"
                row[Entitlements.autoRenewing] shouldBe true

                val prod = quotaModule(Env.PROD, w.db, FIXED_CLOCK, QuotaLimits())
                prod.devPlus.shouldBeNull()
                prod.service.isPlus(w.ana) shouldBe true
                prod.service.isPlus(w.beto) shouldBe false
                (quotaModule(Env.DEV, w.db, FIXED_CLOCK, QuotaLimits()).devPlus != null) shouldBe true
            }
        }

    test("Postgres: el mismo token desde otra cuenta → 403 y la fila de la primera no cambia")
        .config(enabled = TestDatabase.enabled) {
            withBilling { w ->
                w.play.purchase("tok-ana", "active", owner = w.ana)
                w.verify(w.ana, "tok-ana")
                val antes = w.store.entitlement(w.ana)
                shouldThrow<Forbidden> { w.verify(w.beto, "tok-ana") }
                w.store.entitlement(w.ana) shouldBe antes
                w.store.entitlement(w.beto).shouldBeNull()
                transaction(w.db) { Entitlements.selectAll().count() } shouldBe 1L
            }
        }

    test("Postgres: la misma RTDN dos veces se procesa una vez; REVOKED queda guardado y sin Plus")
        .config(enabled = TestDatabase.enabled) {
            withBilling { w ->
                w.play.purchase("tok-r", "active-anual-acknowledged", owner = w.ana)
                w.verify(w.ana, "tok-r").plus shouldBe true
                val gets = w.play.getCalls("tok-r")
                val rtdn = decodeRtdn(rtdnBody("msg-1", "tok-r", RtdnTypes.SUBSCRIPTION_REVOKED))

                w.service.handleRtdn(rtdn) shouldBe BillingService.RtdnOutcome.PROCESSED
                w.service.handleRtdn(rtdn) shouldBe BillingService.RtdnOutcome.DUPLICATE

                w.play.getCalls("tok-r") shouldBe gets + 1
                val event = transaction(w.db) { PurchaseEvents.selectAll().single() }
                event[PurchaseEvents.messageId] shouldBe "msg-1"
                event[PurchaseEvents.purchaseToken] shouldBe "tok-r"
                event[PurchaseEvents.payload].jsonObject["subscriptionNotification"]!!.jsonObject["notificationType"] shouldBe
                    JsonPrimitive(RtdnTypes.SUBSCRIPTION_REVOKED)
                w.store.entitlement(w.ana)!!.state shouldBe EntitlementState.REVOKED
                StoredEntitlementService(w.store).isPlus(w.ana, FIXED_CLOCK.instant()) shouldBe false
            }
        }

    test("Postgres: claimEvent es atómico (10 entregas simultáneas, un solo reclamo) y releaseEvent lo libera")
        .config(enabled = TestDatabase.enabled) {
            withBilling { w ->
                val payload = decodeRtdn(rtdnBody("msg-c", "tok", 4)).raw
                val claims = coroutineScope {
                    (0 until 10).map { async(Dispatchers.IO) { w.store.claimEvent("msg-c", "tok", payload, FIXED_CLOCK.instant()) } }
                        .awaitAll()
                }
                claims.count { it } shouldBe 1
                w.store.releaseEvent("msg-c")
                w.store.claimEvent("msg-c", "tok", payload, FIXED_CLOCK.instant()) shouldBe true
            }
        }

    test("Postgres: grant-plus escribe la fila dev-grant y un verify real la reemplaza")
        .config(enabled = TestDatabase.enabled) {
            withBilling { w ->
                val entitlements = StoredEntitlementService(w.store)
                entitlements.grantPlus(w.ana, 7, FIXED_CLOCK.instant())
                entitlements.isPlus(w.ana, FIXED_CLOCK.instant()) shouldBe true
                w.store.entitlement(w.ana)!!.productId shouldBe "dev-grant"
                w.play.purchase("tok-real", "active", owner = w.ana)
                w.verify(w.ana, "tok-real").plus shouldBe true
                w.store.entitlement(w.ana)!!.purchaseToken shouldBe "tok-real"
            }
        }
})
