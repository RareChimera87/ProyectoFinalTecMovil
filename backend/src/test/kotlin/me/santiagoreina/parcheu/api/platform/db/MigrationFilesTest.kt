package me.santiagoreina.parcheu.api.platform.db

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContainDuplicates
import io.kotest.matchers.shouldBe
import java.io.File

/** Reglas de nombre de ADR-0011 sobre los archivos de `db/migration` (sin base de datos). */
class MigrationFilesTest : FunSpec({
    val dir = File(requireNotNull(javaClass.getResource("/db/migration")) { "Falta db/migration en el classpath" }.toURI())
    val files = dir.listFiles().orEmpty().map { it.name }.sorted()

    test("existen V1__esquema.sql y V2__semillas.sql") {
        files shouldContainAll listOf("V1__esquema.sql", "V2__semillas.sql")
    }

    test("las demás migraciones se nombran V<yyyyMMdd>_<HHmm>__<descripcion>.sql") {
        val dated = Regex("""V20\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])_([01]\d|2[0-3])[0-5]\d__[a-z0-9_]+\.sql""")
        val invalid = files.filterNot { it == "V1__esquema.sql" || it == "V2__semillas.sql" || dated.matches(it) }
        invalid shouldBe emptyList()
    }

    test("no hay versiones repetidas") {
        files.map { it.substringBefore("__") }.shouldNotContainDuplicates()
    }
})
