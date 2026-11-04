package me.santiagoreina.parcheu.api.premium

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import me.santiagoreina.parcheu.api.platform.Premium
import me.santiagoreina.parcheu.api.premium.fixtures.UnlistedPremiumFake
import kotlin.io.path.isDirectory

/**
 * Test de arquitectura (E7.4): toda función de main marcada con `@Premium(endpoint)` debe tener su
 * entrada en [PremiumEndpoints] (con su sonda gratis/ParcheU+), y viceversa. Así un endpoint premium
 * nuevo no puede quedar fuera de la auditoría sin que falle el build.
 */
class PremiumEndpointsRegistryTest : FunSpec({
    val mainRoot = PremiumScanner.classesRootOf(Premium::class.java)
    val mainMarks = PremiumScanner.marksIn(mainRoot)

    test("la raíz de clases de main es un directorio y tiene funciones @Premium (si no, el escaneo pasaría en vacío)") {
        withClue("raíz de clases main: $mainRoot") { mainRoot.isDirectory() shouldBe true }
        mainMarks.shouldNotBeEmpty()
    }

    test("todo endpoint @Premium de main está en PremiumEndpoints") {
        PremiumScanner.unlisted(mainMarks, PremiumEndpoints.endpoints).shouldBeEmpty()
    }

    test("toda entrada de PremiumEndpoints corresponde a una función @Premium de main") {
        PremiumScanner.unmarked(mainMarks, PremiumEndpoints.endpoints).shouldBeEmpty()
    }

    test("los endpoints de PremiumEndpoints son únicos y declaran qué ocultan") {
        val names = PremiumEndpoints.all.map { it.endpoint }
        names.size shouldBe names.toSet().size
        PremiumEndpoints.all.filter { it.hides.isEmpty() || it.freeGets.isBlank() }.map { it.endpoint }.shouldBeEmpty()
    }

    test("el chequeo falla con un @Premium sin listar (clase de prueba) y deja de fallar al listarlo") {
        val testRoot = PremiumScanner.classesRootOf(UnlistedPremiumFake::class.java)
        val fake = PremiumScanner.marksIn(testRoot).filter { it.owner == UnlistedPremiumFake::class.java.name }
        fake.map { it.endpoint } shouldBe listOf("GET /v1/fake-premium")
        PremiumScanner.unlisted(fake, PremiumEndpoints.endpoints).map { it.endpoint } shouldContain "GET /v1/fake-premium"
        PremiumScanner.unlisted(fake, PremiumEndpoints.endpoints + "GET /v1/fake-premium").shouldBeEmpty()
    }

    test("la clase de prueba no contamina el chequeo de main") {
        mainMarks.filter { it.owner == UnlistedPremiumFake::class.java.name }.shouldBeEmpty()
    }
})
