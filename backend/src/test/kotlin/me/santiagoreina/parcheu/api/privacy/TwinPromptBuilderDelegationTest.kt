package me.santiagoreina.parcheu.api.privacy

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.Enabled
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import me.santiagoreina.parcheu.api.simulation.MemorySelection
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Handle
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.io.File
import java.nio.file.Files

/**
 * Test de arquitectura (ADR-0032): toda clase `TwinPromptBuilder*` de main (con sus anidadas y
 * lambdas) debe **invocar** de verdad `MemorySelection.select` y `MemorySelection.sanitize`. Se leen
 * las instrucciones de invocación con ASM (owner + nombre del método), no subcadenas del pool de
 * constantes: un builder con un método propio `selectMemories` que hace `take(10)` ya no pasa.
 *
 * Mientras E4.3 no esté mezclado no hay ningún `TwinPromptBuilder` en main: el test del builder
 * real queda **deshabilitado con motivo** (se ve como ignorado en el reporte) y se habilita solo en
 * cuanto aparezca una clase candidata, que entonces debe delegar o el build falla.
 */
class TwinPromptBuilderDelegationTest : FunSpec({

    val mainRoot = File(MemorySelection::class.java.protectionDomain.codeSource.location.toURI())

    test("la raíz de clases de main es un directorio (si fuera un jar el escaneo pasaría en vacío)") {
        withClue("raíz de clases main: $mainRoot") { mainRoot.isDirectory shouldBe true }
    }

    test("el TwinPromptBuilder real de main delega en MemorySelection.select y sanitize").config(
        enabledOrReasonIf = {
            if (BuilderDelegation.candidates(mainRoot).isNotEmpty()) {
                Enabled.enabled
            } else {
                Enabled.disabled("no hay clases TwinPromptBuilder* en main (E4.3 aún no está mezclado)")
            }
        },
    ) {
        withClue("candidatas: ${BuilderDelegation.candidates(mainRoot).map { it.relativeTo(mainRoot) }}") {
            BuilderDelegation.violations(mainRoot).shouldBeEmpty()
        }
    }

    test("sin TwinPromptBuilder no hay candidatas ni nada que exigir") {
        withTempRoot { root ->
            SyntheticClass.write(root, "$SIMULATION/Otra", Call.static(OWN_TAKE, "take"))
            BuilderDelegation.candidates(root).shouldBeEmpty()
            BuilderDelegation.violations(root).shouldBeEmpty()
        }
    }

    test("un builder tramposo (selectMemories propio + MemorySelection.sanitize) falla") {
        withTempRoot { root ->
            val builder = "$SIMULATION/TwinPromptBuilder"
            // Su propio selectMemories hace take(10) sin filtrar por modo; solo sanitize es real.
            SyntheticClass.write(
                root,
                builder,
                Call.virtual(builder, "selectMemories"),
                Call.static(OWN_TAKE, "take"),
                Call.virtual(MEMORY_SELECTION, "sanitize"),
            )
            val violations = BuilderDelegation.violations(root)
            violations shouldHaveSize 1
            violations shouldContain BuilderDelegation.MISSING_SELECT
        }
    }

    test("un builder que llama a un select ajeno a MemorySelection falla") {
        withTempRoot { root ->
            SyntheticClass.write(
                root,
                "$SIMULATION/TwinPromptBuilderImpl",
                Call.virtual("me/santiagoreina/parcheu/api/simulation/OtraSeleccion", "select"),
                Call.virtual(MEMORY_SELECTION, "sanitize"),
            )
            BuilderDelegation.violations(root) shouldBe listOf(BuilderDelegation.MISSING_SELECT)
        }
    }

    test("un builder que no llama a nada de MemorySelection falla por las dos") {
        withTempRoot { root ->
            SyntheticClass.write(root, "$SIMULATION/TwinPromptBuilder", Call.static(OWN_TAKE, "take"))
            BuilderDelegation.violations(root) shouldBe
                listOf(BuilderDelegation.MISSING_SELECT, BuilderDelegation.MISSING_SANITIZE)
        }
    }

    test("un builder que delega en select y sanitize pasa, también si lo hace una clase anidada") {
        withTempRoot { root ->
            val builder = "$SIMULATION/TwinPromptBuilder"
            SyntheticClass.write(root, builder, Call.virtual(MEMORY_SELECTION, "select"))
            SyntheticClass.write(root, "$builder\$Helper", Call.static(MEMORY_SELECTION, "sanitize"))
            BuilderDelegation.violations(root).shouldBeEmpty()
        }
    }

    test("una referencia a método (invokedynamic) a MemorySelection.sanitize cuenta como delegación") {
        withTempRoot { root ->
            SyntheticClass.write(
                root,
                "$SIMULATION/TwinPromptBuilder",
                Call.virtual(MEMORY_SELECTION, "select"),
                Call.indy(MEMORY_SELECTION, "sanitize"),
            )
            BuilderDelegation.violations(root).shouldBeEmpty()
        }
    }
}) {
    companion object {
        const val SIMULATION = "me/santiagoreina/parcheu/api/simulation"
        const val MEMORY_SELECTION = "$SIMULATION/MemorySelection"
        const val OWN_TAKE = "kotlin/collections/CollectionsKt"

        fun withTempRoot(block: (File) -> Unit) {
            val root = Files.createTempDirectory("arq-builder").toFile()
            try {
                block(root)
            } finally {
                root.deleteRecursively()
            }
        }
    }
}

