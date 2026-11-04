package me.santiagoreina.parcheu.api.ai

import com.google.genai.types.Candidate
import com.google.genai.types.Content
import com.google.genai.types.FinishReason
import com.google.genai.types.GenerateContentResponse
import com.google.genai.types.GenerateContentResponsePromptFeedback
import com.google.genai.types.GenerateContentResponseUsageMetadata
import com.google.genai.types.Part
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/** Hallazgos de revisión de E0.9: bloqueos de Gemini y timeouts del cliente. */
class GeminiResponsesTest : FunSpec({
    val tokens = GenerateContentResponseUsageMetadata.builder()
        .promptTokenCount(500).candidatesTokenCount(10).thoughtsTokenCount(5).build()

    fun response(text: String?, finish: String?, promptBlock: String? = null, noCandidates: Boolean = false): GenerateContentResponse {
        val b = GenerateContentResponse.builder().usageMetadata(tokens)
        if (!noCandidates) {
            val c = Candidate.builder()
            text?.let { c.content(Content.builder().parts(Part.fromText(it)).build()) }
            finish?.let { c.finishReason(FinishReason(it)) }
            b.candidates(c.build())
        }
        promptBlock?.let { b.promptFeedback(GenerateContentResponsePromptFeedback.builder().blockReason(it).build()) }
        return b.build()
    }

    val usage = LlmUsage(500, 15)

    test("STOP devuelve el texto sin truncar") {
        val r = response("hola", "STOP")
        GeminiResponses.usageOf(r) shouldBe usage
        GeminiResponses.textOf(r, usage) shouldBe GeminiText("hola", false)
    }

    test("MAX_TOKENS devuelve el texto marcado como truncado") {
        GeminiResponses.textOf(response("corta", "MAX_TOKENS"), usage) shouldBe GeminiText("corta", true)
    }

    test("las partes de razonamiento no entran en el texto") {
        val thought = Part.builder().text("pienso").thought(true).build()
        val c = Candidate.builder()
            .content(Content.builder().parts(thought, Part.fromText("respuesta")).build())
            .finishReason(FinishReason("STOP")).build()
        GeminiResponses.textOf(GenerateContentResponse.builder().candidates(c).build(), usage).text shouldBe "respuesta"
    }

    listOf("SAFETY", "PROHIBITED_CONTENT", "RECITATION", "BLOCKLIST", "SPII").forEach { reason ->
        test("finishReason $reason se vuelve LlmInvalidOutput con el uso cobrado") {
            val e = shouldThrow<LlmInvalidOutput> { GeminiResponses.textOf(response(null, reason), usage) }
            e.usage shouldBe usage
            e.message shouldContain reason
        }
    }

    test("un prompt bloqueado se vuelve LlmInvalidOutput con el uso") {
        val e = shouldThrow<LlmInvalidOutput> {
            GeminiResponses.textOf(response(null, null, promptBlock = "SAFETY", noCandidates = true), usage)
        }
        e.usage shouldBe usage
        e.message shouldContain "bloque"
    }

    test("sin candidatos y sin bloqueo también es LlmInvalidOutput con el uso") {
        shouldThrow<LlmInvalidOutput> { GeminiResponses.textOf(response(null, null, noCandidates = true), usage) }.usage shouldBe usage
    }

    test("el bloqueo de Gemini queda registrado en ai_usage al pasar por la pasarela") {
        val client = object : LlmClient {
            override val provider = AiProvider.GEMINI
            override suspend fun generateText(request: LlmRequest): LlmTextResult =
                GeminiResponses.textOf(response(null, "SAFETY"), usage).let { error("inalcanzable") }
            override suspend fun generateJson(request: LlmRequest, schema: kotlinx.serialization.json.JsonObject): LlmJsonResult = error("no se usa")
        }
        val store = InMemoryAiUsageStore()
        shouldThrow<LlmInvalidOutput> { AiGateway(client, testAiConfig(), store, AI_CLOCK).text(AiCall(AiPurpose.SIMULATION_TURN, "x")) }
        store.entries.size shouldBe 1
        store.entries[0].tokensIn shouldBe 500
        store.entries[0].tokensOut shouldBe 15
    }

    test("el cliente se construye con timeout y conexiones por host explícitos") {
        GeminiVertexClient.httpOptions(60_000).timeout().get() shouldBe 60_000
        val opts = GeminiVertexClient.clientOptions(32)
        opts.maxConnectionsPerHost().get() shouldBe 32
        opts.maxConnections().get() shouldBe 32
    }
})
