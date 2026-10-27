package me.santiagoreina.parcheu.api.ai

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import me.santiagoreina.parcheu.api.platform.errors.AiBudgetExceeded
import me.santiagoreina.parcheu.api.simulation.SimFixture
import me.santiagoreina.parcheu.api.simulation.SimulationEngine
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.StartSimulation
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * E10.5: los logs que alimentan las métricas basadas en logs de Terraform (monitoring.tf) mantienen su
 * `message` y sus campos; si alguien los renombra, las alertas dejarían de dispararse sin avisar.
 */
class ObservabilityLogsTest : FunSpec({
    fun <T> capture(loggerClass: Class<*>, block: suspend () -> T): List<ILoggingEvent> {
        val logger = LoggerFactory.getLogger(loggerClass) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            kotlinx.coroutines.runBlocking { runCatching { block() } }
        } finally {
            logger.detachAppender(appender)
        }
        return appender.list.toList()
    }

    fun ILoggingEvent.fields(): Map<String, Any?> = keyValuePairs.orEmpty().associate { it.key to it.value }

    test("AiBudgetExceeded deja el log con el evento que usa la alerta") {
        val usage = InMemoryAiUsageStore().apply { seedSpent(AI_DAY, 10_000_000L) }
        val gateway = testGateway(FakeLlmClient(), usage, testAiConfig())
        val events = capture(DailyBudgetGuard::class.java) {
            shouldThrow<AiBudgetExceeded> { gateway.text(AiCall(AiPurpose.PERSONA, "hola")) }
        }.filter { it.formattedMessage == "AiBudgetExceeded" }
        events shouldHaveSize 1
        events.single().fields()["event"] shouldBe "AiBudgetExceeded"
    }

    test("cada llamada de IA registra ai_call con su costo en micro-USD") {
        val usage = InMemoryAiUsageStore()
        val gateway = testGateway(FakeLlmClient(), usage, testAiConfig())
        val events = capture(AiGateway::class.java) {
            gateway.text(AiCall(AiPurpose.PERSONA, "hola"))
        }.filter { it.formattedMessage == "ai_call" }
        events shouldHaveSize 1
        events.single().fields()["costMicros"] shouldBe usage.entries.single().costMicros
    }

    // Los logs de simulación alimentan la alerta simulaciones-failed y el panel de simulaciones.
    fun List<ILoggingEvent>.named(message: String) = filter { it.formattedMessage == message }

    test("una simulación que termina bien registra simulation_done con su id") {
        val f = SimFixture()
        val ana = f.person("ana")
        val beto = f.person("beto")
        val events = capture(SimulationEngine::class.java) {
            f.module.service.start(ana, StartSimulation(beto.toString(), Mode.AMISTAD))
        }.named("simulation_done")
        events shouldHaveSize 1
        val fields = events.single().fields()
        fields["event"] shouldBe "simulation_done"
        fields["simulationId"] shouldBe f.repo.all().single().id.toString()
    }

    test("un fallo del LLM registra simulation_failed con cause ERROR") {
        val f = SimFixture(judgeReply = { error("el juez no responde") })
        val ana = f.person("ana")
        val beto = f.person("beto")
        val events = capture(SimulationEngine::class.java) {
            f.module.service.start(ana, StartSimulation(beto.toString(), Mode.AMISTAD))
        }
        events.named("simulation_done") shouldHaveSize 0
        val failed = events.named("simulation_failed")
        failed shouldHaveSize 1
        val fields = failed.single().fields()
        fields["event"] shouldBe "simulation_failed"
        fields["cause"] shouldBe "ERROR"
        fields["simulationId"] shouldBe f.repo.all().single().id.toString()
    }

    test("un bloqueo registra simulation_failed con cause INELIGIBLE") {
        val f = SimFixture(inline = false)
        val ana = f.person("ana")
        val beto = f.person("beto")
        val id = UUID.fromString(f.module.service.start(ana, StartSimulation(beto.toString(), Mode.AMISTAD)).id)
        f.repo.block(beto, ana)
        val failed = capture(SimulationEngine::class.java) { f.module.engine.run(id) }.named("simulation_failed")
        failed shouldHaveSize 1
        val fields = failed.single().fields()
        fields["event"] shouldBe "simulation_failed"
        fields["cause"] shouldBe "INELIGIBLE"
        fields["simulationId"] shouldBe id.toString()
    }
})
