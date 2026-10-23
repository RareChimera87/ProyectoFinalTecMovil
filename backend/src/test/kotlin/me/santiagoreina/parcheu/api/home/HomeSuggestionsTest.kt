package me.santiagoreina.parcheu.api.home

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import me.santiagoreina.parcheu.api.graph.AnonIdSigner
import me.santiagoreina.parcheu.api.graph.GraphVisibilityPolicy
import me.santiagoreina.parcheu.api.graph.ThirdPartyViews
import me.santiagoreina.parcheu.api.graph.memoryRepo
import me.santiagoreina.parcheu.api.graph.person
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Visibility
import java.time.Instant
import java.util.UUID

/** Reglas de [HomeSuggestions]: candidatos directos sin Pareja y amigos en común sin fugas (ADR-0012). */
class HomeSuggestionsTest : FunSpec({
    val me = person(0, name = "Ana")
    val beto = person(1, name = "Beto")
    val caro = person(2, name = "Caro")
    val dani = person(3, name = "Dani")
    val eli = person(4, name = "Eli", visibility = Visibility.HIDDEN)
    val fede = person(5, name = "Fede")
    val gabi = person(6, name = "Gabi", deletedAt = Instant.parse("2026-10-01T00:00:00Z"))

    /** Modos activos por persona; quien no aparece tiene los tres activos. */
    fun view(repo: me.santiagoreina.parcheu.api.graph.GraphRepository, modes: Map<UUID, Set<Mode>> = emptyMap()) =
        HomeSuggestions(repo, GraphVisibilityPolicy(AnonIdSigner.forTests())) { modes[it] ?: Mode.entries.toSet() }

    test("está registrada como vista de terceros") {
        (HomeSuggestions::class in ThirdPartyViews.registered) shouldBe true
    }

    test("solo conexiones directas CONNECTED sin Pareja, sin bloqueados ni borrados, por afinidad y con límite") {
        val repo = memoryRepo(me, beto, caro, dani, eli, fede, gabi)
        repo.putEdge(me.id, beto.id, Mode.AMISTAD, EdgeState.CONNECTED, 60)
        repo.putEdge(me.id, beto.id, Mode.ESTUDIO, EdgeState.CONNECTED, 90)
        repo.putEdge(me.id, caro.id, Mode.AMISTAD, EdgeState.SIMULATED, 99)
        repo.putEdge(me.id, dani.id, Mode.PAREJA, EdgeState.CONNECTED, 97)
        repo.putEdge(me.id, eli.id, Mode.AMISTAD, EdgeState.CONNECTED, 40)
        repo.putEdge(me.id, fede.id, Mode.AMISTAD, EdgeState.CONNECTED, 80)
        repo.putEdge(me.id, gabi.id, Mode.AMISTAD, EdgeState.CONNECTED, 85)
        repo.block(fede.id, me.id)

        val all = view(repo).forHome(me.id, plus = false, exclude = emptySet(), limit = 10)
        // Beto una sola vez con su mejor arista; Eli (HIDDEN) sí: es conexión directa y ve su nombre.
        all.map { it.other.label } shouldContainExactly listOf("Beto", "Eli")
        all.first().mode shouldBe Mode.ESTUDIO
        all.first().estimatedAffinity shouldBe 90
        all.first().other.id shouldBe beto.id.toString()

        view(repo).forHome(me.id, plus = false, exclude = setOf(beto.id)).map { it.other.label } shouldContainExactly listOf("Eli")
        view(repo).forHome(me.id, plus = false, exclude = emptySet(), limit = 1).map { it.other.label } shouldContainExactly listOf("Beto")
    }

    test("amigos en común: sin Pareja, sin HIDDEN, bloqueados ni borrados; candidato HIDDEN sin conteo") {
        val repo = memoryRepo(me, beto, caro, dani, eli, fede, gabi)
        listOf(beto, caro, dani, eli, fede, gabi).forEach { repo.putEdge(me.id, it.id, Mode.AMISTAD, EdgeState.CONNECTED, 50) }
        repo.putEdge(beto.id, caro.id, Mode.AMISTAD, EdgeState.CONNECTED, 50) // cuenta
        repo.putEdge(beto.id, dani.id, Mode.PAREJA, EdgeState.CONNECTED, 50) // Pareja ajena: no cuenta
        repo.putEdge(beto.id, eli.id, Mode.AMISTAD, EdgeState.CONNECTED, 50) // HIDDEN: no cuenta
        repo.putEdge(beto.id, fede.id, Mode.ESTUDIO, EdgeState.PENDING, 50) // no conectados: no cuenta
        repo.putEdge(beto.id, gabi.id, Mode.AMISTAD, EdgeState.CONNECTED, 50) // borrada: no cuenta
        repo.putEdge(eli.id, caro.id, Mode.AMISTAD, EdgeState.CONNECTED, 50)

        val suggestions = view(repo).forHome(me.id, plus = true, exclude = emptySet(), limit = 10).associateBy { it.other.label }
        suggestions.getValue("Beto").mutualCount shouldBe 1
        suggestions.getValue("Caro").mutualCount shouldBe 1 // Beto (Eli es HIDDEN)
        suggestions.getValue("Eli").mutualCount shouldBe 0

        // Si Caro bloquea a Ana desaparece de las sugerencias y deja de contar como amiga en común.
        repo.block(caro.id, me.id)
        val blocked = view(repo).forHome(me.id, plus = true, exclude = emptySet(), limit = 10).associateBy { it.other.label }
        blocked.containsKey("Caro") shouldBe false
        blocked.getValue("Beto").mutualCount shouldBe 0
    }

    test("gratis: mutualCount nunca se calcula") {
        val repo = memoryRepo(me, beto, caro)
        repo.putEdge(me.id, beto.id, Mode.AMISTAD, EdgeState.CONNECTED, 50)
        repo.putEdge(me.id, caro.id, Mode.AMISTAD, EdgeState.CONNECTED, 50)
        repo.putEdge(beto.id, caro.id, Mode.AMISTAD, EdgeState.CONNECTED, 50)
        view(repo).candidates(me.id, withMutuals = false).forEach { it.mutuals shouldBe null }
        view(repo).forHome(me.id, plus = false, exclude = emptySet()).forEach { it.mutualCount shouldBe null }
    }

    test("solo modos activos para los dos: la arista del modo activo gana aunque tenga menos afinidad") {
        val repo = memoryRepo(me, beto, caro, dani)
        repo.putEdge(me.id, beto.id, Mode.ESTUDIO, EdgeState.CONNECTED, 90)
        repo.putEdge(me.id, beto.id, Mode.AMISTAD, EdgeState.CONNECTED, 60)
        repo.putEdge(me.id, caro.id, Mode.ESTUDIO, EdgeState.CONNECTED, 80)
        repo.putEdge(me.id, dani.id, Mode.AMISTAD, EdgeState.CONNECTED, 70)
        val all = Mode.entries.toSet()

        // Beto apagó Estudio: se le sugiere en Amistad (60) y no en Estudio (90). Caro solo tiene Estudio: sale.
        val betoOff = view(repo, mapOf(beto.id to all - Mode.ESTUDIO, caro.id to all - Mode.ESTUDIO))
            .forHome(me.id, plus = false, exclude = emptySet(), limit = 10)
        betoOff.map { it.other.label to it.mode } shouldContainExactly listOf("Dani" to Mode.AMISTAD, "Beto" to Mode.AMISTAD)
        betoOff.single { it.other.label == "Beto" }.estimatedAffinity shouldBe 60

        // Si es el observador quien apaga Amistad, solo quedan sugerencias en Estudio.
        val viewerOff = view(repo, mapOf(me.id to all - Mode.AMISTAD)).forHome(me.id, plus = false, exclude = emptySet(), limit = 10)
        viewerOff.map { it.other.label to it.mode } shouldContainExactly listOf("Beto" to Mode.ESTUDIO, "Caro" to Mode.ESTUDIO)

        // Sin modos activos (usuario sin fila o borrado) no hay sugerencias.
        view(repo, mapOf(me.id to emptySet())).forHome(me.id, plus = false, exclude = emptySet()) shouldBe emptyList()
    }

    test("con DEMO_USERS_ENABLED=false un demo conectado no se sugiere ni cuenta en la selección por persona") {
        val valentina = person(7, name = "Valentina").copy(isDemo = true)
        val repo = memoryRepo(me, beto, valentina)
        repo.putEdge(me.id, valentina.id, Mode.AMISTAD, EdgeState.CONNECTED, 95)
        repo.putEdge(me.id, beto.id, Mode.AMISTAD, EdgeState.CONNECTED, 60)
        val policy = GraphVisibilityPolicy(AnonIdSigner.forTests())

        val on = HomeSuggestions(repo, policy, demoUsersEnabled = true) { Mode.entries.toSet() }
            .forHome(me.id, plus = false, exclude = emptySet())
        on.map { it.other.label } shouldContainExactly listOf("Valentina", "Beto")
        on.first().other.isDemo shouldBe true

        val off = HomeSuggestions(repo, policy, demoUsersEnabled = false) { Mode.entries.toSet() }
            .forHome(me.id, plus = false, exclude = emptySet())
        off.map { it.other.label } shouldContainExactly listOf("Beto")
    }
})
