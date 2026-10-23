package me.santiagoreina.parcheu.api.ai

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Esquema del juez (arquitectura.md §7.2), usado también por otros tests. */
val JUDGE_SCHEMA: JsonObject = Json.parseToJsonElement(
    """
    {"type": "object",
     "properties": {
       "afinidad": {"type": "integer", "minimum": 0, "maximum": 100},
       "dimensiones": {"type": "array", "items": {"type": "object",
          "properties": {"nombre": {"type": "string"}, "puntaje": {"type": "integer", "minimum": 0, "maximum": 100}, "nota": {"type": "string"}},
          "required": ["nombre", "puntaje", "nota"]}},
       "porQue": {"type": "array", "items": {"type": "string"}},
       "dondeChocarian": {"type": "array", "items": {"type": "string"}},
       "resumen": {"type": "string"}},
     "required": ["afinidad", "dimensiones", "porQue", "dondeChocarian", "resumen"]}
    """,
).jsonObject

class JsonSchemaCheckTest : FunSpec({
    test("acepta una salida válida del juez, también envuelta en ```json") {
        val ok = """{"afinidad": 72, "dimensiones": [{"nombre": "humor", "puntaje": 80, "nota": "ok"}],
            "porQue": ["a", "b", "c"], "dondeChocarian": ["x"], "resumen": "bien"}"""
        JsonSchemaCheck.parseAndValidate(ok, JUDGE_SCHEMA)["afinidad"].toString() shouldBe "72"
        JsonSchemaCheck.parseAndValidate("```json\n$ok\n```", JUDGE_SCHEMA).size shouldBe 5
    }

    test("rechaza texto que no es JSON, campos faltantes, tipos y rangos inválidos") {
        shouldThrow<LlmInvalidOutput> { JsonSchemaCheck.parseAndValidate("Aquí está el JSON:", JUDGE_SCHEMA) }
        shouldThrow<LlmInvalidOutput> { JsonSchemaCheck.parseAndValidate("[1, 2]", JUDGE_SCHEMA) }
        val e = shouldThrow<LlmInvalidOutput> {
            JsonSchemaCheck.parseAndValidate(
                """{"afinidad": 140, "dimensiones": [{"nombre": 3, "puntaje": 1, "nota": "n"}], "porQue": [], "dondeChocarian": []}""",
                JUDGE_SCHEMA,
            )
        }
        e.message!! shouldContain "$.afinidad > 100.0"
        e.message!! shouldContain "$.resumen es obligatorio"
        e.message!! shouldContain "$.dimensiones[0].nombre debe ser string"
    }

    test("el ejemplo que genera el Fake cumple cualquier esquema del juez") {
        JsonSchemaCheck.errors(FakeLlmClient.sample(JUDGE_SCHEMA), JUDGE_SCHEMA).shouldBeEmpty()
    }
})
