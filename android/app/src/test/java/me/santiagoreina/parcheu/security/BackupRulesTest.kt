package me.santiagoreina.parcheu.security

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fija que la sesión de Firebase Auth (SharedPreferences) y los datos locales no entren en respaldos. */
class BackupRulesTest {
    private fun main(path: String): String {
        val file = File("src/main/$path")
        assertTrue("No existe ${file.absolutePath}", file.exists())
        return file.readText()
    }

    @Test
    fun `allowBackup esta desactivado`() {
        assertTrue(main("AndroidManifest.xml").contains("""android:allowBackup="false""""))
    }

    @Test
    fun `backup_rules excluye las SharedPreferences`() {
        val xml = main("res/xml/backup_rules.xml")
        assertTrue(xml.contains("""<exclude domain="sharedpref" path="." />"""))
        assertTrue(!xml.contains("<include"))
    }

    @Test
    fun `data_extraction_rules excluye las SharedPreferences en nube y transferencia`() {
        val xml = main("res/xml/data_extraction_rules.xml")
        val cloud = xml.substringAfter("<cloud-backup>").substringBefore("</cloud-backup>")
        val transfer = xml.substringAfter("<device-transfer>").substringBefore("</device-transfer>")
        listOf(cloud, transfer).forEach {
            assertTrue(it.contains("""<exclude domain="sharedpref" path="." />"""))
            assertTrue(!it.contains("<include"))
        }
    }
}
