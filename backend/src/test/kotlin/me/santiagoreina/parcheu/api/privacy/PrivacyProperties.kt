package me.santiagoreina.parcheu.api.privacy

import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.PropertyTesting
import io.kotest.property.checkAll
import kotlin.random.Random

/**
 * Configuración común de las propiedades de privacidad (ADR-0012): al menos [ITERATIONS] casos por
 * propiedad y una semilla que siempre se imprime para poder reproducir la corrida.
 *
 * Reproducir: `PRIVACY_SEED=<semilla> ./gradlew test -PsinContenedores --tests '*.privacy.*'`
 * (o `-PprivacySeed=<semilla>`; en el IDE, `-Dprivacy.seed=<semilla>`).
 */
object PrivacyProperties {
    const val ITERATIONS = 1_000

    init {
        // La semilla se pasa explícita a propósito y no se guardan semillas fallidas en el árbol
        // (la demo de mutación debe dejarlo limpio).
        PropertyTesting.failOnSeed = false
        PropertyTesting.writeFailedSeed = false
    }

    fun seed(): Long =
        (System.getProperty("privacy.seed") ?: System.getenv("PRIVACY_SEED"))?.trim()?.toLongOrNull()
            ?: Random.nextLong()

    /**
     * Corre [block] con [ITERATIONS] valores de [arb] y devuelve cuántos casos se generaron. Si la
     * propiedad falla, el error lleva la semilla en el mensaje (Gradle lo muestra en consola).
     */
    suspend fun <A> check(property: String, arb: Arb<A>, block: suspend (A) -> Unit): Int {
        val seed = seed()
        val banner = "[privacidad] $property: semilla=$seed (repite con PRIVACY_SEED=$seed)"
        println("$banner, iteraciones=$ITERATIONS")
        var cases = 0
        try {
            checkAll(PropTestConfig(seed = seed, iterations = ITERATIONS), arb) { value ->
                cases++
                block(value)
            }
        } catch (e: Throwable) {
            throw AssertionError("$banner\n${e.message}", e)
        }
        println("[privacidad] $property: $cases casos OK")
        return cases
    }
}
