package me.santiagoreina.parcheu.e2e

import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Smoke e2e (E10.6) contra la API desplegada en dev. Lo corre `deploy-dev` con `./gradlew e2eTest` justo después
 * del despliegue; **no corre en `./gradlew test`** (etiqueta `e2e`). Usa una simulación real con Flash por corrida.
 *
 * Sin las variables de [E2eConfig] se omite; con `E2E_REQUIRED=true` (así lo fija deploy-dev) falla, para que un
 * secreto mal puesto no deje el despliegue en verde sin haber probado nada.
 */
@Tag("e2e")
class E2eSmokeTest {
    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    fun `dos usuarios de prueba recorren sesion, gemelo, fila, simulacion, decision y chat`() {
        val config = E2eConfig.fromEnv()
        if (config == null) {
            val message = "Faltan variables del smoke e2e: ${E2eConfig.missing().joinToString()}"
            if (E2eConfig.required()) fail<Unit>(message)
            assumeTrue(false, message)
            return
        }
        val scenario = SmokeScenario(
            api = ApiClient(config.baseUrl),
            auth = FirebaseTestAuth(config.projectId, config.apiKey, config.serviceAccount),
            reader = FirestoreChatReader(config.projectId),
        )
        var failure: Throwable? = null
        try {
            val result = scenario.run()
            println("[e2e] OK en ${result.elapsed.seconds}s: match ${result.matchId}, simulación ${result.simulationId} (puntaje ${result.score}), chat ${result.chatId}")
            System.getenv("GITHUB_STEP_SUMMARY")?.takeIf { it.isNotBlank() }?.let {
                File(it).appendText("### Smoke e2e: OK en ${result.elapsed.seconds}s (puntaje ${result.score})\n")
            }
        } catch (e: Throwable) {
            failure = e
        }
        try {
            scenario.cleanup()
        } catch (e: Throwable) {
            if (failure == null) failure = e else failure.addSuppressed(e)
        }
        failure?.let { throw it }
    }
}
