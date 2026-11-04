package me.santiagoreina.parcheu.designsystem.components.base

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Color.kt (E1.1) pide un test que impida usar `colorScheme.primary` como color de TEXTO en :designsystem:
 * el violeta #6D4AFF solo llega a AA como relleno. Aquí se revisan los componentes; el texto de marca
 * es `ParcheUTheme.colors.prt`.
 */
class NoPrimaryAsTextTest {
    @Test
    fun losComponentesNoUsanColorSchemePrimary() {
        val dir = File("src/main/java/me/santiagoreina/parcheu/designsystem/components")
        val offenders =
            dir.walkTopDown().filter { it.extension == "kt" }.filter {
                it.readText().contains("colorScheme.primary")
            }.map { it.name }.toList()
        assertTrue("Usan colorScheme.primary: $offenders", offenders.isEmpty())
        assertTrue("No se encontraron componentes", dir.walkTopDown().any { it.extension == "kt" })
    }
}
