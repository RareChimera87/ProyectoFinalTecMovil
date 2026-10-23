package me.santiagoreina.parcheu.api.simulation

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeBetween
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.santiagoreina.parcheu.api.ai.LlmRequest
import me.santiagoreina.parcheu.api.platform.errors.AiBudgetExceeded
import me.santiagoreina.parcheu.api.platform.errors.QuotaExceeded
import me.santiagoreina.parcheu.contract.ContractJson
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Progress
import me.santiagoreina.parcheu.contract.SimulationStatus
import me.santiagoreina.parcheu.contract.StartSimulation
import java.util.UUID

class SimulationEngineTest : FunSpec({

    suspend fun SimFixture.start(a: UUID, b: UUID, mode: Mode = Mode.AMISTAD): SimulationRecord =
        checkNotNull(repo.find(UUID.fromString(module.service.start(a, StartSimulation(b.toString(), mode)).id)))

    test("una ejecución completa con FakeLlm termina en DONE con puntaje 0-100 y 3 escenarios juzgados") {
        val f = SimFixture()
        val ana = f.person("ana", traits = listOf(0.2, 0.4, 0.6, 0.8, 0.5, 0.5))
        val beto = f.person("beto", traits = listOf(0.3, 0.4, 0.5, 0.7, 0.5, 0.6))
        val sim = f.start(ana, beto)

        sim.status shouldBe SimulationStatus.DONE
        sim.score!!.shouldBeBetween(0, 100)
        sim.transcript.scenarios shouldHaveSize SCENARIOS_PER_SIMULATION
        sim.transcript.scenarios.forEach { s ->
            s.verdict shouldNotBe null
            s.turns.size.shouldBeLessThanOrEqual(MAX_TURNS_PER_SCENARIO)
            s.turns.mapIndexed { i, t -> t.speaker == if (i % 2 == 0) Side.A else Side.B }.all { it } shouldBe true
        }
        // 0,35 · similitud + 0,65 · promedio del juez (80).
        val expected = SimulationScoring.score(SimulationScoring.traitSimilarity(listOf(0.2, 0.4, 0.6, 0.8, 0.5, 0.5), listOf(0.3, 0.4, 0.5, 0.7, 0.5, 0.6)), 80.0)
        sim.score shouldBe expected
        sim.report!!.reasons shouldHaveSize 3
        sim.report!!.frictions shouldHaveSize 2
        sim.report!!.dimensions.map { it.name } shouldBe listOf("humor", "planes", "valores", "comunicacion", "ritmo")
        sim.usage.tokensIn shouldNotBe 0
        // 8 turnos por escenario (el Fake nunca cierra con [FIN]) + 1 juez por escenario.
        f.fake.calls shouldBe 3 * MAX_TURNS_PER_SCENARIO + 3
        f.turnRequests().all { it.maxOutputTokens == TwinAgent.TURN_OUTPUT_CEILING } shouldBe true

        // Arista SIMULATED con la afinidad, espejo de Firestore y aviso a ambos.
        val edge = f.graphRepo.edge(ana, beto, Mode.AMISTAD)!!
        edge.state shouldBe EdgeState.SIMULATED
        edge.affinity shouldBe sim.score
        val doc = f.mirror.last(sim.id)!!
        doc.status shouldBe SimulationStatus.DONE
        doc.participants shouldBe listOf("uid-ana", "uid-beto")
        doc.score shouldBe sim.score
        f.mirror.docs.any { it.status == SimulationStatus.RUNNING && it.progress == Progress(1, 3, MAX_TURNS_PER_SCENARIO) } shouldBe true
        f.notifier.sent.map { it.userId to it.status } shouldBe listOf(ana to SimulationStatus.DONE, beto to SimulationStatus.DONE)
        f.usage.refunded(sim.id.toString()) shouldBe false
    }

    test("un agente puede cerrar con [FIN] desde el turno 4, no antes") {
        val f = SimFixture(textReply = { "Hecho, cuadramos. [FIN]" })
        val sim = f.start(f.person("ana"), f.person("beto"))
        sim.status shouldBe SimulationStatus.DONE
        sim.transcript.scenarios.forEach { s ->
            s.turns shouldHaveSize MIN_TURN_TO_END
            s.turns.none { END_MARK in it.text } shouldBe true
        }
    }

    test("si el juez falla 3 veces la simulación queda FAILED y el uso refunded = true") {
        val f = SimFixture(judgeReply = { error("el juez no responde") })
        val ana = f.person("ana")
        val sim = f.start(ana, f.person("beto"))

        sim.status shouldBe SimulationStatus.FAILED
        sim.error!! shouldContain "juez"
        f.judgeRequests() shouldHaveSize LLM_ATTEMPTS
        f.usage.refunded(sim.id.toString()) shouldBe true
        f.quota.service.status(ana).simulations.used shouldBe 0
        f.mirror.last(sim.id)!!.status shouldBe SimulationStatus.FAILED
        f.notifier.sent.map { it.status }.toSet() shouldBe setOf(SimulationStatus.FAILED)
        f.graphRepo.edge(ana, sim.userB, Mode.AMISTAD) shouldBe null
    }

    test("un JSON del juez que no cumple el esquema cuenta como fallo y se reintenta") {
        var calls = 0
        val f = SimFixture()
        f.judgeReply = { calls++; if (calls == 1) kotlinx.serialization.json.buildJsonObject { } else judgeJson(60) }
        val sim = f.start(f.person("ana"), f.person("beto"))
        sim.status shouldBe SimulationStatus.DONE
        f.judgeRequests() shouldHaveSize 4
    }

    test("reintentar la misma tarea no duplica turnos ni cobros") {
        val f = SimFixture()
        val ana = f.person("ana")
        val sim = f.start(ana, f.person("beto"))
        val calls = f.fake.calls
        f.module.engine.run(sim.id)
        f.module.engine.run(sim.id)
        f.fake.calls shouldBe calls
        f.repo.find(sim.id)!!.transcript shouldBe sim.transcript
        f.usage.charges shouldBe listOf(sim.id.toString())
        // Cloud Tasks deduplica por nombre: volver a encolar la misma tarea no crea otra.
        f.queue.tasks.map { it.name } shouldBe listOf("simulate-${sim.id}")
    }

    test("si la tarea se cae a mitad de camino, el reintento retoma sin repetir turnos guardados") {
        val inner = InMemorySimulationRepository()
        var failAt = 5
        val flaky = object : SimulationRepository by inner {
            override suspend fun saveStep(
                id: UUID,
                expectedSteps: Int,
                transcript: StoredTranscript,
                progress: Progress,
                usage: UsageTotals,
            ): Boolean {
                if (expectedSteps == failAt) {
                    failAt = -1
                    error("Cloud SQL no responde")
                }
                return inner.saveStep(id, expectedSteps, transcript, progress, usage)
            }
        }
        val f = SimFixture(repo = inner)
        val module = inMemorySimulationModule(
            me.santiagoreina.parcheu.api.FIXED_CLOCK, f.ai, f.quota, f.graph, flaky, runInline = false,
        )
        val ana = f.person("ana")
        val beto = f.person("beto")
        val summary = module.service.start(ana, StartSimulation(beto.toString(), Mode.AMISTAD))
        val id = UUID.fromString(summary.id)

        shouldThrow<IllegalStateException> { module.engine.run(id) }
        val partial = inner.find(id)!!
        partial.status shouldBe SimulationStatus.RUNNING
        partial.transcript.steps shouldBe 5

        module.engine.run(id)
        val done = inner.find(id)!!
        done.status shouldBe SimulationStatus.DONE
        done.transcript.scenarios.take(1).single().turns.take(5) shouldBe partial.transcript.scenarios.first().turns
        done.transcript.steps shouldBe 3 * (MAX_TURNS_PER_SCENARIO + 1)
        // 27 llamadas útiles + 1 turno que se generó y no alcanzó a guardarse.
        f.fake.calls shouldBe 3 * (MAX_TURNS_PER_SCENARIO + 1) + 1
        f.usage.charges shouldBe listOf(id.toString())
    }

    test("dos entregas que guardan el mismo paso no duplican turnos") {
        val f = SimFixture()
        val module = inMemorySimulationModule(me.santiagoreina.parcheu.api.FIXED_CLOCK, f.ai, f.quota, f.graph, f.repo, runInline = false)
        val ana = f.person("ana")
        val id = UUID.fromString(module.service.start(ana, StartSimulation(f.person("beto").toString(), Mode.AMISTAD)).id)
        f.repo.markRunning(id) shouldBe true
        val base = f.repo.find(id)!!
        val one = base.transcript.copy(scenarios = base.transcript.scenarios.toMutableList().also { it[0] = it[0].copy(turns = listOf(StoredTurn(Side.A, "hola"))) })
        f.repo.saveStep(id, 0, one, Progress(0, 1, 8), UsageTotals()) shouldBe true
        f.repo.saveStep(id, 0, one, Progress(0, 1, 8), UsageTotals()) shouldBe false
        f.repo.find(id)!!.transcript.steps shouldBe 1
    }

    test("un acierto de caché devuelve el resultado sin llamar al LLM (contador del Fake = 0)") {
        val first = SimFixture()
        val ana = first.person("ana")
        val beto = first.person("beto")
        val original = first.start(ana, beto)
        original.status shouldBe SimulationStatus.DONE

        // Mismo mundo (repositorio, cuotas y grafo) con un Fake nuevo: la segunda simulación, pedida
        // por el otro lado del par, sale de la caché.
        val second = SimFixture(repo = first.repo, usage = first.usage, graphRepo = first.graphRepo)
        val copy = second.start(beto, ana)
        second.fake.calls shouldBe 0
        copy.status shouldBe SimulationStatus.DONE
        copy.cachedFrom shouldBe original.id
        copy.score shouldBe original.score
        copy.report shouldBe original.report
        // El par va al revés: A y B se intercambian en la transcripción copiada.
        copy.transcript shouldBe original.transcript.swapped()
        second.usage.charges shouldBe listOf(original.id.toString(), copy.id.toString())
    }

    test("la caché no aplica si cambió la versión de un gemelo o pasaron más de 7 días") {
        val f = SimFixture()
        val ana = f.person("ana")
        val beto = f.person("beto")
        f.start(ana, beto)
        val calls = f.fake.calls
        f.person("beto", version = 2)
        f.start(ana, beto).cachedFrom shouldBe null
        f.fake.calls shouldBe calls * 2
        f.repo.findCached(ana, 1, beto, 2, Mode.AMISTAD, me.santiagoreina.parcheu.api.FIXED_CLOCK.instant().plusSeconds(1), UUID.randomUUID()) shouldBe null
    }

    test("el prompt del gemelo A no contiene la persona ni las memorias de B (y viceversa)") {
        val f = SimFixture()
        val ana = f.person("ana", persona = "PERSONA-ANA le encanta el café y madruga.")
        val beto = f.person("beto", persona = "PERSONA-BETO trasnocha y odia madrugar.")
        f.repo.putMemory(ana, TwinMemory(Mode.AMISTAD, "MEMORIA-ANA va al gimnasio los lunes"))
        f.repo.putMemory(beto, TwinMemory(Mode.AMISTAD, "MEMORIA-BETO toca guitarra en un parche"))
        f.start(ana, beto).status shouldBe SimulationStatus.DONE

        val turns = f.turnRequests()
        val ofAna = turns.filter { "PERSONA-ANA" in it.system!! }
        val ofBeto = turns.filter { "PERSONA-BETO" in it.system!! }
        ofAna.size + ofBeto.size shouldBe turns.size
        ofAna.size shouldBe 3 * MAX_TURNS_PER_SCENARIO / 2
        fun LlmRequest.all() = system + "\n" + prompt
        ofAna.forEach {
            it.all() shouldContain "MEMORIA-ANA"
            it.all() shouldNotContain "PERSONA-BETO"
            it.all() shouldNotContain "MEMORIA-BETO"
        }
        ofBeto.forEach {
            it.all() shouldContain "MEMORIA-BETO"
            it.all() shouldNotContain "PERSONA-ANA"
            it.all() shouldNotContain "MEMORIA-ANA"
        }
        // El juez solo ve el escenario y la conversación.
        f.judgeRequests().forEach {
            it.all() shouldNotContain "PERSONA-"
            it.all() shouldNotContain "MEMORIA-"
        }
    }

    test("memorias por modo: en Amistad el prompt solo trae las de Amistad y ninguna de Pareja llega al transcript ni al informe") {
        // El Fake repite las memorias que ve en su prompt: lo que no entra al prompt no puede aparecer.
        val f = SimFixture(
            textReply = { r -> r.system.orEmpty().lines().filter { it.startsWith("- ") }.joinToString(" ").ifBlank { "Dale, de una." } },
            judgeReply = { r -> judgeJson().let { j -> kotlinx.serialization.json.JsonObject(j + ("resumen" to kotlinx.serialization.json.JsonPrimitive(r.prompt.take(300)))) } },
        )
        val ana = f.person("ana")
        val beto = f.person("beto")
        f.repo.putMemory(ana, TwinMemory(Mode.PAREJA, "PAREJA-SECRETO terminó con alguien hace un mes"))
        f.repo.putMemory(ana, TwinMemory(Mode.AMISTAD, "AMISTAD-DATO juega microfútbol los jueves"))
        f.repo.putMemory(ana, TwinMemory(Mode.ESTUDIO, "ESTUDIO-DATO estudia mejor de madrugada"))
        f.repo.putMemory(ana, TwinMemory(Mode.AMISTAD, "AMISTAD-PENDIENTE nunca aceptada"), accepted = false)

        val sim = f.start(ana, beto, Mode.AMISTAD)
        sim.status shouldBe SimulationStatus.DONE
        val prompts = f.requests.joinToString("\n") { it.system + "\n" + it.prompt }
        prompts shouldContain "AMISTAD-DATO"
        prompts shouldNotContain "PAREJA-SECRETO"
        prompts shouldNotContain "ESTUDIO-DATO"
        prompts shouldNotContain "AMISTAD-PENDIENTE"

        val saved = f.repo.find(sim.id)!!
        val transcriptJson = ContractJson.encodeToString(StoredTranscript.serializer(), saved.transcript)
        val reportJson = ContractJson.encodeToString(StoredReport.serializer(), saved.report!!)
        transcriptJson shouldContain "AMISTAD-DATO"
        transcriptJson shouldNotContain "PAREJA-SECRETO"
        reportJson shouldNotContain "PAREJA-SECRETO"
        transcriptJson shouldNotContain "ESTUDIO-DATO"
    }

    test("un usuario gratis solo recibe escenarios básicos; en Pareja el prompt incluye las reglas de contenido") {
        val f = SimFixture()
        val ana = f.person("ana")
        val beto = f.person("beto")
        val basic = DefaultScenarios.all.filter { it.isBasic }.map { it.id }.toSet()
        val sim = f.start(ana, beto, Mode.PAREJA)
        sim.status shouldBe SimulationStatus.DONE
        sim.transcript.scenarios.map { it.scenarioId }.all { it in basic } shouldBe true
        sim.transcript.scenarios.map { it.scenarioId }.toSet() shouldBe DefaultScenarios.all.filter { it.mode == Mode.PAREJA && it.isBasic }.map { it.id }.toSet()
        f.turnRequests().forEach { it.system!! shouldContain TwinPromptBuilder.PAREJA_CONTENT_RULES }

        // Amistad no lleva las reglas de Pareja.
        val g = SimFixture()
        g.start(g.person("ana"), g.person("beto"), Mode.AMISTAD)
        g.turnRequests().forEach { it.system!! shouldNotContain TwinPromptBuilder.PAREJA_CONTENT_RULES }
    }

    test("con ParcheU+ los escenarios salen de todo el catálogo del modo") {
        val f = SimFixture()
        val ana = f.person("ana")
        f.plus(ana)
        val premium = DefaultScenarios.all.filter { !it.isBasic && it.mode == Mode.ESTUDIO }.map { it.id }.toSet()
        // Una persona distinta cada vez para que ninguna salga de la caché.
        val seen = (1..12).flatMap { n -> f.start(ana, f.person("p$n"), Mode.ESTUDIO).transcript.scenarios.map { it.scenarioId } }.toSet()
        seen.any { it in premium } shouldBe true
    }

    test("con el presupuesto agotado se responde AI_BUDGET y no se consume cupo") {
        val f = SimFixture()
        val ana = f.person("ana")
        val beto = f.person("beto")
        f.exhaustBudget()
        shouldThrow<AiBudgetExceeded> { f.module.service.start(ana, StartSimulation(beto.toString(), Mode.AMISTAD)) }
        f.usage.charges.shouldBeEmpty()
        f.repo.all().shouldBeEmpty()
        f.fake.calls shouldBe 0
        f.quota.service.status(ana).simulations.used shouldBe 0
    }

    test("si el presupuesto se agota a mitad de la simulación, queda FAILED y se devuelve el cupo") {
        val f = SimFixture()
        var turns = 0
        f.textReply = { turns++; if (turns == 3) f.exhaustBudget(); "Va, me sirve." }
        val sim = f.start(f.person("ana"), f.person("beto"))
        sim.status shouldBe SimulationStatus.FAILED
        sim.error!! shouldContain "Presupuesto"
        f.usage.refunded(sim.id.toString()) shouldBe true
    }

    test("la 6.a simulación gratis de la semana da QUOTA_EXCEEDED sin crear la simulación") {
        val f = SimFixture()
        val ana = f.person("ana")
        val others = (1..6).map { f.person("p$it") }
        others.take(5).forEach { f.start(ana, it) }
        shouldThrow<QuotaExceeded> { f.module.service.start(ana, StartSimulation(others[5].toString(), Mode.AMISTAD)) }
        f.repo.all() shouldHaveSize 5
    }

    // M1 (revisión ronda 1): la tarea repite las validaciones del POST antes de gastar tokens.
    suspend fun SimFixture.queued(a: UUID, b: UUID, mode: Mode = Mode.AMISTAD): Pair<SimulationModule, UUID> {
        val module = inMemorySimulationModule(
            me.santiagoreina.parcheu.api.FIXED_CLOCK, ai, quota, graph, repo, mirror = mirror, notifier = notifier, runInline = false,
        )
        return module to UUID.fromString(module.service.start(a, StartSimulation(b.toString(), mode)).id)
    }

    suspend fun SimFixture.shouldHaveFailedSilently(id: UUID, a: UUID, b: UUID, mode: Mode) {
        val sim = repo.find(id)!!
        sim.status shouldBe SimulationStatus.FAILED
        sim.transcript.steps shouldBe 0
        sim.report shouldBe null
        fake.calls shouldBe 0
        usage.refunded(id.toString()) shouldBe true
        graphRepo.edge(a, b, mode) shouldBe null
        notifier.sent.shouldBeEmpty()
    }

    test("si B bloquea a A mientras la simulación está en QUEUED, la tarea falla sin LLM, sin arista ni avisos y devuelve el cupo") {
        val f = SimFixture()
        val ana = f.person("ana")
        val beto = f.person("beto")
        val (module, id) = f.queued(ana, beto)
        f.repo.find(id)!!.status shouldBe SimulationStatus.QUEUED
        f.repo.block(beto, ana)
        module.engine.run(id)
        f.shouldHaveFailedSilently(id, ana, beto, Mode.AMISTAD)
        f.mirror.last(id)!!.status shouldBe SimulationStatus.FAILED
    }

    test("si una cuenta se borra mientras está en QUEUED, la tarea no corre ni escribe en Firestore") {
        val f = SimFixture()
        val ana = f.person("ana")
        val beto = f.person("beto")
        val (module, id) = f.queued(ana, beto)
        f.repo.putParticipant(f.repo.participant(beto)!!.copy(deleted = true))
        module.engine.run(id)
        f.shouldHaveFailedSilently(id, ana, beto, Mode.AMISTAD)
        f.mirror.docs.none { it.status != SimulationStatus.QUEUED } shouldBe true
    }

    test("si B apaga el modo o revoca Pareja, la tarea falla sin mandar su persona a Vertex") {
        val f = SimFixture()
        val ana = f.person("ana")
        val beto = f.person("beto")
        val (module, id) = f.queued(ana, beto, Mode.PAREJA)
        f.repo.putParticipant(f.repo.participant(beto)!!.copy(parejaConfirmed = false))
        module.engine.run(id)
        f.shouldHaveFailedSilently(id, ana, beto, Mode.PAREJA)
        f.mirror.last(id)!!.status shouldBe SimulationStatus.FAILED

        val (module2, id2) = f.queued(ana, f.person("carla"))
        f.repo.putParticipant(f.repo.participant(UUID.nameUUIDFromBytes("sim:carla".toByteArray()))!!.copy(activeModes = setOf(Mode.ESTUDIO)))
        module2.engine.run(id2)
        f.repo.find(id2)!!.status shouldBe SimulationStatus.FAILED
        f.fake.calls shouldBe 0
    }

    test("si una cuenta se borra durante un turno, ese turno no se guarda ni se publica en Firestore (E8.3)") {
        val f = SimFixture()
        val ana = f.person("ana")
        val beto = f.person("beto")
        val betoDeleted = f.repo.participant(beto)!!.copy(deleted = true)
        var turns = 0
        f.textReply = {
            turns++
            if (turns == 3) f.repo.putParticipant(betoDeleted)
            "Va, me sirve."
        }
        val sim = f.start(ana, beto)
        sim.status shouldBe SimulationStatus.FAILED
        f.usage.refunded(sim.id.toString()) shouldBe true
        // Solo los dos turnos anteriores al borrado quedan guardados; el tercero no llega a Postgres.
        sim.transcript.scenarios.first().turns shouldHaveSize 2
        // Ni el turno 3 ni el FAILED se publican: no se recrea `simulations/{id}` con el uid borrado.
        f.mirror.docs.filter { it.id == sim.id }.none { it.progress.turn >= 3 } shouldBe true
        f.mirror.last(sim.id)!!.status shouldBe SimulationStatus.RUNNING
        f.graphRepo.edge(ana, beto, Mode.AMISTAD) shouldBe null
        f.notifier.sent.shouldBeEmpty()
    }

    test("si el bloqueo llega a mitad de camino, la simulación se detiene antes de la arista y del aviso") {
        val f = SimFixture()
        val ana = f.person("ana")
        val beto = f.person("beto")
        var turns = 0
        f.textReply = { turns++; if (turns == 3) f.repo.block(beto, ana); "Va, me sirve." }
        val sim = f.start(ana, beto)
        sim.status shouldBe SimulationStatus.FAILED
        f.usage.refunded(sim.id.toString()) shouldBe true
        f.graphRepo.edge(ana, beto, Mode.AMISTAD) shouldBe null
        f.notifier.sent.shouldBeEmpty()
        f.mirror.last(sim.id)!!.status shouldBe SimulationStatus.FAILED
    }
})
