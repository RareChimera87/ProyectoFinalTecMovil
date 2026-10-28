package me.santiagoreina.parcheu.api.graph.communities

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.ai.FakeLlmClient
import me.santiagoreina.parcheu.api.ai.testGateway
import me.santiagoreina.parcheu.api.graph.InMemoryGraphRepository
import me.santiagoreina.parcheu.api.platform.BogotaZone
import me.santiagoreina.parcheu.api.platform.tasks.FakeTaskQueue
import me.santiagoreina.parcheu.api.platform.tasks.QueueKind
import me.santiagoreina.parcheu.api.platform.tasks.TaskQueue
import me.santiagoreina.parcheu.api.platform.tasks.TaskRequest
import me.santiagoreina.parcheu.api.quota.MovableClock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID

/** Job semanal y nombre de comunidades (E6.4) con repositorio, cola y modelo Fake. */
class CommunityServiceTest : FunSpec({
    val monday: LocalDate = LocalDate.parse("2026-10-19") // FIXED_CLOCK es el domingo 25 de esa semana

    class Fixture(clock: Clock = FIXED_CLOCK, traits: Map<UUID, List<Double>> = emptyMap()) {
        val graph = InMemoryGraphRepository()
        val repo = InMemoryCommunityRepository(graph) { traits[it] }
        val queue = FakeTaskQueue()
        val llm = FakeLlmClient(textResponder = { "\"Los del tinto de las 7\"\nporque sí" })
        val service = CommunityService(repo, queue, CommunityNamer(testGateway(llm)), clock)
    }

    test("el job guarda las comunidades de la semana y encola un name-community por cada una") {
        val f = Fixture()
        val a = f.graph.group(1, 4).also { f.graph.clique(it) }
        val b = f.graph.group(10, 3).also { f.graph.clique(it) }

        val summary = f.service.runWeekly()

        summary shouldBe CommunityRunSummary(monday, communities = 2, kept = 0, created = 2, removed = 0)
        f.repo.all().map { it.members } shouldContainExactlyInAnyOrder listOf(a.map { it.id }.toSet(), b.map { it.id }.toSet())
        f.repo.all().all { it.week == monday && it.name == CommunityDetector.PENDING_NAME } shouldBe true
        f.queue.tasks.map { it.path }.distinct() shouldContainExactly listOf("/tasks/name-community")
        f.queue.tasks.map { it.queue }.distinct() shouldContainExactly listOf(QueueKind.AI_LIGHT)
        f.queue.tasks shouldHaveSize 2
        // Los miembros también alimentan el filtro `community` del ego-grafo.
        f.graph.communities.values.map { it.toSet() } shouldContainExactlyInAnyOrder f.repo.all().map { it.members }
    }

    test("repetir el job con los mismos datos no cambia ids ni miembros") {
        val f = Fixture()
        f.graph.group(1, 4).also { f.graph.clique(it) }
        f.service.runWeekly()
        val before = f.repo.all()
        f.service.nameCommunity(before.single().id)
        val named = f.repo.all()

        val summary = f.service.runWeekly()

        summary.kept shouldBe 1
        summary.created shouldBe 0
        f.repo.all() shouldBe named
    }

    test("name-community pone el nombre del modelo, sin comillas, y es idempotente") {
        val f = Fixture()
        f.graph.group(1, 3).also { f.graph.clique(it) }
        f.service.runWeekly()
        val id = f.repo.all().single().id

        f.service.nameCommunity(id)
        f.repo.find(id)!!.name shouldBe "Los del tinto de las 7"

        f.llm.textResponder = { "Otro nombre" }
        f.service.nameCommunity(id)
        f.repo.find(id)!!.name shouldBe "Los del tinto de las 7"
        f.llm.calls shouldBe 1
        f.service.nameCommunity(UUID.randomUUID()) // una comunidad que ya no existe no falla
    }

    test("si el modelo no devuelve nada utilizable el nombre sale de los rasgos") {
        val traits = (1..3).associate { UUID.fromString("00000000-0000-4000-8000-%012d".format(it)) to listOf(0.9, 0.5, 0.5, 0.5, 0.5, 0.5) }
        val f = Fixture(traits = traits)
        f.graph.group(1, 3).also { f.graph.clique(it) }
        f.llm.textResponder = { "   \n  " }
        f.service.runWeekly()
        val id = f.repo.all().single().id

        f.service.nameCommunity(id)

        f.repo.find(id)!!.name shouldBe "Los muy sociables"
    }

    test("el prompt del nombre no contiene nombres ni correos") {
        val f = Fixture()
        val people = f.graph.group(1, 4).also { f.graph.clique(it, mode = me.santiagoreina.parcheu.contract.Mode.ESTUDIO) }
        val identifying = people.flatMap { listOf(it.displayName!!, it.displayName!!.substringBefore(' '), it.id.toString(), it.photoUrl!!) } +
            listOf("@", ".edu.co", "mailto")
        f.service.runWeekly()

        f.service.nameCommunity(f.repo.all().single().id)

        val request = f.llm.lastRequest.shouldNotBeNull()
        val text = "${request.system}\n${request.prompt}"
        identifying.forEach { text shouldNotContain it }
        // Lo único que el modelo recibe es el tamaño, los rasgos agregados y el modo.
        request.prompt shouldBe CommunityNamer.prompt(CommunityProfile(4, emptyList(), listOf("Estudio")))
    }

    test("los rasgos agregados salen de los gemelos y se limitan a tres") {
        val high = listOf(0.9, 0.1, 0.5, 0.8, 0.2, 0.55)
        val profile = CommunityProfile.of(3, List(3) { high }, emptyList())
        profile.traits shouldContainExactly listOf("muy sociables", "espontáneos", "de mucho humor")
        CommunityProfile.of(3, List(3) { List(6) { 0.5 } }, emptyList()).traits shouldBe emptyList()
        CommunityNamer.clean("  «Los del parque»  ") shouldBe "Los del parque"
        CommunityNamer.clean("x".repeat(80))!!.length shouldBe CommunityNamer.MAX_LENGTH
        CommunityNamer.clean("\n  \n") shouldBe null
    }

    test("runIfDue corre desde el lunes 03:00 de Bogotá y una sola vez por semana") {
        fun at(day: Int, hour: Int, minute: Int): Clock =
            Clock.fixed(ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, BogotaZone).toInstant(), BogotaZone)
        val graph = InMemoryGraphRepository().also { g -> g.group(1, 3).also { g.clique(it) } }
        val repo = InMemoryCommunityRepository(graph)
        fun service(clock: Clock) = CommunityService(repo, FakeTaskQueue(), CommunityNamer(testGateway()), clock)

        service(at(26, 2, 59)).runIfDue() shouldBe false // lunes 02:59
        // Domingo 25: la semana del 19 ya pasó su lunes 03:00 y no hay filas, así que corre (recupera una corrida perdida).
        service(at(25, 23, 0)).runIfDue() shouldBe true
        repo.all().single().week shouldBe LocalDate.parse("2026-10-19")

        val next = service(at(26, 3, 0))
        next.runIfDue() shouldBe true
        next.runIfDue() shouldBe false
        service(at(28, 12, 0)).runIfDue() shouldBe false // otro proceso: ya hay filas de la semana
        repo.all().single().week shouldBe LocalDate.parse("2026-10-26")
    }

    test("runIfDue reencola las comunidades sin nombre en la salida temprana (reinicio o name-community agotado)") {
        val f = Fixture()
        f.graph.group(1, 3).also { f.graph.clique(it) }
        f.service.runWeekly()
        f.queue.tasks shouldHaveSize 1
        val id = f.repo.all().single().id
        // Simula la pérdida de la cola: un proceso nuevo ve las filas de la semana y reencola el nombre pendiente.
        val queue2 = FakeTaskQueue()
        CommunityService(f.repo, queue2, CommunityNamer(testGateway(f.llm)), FIXED_CLOCK).runIfDue() shouldBe false
        queue2.tasks.single().name shouldBe CommunityService.nameTask(id).name
        // Ya nombrada, no hay nada que reencolar.
        f.service.nameCommunity(id)
        val queue3 = FakeTaskQueue()
        CommunityService(f.repo, queue3, CommunityNamer(testGateway(f.llm)), FIXED_CLOCK).runIfDue() shouldBe false
        queue3.tasks shouldHaveSize 0
    }

    test("runIfDue reencola un name-community pendiente como máximo una vez al día por comunidad") {
        val clock = MovableClock(Instant.parse("2026-10-22T15:00:00Z")) // jueves de la semana del 19, que ya corrió
        val f = Fixture(clock)
        f.graph.group(1, 3).also { f.graph.clique(it) }
        f.service.runWeekly()
        val id = f.repo.all().single().id
        // name-community falla de forma persistente (cuota o error del LLM): la comunidad sigue PENDING_NAME.
        val enqueued = mutableListOf<String>()
        val queue = object : TaskQueue {
            override suspend fun enqueue(task: TaskRequest) {
                enqueued += task.name
            }
        }
        val service = CommunityService(f.repo, queue, CommunityNamer(testGateway(f.llm)), clock)

        service.runIfDue() shouldBe false // primer barrido del proceso: reencola
        enqueued shouldBe listOf(CommunityService.nameTask(id).name)
        repeat(23) { // las horas siguientes del día no reencolan
            clock.now = clock.now.plus(Duration.ofHours(1))
            service.runIfDue() shouldBe false
        }
        enqueued shouldHaveSize 1
        clock.now = clock.now.plus(Duration.ofHours(1)) // 24 h después vuelve a reencolar, una sola vez
        service.runIfDue() shouldBe false
        service.runIfDue() shouldBe false
        enqueued shouldHaveSize 2
    }

    test("un encolado fallido de name-community no cuenta como reencolado: el siguiente barrido reintenta") {
        val clock = MovableClock(Instant.parse("2026-10-22T15:00:00Z"))
        val f = Fixture(clock)
        f.graph.group(1, 3).also { f.graph.clique(it) }
        f.service.runWeekly()
        val id = f.repo.all().single().id
        val queue = FakeTaskQueue()
        val service = CommunityService(f.repo, queue, CommunityNamer(testGateway(f.llm)), clock)
        queue.failWith = IllegalStateException("cola caída")
        service.runIfDue() shouldBe false
        queue.tasks shouldHaveSize 0
        queue.failWith = null
        service.runIfDue() shouldBe false // misma hora: reintenta porque el anterior no se encoló
        queue.tasks.single().name shouldBe CommunityService.nameTask(id).name
    }

    test("runWeekly siembra el tope: runIfDue de la misma hora no reencola lo que acaba de encolar") {
        val clock = MovableClock(Instant.parse("2026-10-22T15:00:00Z"))
        val f = Fixture(clock)
        f.graph.group(1, 3).also { f.graph.clique(it) }
        f.service.runWeekly()
        val enqueued = mutableListOf<String>()
        val counting = object : TaskQueue {
            override suspend fun enqueue(task: TaskRequest) { enqueued += task.name }
        }
        val service = CommunityService(f.repo, counting, CommunityNamer(testGateway(f.llm)), clock)
        service.runWeekly()
        service.runIfDue() shouldBe false
        enqueued shouldHaveSize 1
    }
})
