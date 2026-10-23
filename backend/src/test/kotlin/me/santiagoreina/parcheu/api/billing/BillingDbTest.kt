package me.santiagoreina.parcheu.api.billing

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
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
import me.santiagoreina.parcheu.contract.EntitlementDto
import me.santiagoreina.parcheu.contract.EntitlementState
import me.santiagoreina.parcheu.contract.VerifyPurchase
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

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

    test("Postgres: claimEvent es atómico (10 entregas simultáneas, un solo reclamo) y un rollback lo deshace")
        .config(enabled = TestDatabase.enabled) {
            withBilling { w ->
                val payload = decodeRtdn(rtdnBody("msg-c", "tok", 4)).raw
                val claims = coroutineScope {
                    (0 until 10).map { async(Dispatchers.IO) { w.store.claimEvent("msg-c", "tok", payload, FIXED_CLOCK.instant()) } }
                        .awaitAll()
                }
                claims.count { it } shouldBe 1
                shouldThrow<IllegalStateException> {
                    w.store.atomically {
                        w.store.claimEvent("msg-d", "tok", payload, FIXED_CLOCK.instant()) shouldBe true
                        error("la instancia muere a mitad")
                    }
                }
                w.store.claimEvent("msg-d", "tok", payload, FIXED_CLOCK.instant()) shouldBe true
            }
        }

    test("Postgres: si la base falla al guardar la renovación, el reclamo se deshace y el reintento la procesa")
        .config(enabled = TestDatabase.enabled) {
            withBilling { w ->
                w.play.purchase("tok-ren", "active", owner = w.ana)
                w.verify(w.ana, "tok-ren")
                val antes = w.store.entitlement(w.ana)!!
                w.play.update("tok-ren", "active-anual-acknowledged", owner = w.ana)
                val flaky = FlakyBillingStore(w.store)
                val service = BillingService(flaky, w.play.client, FIXED_CLOCK)
                val rtdn = decodeRtdn(rtdnBody("msg-ren", "tok-ren", RtdnTypes.SUBSCRIPTION_RENEWED))
                flaky.failSaves = true

                shouldThrow<IllegalStateException> { service.handleRtdn(rtdn) }

                transaction(w.db) { PurchaseEvents.selectAll().count() } shouldBe 0
                w.store.entitlement(w.ana) shouldBe antes
                flaky.failSaves = false
                service.handleRtdn(rtdn) shouldBe BillingService.RtdnOutcome.PROCESSED
                w.store.entitlement(w.ana)!!.expiresAt shouldNotBe antes.expiresAt
                transaction(w.db) { PurchaseEvents.selectAll().count() } shouldBe 1
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

    test("Postgres: carrera verify/revocación — la RTDN REVOKED mantiene el for update, verify espera y el resultado es REVOKED sin Plus (B3)")
        .config(enabled = TestDatabase.enabled) {
            withBilling { w ->
                w.play.purchase("tok-race", "active-anual-acknowledged", owner = w.ana)
                w.verify(w.ana, "tok-race").plus shouldBe true // Play seguirá mostrando ACTIVE (periodo pagado)

                val locked = CountDownLatch(1)
                val release = CountDownLatch(1)
                // La RTDN toma el bloqueo (`select ... for update` real) y se detiene antes de guardar.
                val holding = object : BillingStore by w.store {
                    override fun entitlementByToken(purchaseToken: String, forUpdate: Boolean): EntitlementRow? =
                        w.store.entitlementByToken(purchaseToken, forUpdate).also {
                            if (forUpdate) {
                                locked.countDown()
                                check(release.await(20, TimeUnit.SECONDS)) { "la prueba no liberó el bloqueo" }
                            }
                        }
                }
                val pool = Executors.newFixedThreadPool(2)
                try {
                    val rtdn = pool.submit<BillingService.RtdnOutcome> {
                        BillingService(holding, w.play.client, FIXED_CLOCK)
                            .handleRtdn(decodeRtdn(rtdnBody("msg-race", "tok-race", RtdnTypes.SUBSCRIPTION_REVOKED)))
                    }
                    check(locked.await(20, TimeUnit.SECONDS)) { "la RTDN no tomó el bloqueo" }
                    val verify = pool.submit<EntitlementDto> { w.verify(w.ana, "tok-race") }

                    // Mientras la RTDN conserva el bloqueo, verify no puede terminar (espera en su propio for update).
                    shouldThrow<TimeoutException> { verify.get(1, TimeUnit.SECONDS) }
                    release.countDown()

                    rtdn.get(20, TimeUnit.SECONDS) shouldBe BillingService.RtdnOutcome.PROCESSED
                    val dto = verify.get(20, TimeUnit.SECONDS)
                    dto.state shouldBe EntitlementState.REVOKED
                    dto.plus shouldBe false
                } finally {
                    release.countDown()
                    pool.shutdownNow()
                }
                w.store.entitlement(w.ana)!!.state shouldBe EntitlementState.REVOKED
                StoredEntitlementService(w.store).isPlus(w.ana, FIXED_CLOCK.instant()) shouldBe false
            }
        }

    test("Postgres: un token reembolsado no vuelve a dar Plus aunque la fila haya pasado a otra compra (B1)")
        .config(enabled = TestDatabase.enabled) {
            withBilling { w ->
                w.play.purchase("t1", "active-anual-acknowledged", owner = w.ana)
                w.play.purchase("t0", "active", owner = w.ana)
                w.verify(w.ana, "t1").plus shouldBe true
                w.service.handleRtdn(decodeRtdn(rtdnBody("msg-rev", "t1", RtdnTypes.SUBSCRIPTION_REVOKED)))
                w.store.tokenRevoked("t1") shouldBe true
                w.store.tokenRevoked("t0") shouldBe false
                w.verify(w.ana, "t0").plus shouldBe true
                w.store.entitlement(w.ana)!!.purchaseToken shouldBe "t0"

                // Play aún muestra t1 ACTIVE: re-verificarlo no pisa la vigente...
                w.verify(w.ana, "t1").plus shouldBe true
                w.store.entitlement(w.ana)!!.purchaseToken shouldBe "t0"
                // ...y sin otra vigente queda REVOKED, no Plus.
                w.play.update("t0", "expired", owner = w.ana)
                w.service.handleRtdn(decodeRtdn(rtdnBody("msg-exp", "t0", 13)))
                w.verify(w.ana, "t1").state shouldBe EntitlementState.REVOKED
                StoredEntitlementService(w.store).isPlus(w.ana, FIXED_CLOCK.instant()) shouldBe false
            }
        }

    test("Postgres: la RTDN liga el evento a su dueño y, si la cuenta ya fue borrada (lápida), lo guarda redactado y sin token (B4)")
        .config(enabled = TestDatabase.enabled) {
            withBilling { w ->
                w.play.purchase("tok-viva", "active-anual-acknowledged", owner = w.ana)
                w.verify(w.ana, "tok-viva")
                w.service.handleRtdn(decodeRtdn(rtdnBody("msg-viva", "tok-viva", RtdnTypes.SUBSCRIPTION_RENEWED)))
                transaction(w.db) { PurchaseEvents.selectAll().single() }.let {
                    it[PurchaseEvents.userId] shouldBe w.ana
                    it[PurchaseEvents.purchaseToken] shouldBe "tok-viva"
                }
                w.store.isTombstone(w.ana) shouldBe false

                val lapida = ExposedIdentityRepository(w.db).createIfAbsent("deleted:abc123", null, null, FIXED_CLOCK.instant()).id
                w.store.isTombstone(lapida) shouldBe true
                w.play.purchase("tok-lapida", "active-anual-acknowledged", owner = lapida)
                w.store.save(
                    EntitlementRow(lapida, "parcheu_plus", "anual", "tok-lapida", EntitlementState.ACTIVE, null, true, FIXED_CLOCK.instant()),
                )
                w.service.handleRtdn(decodeRtdn(rtdnBody("msg-lapida", "tok-lapida", RtdnTypes.SUBSCRIPTION_RENEWED)))
                transaction(w.db) { PurchaseEvents.selectAll().where { PurchaseEvents.messageId eq "msg-lapida" }.single() }.let {
                    it[PurchaseEvents.purchaseToken].shouldBeNull()
                    it[PurchaseEvents.userId].shouldBeNull()
                    it[PurchaseEvents.payload] shouldBe
                        Json.parseToJsonElement("""{"redacted":true,"eventTimeMillis":"1793102400000","notificationType":2}""")
                }
            }
        }
})
