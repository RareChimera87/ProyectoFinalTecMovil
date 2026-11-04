package me.santiagoreina.parcheu.contract

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertEquals

/** Round-trip JSON de cada DTO con su ejemplo canónico. */
class RoundTripTest {

    @TestFactory
    fun `cada DTO hace round-trip con su ejemplo canonico`(): List<DynamicTest> =
        Examples.serializers.map { (name, serializer) ->
            DynamicTest.dynamicTest(name) {
                @Suppress("UNCHECKED_CAST")
                val s = serializer as KSerializer<Any?>
                val original = ContractJson.parseToJsonElement(Examples.read(name))
                val value = ContractJson.decodeFromJsonElement(s, original)
                val encoded: JsonElement = ContractJson.encodeToJsonElement(s, value)
                assertEquals(original, encoded, "El JSON de $name no sobrevive el round-trip")
                assertEquals(value, ContractJson.decodeFromJsonElement(s, encoded), "$name no es igual tras el round-trip")
            }
        }

    @TestFactory
    fun `cada ejemplo con claves desconocidas se deserializa sin error`(): List<DynamicTest> =
        Examples.serializers.map { (name, serializer) ->
            DynamicTest.dynamicTest(name) {
                @Suppress("UNCHECKED_CAST")
                val s = serializer as KSerializer<Any?>
                val original = ContractJson.parseToJsonElement(Examples.read(name)) as JsonObject
                val withUnknown = JsonObject(
                    original + mapOf(
                        "campoNuevoDeUnaVersionFutura" to JsonPrimitive(42),
                        "otroCampo" to JsonObject(mapOf("x" to JsonPrimitive("y"))),
                    ),
                )
                assertEquals(
                    ContractJson.decodeFromJsonElement(s, original),
                    ContractJson.decodeFromJsonElement(s, withUnknown),
                )
            }
        }
}
