package me.santiagoreina.parcheu.api.privacy

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.assertions.withClue
import me.santiagoreina.parcheu.api.graph.AnonIdSigner
import me.santiagoreina.parcheu.api.graph.ViewContext
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Visibility
import me.santiagoreina.parcheu.api.graph.person
import me.santiagoreina.parcheu.contract.EdgeState
import java.time.Instant
import kotlin.random.Random

/**
 * Suite de invariantes de privacidad (E3.5, ADR-0012). Para cada sonda (las de referencia y una por
 * cada vista de `ThirdPartyViews`) genera ≥ 1.000 mundos al azar y, para cada observador, comprueba
 * que lo expuesto no rompe ninguna regla de [PrivacyInvariants].
 */
class PrivacyInvariantsTest : FunSpec({

    ThirdPartyViewProbes.all().forEach { probe ->
        test("invariantes de privacidad: ${probe.name}") {
            val cases = PrivacyProperties.check(probe.name, PrivacyWorld.arb()) { world ->
                world.observers().forEach { viewer ->
                    val exposure = probe.expose(world, viewer)
                    val problems = PrivacyInvariants.violations(world, viewer, exposure, probe.context)
                    withClue("observador ${world.people.indexOfFirst { it.id == viewer }} en $world") {
                        problems.shouldBeEmpty()
                    }
                }
            }
            cases shouldBeGreaterThanOrEqual PrivacyProperties.ITERATIONS
        }
    }

    test("los mundos generados cubren Pareja ajena, HIDDEN, bloqueos y borrados") {
        val worlds = List(200) { PrivacyWorld.generate(Random(it)) }
        worlds.count { w -> w.edges.any { it.mode == Mode.PAREJA } && w.people.size >= 3 } shouldBeGreaterThanOrEqual 100
        worlds.count { w -> w.people.any { it.visibility == Visibility.HIDDEN } } shouldBeGreaterThanOrEqual 100
        worlds.count { w -> w.blocks.isNotEmpty() } shouldBeGreaterThanOrEqual 100
        worlds.count { w -> w.people.any { it.deletedAt != null } } shouldBeGreaterThanOrEqual 100
    }

    test("el verificador detecta cada tipo de fuga (control de la suite)") {
        val people = listOf(
            person(0, visibility = Visibility.NAME),
            person(1, visibility = Visibility.HIDDEN),
            person(2, visibility = Visibility.NAME, deletedAt = Instant.EPOCH),
            person(3, visibility = Visibility.NAME),
        )
        val world = PrivacyWorld(
            people = people,
            edges = listOf(WorldEdge(people[1].id, people[3].id, Mode.PAREJA, EdgeState.CONNECTED, 90)),
            blocks = setOf(people[3].id to people[0].id),
        )
        val viewer = world.people[0].id
        val anon = AnonIdSigner.forTests()
        fun pid(i: Int) = anon.anonId(viewer, world.people[i].id)

        val leaks = PrivacyInvariants.violations(
            world,
            viewer,
            Exposure(listOf(pid(1), pid(2), pid(3), "a_desconocido"), listOf(ExposedEdge(pid(1), pid(3), Mode.PAREJA))),
            ViewContext.THIRD_PARTY,
        )
        leaks.joinToString("\n").let { text ->
            text shouldContain "HIDDEN"
            text shouldContain "borrado"
            text shouldContain "bloqueado"
            text shouldContain "arista de Pareja"
            text shouldContain "no corresponde"
        }
        PrivacyInvariants.violations(world, viewer, Exposure(listOf(world.people[1].id.toString()), emptyList()), ViewContext.EGO)
            .shouldContain("uuid real de ${world.people[1].id} expuesto sin conexión mutua")
        PrivacyInvariants.violations(world, viewer, Exposure(listOf(pid(1)), emptyList()), ViewContext.EGO) shouldBe emptyList()
    }
})
