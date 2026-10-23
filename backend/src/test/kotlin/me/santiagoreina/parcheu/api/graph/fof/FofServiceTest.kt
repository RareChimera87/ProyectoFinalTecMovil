package me.santiagoreina.parcheu.api.graph.fof

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import me.santiagoreina.parcheu.api.graph.AnonIdSigner
import me.santiagoreina.parcheu.api.graph.GraphFilter
import me.santiagoreina.parcheu.api.graph.GraphService
import me.santiagoreina.parcheu.api.graph.GraphVisibilityPolicy
import me.santiagoreina.parcheu.api.graph.InMemoryGraphRepository
import me.santiagoreina.parcheu.api.graph.PersonRow
import me.santiagoreina.parcheu.api.graph.ThirdPartyViews
import me.santiagoreina.parcheu.api.graph.memoryRepo
import me.santiagoreina.parcheu.api.graph.person
import me.santiagoreina.parcheu.contract.ContractJson
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.FofDto
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Visibility
import java.time.Instant
import java.util.UUID

/** Criterios de E6.3 sobre la vista con el repositorio en memoria (misma semántica que el SQL). */
class FofServiceTest : FunSpec({
    val signer = AnonIdSigner.forTests()

    fun service(
        repo: InMemoryGraphRepository,
        plus: Boolean = true,
        traits: Map<UUID, List<Double>> = emptyMap(),
        demoUsersEnabled: Boolean = true,
    ) = FofService(
        InMemoryFofRepository(repo) { traits[it] },
        repo,
        GraphVisibilityPolicy(signer),
        isPlus = { plus },
        demoUsersEnabled = demoUsersEnabled,
    )

    fun InMemoryGraphRepository.connect(a: PersonRow, b: PersonRow, mode: Mode = Mode.AMISTAD) =
        putEdge(a.id, b.id, mode, EdgeState.CONNECTED, 80)

    val me = person(0, name = "Ana")
    val beto = person(1, name = "Beto")
    val caro = person(2, name = "Caro")

    test("candidato a dos saltos con amigos en común contados una vez, enmascarado con anonId") {
        val dani = person(3, name = "Dani")
        val repo = memoryRepo(me, beto, caro, dani)
        repo.connect(me, beto)
        repo.connect(me, beto, Mode.ESTUDIO) // el mismo amigo en dos modos
        repo.connect(me, caro)
        repo.connect(beto, dani)
        repo.connect(beto, dani, Mode.ESTUDIO) // y conectado al candidato en dos modos
        repo.connect(caro, dani, Mode.ESTUDIO)

        val dto = service(repo).fof(me.id)
        dto.count shouldBe 1
        val item = dto.items.shouldNotBeNull().single()
        item.mutuals shouldBe 2
        item.anonId shouldBe signer.anonId(me.id, dani.id)
        item.anonId shouldStartWith AnonIdSigner.PREFIX
        item.label shouldBe "Alguien de la UIS"
        item.mode shouldBe Mode.ESTUDIO
    }

    test("la pareja del observador nunca aparece como sugerencia y Pareja no es un salto") {
        val pareja = person(3, name = "Pareja", visibility = Visibility.NAME)
        val viaPareja = person(4, name = "Solo por Pareja", visibility = Visibility.NAME)
        val amigaDeAna = person(5, name = "Amiga de Ana", visibility = Visibility.NAME)
        val repo = memoryRepo(me, beto, pareja, viaPareja, amigaDeAna)
        repo.connect(me, beto)
        repo.connect(me, pareja, Mode.PAREJA)
        repo.connect(me, amigaDeAna)
        repo.connect(beto, pareja) // la pareja de Ana es amiga de Beto
        repo.connect(beto, viaPareja, Mode.PAREJA) // arista de Pareja ajena

        val dto = service(repo).fof(me.id)
        dto.count shouldBe 0
        dto.items.shouldNotBeNull().shouldBeEmpty()
        // La Pareja tampoco es un primer salto: la pareja de Ana no recibe a la amiga de Ana.
        service(repo).fof(pareja.id).items!!.map { it.label } shouldNotContain "Amiga de Ana"
    }

    test("un HIDDEN o borrado nunca aparece, ni como candidato ni como amigo en común") {
        val hidden = person(3, name = "Oculta", visibility = Visibility.HIDDEN)
        val deleted = person(4, name = "Borrada", deletedAt = Instant.EPOCH)
        val hiddenFriend = person(5, name = "Amiga oculta", visibility = Visibility.HIDDEN)
        val viaHidden = person(6, name = "Solo por la oculta")
        val repo = memoryRepo(me, beto, hidden, deleted, hiddenFriend, viaHidden)
        repo.connect(me, beto)
        repo.connect(beto, hidden)
        repo.connect(beto, deleted)
        repo.connect(me, hiddenFriend)
        repo.connect(hiddenFriend, viaHidden)

        service(repo).fof(me.id).count shouldBe 0
    }

    test("excluye a quien ya tiene cualquier arista con el observador (también SIMULATED o Pareja)") {
        val simulated = person(3)
        val parejaSim = person(4)
        val repo = memoryRepo(me, beto, simulated, parejaSim)
        repo.connect(me, beto)
        repo.connect(beto, simulated)
        repo.connect(beto, parejaSim)
        repo.putEdge(me.id, simulated.id, Mode.AMISTAD, EdgeState.SIMULATED, 70)
        repo.putEdge(me.id, parejaSim.id, Mode.PAREJA, EdgeState.PENDING, null)

        service(repo).fof(me.id).count shouldBe 0
    }

    test("el anonId es estable para el mismo observador y distinto entre observadores") {
        val dani = person(3, name = "Dani")
        val repo = memoryRepo(me, beto, caro, dani)
        repo.connect(me, beto)
        repo.connect(caro, beto)
        repo.connect(beto, dani)

        val first = service(repo).fof(me.id).items!!.map { it.anonId }
        val again = service(repo).fof(me.id).items!!.map { it.anonId }
        first shouldBe again
        val fromAna = service(repo).fof(me.id).items!!.single { it.anonId == signer.anonId(me.id, dani.id) }
        val fromCaro = service(repo).fof(caro.id).items!!.single { it.anonId == signer.anonId(caro.id, dani.id) }
        fromAna.anonId shouldNotBe fromCaro.anonId
        // Nunca el uuid real.
        listOf(fromAna.anonId, fromCaro.anonId).forEach { it shouldNotBe dani.id.toString() }
    }

    test("la respuesta gratis no tiene lista de candidatos (ni la clave en el JSON)") {
        val dani = person(3, name = "Dani", visibility = Visibility.NAME)
        val repo = memoryRepo(me, beto, dani)
        repo.connect(me, beto)
        repo.connect(beto, dani)

        val free = service(repo, plus = false).fof(me.id)
        free.count shouldBe 1
        free.items.shouldBeNull()
        val json = ContractJson.encodeToString(FofDto.serializer(), free)
        json shouldBe """{"count":1}"""
        // Con ParcheU+ sí viaja, y una persona NAME sale con su nombre pero con el anonId.
        val plus = service(repo, plus = true).fof(me.id).items!!.single()
        plus.label shouldBe "Dani"
        plus.anonId shouldBe signer.anonId(me.id, dani.id)
    }

    test("tras un bloqueo en cualquier sentido el bloqueado desaparece de fof del observador") {
        val dani = person(3, name = "Dani")
        val repo = memoryRepo(me, beto, dani)
        repo.connect(me, beto)
        repo.connect(beto, dani)
        service(repo).fof(me.id).count shouldBe 1

        repo.block(me.id, dani.id)
        service(repo).fof(me.id).count shouldBe 0
        repo.unblock(me.id, dani.id)
        repo.block(dani.id, me.id)
        service(repo).fof(me.id).count shouldBe 0
        repo.unblock(dani.id, me.id)

        // Bloquear al amigo en común también corta el camino.
        repo.block(me.id, beto.id)
        service(repo).fof(me.id).count shouldBe 0
        repo.unblock(me.id, beto.id)
        // Y un bloqueo entre el amigo en común y el candidato.
        repo.block(beto.id, dani.id)
        service(repo).fof(me.id).count shouldBe 0
    }

    test("orden por amigos en común y luego por afinidad estimada; afinidad = similitud de rasgos") {
        val dani = person(3)
        val eli = person(4)
        val fer = person(5)
        val repo = memoryRepo(me, beto, caro, dani, eli, fer)
        repo.connect(me, beto)
        repo.connect(me, caro)
        repo.connect(beto, dani)
        repo.connect(beto, eli)
        repo.connect(beto, fer)
        repo.connect(caro, fer)
        val base = listOf(0.5, 0.5, 0.5, 0.5, 0.5, 0.5)
        val traits = mapOf(
            me.id to base,
            dani.id to listOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
            eli.id to base,
        )
        val items = service(repo, traits = traits).fof(me.id).items!!
        items.map { it.anonId } shouldContainExactly listOf(fer, eli, dani).map { signer.anonId(me.id, it.id) }
        items.map { it.mutuals } shouldContainExactly listOf(2, 1, 1)
        items[0].estimatedAffinity shouldBe FofService.NEUTRAL_AFFINITY // Fer sin rasgos
        items[1].estimatedAffinity shouldBe 100
        items[2].estimatedAffinity shouldBe 50 // distancia √6·0,5 → 100·(1 − 0,5)
    }

    test("con la bandera de demo apagada un demo no se propone") {
        val demo = person(3).copy(isDemo = true)
        val repo = memoryRepo(me, beto, demo)
        repo.connect(me, beto)
        repo.connect(beto, demo)
        service(repo).fof(me.id).count shouldBe 1
        service(repo, demoUsersEnabled = false).fof(me.id).count shouldBe 0
    }

    test("viaId es un amigo en común del modo del candidato y coincide con su id en el ego-grafo") {
        val dani = person(3, name = "Dani")
        val repo = memoryRepo(me, beto, caro, dani)
        repo.connect(me, beto)
        repo.connect(me, caro)
        repo.connect(beto, dani, Mode.AMISTAD)
        repo.connect(caro, dani, Mode.ESTUDIO)
        repo.connect(beto, dani, Mode.ESTUDIO)

        val item = service(repo).fof(me.id).items!!.single()
        item.mode shouldBe Mode.ESTUDIO
        item.viaId shouldBe listOf(beto, caro).map { it.id.toString() }.min()
        val egoIds = GraphService(repo, GraphVisibilityPolicy(signer)).graph(me.id, GraphFilter()).nodes.map { it.id }
        egoIds shouldContain item.viaId
    }

    test("viaId nunca sale de una arista de Pareja aunque el repositorio la deje pasar (segunda barrera)") {
        val dani = person(3, name = "Dani")
        val repo = memoryRepo(me, beto, caro, dani)
        repo.connect(me, beto)
        repo.connect(me, caro)
        repo.connect(beto, dani)
        // Un SQL defectuoso que devolviera además la arista de Pareja Caro–Dani.
        val leaky = object : FofRepository {
            override suspend fun candidates(viewer: UUID, limit: Int) = listOf(
                FofRow(dani, 2, listOf(FofLink(caro.id, Mode.PAREJA), FofLink(beto.id, Mode.AMISTAD)), null),
            )

            override suspend fun traits(userId: UUID): List<Double>? = null
        }
        val item = FofService(leaky, repo, GraphVisibilityPolicy(signer), isPlus = { true }).fof(me.id).items!!.single()
        item.mutuals shouldBe 1
        item.viaId shouldBe beto.id.toString()
    }

    test("fof está registrada como vista de terceros") {
        ThirdPartyViews.names() shouldContain "FofService"
    }

    test("la lista se corta en 30 y count cuenta a todos") {
        val people = (10 until 50).map { person(it) }
        val repo = memoryRepo(me, beto, *people.toTypedArray())
        repo.connect(me, beto)
        people.forEach { repo.connect(beto, it) }
        val dto = service(repo).fof(me.id)
        dto.count shouldBe 40
        dto.items!!.size shouldBe FofService.MAX_ITEMS
    }
})
