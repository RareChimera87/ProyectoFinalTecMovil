package me.santiagoreina.parcheu.api.billing

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.runBlocking
import me.santiagoreina.parcheu.api.identity.ExposedIdentityRepository
import me.santiagoreina.parcheu.api.platform.config.Env
import me.santiagoreina.parcheu.api.platform.db.TestDatabase
import me.santiagoreina.parcheu.api.platform.db.connectExposed
import me.santiagoreina.parcheu.api.platform.db.runMigrations
import me.santiagoreina.parcheu.api.platform.db.tables.Entitlements
import me.santiagoreina.parcheu.api.platform.db.tables.PurchaseEvents
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.api.quota.MovableClock
import me.santiagoreina.parcheu.api.quota.QuotaLimits
import me.santiagoreina.parcheu.api.quota.quotaModule
import me.santiagoreina.parcheu.contract.EntitlementState
import me.santiagoreina.parcheu.contract.VerifyPurchase
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * Plus manual contra Postgres 16: búsqueda real en `users`, la fila de `entitlements` (con su `unique` y su CHECK)
 * y la auditoría en `purchase_events`. Necesita Docker o `PARCHEU_TEST_DB_URL` (se omite con `-PsinContenedores`).
 */
class ManualPlusDbTest : FunSpec({
    val now = Instant.parse("2026-10-25T12:00:00Z")

    class Db(val db: Database, val ana: UUID, val clock: MovableClock, val service: ManualPlusService, val play: FakePlayApi, val billing: BillingService)

    fun withDb(block: suspend (Db) -> Unit) = TestDatabase.withCleanDatabase { ds ->
        runMigrations(ds)
        val db = connectExposed(ds)
        runBlocking {
            val repo = ExposedIdentityRepository(db)
            val ana = repo.createIfAbsent("fb-ana", "ana@example.com", "Ana", now).id
            listOf("demo", "borrado", "susp").forEach { repo.createIfAbsent("fb-$it", "$it@example.com", it, now) }
            transaction(db) {
                Users.update({ Users.firebaseUid eq "fb-demo" }) { it[isDemo] = true }
                Users.update({ Users.firebaseUid eq "fb-borrado" }) { it[deletedAt] = now.atOffset(ZoneOffset.UTC) }
                Users.update({ Users.firebaseUid eq "fb-susp" }) { it[suspendedUntil] = now.plusSeconds(3600).atOffset(ZoneOffset.UTC) }
            }
            val clock = MovableClock(now)
            val store = ExposedBillingStore(db)
            val play = FakePlayApi()
            block(Db(db, ana, clock, ManualPlusService(store, ExposedManualPlusUsers(db), clock), play, BillingService(store, play.client, clock)))
        }
    }

    fun req(email: String, days: Int = 7) = ManualPlusJob.Request(UserSelector.ByEmail(email), days)

    test("Postgres: concede por correo, cuenta como Plus en el cupo de prod, es idempotente y se audita sin el correo")
        .config(enabled = TestDatabase.enabled) {
            withDb { w ->
                ManualPlusJob.execute(w.service, req("ANA@example.com")) shouldBe ManualPlusJob.EXIT_OK
                ManualPlusJob.execute(w.service, req("ana@example.com")) shouldBe ManualPlusJob.EXIT_OK
                val rows = transaction(w.db) { Entitlements.selectAll().toList() }
                rows.size shouldBe 1
                rows.single().let {
                    it[Entitlements.productId] shouldBe MANUAL_GRANT_PRODUCT
                    it[Entitlements.state] shouldBe EntitlementState.ACTIVE
                    it[Entitlements.expiresAt]!!.toInstant() shouldBe now + Duration.ofDays(7)
                }
                val prod = quotaModule(Env.PROD, w.db, w.clock, QuotaLimits())
                prod.service.isPlus(w.ana) shouldBe true

                val events = transaction(w.db) { PurchaseEvents.selectAll().map { it[PurchaseEvents.userId] to it[PurchaseEvents.payload].toString() } }
                events.size shouldBe 2
                events.forEach { (user, payload) -> user shouldBe w.ana; payload shouldNotContain "@" }

                // Vence solo.
                w.clock.now = now + Duration.ofDays(7)
                quotaModule(Env.PROD, w.db, w.clock, QuotaLimits()).service.isPlus(w.ana) shouldBe false
            }
        }

    test("Postgres: por uid, y rechaza inexistente, demo, borrado y suspendido (código 3) sin escribir")
        .config(enabled = TestDatabase.enabled) {
            withDb { w ->
                listOf("nadie@example.com", "demo@example.com", "borrado@example.com", "susp@example.com", "ana@example").forEach {
                    ManualPlusJob.execute(w.service, req(it)) shouldBe ManualPlusJob.EXIT_REFUSED
                }
                transaction(w.db) { Entitlements.selectAll().count() + PurchaseEvents.selectAll().count() } shouldBe 0L
                w.service.grant(UserSelector.ByFirebaseUid("fb-ana"), 1).userId shouldBe w.ana
            }
        }

    test("Postgres: revoke-plus retira, Play vigente no se pisa y una RTDN no revive el manual")
        .config(enabled = TestDatabase.enabled) {
            withDb { w ->
                w.service.grant(UserSelector.ByEmail("ana@example.com"), 7)
                w.play.purchase("tok-x", "active", owner = w.ana)
                w.billing.handleRtdn(decodeRtdn(rtdnBody("m-1", "tok-x", RtdnTypes.SUBSCRIPTION_RENEWED))) shouldBe BillingService.RtdnOutcome.IGNORED
                ManualPlusJob.execute(w.service, ManualPlusJob.Request(UserSelector.ByEmail("ana@example.com"), 0)) shouldBe ManualPlusJob.EXIT_OK
                StoredEntitlementService(ExposedBillingStore(w.db)).isPlus(w.ana, w.clock.instant()) shouldBe false
                w.billing.handleRtdn(decodeRtdn(rtdnBody("m-2", "tok-x", RtdnTypes.SUBSCRIPTION_RENEWED)))
                StoredEntitlementService(ExposedBillingStore(w.db)).isPlus(w.ana, w.clock.instant()) shouldBe false

                // Compra real: reemplaza la fila; después el manual se niega a pisarla.
                w.billing.verify(w.ana, VerifyPurchase("tok-x", BillingService.PLUS_PRODUCT_ID)).plus shouldBe true
                shouldThrow<ManualPlusRefused> { w.service.grant(UserSelector.ByEmail("ana@example.com"), 7) }.reason shouldBe
                    ManualPlusRefused.Reason.PLAY_ACTIVE
                transaction(w.db) { Entitlements.selectAll().single()[Entitlements.purchaseToken] } shouldBe "tok-x"
            }
        }
})
