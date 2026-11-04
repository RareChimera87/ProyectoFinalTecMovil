package me.santiagoreina.parcheu.api.demo

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import me.santiagoreina.parcheu.api.demo.DemoVisibility.withoutDisabledDemos
import me.santiagoreina.parcheu.api.platform.config.Env
import me.santiagoreina.parcheu.contract.Mode
import java.time.Duration
import java.time.LocalDate
import java.time.Period
import java.util.UUID

/** Reglas puras de E4.9: perfiles, configuración, decisión determinista y filtro de la bandera. */
class DemoRulesTest : FunSpec({
    fun migration(name: String): String =
        checkNotNull(javaClass.classLoader.getResource("db/migration/$name")) { "Falta $name" }.readText()

    test("20 perfiles demo con UID único y prefijo demo-, solo Amistad y Estudio, nunca Pareja") {
        val all = DemoProfiles.all
        all shouldHaveSize 20
        all.map { it.firebaseUid }.toSet() shouldHaveSize 20
        all.forEach { it.firebaseUid.startsWith(DemoProfile.UID_PREFIX) shouldBe true }
        DemoProfiles.MODES shouldContainExactly setOf(Mode.AMISTAD, Mode.ESTUDIO)
        DemoProfiles.MODES shouldNotContain Mode.PAREJA
        all.forEach { it.queueMode shouldBeIn DemoProfiles.MODES }
        // Mitad y mitad en la fila.
        all.count { it.queueMode == Mode.AMISTAD } shouldBe 10
    }

    test("los perfiles demo se declaran como demo y usan intereses del catálogo, 1 a 5, y edad adulta") {
        val catalog = Regex("""\('([a-z_]+)',\s*'""").findAll(migration("V2__semillas.sql").substringAfter("insert into interests").substringBefore(";"))
            .map { it.groupValues[1] }.toSet()
        catalog.size shouldBe 25
        val today = LocalDate.of(2026, 10, 23)
        DemoProfiles.all.forEach { p ->
            p.name.endsWith("(demo)") shouldBe true
            // Igual que PersonaBuilder: sin nombre ni carrera en lo que lee el agente de la simulación.
            p.persona.contains(p.name.removeSuffix(" (demo)")) shouldBe false
            p.persona.contains(p.career) shouldBe false
            p.interests.size shouldBeIn (1..5).toList()
            p.interests.forEach { it shouldBeIn catalog }
            (Period.between(p.birthDate, today).years >= 18) shouldBe true
            p.answers.length shouldBe DemoProfiles.QUESTIONS
        }
    }

    test("DemoSettings: la bandera sigue a DEMO_USERS_ENABLED (true en local y dev, false en prod) y valida los números") {
        DemoSettings.fromEnv(Env.DEV, emptyMap()).enabled shouldBe true
        DemoSettings.fromEnv(Env.LOCAL, emptyMap()).enabled shouldBe true
        DemoSettings.fromEnv(Env.PROD, emptyMap()).enabled shouldBe false
        DemoSettings.fromEnv(Env.PROD, mapOf("DEMO_USERS_ENABLED" to "true")).enabled shouldBe true
        DemoSettings.fromEnv(Env.DEV, mapOf("DEMO_USERS_ENABLED" to "false")).enabled shouldBe false

        val custom = DemoSettings.fromEnv(Env.DEV, mapOf("DEMO_CONNECT_PROBABILITY" to "0,25", "DEMO_TICKET_DAYS" to "3"))
        custom.connectProbability shouldBe 0.25
        custom.ticketTtl shouldBe Duration.ofDays(3)
        DemoSettings.fromEnv(Env.DEV, emptyMap()).connectProbability shouldBe DemoSettings.DEFAULT_CONNECT_PROBABILITY

        shouldThrow<IllegalStateException> { DemoSettings.fromEnv(Env.DEV, mapOf("DEMO_CONNECT_PROBABILITY" to "1.5")) }
        shouldThrow<IllegalStateException> { DemoSettings.fromEnv(Env.DEV, mapOf("DEMO_CONNECT_PROBABILITY" to "mucho")) }
        shouldThrow<IllegalStateException> { DemoSettings.fromEnv(Env.DEV, mapOf("DEMO_TICKET_DAYS" to "0")) }
    }

    test("DemoConnectPolicy es determinista por simulación y respeta la probabilidad") {
        val ids = (1..10_000).map { UUID.nameUUIDFromBytes("sim-$it".toByteArray()) }
        ids.none { DemoConnectPolicy(0.0).wantsToConnect(it) } shouldBe true
        ids.all { DemoConnectPolicy(1.0).wantsToConnect(it) } shouldBe true
        val policy = DemoConnectPolicy(0.7)
        val share = ids.count { policy.wantsToConnect(it) }.toDouble() / ids.size
        share shouldBe (0.7 plusOrMinus 0.03)
        ids.take(100).forEach { policy.wantsToConnect(it) shouldBe policy.wantsToConnect(it) }
        shouldThrow<IllegalArgumentException> { DemoConnectPolicy(-0.1) }
    }

    test("con la bandera apagada un demo no se propone (sugerencias y presentaciones usan este filtro)") {
        data class Candidate(val name: String, val isDemo: Boolean)
        val people = listOf(Candidate("ana", false), Candidate("demo-01", true))
        people.withoutDisabledDemos(demoUsersEnabled = false) { it.isDemo }.map { it.name } shouldContainExactly listOf("ana")
        people.withoutDisabledDemos(demoUsersEnabled = true) { it.isDemo } shouldBe people
        DemoVisibility.proposable(isDemo = true, demoUsersEnabled = false) shouldBe false
        DemoVisibility.proposable(isDemo = false, demoUsersEnabled = false) shouldBe true
    }

    test("el traspaso a E4.7 quedó cerrado: el backlog marca el criterio y los docs ya no hablan de esqueleto") {
        // El filtro lo consume HomeSuggestions (HomeApiTest lo prueba con DEMO_USERS_ENABLED=false).
        val docs = generateSequence(java.io.File("").absoluteFile) { it.parentFile }
            .map { java.io.File(it, "docs") }
            .first { java.io.File(it, "backlog.md").isFile }
        val e47 = java.io.File(docs, "backlog.md").readText()
            .substringAfter("#### E4.7 · Pantalla de inicio").substringBefore("#### E4.8")
        e47.contains("- [x] Test: con `DEMO_USERS_ENABLED=false`") shouldBe true
        val e49 = java.io.File(docs, "pendientes-humanos.md").readText()
            .substringAfter("## E4.9 · Usuarios semilla de demo")
        e49.contains("todavía es un esqueleto") shouldBe false
    }
})
