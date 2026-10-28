package me.santiagoreina.parcheu.api.premium

import me.santiagoreina.parcheu.api.platform.Premium
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.relativeTo

/** Una función marcada con [Premium]: clase, método y el endpoint que declara. */
data class PremiumMark(val owner: String, val method: String, val endpoint: String)

/**
 * Busca, sin inicializar las clases, las funciones marcadas con [Premium] en un directorio de clases
 * compiladas (`build/classes/kotlin/main` o `.../test`). Las funciones `suspend` y las privadas
 * también cuentan: la anotación queda en el método de la JVM.
 */
object PremiumScanner {
    /** Directorio de clases del que se cargó [type]. */
    fun classesRootOf(type: Class<*>): Path = Path.of(type.protectionDomain.codeSource.location.toURI())

    fun marksIn(root: Path, loader: ClassLoader = Premium::class.java.classLoader): List<PremiumMark> {
        require(root.isDirectory()) { "Se esperaba un directorio de clases: $root" }
        val names = Files.walk(root).use { paths ->
            paths.filter { it.extension == "class" }
                .map { it.relativeTo(root).invariantSeparatorsPathString.removeSuffix(".class").replace('/', '.') }
                .filter { !it.endsWith("module-info") && !it.endsWith("package-info") }
                .toList()
        }
        return names.mapNotNull { name -> runCatching { Class.forName(name, false, loader) }.getOrNull() }
            .flatMap { type ->
                runCatching { type.declaredMethods.toList() }.getOrDefault(emptyList())
                    .mapNotNull { m -> m.getAnnotation(Premium::class.java)?.let { PremiumMark(type.name, m.name, it.endpoint) } }
            }
            .sortedWith(compareBy({ it.endpoint }, { it.owner }, { it.method }))
    }

    /** Marcas cuyo endpoint no está en [listed]. */
    fun unlisted(marks: List<PremiumMark>, listed: Set<String>): List<PremiumMark> = marks.filter { it.endpoint !in listed }

    /** Endpoints de [listed] sin ninguna función marcada (entrada sobrante o anotación borrada). */
    fun unmarked(marks: List<PremiumMark>, listed: Set<String>): Set<String> = listed - marks.map { it.endpoint }.toSet()
}
