package me.santiagoreina.parcheu.api.simulation

import kotlinx.coroutines.runBlocking
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.ai.AiConfig
import me.santiagoreina.parcheu.api.ai.AiGateway
import me.santiagoreina.parcheu.api.ai.AiModule
import me.santiagoreina.parcheu.api.ai.InMemoryAiUsageStore
import me.santiagoreina.parcheu.api.graph.inMemoryGraphModule
import me.santiagoreina.parcheu.api.platform.config.Env
import me.santiagoreina.parcheu.api.quota.inMemoryQuotaModule
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.SimulationStatus
import me.santiagoreina.parcheu.contract.StartSimulation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Clock
import java.util.UUID

/**
 * Humo manual contra GCP dev: una simulación completa en Vertex AI (unas 27 llamadas, ~1-3 centavos
 * de USD) y la escritura del espejo `simulations/{id}` en Firestore (se borra al terminar).
 * **No corre en CI ni en `./gradlew test`**: el tag `vertex` se excluye salvo con `-Pvertex`.
 *
 * ```bash
 * cd backend && ./gradlew test -Pvertex -PsinContenedores --tests '*SimulationVertexSmokeTest*'
 * ```
 */
@Tag("vertex")
class SimulationVertexSmokeTest {
    @Test
    fun `una simulación real en Vertex termina en DONE con turnos de 120 tokens como máximo`(): Unit = runBlocking {
        val vars = System.getenv().toMutableMap()
        vars.putIfAbsent("AI_VERTEX_PROJECT", "parcheu-dev")
        vars.putIfAbsent("AI_PROVIDER", "gemini")
        val config = AiConfig.fromEnv(vars, Env.DEV)
        val client = AiModule.createClient(config)
        val usage = InMemoryAiUsageStore()
        // Reloj real para la pasarela (precios y presupuesto de hoy); el resto con el reloj fijo de pruebas.
        val ai = AiGateway(client, config, usage, Clock.systemUTC())
        val quota = inMemoryQuotaModule(FIXED_CLOCK)
        val graph = inMemoryGraphModule(FIXED_CLOCK)
        val repo = InMemorySimulationRepository()
        val module = inMemorySimulationModule(FIXED_CLOCK, ai, quota, graph, repo)
        val ana = UUID.randomUUID()
        val beto = UUID.randomUUID()
        repo.putParticipant(Participant(ana, "uid-ana", Mode.entries.toSet(), true, true, false, false))
        repo.putParticipant(Participant(beto, "uid-beto", Mode.entries.toSet(), true, true, false, false))
        repo.putTwin(
            TwinProfile(
                ana, 1, listOf(0.8, 0.9, 0.6, 0.7, 0.5, 0.6),
                "Es muy social y le encanta organizar planes con tiempo; prefiere hablar los problemas de frente, " +
                    "tiene un humor ligero y le va bien en la U sin obsesionarse. Le gusta probar cosas nuevas.",
                true,
            ),
        )
        repo.putTwin(
            TwinProfile(
                beto, 1, listOf(0.3, 0.2, 0.4, 0.8, 0.7, 0.5),
                "Es más bien tranquilo, improvisa los planes a última hora y evita las discusiones; es muy chistoso, " +
                    "le mete juicio al estudio y le cuesta salir de su rutina.",
                true,
            ),
        )
        repo.putMemory(ana, TwinMemory(Mode.AMISTAD, "Los jueves juega microfútbol con el parche de la facultad."))

        val summary = module.service.start(ana, StartSimulation(beto.toString(), Mode.AMISTAD))
        val sim = repo.find(UUID.fromString(summary.id))!!
        println("Estado: ${sim.status}, puntaje: ${sim.score}, error: ${sim.error}, uso: ${sim.usage}")
        sim.transcript.scenarios.forEach { s ->
            println("== ${s.title} (afinidad ${s.verdict?.affinity})")
            s.turns.forEach { println("  ${it.speaker}: ${it.text}") }
        }
        println("Informe: ${sim.report}")
        assertEquals(SimulationStatus.DONE, sim.status)
        assertTrue(sim.score!! in 0..100)
        assertTrue(sim.transcript.scenarios.all { s -> s.turns.all { it.text.length <= TwinAgent.TURN_MAX_CHARS && it.text.isNotBlank() } })
        assertTrue(usage.entries.sumOf { it.costMicros } > 0)
        (client as? AutoCloseable)?.close()
    }

    @Test
    fun `el espejo escribe simulations-id en Firestore dev con participants y progreso`(): Unit = runBlocking {
        val project = System.getenv("FIREBASE_PROJECT_ID") ?: "parcheu-dev"
        val mirror = FirestoreSimulationMirror(project)
        val id = UUID.randomUUID()
        mirror.publish(
            SimulationMirrorDoc(id, listOf("uid-a", "uid-b"), SimulationStatus.RUNNING, me.santiagoreina.parcheu.contract.Progress(1, 3, 8), null, java.time.Instant.now()),
        )
        val db = com.google.firebase.cloud.FirestoreClient.getFirestore()
        val doc = db.collection(FirestoreSimulationMirror.COLLECTION).document(id.toString()).get().get()
        println("Documento: ${doc.data}")
        assertEquals(listOf("uid-a", "uid-b"), doc.get("participants"))
        assertEquals("RUNNING", doc.getString("status"))
        assertEquals(3L, (doc.get("progress") as Map<*, *>)["turn"])
        db.collection(FirestoreSimulationMirror.COLLECTION).document(id.toString()).delete().get()
    }
}
