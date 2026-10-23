package me.santiagoreina.parcheu.api.social.invites

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.runBlocking
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.graph.InMemoryGraphRepository
import me.santiagoreina.parcheu.api.graph.PersonRow
import me.santiagoreina.parcheu.api.graph.inMemoryGraphModule
import me.santiagoreina.parcheu.api.platform.config.AppConfig
import me.santiagoreina.parcheu.api.platform.config.Env
import me.santiagoreina.parcheu.api.platform.notifier.NoopNotifier
import me.santiagoreina.parcheu.contract.Visibility
import java.util.UUID
import kotlin.random.Random

/** Formato `^[A-Z]{4}-[0-9]{4}$`, unicidad y enlace de los códigos de E6.1. */
class InviteCodesTest : FunSpec({
    val format = "^[A-Z]{4}-[0-9]{4}$"

    test("todo código generado cumple ^[A-Z]{4}-[0-9]{4}$, con o sin nombre") {
        val random = Random(7)
        val names = listOf(null, "", "Mariana Gómez", "Ñoño", "Jo", "Émile-Zoë", "李小龙", "1234", "Ana María")
        repeat(2_000) { i ->
            val code = InviteCodes.generate(random, names[i % names.size], fullyRandom = i % 3 == 0)
            code shouldMatch format
            InviteCodes.isValid(code) shouldBe true
        }
    }

    test("las letras salen del nombre sin tildes (como MARI-4827 del mockup)") {
        InviteCodes.generate(Random(1), "Mariana Gómez") shouldStartWith "MARI-"
        InviteCodes.generate(Random(1), "Éver") shouldStartWith "EVER-"
        InviteCodes.generate(Random(1), "Ñoño") shouldStartWith "NONO-"
    }

    test("isValid rechaza formatos cercanos y normalize limpia lo que escribe la persona") {
        listOf("ABCD1234", "ABC-1234", "ABCD-123", "abcd-1234", "ABCD-12345", "AB1D-1234", " ABCD-1234").forEach {
            InviteCodes.isValid(it) shouldBe false
        }
        InviteCodes.normalize(" abcd-1234 \n") shouldBe "ABCD-1234"
    }

    test("cada persona recibe un código distinto aunque el generador choque, y siempre el mismo") {
        val graphRepo = InMemoryGraphRepository()
        val graph = inMemoryGraphModule(FIXED_CLOCK, graphRepo)
        val repo = InMemoryInviteRepository()
        // Un generador con pocas salidas posibles fuerza choques: el servicio debe reintentar.
        val collisions = object : Random() {
            private val inner = Random(3)
            override fun nextBits(bitCount: Int): Int = inner.nextBits(bitCount)
            override fun nextInt(until: Int): Int = inner.nextInt(minOf(until, 3))
        }
        val service = InviteService(
            repo, graphRepo, graph.edges, graph.anonIds, { null }, NoopNotifier, InviteLinks(InviteLinks.LOCAL_BASE), FIXED_CLOCK, collisions,
        )
        val people = List(20) { UUID.randomUUID() }
        people.forEach { graphRepo.addPerson(PersonRow(it, "Ana", null, "UIS", Visibility.NAME, false, null)) }
        val codes = runBlocking { people.map { service.codeFor(it) } }
        codes.forEach { it shouldMatch format }
        codes.toSet() shouldHaveSize people.size
        runBlocking { people.map { service.codeFor(it) } } shouldBe codes
    }

    test("sin códigos libres falla en vez de repetir uno") {
        val graphRepo = InMemoryGraphRepository()
        val graph = inMemoryGraphModule(FIXED_CLOCK, graphRepo)
        val constant = object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
        }
        val service = InviteService(
            InMemoryInviteRepository(), graphRepo, graph.edges, graph.anonIds, { null }, NoopNotifier,
            InviteLinks(InviteLinks.LOCAL_BASE), FIXED_CLOCK, constant,
        )
        runBlocking { service.codeFor(UUID.randomUUID()) } shouldBe "AAAA-0000"
        shouldThrow<IllegalStateException> { runBlocking { service.codeFor(UUID.randomUUID()) } }
    }

    test("el enlace es https://<hosting>/i/{codigo}: INVITE_LINK_BASE, sitio del proyecto o el de dev en local") {
        val base = AppConfig(Env.DEV, 0, "v", "https://x", "sa@x.iam.gserviceaccount.com", "parcheu-dev", null)
        InviteLinks.fromConfig(base, emptyMap()).linkFor("ABCD-1234") shouldBe "https://parcheu-dev.web.app/i/ABCD-1234"
        InviteLinks.fromConfig(base.copy(env = Env.PROD, firebaseProjectId = "parcheu-prod"), emptyMap())
            .linkFor("ABCD-1234") shouldBe "https://parcheu-prod.web.app/i/ABCD-1234"
        InviteLinks.fromConfig(base, mapOf("INVITE_LINK_BASE" to "https://parche.example/")).linkFor("ABCD-1234") shouldBe
            "https://parche.example/i/ABCD-1234"
        InviteLinks.fromConfig(base.copy(env = Env.LOCAL, firebaseProjectId = null), emptyMap()).linkFor("ABCD-1234") shouldBe
            "https://parcheu-dev.web.app/i/ABCD-1234"
        shouldThrow<IllegalArgumentException> { InviteLinks("http://inseguro.example") }
    }
})
