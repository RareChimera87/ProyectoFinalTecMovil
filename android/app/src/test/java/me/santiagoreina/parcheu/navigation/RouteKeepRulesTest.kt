package me.santiagoreina.parcheu.navigation

import java.io.File
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.serializer
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Navigation Compose resuelve por reflexión (Class.forName sobre el serialName) la clase de cada argumento
 * de ruta que no es primitivo (enums como [me.santiagoreina.parcheu.contract.Mode], tipos propios). Si R8 la
 * renombra, el APK de release se cierra al construir el grafo (crash de 0.2.0). Esta prueba recorre todas las
 * rutas y exige que cada uno de esos tipos quede cubierto por una regla `-keep ... { *; }` sin
 * `allowobfuscation` de app/proguard-rules.pro, y que ese archivo esté enlazado en el buildType release.
 */
class RouteKeepRulesTest {
    private val moduleDir = listOf(File("."), File("app")).first {
        File(it, "proguard-rules.pro").isFile
    }
    private val rules = File(moduleDir, "proguard-rules.pro").readLines()

    /**
     * Patrones de clase de las reglas -keep incondicionales que conservan nombre y miembros. Se ignoran las
     * que dependen de una anotación (`-keep @X class ...`): la prueba no puede saber si el tipo la lleva.
     */
    private val keptPatterns: List<Regex> =
        rules
            .map { it.trim() }
            .filter {
                it.startsWith("-keep ") &&
                    "allowobfuscation" !in it &&
                    "@" !in it &&
                    "{ *; }" in it
            }
            .mapNotNull { line ->
                Regex("""\b(?:class|enum|interface)\s+(\S+)""").find(line)?.groupValues?.get(1)
            }
            .map(::proguardPatternToRegex)

    @OptIn(InternalSerializationApi::class)
    @Test
    fun everyNonPrimitiveRouteArgumentIsKeptByR8Rules() {
        val argTypes = AppRoute::class.sealedSubclasses
            .flatMap { route -> nonPrimitiveArgs(route.serializer().descriptor) }
            .toSet()
        // Sin al menos Mode (MatchQueue) la prueba no estaría mirando nada.
        assertTrue(
            "no se encontró Mode entre los argumentos de ruta: $argTypes",
            "me.santiagoreina.parcheu.contract.Mode" in argTypes
        )
        val uncovered = argTypes.filterNot { type -> keptPatterns.any { it.matches(type) } }
        assertTrue(
            "Argumentos de ruta sin regla -keep en app/proguard-rules.pro (R8 los renombraría " +
                "y la app se cerraría al arrancar en release): $uncovered",
            uncovered.isEmpty()
        )
    }

    @OptIn(InternalSerializationApi::class)
    @Test
    fun everyRouteClassIsKeptByR8Rules() {
        val uncovered = AppRoute::class.sealedSubclasses
            .map { it.serializer().descriptor.serialName }
            .filterNot { name -> keptPatterns.any { it.matches(name) } }
        assertTrue("Rutas sin regla -keep: $uncovered", uncovered.isEmpty())
    }

    @Test
    fun rulesFileIsLinkedInReleaseBuildType() {
        val buildScript = File(moduleDir, "build.gradle.kts").readText()
        assertTrue(
            "app/build.gradle.kts debe enlazar proguard-rules.pro en el buildType release",
            Regex("""release\s*\{[\s\S]*?proguardFiles\("proguard-rules\.pro"\)""").containsMatchIn(
                buildScript
            )
        )
    }

    /** serialNames (sin '?') de los argumentos no primitivos, recorriendo listas y mapas. */
    private fun nonPrimitiveArgs(descriptor: SerialDescriptor): List<String> =
        (0 until descriptor.elementsCount).flatMap { collect(descriptor.getElementDescriptor(it)) }

    private fun collect(descriptor: SerialDescriptor): List<String> = when (descriptor.kind) {
        is PrimitiveKind -> emptyList()
        StructureKind.LIST, StructureKind.MAP ->
            (0 until descriptor.elementsCount).flatMap {
                collect(descriptor.getElementDescriptor(it))
            }
        // Enums, clases, objects y contextuales: Navigation busca su clase por nombre.
        else -> listOf(descriptor.serialName.removeSuffix("?"))
    }

    /** `**` = cualquier cosa (con puntos); `*` = un segmento; `?` = un carácter que no es punto. */
    private fun proguardPatternToRegex(pattern: String): Regex {
        val out = StringBuilder()
        var i = 0
        while (i < pattern.length) {
            when {
                pattern.startsWith("**", i) -> {
                    out.append(".*")
                    i += 2
                }
                pattern[i] == '*' -> {
                    out.append("[^.]*")
                    i++
                }
                pattern[i] == '?' -> {
                    out.append("[^.]")
                    i++
                }
                else -> {
                    out.append(Regex.escape(pattern[i].toString()))
                    i++
                }
            }
        }
        return Regex(out.toString())
    }
}
