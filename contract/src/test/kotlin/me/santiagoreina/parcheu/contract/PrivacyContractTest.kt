package me.santiagoreina.parcheu.contract

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Cubre los hallazgos de privacidad de la revisión: exportación, premium y ids de reporte. */
class PrivacyContractTest {

    private fun example(name: String): JsonObject =
        ContractJson.parseToJsonElement(Examples.read(name)).jsonObject

    @Test
    fun `la exportacion trae conexiones y solo mensajes propios sin senderId`() {
        val dto = ContractJson.decodeFromString(ExportDto.serializer(), Examples.read("ExportDto"))
        assertTrue(dto.connections.isNotEmpty())
        val chat = (example("ExportDto")["chats"] as JsonArray).first().jsonObject
        val msg = (chat["messages"] as JsonArray).first().jsonObject
        assertFalse("senderId" in msg, "la exportación no debe llevar ids de terceros")
    }

    @Test
    fun `ExportConnection no expone ids internos de la otra persona`() {
        val keys = example("ExportConnection").keys
        assertEquals(setOf("label", "mode", "state", "connectedAt"), keys)
    }

    @Test
    fun `mutualCount de Suggestion es opcional y reason no lo contiene`() {
        val free = Suggestion(other = PersonRef(id = "x", label = "A", initials = "A"), mode = Mode.AMISTAD)
        assertNull(free.mutualCount)
        val encoded = ContractJson.encodeToString(Suggestion.serializer(), free)
        assertFalse("mutualCount" in encoded, "para gratis el campo se omite")
        val plus = ContractJson.decodeFromString(Suggestion.serializer(), Examples.read("Suggestion"))
        assertNotNull(plus.mutualCount)
        assertFalse(plus.reason.orEmpty().any { it.isDigit() }, "reason no lleva conteos")
    }

    @Test
    fun `AnonProfile lleva anonId para reportar y la presentacion lleva las personas`() {
        val anon = ContractJson.decodeFromString(AnonProfile.serializer(), Examples.read("AnonProfile"))
        val report = ReportRequest(kind = ReportKind.USER, anonId = anon.anonId, reason = ReportReason.HARASSMENT)
        assertEquals(anon.anonId, report.anonId)
        val intro = ContractJson.decodeFromString(IntroductionDto.serializer(), Examples.read("IntroductionDto"))
        assertTrue(intro.introducer.id != intro.other.id)
        assertEquals(Mode.AMISTAD, intro.mode)
    }

    @Test
    fun `MemoryDto siempre lleva el modo para el chip de C4 (ADR-0032)`() {
        val memory = ContractJson.decodeFromString(MemoryDto.serializer(), Examples.read("MemoryDto"))
        assertEquals(Mode.AMISTAD, memory.mode)
        val sinModo = JsonObject(example("MemoryDto") - "mode").toString()
        assertFailsWith<SerializationException>("una memoria sin modo no es válida") {
            ContractJson.decodeFromString(MemoryDto.serializer(), sinModo)
        }
        val conModoNulo = JsonObject(example("MemoryDto") + ("mode" to JsonNull)).toString()
        assertFailsWith<SerializationException> { ContractJson.decodeFromString(MemoryDto.serializer(), conModoNulo) }
    }
}
