package me.santiagoreina.parcheu.e2e

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.Duration

/** Piezas del smoke e2e que no necesitan servidor ni red (corren en `./gradlew test`). */
class SmokeSupportTest : FunSpec({
    test("E2eConfig exige las tres variables y recorta la barra final") {
        E2eConfig.fromEnv(emptyMap()) shouldBe null
        E2eConfig.missing(mapOf("E2E_BASE_URL" to "https://x")) shouldBe listOf("E2E_FIREBASE_PROJECT_ID", "E2E_FIREBASE_API_KEY")
        val config = E2eConfig.fromEnv(
            mapOf("E2E_BASE_URL" to "https://api/", "E2E_FIREBASE_PROJECT_ID" to "p", "E2E_FIREBASE_API_KEY" to "k", "E2E_SERVICE_ACCOUNT" to " "),
        )
        config shouldBe E2eConfig("https://api", "p", "k", null)
        E2eConfig.required(mapOf("E2E_REQUIRED" to "true")) shouldBe true
        E2eConfig.required(emptyMap()) shouldBe false
    }

    test("pollUntil devuelve el primer valor no nulo y cuenta los intentos") {
        var calls = 0
        val result = pollUntil("algo", Duration.ofSeconds(10), Duration.ofSeconds(1), sleep = {}, probe = { if (++calls == 3) "listo" else null })
        result shouldBe "listo"
        calls shouldBe 3
    }

    test("pollUntil falla al vencer el plazo con el último error de la API") {
        var clock = 0L
        val error = shouldThrow<PollTimeout> {
            pollUntil(
                "el gemelo", Duration.ofSeconds(5), Duration.ofSeconds(2),
                sleep = { clock += it.toNanos() }, now = { clock },
            ) { throw ApiException("GET", "/v1/twin", 503, "frío") }
        }
        error.message shouldContain "el gemelo"
        error.message shouldContain "503"
    }

    test("pollUntil corta de inmediato con PollFatal") {
        var calls = 0
        val error = shouldThrow<AssertionError> {
            pollUntil("la simulación", Duration.ofMinutes(1), sleep = {}) { calls++; throw PollFatal("terminó FAILED") }
        }
        calls shouldBe 1
        error.message shouldContain "terminó FAILED"
    }

    test("FirebaseTestAuth extrae el idToken de la respuesta de Identity Toolkit") {
        FirebaseTestAuth.parseIdToken("""{"kind":"x","idToken":"abc.def","refreshToken":"r","expiresIn":"3600"}""") shouldBe "abc.def"
        shouldThrow<IllegalArgumentException> { FirebaseTestAuth.parseIdToken("""{"error":{}}""") }
    }

    test("FirestoreChatReader lee los textos de la respuesta REST y descarta los mensajes ocultos") {
        val json = """
            {"documents":[
              {"name":"projects/p/databases/(default)/documents/chats/c/messages/1",
               "fields":{"text":{"stringValue":"hola"},"hidden":{"booleanValue":false},"system":{"booleanValue":false}}},
              {"name":".../2","fields":{"text":{"stringValue":"oculto"},"hidden":{"booleanValue":true}}},
              {"name":".../3","fields":{"text":{"stringValue":"sin campo hidden"}}}
            ]}
        """.trimIndent()
        FirestoreChatReader.parseVisibleTexts(json) shouldBe listOf("hola", "sin campo hidden")
        FirestoreChatReader.parseVisibleTexts("{}") shouldBe emptyList()
    }
})