/** Lee con ASM las invocaciones de las clases `TwinPromptBuilder*` bajo una raíz de `.class`. */
internal object BuilderDelegation {
    const val MISSING_SELECT = "ninguna clase TwinPromptBuilder* invoca MemorySelection.select"
    const val MISSING_SANITIZE = "ninguna clase TwinPromptBuilder* invoca MemorySelection.sanitize"

    private const val OWNER = TwinPromptBuilderDelegationTest.MEMORY_SELECTION

    /** `.class` cuyo nombre simple de clase externa empieza por `TwinPromptBuilder` (en cualquier paquete). */
    fun candidates(root: File): List<File> = root.walkTopDown()
        .filter { it.isFile && it.extension == "class" }
        .filter { it.name.substringBefore('$').removeSuffix(".class").startsWith("TwinPromptBuilder") }
        .sortedBy { it.path }
        .toList()

    /** Faltas de delegación; vacío si no hay candidatas o si entre todas invocan `select` y `sanitize`. */
    fun violations(root: File): List<String> {
        val files = candidates(root)
        if (files.isEmpty()) return emptyList()
        val invoked = files.flatMap { invocations(it.readBytes()) }.toSet()
        return buildList {
            if (OWNER to "select" !in invoked) add(MISSING_SELECT)
            if (OWNER to "sanitize" !in invoked) add(MISSING_SANITIZE)
        }
    }

    /** Pares (owner, nombre) de cada invocación real: `invoke*` y handles de `invokedynamic`. */
    fun invocations(bytes: ByteArray): Set<Pair<String, String>> {
        val found = mutableSetOf<Pair<String, String>>()
        val methodVisitor = object : MethodVisitor(Opcodes.ASM9) {
            override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
                found += owner to name
            }

            override fun visitInvokeDynamicInsn(name: String, descriptor: String, bootstrap: Handle, vararg args: Any?) {
                args.filterIsInstance<Handle>().forEach { found += it.owner to it.name }
            }
        }
        ClassReader(bytes).accept(
            object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor = methodVisitor
            },
            ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
        )
        return found
    }
}

/** Una invocación a emitir en una clase sintética. */
internal data class Call(val opcode: Int, val owner: String, val name: String) {
    companion object {
        fun virtual(owner: String, name: String) = Call(Opcodes.INVOKEVIRTUAL, owner, name)
        fun static(owner: String, name: String) = Call(Opcodes.INVOKESTATIC, owner, name)

        /** Referencia a método (`MemorySelection::sanitize`) compilada como `invokedynamic`. */
        fun indy(owner: String, name: String) = Call(Opcodes.INVOKEDYNAMIC, owner, name)
    }
}

/** Genera `.class` reales con ASM (no bytes falsos) para los casos del test. */
internal object SyntheticClass {
    private val METAFACTORY = Handle(
        Opcodes.H_INVOKESTATIC,
        "java/lang/invoke/LambdaMetafactory",
        "metafactory",
        "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;" +
            "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)" +
            "Ljava/lang/invoke/CallSite;",
        false,
    )

    fun write(root: File, internalName: String, vararg calls: Call) {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL, internalName, null, "java/lang/Object", null)
        val mv = writer.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "build", "()V", null, null)
        mv.visitCode()
        calls.forEach { call ->
            when (call.opcode) {
                Opcodes.INVOKEDYNAMIC -> {
                    val target = Handle(Opcodes.H_INVOKEVIRTUAL, call.owner, call.name, "(Ljava/lang/String;)Ljava/lang/String;", false)
                    mv.visitInvokeDynamicInsn(
                        "invoke",
                        "()Lkotlin/jvm/functions/Function1;",
                        METAFACTORY,
                        Type.getType("(Ljava/lang/Object;)Ljava/lang/Object;"),
                        target,
                        Type.getType("(Ljava/lang/String;)Ljava/lang/String;"),
                    )
                    mv.visitInsn(Opcodes.POP)
                }
                else -> mv.visitMethodInsn(call.opcode, call.owner, call.name, "()V", false)
            }
        }
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(1, 0)
        mv.visitEnd()
        writer.visitEnd()
        val file = File(root, "$internalName.class")
        file.parentFile.mkdirs()
        file.writeBytes(writer.toByteArray())
    }
}
