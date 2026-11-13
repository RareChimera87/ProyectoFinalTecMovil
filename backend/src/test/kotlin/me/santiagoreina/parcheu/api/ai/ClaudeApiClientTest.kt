package me.santiagoreina.parcheu.api.ai

import com.anthropic.errors.AnthropicServiceException
import com.sun.net.httpserver.HttpServer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import me.santiagoreina.parcheu.api.platform.config.Env
import me.santiagoreina.parcheu.api.platform.errors.AiBudgetExceeded
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * [ClaudeApiClient] (`AI_PROVIDER=anthropic`) contra un servidor HTTP falso que imita `POST /v1/messages`: nunca
 * llama a la API real ni necesita una clave de verdad.
 */
class ClaudeApiClientTest : FunSpec({
    val key = "sk-ant-prueba-no-real"

    /** Respuestas en orden; la última se repite. Guarda cada petición recibida. */
    class FakeAnthropic(vararg responses: Pair<Int, String>) : AutoCloseable {
        val requests = ConcurrentLinkedQueue<Triple<String, Map<String, String>, String>>()
        private val queue = responses.toList()
        private val count = AtomicInteger()
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                val body = exchange.requestBody.readBytes().decodeToString()
                val headers = exchange.requestHeaders.entries.associate { it.key.lowercase() to it.value.joinToString(",") }
                requests += Triple(exchange.requestURI.path, headers, body)
                val (status, payload) = queue[minOf(count.getAndIncrement(), queue.lastIndex)]
                val bytes = payload.toByteArray()
                exchange.responseHeaders.add("content-type", "application/json")
                exchange.responseHeaders.add("retry-after-ms", "10")
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }
        val url: String get() = "http://127.0.0.1:${server.address.port}"
        val calls: Int get() = requests.size
        fun lastBody(): JsonObject = Json.parseToJsonElement(requests.last().third).jsonObject
        override fun close() = server.stop(0)
    }

    fun message(text: String, stop: String = "end_turn", usage: String? = """{"input_tokens":120,"output_tokens":30}""") =
        """{"id":"msg_1","type":"message","role":"assistant","model":"claude-sonnet-5-5",""" +
            """"content":[{"type":"text","text":${Json.encodeToString(String.serializer(), text)}}],""" +
            """"stop_reason":"$stop","stop_sequence":null""" + (usage?.let { ""","usage":$it""" } ?: "") + "}"

    fun error(type: String) = """{"type":"error","error":{"type":"$type","message":"falso"}}"""

    fun client(server: FakeAnthropic, retries: Int = 2) =
        ClaudeApiClient(AnthropicApiKey.of(key), baseUrl = server.url, timeout = Duration.ofSeconds(5), maxRetries = retries)

    val schema = Json.parseToJsonElement(
        """{"type":"object","properties":{"score":{"type":"integer"},"motivo":{"type":"string"}},"required":["score","motivo"]}""",
    ).jsonObject

    test("texto normal: mapea modelo, system, techo de tokens y uso; manda solo la clave configurada") {
        FakeAnthropic(200 to message("hola, parce")).use { server ->
            val result = client(server).generateText(
                LlmRequest(model = "claude-sonnet-5-5", prompt = "<dato>hola</dato>", system = "Eres el gemelo", maxOutputTokens = 256),
            )
            result shouldBe LlmTextResult("hola, parce", "claude-sonnet-5-5", LlmUsage(120, 30), truncated = false)
            val (path, headers, _) = server.requests.single()
            path shouldBe "/v1/messages"
            // El SDK 3.x manda la clave estática como `Authorization: Bearer` (o `x-api-key` si ya viene puesta).
            (headers["x-api-key"] ?: headers["authorization"]?.removePrefix("Bearer ")) shouldBe key
            if (headers["x-api-key"] != null) headers["authorization"] shouldBe null
            val body = server.lastBody()
            body["model"]!!.jsonPrimitive.content shouldBe "claude-sonnet-5-5"
            body["max_tokens"]!!.jsonPrimitive.int shouldBe 256
            body["system"].toString() shouldContain "Eres el gemelo"
            // La delimitación de PromptData llega intacta: el cliente no toca el prompt.
            body["messages"]!!.jsonArray.single().toString() shouldContain "<dato>hola</dato>"
            body["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content shouldBe "low"
        }
    }

    test("JSON del juez: pide salida estructurada con objetos cerrados y valida con JsonSchemaCheck") {
        FakeAnthropic(200 to message("""{"score":7,"motivo":"buena química"}""")).use { server ->
            val result = client(server).generateJson(LlmRequest(model = "claude-sonnet-5-5", prompt = "juzga"), schema)
            result.json["score"]!!.jsonPrimitive.int shouldBe 7
            result.usage shouldBe LlmUsage(120, 30)
            val format = server.lastBody()["output_config"]!!.jsonObject["format"]!!.jsonObject
            format["type"]!!.jsonPrimitive.content shouldBe "json_schema"
            format["schema"]!!.jsonObject["additionalProperties"]!!.jsonPrimitive.content shouldBe "false"
        }
    }

    test("JSON del juez que no cumple el esquema es LlmInvalidOutput con el uso cobrado") {
        FakeAnthropic(200 to message("""{"score":"siete"}""")).use { server ->
            val e = shouldThrow<LlmInvalidOutput> {
                client(server).generateJson(LlmRequest(model = "claude-sonnet-5-5", prompt = "juzga"), schema)
            }
            e.usage shouldBe LlmUsage(120, 30)
        }
    }

    test("429 se reintenta y la segunda respuesta vale") {
        FakeAnthropic(429 to error("rate_limit_error"), 200 to message("ok")).use { server ->
            client(server).generateText(LlmRequest(model = "claude-haiku-5-5", prompt = "x")).text shouldBe "ok"
            server.calls shouldBe 2
            server.requests.last().second["x-stainless-retry-count"] shouldBe "1"
        }
    }

    test("5xx persistente: reintenta MAX_RETRIES veces y luego lanza (la pasarela registra el costo estimado)") {
        FakeAnthropic(529 to error("overloaded_error")).use { server ->
            val e = shouldThrow<AnthropicServiceException> {
                client(server).generateText(LlmRequest(model = "claude-haiku-5-5", prompt = "x"))
            }
            e.statusCode() shouldBe 529
            server.calls shouldBe 1 + ClaudeMessagesClient.MAX_RETRIES
            // El mensaje de error del SDK no incluye la clave.
            e.message.orEmpty() shouldNotContain key
        }
    }

    test("respuesta sin usage: registra el peor caso (entrada estimada y todo el techo de salida)") {
        FakeAnthropic(200 to message("sin uso", usage = null)).use { server ->
            val result = client(server).generateText(LlmRequest(model = "claude-haiku-5-5", prompt = "a".repeat(300), maxOutputTokens = 400))
            result.text shouldBe "sin uso"
            result.usage shouldBe LlmUsage((300 + 2) / 3, 400)
        }
    }

    test("max_tokens marca la respuesta como truncada; refusal es LlmInvalidOutput") {
        FakeAnthropic(200 to message("corta", stop = "max_tokens")).use { server ->
            client(server).generateText(LlmRequest(model = "claude-haiku-5-5", prompt = "x")).truncated shouldBe true
        }
        FakeAnthropic(200 to message("", stop = "refusal")).use { server ->
            shouldThrow<LlmInvalidOutput> {
                client(server).generateText(LlmRequest(model = "claude-haiku-5-5", prompt = "x"))
            }.usage shouldBe LlmUsage(120, 30)
        }
    }

    test("por la pasarela: el uso se cobra con los precios de anthropic y respeta el tope diario") {
        FakeAnthropic(200 to message("hola")).use { server ->
            val config = AiConfig.fromEnv(
                mapOf("AI_PROVIDER" to "anthropic", "SECRET_ANTHROPIC_API_KEY" to "projects/p/secrets/anthropic-api-key", "AI_DAILY_BUDGET_USD" to "10"),
                Env.DEV,
            )
            val store = InMemoryAiUsageStore()
            val gateway = AiGateway(client(server), config, store, AI_CLOCK)
            gateway.provider shouldBe AiProvider.ANTHROPIC
            gateway.text(AiCall(AiPurpose.SIMULATION_TURN, "hola"))
            // Sonnet 5.5 a 2,20/11,00 USD por millón: 120 · 2,20 + 30 · 11,00 = 594 micro-USD.
            store.spentMicros(AI_DAY) shouldBe 594L
            // Tope diario: con el presupuesto agotado no se llama al proveedor.
            store.seedSpent(AI_DAY, 10_000_000L)
            shouldThrow<AiBudgetExceeded> { gateway.text(AiCall(AiPurpose.SIMULATION_TURN, "otra")) }
            server.calls shouldBe 1
        }
    }

    test("el toString de la clave no la muestra") {
        AnthropicApiKey.of(key).toString() shouldNotContain key
    }
})
