package me.santiagoreina.parcheu

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El reductor de recursos de R8 elimina los recursos que el código solo lee por nombre
 * (`Resources.getIdentifier`). En 0.2.1 se llevó `default_web_client_id` y el login con Google dejó de funcionar
 * en el APK minificado. Esta prueba busca cada `getIdentifier(` en el código de app y designsystem y exige que
 * su nombre sea un literal y que figure en `tools:keep` de app/src/main/res/raw/keep.xml.
 */
class ResourceKeepTest {
    private val moduleDir =
        listOf(File("."), File("app")).first { File(it, "src/main/res/raw/keep.xml").isFile }
    private val keepXml = File(moduleDir, "src/main/res/raw/keep.xml").readText()

    /** Entradas `@tipo/nombre` (con comodín `*`) de tools:keep, como regex sobre "tipo/nombre". */
    private val kept: List<Regex> =
        Regex("""tools:keep="([^"]*)"""").find(keepXml)?.groupValues?.get(1).orEmpty()
            .split(',')
            .map { it.trim().removePrefix("@") }
            .filter { it.isNotEmpty() }
            .map { entry ->
                Regex(entry.split('*').joinToString("[^/]*") { Regex.escape(it) })
            }

    private val sources: List<File> =
        listOf(File(moduleDir, "src/main"), File(moduleDir, "../designsystem/src/main"))
            .filter { it.isDirectory }
            .flatMap { dir -> dir.walk().filter { it.extension == "kt" || it.extension == "java" } }

    @Test
    fun everyResourceReadByNameIsInKeepXml() {
        val call = Regex("""getIdentifier\(\s*([^,]+?)\s*,\s*([^,]+?)\s*,""")
        val problems = mutableListOf<String>()
        var found = 0
        sources.forEach { file ->
            call.findAll(file.readText()).forEach { match ->
                found++
                val (rawName, rawType) = match.destructured
                val name = rawName.removeSurrounding("\"")
                val type = rawType.removeSurrounding("\"")
                when {
                    name == rawName || type == rawType ->
                        problems += "${file.name}: getIdentifier con nombre o tipo no literal " +
                            "($rawName, $rawType); no se puede comprobar keep.xml"
                    kept.none { it.matches("$type/$name") } ->
                        problems += "${file.name}: @$type/$name no está en tools:keep de " +
                            "res/raw/keep.xml (R8 lo eliminaría del APK minificado)"
                }
            }
        }
        // Hoy existe al menos el de default_web_client_id: si la búsqueda no encuentra nada, está rota.
        assertTrue("no se encontró ningún getIdentifier en ${sources.size} archivos", found > 0)
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun webClientIdIsKept() {
        assertTrue(kept.any { it.matches("string/default_web_client_id") })
    }
}
