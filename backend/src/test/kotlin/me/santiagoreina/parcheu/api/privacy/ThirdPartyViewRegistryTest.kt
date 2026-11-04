package me.santiagoreina.parcheu.api.privacy

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import me.santiagoreina.parcheu.api.graph.ThirdPartyView
import me.santiagoreina.parcheu.api.graph.ThirdPartyViews
import me.santiagoreina.parcheu.api.privacy.fixtures.UnregisteredFakeView

/**
 * Test de arquitectura (ADR-0012): toda clase de `main` que implementa [ThirdPartyView] debe estar
 * en [ThirdPartyViews.registered] y tener su sonda en [ThirdPartyViewProbes.byView], para que la
 * suite de invariantes la recorra.
 */
class ThirdPartyViewRegistryTest : FunSpec({
    val mainRoot = ThirdPartyViewScanner.classesRootOf(ThirdPartyView::class.java)
    val mainViews = ThirdPartyViewScanner.implementationsIn(mainRoot)

    test("toda clase ThirdPartyView de main está registrada en ThirdPartyViews") {
        val missing = ThirdPartyViewScanner.missingRegistrations(mainViews, ThirdPartyViews.registered)
        missing.map { it.name }.shouldBeEmpty()
    }

    test("toda vista registrada tiene su sonda en la suite de invariantes") {
        val withoutProbe = ThirdPartyViews.registered.filter { it !in ThirdPartyViewProbes.byView.keys }
        withoutProbe.map { it.qualifiedName }.shouldBeEmpty()
    }

    test("los nombres de las vistas registradas son únicos") {
        val names = ThirdPartyViews.registered.map { it.simpleName }
        names.size shouldBe names.toSet().size
    }

    test("el chequeo falla con una clase ThirdPartyView sin registrar (clase de prueba)") {
        val testRoot = ThirdPartyViewScanner.classesRootOf(UnregisteredFakeView::class.java)
        val found = ThirdPartyViewScanner.implementationsIn(testRoot)
        found shouldContain UnregisteredFakeView::class.java

        val missing = ThirdPartyViewScanner.missingRegistrations(found, ThirdPartyViews.registered)
        missing shouldContain UnregisteredFakeView::class.java

        // Registrada, deja de faltar.
        ThirdPartyViewScanner.missingRegistrations(found, ThirdPartyViews.registered + UnregisteredFakeView::class)
            .shouldNotContain(UnregisteredFakeView::class.java)
    }

    test("la clase de prueba no contamina el chequeo de main") {
        mainViews shouldNotContain UnregisteredFakeView::class.java
    }
})
