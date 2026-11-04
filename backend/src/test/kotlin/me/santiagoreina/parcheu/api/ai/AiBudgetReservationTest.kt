package me.santiagoreina.parcheu.api.ai

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import me.santiagoreina.parcheu.api.platform.errors.AiBudgetExceeded
import me.santiagoreina.parcheu.api.platform.errors.UserAiBudgetExceeded
import me.santiagoreina.parcheu.contract.ErrorCode
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Cliente que tarda [delayMs] en responder (para que las llamadas se solapen) o que falla con [failure]. */
class SlowLlmClient(
    private val inner: FakeLlmClient = FakeLlmClient(textResponder = { "a".repeat(400) }),
    private val delayMs: Long = 30,
    var failure: (() -> Throwable)? = null,
    var hang: CompletableDeferred<Unit>? = null,
) : LlmClient {
    override val provider = inner.provider
    val calls: Int get() = inner.calls

    override suspend fun generateText(request: LlmRequest): LlmTextResult {
        hang?.let { it.complete(Unit); awaitCancellation() }
        delay(delayMs)
        failure?.let { throw it() }
        return inner.generateText(request)
    }

    override suspend fun generateJson(request: LlmRequest, schema: JsonObject): LlmJsonResult {
        delay(delayMs)
        failure?.let { throw it() }
        return inner.generateJson(request, schema)
    }
}

/** Store que anota el mayor gasto (registrado + reservas) que dejó cada reserva, para comprobar que nunca pasa el tope. */
class PeakObservingStore(val inner: AiUsageStore) : AiUsageStore by inner {
    val peakGlobal = AtomicLong()
    val peakUser = AtomicLong()

    override suspend fun hold(day: LocalDate, userId: UUID?, decide: (BudgetSnapshot) -> Long): BudgetHold =
        inner.hold(day, userId) { snapshot ->
            decide(snapshot).also { micros ->
                peakGlobal.accumulateAndGet(snapshot.globalMicros + micros, ::maxOf)
                snapshot.userMicros?.let { peakUser.accumulateAndGet(it + micros, ::maxOf) }
            }
        }
}

