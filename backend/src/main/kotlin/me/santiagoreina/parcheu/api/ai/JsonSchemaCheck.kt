package me.santiagoreina.parcheu.api.ai

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Validación mínima de JSON Schema para la salida de los modelos (arquitectura.md §10.4: "salida
 * del juez validada por esquema"). Cubre lo que usan los prompts del proyecto: `type` (object,
 * array, string, integer, number, boolean), `properties`, `required`, `items`, `enum`,
 * `minimum` y `maximum`. Los proveedores ya restringen la salida; esto es la segunda barrera.
 */
object JsonSchemaCheck {
    private val json = Json { ignoreUnknownKeys = true }

    /** Parsea [text] como objeto JSON y lo valida contra [schema]. */
    fun parseAndValidate(text: String, schema: JsonObject, usage: LlmUsage? = null): JsonObject {
        val element = try {
            json.parseToJsonElement(stripFences(text))
        } catch (e: SerializationException) {
            throw LlmInvalidOutput("La respuesta no es JSON válido: ${e.message}", usage)
        } catch (e: IllegalArgumentException) {
            throw LlmInvalidOutput("La respuesta no es JSON válido: ${e.message}", usage)
        }
        val obj = element as? JsonObject ?: throw LlmInvalidOutput("La respuesta no es un objeto JSON", usage)
        val errors = mutableListOf<String>()
        check(obj, schema, "$", errors)
        if (errors.isNotEmpty()) throw LlmInvalidOutput("La respuesta no cumple el esquema: ${errors.joinToString("; ")}", usage)
        return obj
    }

    /** Errores de [value] frente a [schema]; vacío si cumple. */
    fun errors(value: JsonElement, schema: JsonObject): List<String> =
        mutableListOf<String>().also { check(value, schema, "$", it) }

    private fun check(value: JsonElement, schema: JsonObject, path: String, errors: MutableList<String>) {
        val type = (schema["type"] as? JsonPrimitive)?.contentOrNull
        if (type != null && !matchesType(value, type)) {
            errors += "$path debe ser $type"
            return
        }
        (schema["enum"] as? JsonArray)?.let { allowed ->
            if (value !in allowed) errors += "$path no es uno de ${allowed.joinToString()}"
        }
        if (value is JsonPrimitive && !value.isString) {
            value.doubleOrNull?.let { n ->
                (schema["minimum"] as? JsonPrimitive)?.doubleOrNull?.let { if (n < it) errors += "$path < $it" }
                (schema["maximum"] as? JsonPrimitive)?.doubleOrNull?.let { if (n > it) errors += "$path > $it" }
            }
        }
        if (value is JsonObject) {
            val required = (schema["required"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
            required.filter { it !in value || value[it] is JsonNull }.forEach { errors += "$path.$it es obligatorio" }
            val properties = schema["properties"] as? JsonObject
            properties?.forEach { (name, sub) ->
                val child = value[name]
                if (child != null && child !is JsonNull && sub is JsonObject) check(child, sub, "$path.$name", errors)
            }
        }
        if (value is JsonArray) {
            val items = schema["items"] as? JsonObject ?: return
            value.jsonArray.forEachIndexed { i, child -> check(child, items, "$path[$i]", errors) }
        }
    }

    private fun matchesType(value: JsonElement, type: String): Boolean = when (type) {
        "object" -> value is JsonObject
        "array" -> value is JsonArray
        "string" -> value is JsonPrimitive && value.isString
        "boolean" -> value is JsonPrimitive && !value.isString && value.booleanOrNull != null
        "integer" -> value is JsonPrimitive && !value.isString && (value.longOrNull != null || value.doubleOrNull?.let { it % 1.0 == 0.0 } == true)
        "number" -> value is JsonPrimitive && !value.isString && value.doubleOrNull != null
        "null" -> value is JsonNull
        else -> true
    }

    /** Algunos modelos envuelven el JSON en ```json ... ``` aunque se pida JSON puro. */
    private fun stripFences(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith("```")) return trimmed
        return trimmed.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    }
}
