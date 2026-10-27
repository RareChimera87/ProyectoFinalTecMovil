package me.santiagoreina.parcheu.api.ai

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import me.santiagoreina.parcheu.api.platform.errors.AiBudgetExceeded
import org.slf4j.LoggerFactory

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
})
