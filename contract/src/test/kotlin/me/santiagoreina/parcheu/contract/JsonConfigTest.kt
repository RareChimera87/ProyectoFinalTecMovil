package me.santiagoreina.parcheu.contract

import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class JsonConfigTest {

    @Test
    fun `la configuracion compartida ignora claves desconocidas y omite nulos`() {
        assertTrue(ContractJson.configuration.ignoreUnknownKeys)
        assertFalse(ContractJson.configuration.explicitNulls)
    }

    /** Todo campo nullable de todo DTO (recursivamente) tiene valor por defecto. */
    @Test
    fun `los campos opcionales tienen valor por defecto`() {
        val faltantes = mutableListOf<String>()
        val vistos = mutableSetOf<String>()
        fun visitar(d: SerialDescriptor) {
            if (!vistos.add(d.serialName)) return
            if (d.kind == StructureKind.CLASS) {
                for (i in 0 until d.elementsCount) {
                    val e = d.getElementDescriptor(i)
                    if (e.isNullable && !d.isElementOptional(i)) faltantes += "${d.serialName}.${d.getElementName(i)}"
                }
            }
            for (i in 0 until d.elementsCount) visitar(d.getElementDescriptor(i))
        }
        Examples.serializers.values.forEach { visitar(it.descriptor) }
        assertEquals(emptyList(), faltantes, "Campos nullable sin valor por defecto")
    }

    @Test
    fun `un informe gratis no contiene las claves premium`() {
        val gratis = ReportDto(score = 78, summary = "Buen parche", locked = true, unlockAvailable = true)
        val keys = ContractJson.encodeToJsonElement(ReportDto.serializer(), gratis).jsonObject.keys
        assertFalse("reasons" in keys)
        assertFalse("frictions" in keys)
        assertFalse("dimensions" in keys)
        assertFalse("myDecision" in keys)
    }

    @Test
    fun `un JSON minimo se deserializa con los valores por defecto`() {
        val ref = ContractJson.decodeFromString(PersonRef.serializer(), """{"label":"Alguien de la UIS","initials":"A"}""")
        assertNull(ref.id)
        assertNull(ref.anonId)
        assertNull(ref.photoUrl)
        assertFalse(ref.isDemo)
    }

    @Test
    fun `los instantes se serializan en ISO-8601`() {
        val error = ApiError(ErrorCode.AI_BUDGET, "Sin presupuesto", retryAt = Instant.parse("2026-10-23T05:00:00Z"))
        val json = ContractJson.encodeToString(ApiError.serializer(), error)
        assertEquals("""{"code":"AI_BUDGET","message":"Sin presupuesto","retryAt":"2026-10-23T05:00:00Z"}""", json)
    }

    @Test
    fun `IsoDate valida el formato`() {
        val d = IsoDate("2005-03-14")
        assertEquals(2005, d.year)
        assertEquals(3, d.month)
        assertEquals(14, d.day)
        assertEquals("\"2005-03-14\"", ContractJson.encodeToString(IsoDate.serializer(), d))
        assertFailsWith<IllegalArgumentException> { IsoDate("14/03/2005") }
        assertFailsWith<IllegalArgumentException> { IsoDate("2005-13-01") }
        assertFailsWith<IllegalArgumentException> {
            ContractJson.decodeFromString(AgeVerification.serializer(), """{"birthDate":"2005-3-14"}""")
        }
    }
}