/** B-M1: reserva previa del presupuesto, techo diario por persona y costo de las llamadas que fallan. */
class AiBudgetReservationTest : FunSpec({
    val prompt = "x".repeat(300)
    val ana = UUID.fromString("00000000-0000-0000-0000-00000000000a")
    val bruno = UUID.fromString("00000000-0000-0000-0000-00000000000b")

    fun turn(user: UUID? = null) = AiCall(AiPurpose.SIMULATION_TURN, prompt, userId = user, maxOutputTokens = 200)

    test("con 40 llamadas simultáneas el gasto reservado y el registrado nunca pasan el tope diario") {
        // Presupuesto para unas 8 reservas: sin reserva previa las 40 leerían el mismo gasto y pasarían todas.
        val config = testAiConfig()
        val estimate = config.prices.costMicros(config.models.chat, AI_DAY, turn().ceilingUsage())
        val budgetMicros = estimate * 8
        val store = PeakObservingStore(InMemoryAiUsageStore())
        val client = SlowLlmClient()
        val gateway = AiGateway(client, testAiConfig(mapOf("AI_DAILY_BUDGET_USD" to usd(budgetMicros))), store, AI_CLOCK)
        gateway.guard.budgetMicros shouldBe budgetMicros

        val rejected = AtomicInteger()
        withContext(Dispatchers.Default) {
            (1..40).map {
                async {
                    try {
                        gateway.text(turn())
                    } catch (e: AiBudgetExceeded) {
                        rejected.incrementAndGet()
                    }
                }
            }.awaitAll()
        }

        store.peakGlobal.get() shouldBeLessThanOrEqual budgetMicros
        store.spentMicros(AI_DAY) shouldBeLessThanOrEqual budgetMicros
        (store.inner as InMemoryAiUsageStore).heldMicros(AI_DAY) shouldBe 0L
        client.calls shouldBeGreaterThan 0
        rejected.get() shouldBeGreaterThan 0
        (client.calls + rejected.get()) shouldBe 40
    }

    test("una persona en su techo diario recibe UserAiBudgetExceeded (503 AI_BUDGET) y las demás siguen") {
        val usage = InMemoryAiUsageStore()
        val client = SlowLlmClient(delayMs = 0)
        val gateway = AiGateway(client, testAiConfig(mapOf("AI_USER_DAILY_BUDGET_USD" to "0.01")), usage, AI_CLOCK)
        usage.seedUserSpent(AI_DAY, ana, 10_000)

        val e = shouldThrow<UserAiBudgetExceeded> { gateway.text(turn(ana)) }
        e.shouldBeInstanceOf<AiBudgetExceeded>()
        e.code shouldBe ErrorCode.AI_BUDGET
        e.retryAt?.epochSeconds shouldBe AI_DAY.plusDays(1).atStartOfDay(me.santiagoreina.parcheu.api.platform.BogotaZone).toEpochSecond()
        client.calls shouldBe 0

        gateway.text(turn(bruno)).text shouldBe "a".repeat(400)
        // Sin persona (p. ej. nombres de comunidades) solo cuenta el total.
        gateway.text(turn(null))
        client.calls shouldBe 2
        usage.heldMicros(AI_DAY) shouldBe 0L
    }

    test("la moderación no cuenta para el techo personal: se modera aunque el autor esté en su techo, y solo suma al total") {
        val usage = InMemoryAiUsageStore()
        val client = SlowLlmClient(delayMs = 0)
        val gateway = AiGateway(client, testAiConfig(mapOf("AI_USER_DAILY_BUDGET_USD" to "0.01")), usage, AI_CLOCK)
        usage.seedUserSpent(AI_DAY, ana, 10_000)
        shouldThrow<UserAiBudgetExceeded> { gateway.text(turn(ana)) }

        gateway.json(AiCall(AiPurpose.MODERATION, prompt, userId = ana, maxOutputTokens = 512), JUDGE_SCHEMA)
        client.calls shouldBe 1
        val entry = usage.entries.single()
        entry.userId shouldBe ana
        // Queda en ai_usage con la persona, pero su gasto para el techo general no cambia.
        usage.userSpentMicros(AI_DAY, ana) shouldBe 10_000L
        usage.userSpentMicros(AI_DAY, ana, AiPurpose.MODERATION) shouldBe entry.costMicros
        usage.spentMicros(AI_DAY) shouldBe 10_000L + entry.costMicros
        usage.heldMicros(AI_DAY) shouldBe 0L
    }

    test("sub-techo de moderación por autor: AI_USER_DAILY_MODERATION_USD, 0,25 por defecto") {
        val usage = InMemoryAiUsageStore()
        val gateway = AiGateway(SlowLlmClient(delayMs = 0), testAiConfig(mapOf("AI_USER_DAILY_MODERATION_USD" to "0.000001")), usage, AI_CLOCK)
        gateway.moderationBudgetLeft(ana) shouldBe true
        gateway.json(AiCall(AiPurpose.MODERATION, prompt, userId = ana), JUDGE_SCHEMA)
        gateway.moderationBudgetLeft(ana) shouldBe false
        gateway.moderationBudgetLeft(bruno) shouldBe true
        testAiConfig().userDailyModerationUsd.toPlainString() shouldBe "0.25"
        shouldThrow<IllegalArgumentException> { testAiConfig(mapOf("AI_USER_DAILY_MODERATION_USD" to "0")) }
    }

    test("la llamada que no cabe en lo que le queda a la persona se rechaza antes de llamar") {
        val usage = InMemoryAiUsageStore()
        val client = SlowLlmClient(delayMs = 0)
        val config = testAiConfig(mapOf("AI_USER_DAILY_BUDGET_USD" to "0.01"))
        val gateway = AiGateway(client, config, usage, AI_CLOCK)
        val estimate = config.prices.costMicros(config.models.chat, AI_DAY, turn().ceilingUsage())
        usage.seedUserSpent(AI_DAY, ana, 10_000 - estimate + 1)
        shouldThrow<UserAiBudgetExceeded> { gateway.text(turn(ana)) }
        client.calls shouldBe 0
    }

    test("las llamadas simultáneas de una persona no pasan su techo, y no frenan a otra persona") {
        val config = testAiConfig()
        val estimate = config.prices.costMicros(config.models.chat, AI_DAY, turn().ceilingUsage())
        val userBudget = estimate * 5
        val store = PeakObservingStore(InMemoryAiUsageStore())
        val client = SlowLlmClient()
        val gateway = AiGateway(client, testAiConfig(mapOf("AI_USER_DAILY_BUDGET_USD" to usd(userBudget))), store, AI_CLOCK)

        val anaRejected = AtomicInteger()
        val brunoOk = AtomicInteger()
        withContext(Dispatchers.Default) {
            val anaCalls = (1..30).map {
                async {
                    try {
                        gateway.text(turn(ana))
                    } catch (e: UserAiBudgetExceeded) {
                        anaRejected.incrementAndGet()
                    }
                }
            }
            val brunoCalls = (1..3).map { async { gateway.text(turn(bruno)).also { brunoOk.incrementAndGet() } } }
            (anaCalls + brunoCalls).awaitAll()
        }
        store.peakUser.get() shouldBeLessThanOrEqual userBudget
        val inner = store.inner as InMemoryAiUsageStore
        inner.userSpentMicros(AI_DAY, ana) shouldBeLessThanOrEqual userBudget
        anaRejected.get() shouldBeGreaterThan 0
        brunoOk.get() shouldBe 3
    }

    test("un timeout o un 5xx se registra con el costo estimado de la entrada y suelta la reserva") {
        val usage = InMemoryAiUsageStore()
        val client = SlowLlmClient(delayMs = 0, failure = { RuntimeException("503 del proveedor") })
        val config = testAiConfig()
        val gateway = AiGateway(client, config, usage, AI_CLOCK)

        shouldThrow<RuntimeException> { gateway.text(turn(ana)) }

        val entry = usage.entries.single()
        entry.userId shouldBe ana
        entry.tokensIn shouldBe turn().estimatedTokensIn()
        entry.tokensOut shouldBe 0
        entry.costMicros shouldBe config.prices.costMicros(config.models.chat, AI_DAY, LlmUsage(turn().estimatedTokensIn(), 0))
        entry.costMicros shouldBeGreaterThan 0L
        usage.heldMicros(AI_DAY) shouldBe 0L
    }

    test("JSON inválido con uso reportado registra ese uso, no la estimación") {
        val usage = InMemoryAiUsageStore()
        val client = SlowLlmClient(delayMs = 0, failure = { LlmInvalidOutput("no es JSON", LlmUsage(321, 45)) })
        val gateway = AiGateway(client, testAiConfig(), usage, AI_CLOCK)
        shouldThrow<LlmInvalidOutput> { gateway.json(AiCall(AiPurpose.SIMULATION_JUDGE, prompt), JUDGE_SCHEMA) }
        usage.entries.single().let {
            it.tokensIn shouldBe 321
            it.tokensOut shouldBe 45
        }
        usage.heldMicros(AI_DAY) shouldBe 0L
    }

    test("una llamada cancelada a mitad de camino suelta la reserva y registra la entrada estimada") {
        val usage = InMemoryAiUsageStore()
        val started = CompletableDeferred<Unit>()
        val client = SlowLlmClient(hang = started)
        val gateway = AiGateway(client, testAiConfig(), usage, AI_CLOCK)
        coroutineScope {
            val job = launch { gateway.text(turn(ana)) }
            started.await()
            usage.heldMicros(AI_DAY) shouldBeGreaterThan 0L
            job.cancel()
        }
        usage.heldMicros(AI_DAY) shouldBe 0L
        usage.entries.single().tokensOut shouldBe 0
    }

    test("AI_USER_DAILY_BUDGET_USD inválido o no positivo hace fallar el arranque") {
        shouldThrow<IllegalArgumentException> { testAiConfig(mapOf("AI_USER_DAILY_BUDGET_USD" to "nada")) }
        shouldThrow<IllegalArgumentException> { testAiConfig(mapOf("AI_USER_DAILY_BUDGET_USD" to "0")) }
        testAiConfig().userDailyBudgetUsd.toPlainString() shouldBe "0.75"
    }
})

private fun usd(micros: Long): String = java.math.BigDecimal.valueOf(micros, 6).toPlainString()
