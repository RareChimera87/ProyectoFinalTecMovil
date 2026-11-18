package me.santiagoreina.parcheu.api.premium

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.testApplication
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.billing.InMemoryBillingStore
import me.santiagoreina.parcheu.api.billing.ManualPlusService
import me.santiagoreina.parcheu.api.billing.ManualPlusUser
import me.santiagoreina.parcheu.api.billing.StoredEntitlementService
import me.santiagoreina.parcheu.api.billing.UserSelector
import me.santiagoreina.parcheu.api.startApp
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset

/**
 * La activación manual (job `grant-plus`, enmienda de ADR-0013) desbloquea lo mismo que una compra: el mundo de la
 * auditoría E7.4 con el Plus de ana en `entitlements` (fila `manual-grant` leída por [StoredEntitlementService], el
 * mismo `EntitlementService` de prod). Con el Plus manual ya vencido (concedido hace 8 días por 7) cada endpoint
 * vuelve a lo gratis sin que nadie lo revoque.
 */
class ManualPlusPremiumTest : FunSpec({
    /** Mundo con el Plus de ana concedido por el job hace [daysAgo] días, por 7 días. */
    fun manualWorld(daysAgo: Long): PremiumWorld {
        val store = InMemoryBillingStore()
        val grantedAt = Clock.fixed(FIXED_CLOCK.instant() - Duration.ofDays(daysAgo), ZoneOffset.UTC)
        return PremiumWorld(plus = true, entitlements = StoredEntitlementService(store)) { ana ->
            ManualPlusService(store, { listOf(ManualPlusUser(ana, isDemo = false, deleted = false, suspendedUntil = null)) }, grantedAt)
                .grant(UserSelector.ByFirebaseUid("ana"), 7)
        }
    }

    suspend fun exposed(world: PremiumWorld, endpoint: PremiumEndpoint): Set<String> {
        var result: Set<String> = emptySet()
        testApplication { result = endpoint.probe(PremiumProbe(world, startApp(world.container()))) }
        return result
    }

    PremiumEndpoints.all.forEach { endpoint ->
        test("${endpoint.endpoint}: con Plus manual vigente recibe ${endpoint.hides.joinToString()}") {
            exposed(manualWorld(daysAgo = 0), endpoint) shouldBe endpoint.hides
        }

        test("${endpoint.endpoint}: con el Plus manual vencido vuelve a gratis") {
            withClue("campos premium tras vencer el Plus manual") { exposed(manualWorld(daysAgo = 8), endpoint).shouldBeEmpty() }
        }
    }

    // `GET /v1/quota` resuelve el usuario con su propio lookup en memoria; aquí se mira el mismo servicio de cupos
    // que usan los endpoints de arriba (y `QuotaApiTest` cubre la ruta).
    test("cupos: sin límite visible con Plus manual y con límites al vencer") {
        listOf(0L to true, 8L to false).forEach { (daysAgo, plus) ->
            val world = manualWorld(daysAgo)
            val quota = world.sim.quota.service.status(world.id("ana"))
            quota.plus shouldBe plus
            listOf(quota.simulations, quota.randomMatch, quota.fullReport).forEach { (it.limit == null) shouldBe plus }
        }
    }
})
