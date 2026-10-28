package me.santiagoreina.parcheu.api.premium

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.testApplication
import me.santiagoreina.parcheu.api.startApp

/**
 * Test de contrato parametrizado (E7.4) sobre todos los endpoints de [PremiumEndpoints]: en un mundo
 * donde cada superficie premium tiene contenido, un usuario gratis no recibe ninguno de los campos
 * ocultos y un usuario ParcheU+ recibe todos (la sonda no puede pasar en vacío). La tabla de la
 * auditoría (endpoint → campos ocultos) se imprime al correr la prueba.
 */
class PremiumGatingContractTest : FunSpec({
    suspend fun exposedTo(plus: Boolean, endpoint: PremiumEndpoint): Set<String> {
        val world = PremiumWorld(plus)
        var exposed: Set<String> = emptySet()
        testApplication {
            val client = startApp(world.container())
            exposed = PremiumProbe(world, client).let { endpoint.probe(it) }
        }
        return exposed
    }

    PremiumEndpoints.all.forEach { endpoint ->
        test("${endpoint.endpoint}: gratis no recibe ${endpoint.hides.joinToString()}") {
            withClue("campos premium que recibió un usuario gratis") { exposedTo(plus = false, endpoint).shouldBeEmpty() }
        }

        test("${endpoint.endpoint}: ParcheU+ sí los recibe (la sonda no pasa en vacío)") {
            exposedTo(plus = true, endpoint) shouldBe endpoint.hides
        }
    }

    test("informe de la auditoría: endpoint → campos ocultos para gratis") {
        val table = buildString {
            appendLine("| Endpoint | Oculto para gratis | Qué recibe gratis |")
            appendLine("|---|---|---|")
            PremiumEndpoints.all.forEach { e ->
                appendLine("| `${e.endpoint}` | ${e.hides.joinToString { "`$it`" }} | ${e.freeGets} |")
            }
        }
        println(table)
        table.lines().count { it.startsWith("| `") } shouldBe PremiumEndpoints.all.size
    }
})
