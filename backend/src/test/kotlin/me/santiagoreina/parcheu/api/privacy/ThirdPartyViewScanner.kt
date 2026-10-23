package me.santiagoreina.parcheu.api.privacy

import me.santiagoreina.parcheu.api.graph.ThirdPartyView
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.relativeTo
import kotlin.reflect.KClass

/**
 * Busca, sin librerías, las clases concretas que implementan [ThirdPartyView] en un directorio de
 * clases compiladas (`build/classes/kotlin/main` o `.../test`). No inicializa las clases.
 */
object ThirdPartyViewScanner {
    /** Directorio de clases (o jar) del que se cargó [type]. */
    fun classesRootOf(type: Class<*>): Path = Path.of(type.protectionDomain.codeSource.location.toURI())

    fun implementationsIn(root: Path, loader: ClassLoader = ThirdPartyView::class.java.classLoader): Set<Class<*>> {
        require(root.isDirectory()) { "Se esperaba un directorio de clases: $root" }
        return Files.walk(root).use { paths ->
            paths.filter { it.extension == "class" }
                .map { it.relativeTo(root).invariantSeparatorsPathString.removeSuffix(".class").replace('/', '.') }
                .filter { !it.endsWith("module-info") && !it.endsWith("package-info") }
                .toList()
        }.mapNotNull { name -> runCatching { Class.forName(name, false, loader) }.getOrNull() }
            .filter { ThirdPartyView::class.java.isAssignableFrom(it) }
            .filter { !it.isInterface && !Modifier.isAbstract(it.modifiers) }
            .toSet()
    }

    /** Implementaciones que no están en [registered]. */
    fun missingRegistrations(found: Set<Class<*>>, registered: Set<KClass<out ThirdPartyView>>): Set<Class<*>> =
        found - registered.map { it.java }.toSet()
}
