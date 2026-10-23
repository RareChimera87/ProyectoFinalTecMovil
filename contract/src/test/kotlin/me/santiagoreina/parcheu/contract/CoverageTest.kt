package me.santiagoreina.parcheu.contract

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Garantiza que ningún DTO quede sin ejemplo canónico ni sin registrar. */
class CoverageTest {

    /** Clases del contrato con serializador generado (data classes @Serializable). */
    private fun generatedDtoNames(): Set<String> {
        val root = File(ApiError::class.java.protectionDomain.codeSource.location.toURI())
        val pkgDir = File(root, "me/santiagoreina/parcheu/contract")
        assertTrue(pkgDir.isDirectory, "No se encontraron las clases compiladas en $pkgDir")
        return pkgDir.listFiles()!!
            .map { it.name }
            .filter { it.endsWith("\$\$serializer.class") }
            .map { it.removeSuffix("\$\$serializer.class") }
            .toSet()
    }

    @Test
    fun `todo DTO serializable esta registrado`() {
        assertEquals(generatedDtoNames().sorted(), Examples.serializers.keys.sorted())
    }

    @Test
    fun `cada DTO registrado tiene exactamente un ejemplo y no sobran ejemplos`() {
        assertEquals(Examples.serializers.keys.sorted(), Examples.files().sorted())
    }
}
