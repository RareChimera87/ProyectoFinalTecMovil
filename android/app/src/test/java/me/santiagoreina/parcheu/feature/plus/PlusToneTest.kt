package me.santiagoreina.parcheu.feature.plus

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tono honesto de ParcheU+ (E7.3, ADR-0029): ninguna cadena de `feature/plus` usa urgencia falsa ni
 * contadores con cifras, y la UI de G1-G4 no usa temporizadores. Recorre los `strings*plus*.xml` y las
 * fuentes del paquete (los tests de Gradle corren con `src/` relativo a `android/app`).
 */
class PlusToneTest {
    private val moduleDir = File(System.getProperty("user.dir"))
    private val valuesDir = File(moduleDir, "src/main/res/values")
    private val sourceDir =
        File(moduleDir, "src/main/java/me/santiagoreina/parcheu/feature/plus")

    private val plusStringFiles: List<File> =
        valuesDir.listFiles { f ->
            f.name.matches(Regex("strings.*plus.*\\.xml"))
        }?.toList().orEmpty()

    private val sources: List<File> = sourceDir.walkTopDown().filter {
        it.extension == "kt"
    }.toList()

    /** Urgencia falsa y escasez inventada. */
    private val falseUrgency =
        listOf(
            "solo quedan",
            "sólo quedan",
            "últimas",
            "ultimas",
            "último día",
            "ultimo dia",
            "oferta termina",
            "la oferta termina",
            "termina hoy",
            "termina pronto",
            "personas viendo",
            "personas están viendo",
            "no te lo pierdas",
            "aprovecha ya",
            "por tiempo limitado",
            "se agota",
            "cupos limitados"
        ).map { Regex(Regex.escape(it), RegexOption.IGNORE_CASE) }

    /** Contadores con cifras: "quedan 3", "12 personas", "5 compraron", cuenta atrás "00:59". */
    private val counters =
        listOf(
            Regex("(quedan?|faltan?|solo|sólo|últim[oa]s?)\\s+\\d", RegexOption.IGNORE_CASE),
            Regex(
                "\\d+\\s+(personas|usuarios|usuarias|estudiantes|suscriptores|compraron|viendo)",
                RegexOption.IGNORE_CASE
            ),
            Regex("\\d{1,2}:\\d{2}(:\\d{2})?"),
            Regex("\\d+\\s*%\\s*(de\\s+)?(descuento|dto|off)", RegexOption.IGNORE_CASE)
        )

    private fun stringValues(): List<Pair<String, String>> {
        val entry =
            Regex("<string[^>]*name=\"([^\"]+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
        return plusStringFiles.flatMap { file ->
            entry.findAll(file.readText()).map { it.groupValues[1] to it.groupValues[2] }.toList()
        }
    }

    @Test
    fun theTestSeesTheStringsAndTheSources() {
        assertTrue("No se encontró strings_plus.xml en $valuesDir", plusStringFiles.isNotEmpty())
        assertTrue("No hay cadenas de ParcheU+", stringValues().size > 50)
        assertTrue("No se encontraron fuentes en $sourceDir", sources.size >= 8)
    }

    @Test
    fun noFalseUrgencyInStrings() {
        val hits =
            stringValues().flatMap { (name, value) ->
                falseUrgency.filter { it.containsMatchIn(value) }.map { "$name: \"$value\"" }
            }
        assertTrue("Urgencia falsa en strings: $hits", hits.isEmpty())
    }

    @Test
    fun noCountersWithFiguresInStrings() {
        val hits =
            stringValues().flatMap { (name, value) ->
                counters.filter { it.containsMatchIn(value) }.map { "$name: \"$value\"" }
            }
        assertTrue("Contadores con cifras en strings: $hits", hits.isEmpty())
    }

    @Test
    fun noFalseUrgencyOrCountersInKotlinLiterals() {
        val literal = Regex("\"([^\"\\n]*)\"")
        val hits =
            sources
                .filterNot { it.name.endsWith("Test.kt") }
                .flatMap { file ->
                    literal.findAll(file.readText()).map { it.groupValues[1] }.filter { text ->
                        falseUrgency.any { it.containsMatchIn(text) } ||
                            counters.any { it.containsMatchIn(text) }
                    }.map { "${file.name}: \"$it\"" }.toList()
                }
        assertTrue("Textos engañosos en el código: $hits", hits.isEmpty())
    }

    /** Detecta las palabras de urgencia que el propio test busca, para que no pase en vacío. */
    @Test
    fun theRulesActuallyCatchBadCopy() {
        val bad =
            listOf(
                "Solo quedan 3 cupos",
                "Oferta termina hoy",
                "12 personas viendo esto",
                "Últimas horas"
            )
        bad.forEach { text ->
            assertTrue(
                "La regla no detecta \"$text\"",
                falseUrgency.any { it.containsMatchIn(text) } ||
                    counters.any { it.containsMatchIn(text) }
            )
        }
    }

    /** Composables de G1-G4 que pueden animar o programar efectos: ninguno (la lista blanca está vacía). */
    private val composablesAllowedToUseTimers: Set<String> = emptySet()

    private val timerApis =
        listOf(
            "CountDownTimer",
            "java.util.Timer",
            "Timer(",
            "delay(",
            "postDelayed",
            "Handler(",
            "rememberInfiniteTransition",
            "InfiniteTransition",
            "ticker(",
            "countdown",
            "cuentaRegresiva",
            "System.currentTimeMillis",
            "Clock.System"
        )

    @Test
    fun noScreenUsesTimers() {
        val offenders =
            sources
                .filterNot { it.name.endsWith("Test.kt") }
                .flatMap { file ->
                    val code = file.readLines().filterNot {
                        it.trimStart().startsWith("*") ||
                            it.trimStart().startsWith("//") ||
                            it.trimStart().startsWith("/*")
                    }
                    timerApis.filter { api -> code.any { it.contains(api, ignoreCase = true) } }
                        .map { "${file.name}: $it" }
                }
                .filterNot { off -> composablesAllowedToUseTimers.any { off.contains(it) } }
        assertTrue("Temporizadores en G1-G4: $offenders", offenders.isEmpty())
    }
}
