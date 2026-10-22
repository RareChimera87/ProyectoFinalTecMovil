package me.santiagoreina.parcheu.api.privacy

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import me.santiagoreina.parcheu.api.simulation.MemorySelection
import java.io.File
import java.nio.file.Files

/**
 * Test de arquitectura (ADR-0032): si el módulo main ya declara un `TwinPromptBuilder`, sus
 * `.class` deben referenciar [MemorySelection], `select` y `sanitize`. Sin esto, el builder de E4.3
 * podría filtrar por su cuenta (o dejar de filtrar) y la propiedad de memorias por modo seguiría
 * en verde porque solo ejercita la costura.
 */
class TwinPromptBuilderDelegationTest : FunSpec({

    val selectionRef = "me/santiagoreina/parcheu/api/simulation/MemorySelection"
    val builderPrefix = "me/santiagoreina/parcheu/api/simulation/TwinPromptBuilder"

    /** Falta(s) de delegación del builder bajo [classesRoot]; vacío si no hay builder o delega bien. */
    fun violations(classesRoot: File): List<String> {
        val builders = classesRoot.walkTopDown()
            .filter { it.isFile && it.extension == "class" }
            .filter { it.relativeTo(classesRoot).invariantSeparatorsPath.startsWith(builderPrefix) }
            .toList()
        if (builders.isEmpty()) return emptyList()
        // Se concatenan los .class (clase y anidadas/lambdas): el pool de constantes guarda los nombres en UTF-8.
        val text = builders.joinToString("\n") { String(it.readBytes(), Charsets.ISO_8859_1) }
        return listOf(selectionRef to "la clase MemorySelection", "select" to "el método select", "sanitize" to "el método sanitize")
            .filter { (needle, _) -> !text.contains(needle) }
            .map { (_, what) -> "TwinPromptBuilder no referencia $what" }
    }

    fun write(root: File, name: String, constants: List<String>) {
        val f = File(root, "$name.class")
        f.parentFile.mkdirs()
        f.writeText(constants.joinToString("\u0000"), Charsets.ISO_8859_1)
    }

    test("el TwinPromptBuilder real de main delega en MemorySelection") {
        val root = File(MemorySelection::class.java.protectionDomain.codeSource.location.toURI())
        withClue("raíz de clases main: $root") {
            root.isDirectory shouldBe true // si fuera un jar el escaneo pasaría en vacío
            violations(root) shouldBe emptyList()
        }
    }

    test("sin TwinPromptBuilder en main no hay nada que exigir") {
        val root = Files.createTempDirectory("arq-sin").toFile()
        try {
            write(root, "me/santiagoreina/parcheu/api/simulation/Otra", listOf("nada"))
            violations(root) shouldBe emptyList()
        } finally { root.deleteRecursively() }
    }

    test("un builder que filtra por su cuenta sin MemorySelection falla") {
        val root = Files.createTempDirectory("arq-malo").toFile()
        try {
            write(root, "me/santiagoreina/parcheu/api/simulation/TwinPromptBuilder", listOf("memoriesFor", "filter"))
            violations(root).size shouldBe 3
        } finally { root.deleteRecursively() }
    }

    test("un builder que delega en select y sanitize pasa, también si lo hace una clase anidada") {
        val root = Files.createTempDirectory("arq-bueno").toFile()
        try {
            val base = "me/santiagoreina/parcheu/api/simulation/TwinPromptBuilder"
            write(root, base, listOf(selectionRef, "select"))
            write(root, "$base\$Helper", listOf("sanitize"))
            violations(root) shouldBe emptyList()
        } finally { root.deleteRecursively() }
    }
})
