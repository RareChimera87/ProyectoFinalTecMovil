package me.santiagoreina.parcheu.api.matching

import kotlinx.coroutines.async
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import me.santiagoreina.parcheu.api.identity.deletion.ClosedMatch
import me.santiagoreina.parcheu.api.platform.errors.AgeRequired
import me.santiagoreina.parcheu.api.platform.errors.Conflict
import me.santiagoreina.parcheu.api.platform.errors.NotFound
import me.santiagoreina.parcheu.api.platform.errors.QuotaExceeded
import me.santiagoreina.parcheu.api.platform.errors.ValidationFailed
import me.santiagoreina.parcheu.api.simulation.TwinProfile
import me.santiagoreina.parcheu.contract.AnonProfile
import me.santiagoreina.parcheu.contract.ContractJson
import me.santiagoreina.parcheu.contract.MatchChoice
import me.santiagoreina.parcheu.contract.MatchDto
import me.santiagoreina.parcheu.contract.MatchStatus
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.NotificationKinds
import me.santiagoreina.parcheu.contract.QuotaKind
import me.santiagoreina.parcheu.contract.SimulationOrigin
import me.santiagoreina.parcheu.api.platform.errors.Suspended
import me.santiagoreina.parcheu.contract.TicketStatus
import java.time.Duration
import java.util.UUID

/** Criterios de E4.2 sobre el servicio con repositorio en memoria y reloj movible. */
class MatchingServiceTest : FunSpec({
    test("dos usuarios del mismo modo quedan emparejados; el primero espera y el segundo recibe MATCHED") {
        val f = MatchFixture()
        val ana = f.person("ana")
        val beto = f.person("beto")
        val first = f.service.enqueue(ana, Mode.AMISTAD)
        first.status shouldBe TicketStatus.WAITING
        first.matchId.shouldBeNull()
        val second = f.service.enqueue(beto, Mode.AMISTAD)
        second.status shouldBe TicketStatus.MATCHED
        second.matchId.shouldNotBeNull()
        // Ana ve el mismo match desde su ticket.
        f.service.ticket(ana).run {
            status shouldBe TicketStatus.MATCHED
            matchId shouldBe second.matchId
        }
        f.service.match(ana, second.matchId!!).status shouldBe MatchStatus.PENDING_CHOICE
    }

    test("de modos distintos no se emparejan") {
        val f = MatchFixture()
        val ana = f.person("ana")
        val beto = f.person("beto")
        f.service.enqueue(ana, Mode.AMISTAD).status shouldBe TicketStatus.WAITING
        f.service.enqueue(beto, Mode.ESTUDIO).status shouldBe TicketStatus.WAITING
        f.matchRepo.allMatches().shouldBeEmpty()
    }

    test("los bloqueados (en cualquier sentido) no se emparejan") {
        val f = MatchFixture()
        val ana = f.person("ana")
        val beto = f.person("beto")
        val caro = f.person("caro")
        f.matchRepo.block(beto, ana)
        f.service.enqueue(ana, Mode.AMISTAD)
        f.service.enqueue(beto, Mode.AMISTAD).status shouldBe TicketStatus.WAITING
        // Caro sí puede: queda con Ana (la más antigua que no la bloquea ni ella a Caro).
        f.service.enqueue(caro, Mode.AMISTAD).status shouldBe TicketStatus.MATCHED
        f.matchRepo.allMatches().single().let { setOf(it.userA, it.userB) } shouldBe setOf(ana, caro)
    }

    test("quienes ya están conectados no se emparejan") {
        val f = MatchFixture()
        val ana = f.person("ana")
        val beto = f.person("beto")
        f.matchRepo.connect(ana, beto)
        f.service.enqueue(ana, Mode.ESTUDIO)
        f.service.enqueue(beto, Mode.ESTUDIO).status shouldBe TicketStatus.WAITING
    }

    test("nadie se empareja consigo mismo ni entra dos veces a la fila") {
        val f = MatchFixture()
        val ana = f.person("ana")
        f.service.enqueue(ana, Mode.AMISTAD)
        shouldThrow<Conflict> { f.service.enqueue(ana, Mode.AMISTAD) }
        f.matchRepo.allMatches().shouldBeEmpty()
    }

    test("prioridad Plus: entre dos personas esperando, quien llega se empareja con la Plus aunque la gratis sea más antigua") {
        val f = MatchFixture()
        val libre = f.person("libre")
        val plus = f.person("plus").also { f.plus(it) }
        val llegada = f.person("llegada")
        // Libre y Plus no pueden emparejarse entre sí, así que las dos quedan esperando.
        f.matchRepo.block(libre, plus)
        f.service.enqueue(libre, Mode.AMISTAD)
        f.clock.now = f.clock.now.plusSeconds(60)
        f.service.enqueue(plus, Mode.AMISTAD).status shouldBe TicketStatus.WAITING
        f.clock.now = f.clock.now.plusSeconds(60)
        f.service.enqueue(llegada, Mode.AMISTAD).status shouldBe TicketStatus.MATCHED
        f.matchRepo.allMatches().single().let { setOf(it.userA, it.userB) } shouldBe setOf(plus, llegada)
        f.service.ticket(libre).status shouldBe TicketStatus.WAITING
    }

    test("test de concurrencia: 10 usuarios encolados en paralelo producen 5 matches sin duplicados") {
        val f = MatchFixture()
        val users = (1..10).map { f.person("u$it") }
        runBlocking {
            users.map { id -> async(Dispatchers.Default) { f.service.enqueue(id, Mode.AMISTAD) } }.awaitAll()
        }
        val matches = f.matchRepo.allMatches()
        matches shouldHaveSize 5
        val members = matches.flatMap { listOf(it.userA, it.userB) }
        members shouldHaveSize 10
        members.toSet() shouldContainExactlyInAnyOrder users.toSet()
        // Cada persona quedó en un único match y su ticket lo refleja.
        users.forEach { id ->
            val t = runBlocking { f.service.ticket(id) }
            t.status shouldBe TicketStatus.MATCHED
            matches.count { it.involves(id) } shouldBe 1
        }
    }

    test("cancelar no crea usage_events y borra el espejo") {
        val f = MatchFixture()
        val ana = f.person("ana")
        f.service.enqueue(ana, Mode.AMISTAD)
        f.service.cancel(ana)
        f.usage.charges.shouldBeEmpty()
        shouldThrow<NotFound> { f.service.ticket(ana) }
        f.mirror.removed shouldBe listOf("uid-ana")
        // Cancelar de nuevo es idempotente.
        f.service.cancel(ana)
        // Y quien cancela no estorba: Beto queda esperando.
        f.service.enqueue(f.person("beto"), Mode.AMISTAD).status shouldBe TicketStatus.WAITING
    }

    test("el cupo diario se cobra al encontrar match, no al entrar a la fila (ref_id = matchId:userId)") {
        val f = MatchFixture()
        val ana = f.person("ana")
        val beto = f.person("beto")
        f.service.enqueue(ana, Mode.AMISTAD)
        f.usage.charges.shouldBeEmpty()
        val t = f.service.enqueue(beto, Mode.AMISTAD)
        f.usage.charges shouldContainExactlyInAnyOrder listOf("${t.matchId}:$ana", "${t.matchId}:$beto")
        f.quota.service.status(ana).randomMatch.used shouldBe 1
        // Con el match resuelto, sin cupo hoy no se puede volver a la fila (402)...
        f.service.choose(ana, t.matchId!!, MatchChoice.SIMULATION)
        shouldThrow<QuotaExceeded> { f.service.enqueue(ana, Mode.AMISTAD) }
        // ...y al día siguiente (00:00 Bogotá) sí.
        f.clock.now = f.clock.now.plus(Duration.ofHours(24))
        f.service.enqueue(ana, Mode.AMISTAD).status shouldBe TicketStatus.WAITING
    }

    test("sin cupo no se entra a la fila: 402 y ningún ticket") {
        val f = MatchFixture()
        val ana = f.person("ana")
        f.quota.service.consume(ana, QuotaKind.RANDOM_MATCH, "otro-uso")
        shouldThrow<QuotaExceeded> { f.service.enqueue(ana, Mode.AMISTAD) }
        shouldThrow<NotFound> { f.service.ticket(ana) }
    }

    test("si la pareja que esperaba ya gastó su cupo, su ticket vence y quien llegó sigue buscando") {
        val f = MatchFixture()
        val ana = f.person("ana")
        val beto = f.person("beto")
        val caro = f.person("caro")
        f.service.enqueue(ana, Mode.AMISTAD)
        // Ana gasta su cupo por otro medio mientras espera.
        f.quota.service.consume(ana, QuotaKind.RANDOM_MATCH, "otro-uso")
        f.service.enqueue(beto, Mode.AMISTAD).status shouldBe TicketStatus.WAITING
        f.service.ticket(ana).status shouldBe TicketStatus.EXPIRED
        f.service.enqueue(caro, Mode.AMISTAD).status shouldBe TicketStatus.MATCHED
        f.matchRepo.allMatches().filter { it.status == MatchStatus.PENDING_CHOICE }.single().let { setOf(it.userA, it.userB) } shouldBe setOf(beto, caro)
    }

    test("Pareja exige edad verificada y nunca entra un usuario demo") {
        val f = MatchFixture()
        val sinEdad = f.person("sinedad", ageVerified = false)
        val demo = f.person("demo", isDemo = true)
        shouldThrow<AgeRequired> { f.service.enqueue(sinEdad, Mode.PAREJA) }
        shouldThrow<ValidationFailed> { f.service.enqueue(demo, Mode.PAREJA) }
    }

    test("el modo debe estar activo") {
        val f = MatchFixture()
        val ana = f.person("ana", modes = setOf(Mode.AMISTAD))
        shouldThrow<ValidationFailed> { f.service.enqueue(ana, Mode.ESTUDIO) }
    }

    fun MatchFixture.seedTicket(user: UUID, mode: Mode) = runBlocking {
        // Ticket sembrado directamente en el repositorio, como haría el job de demos (E4.9).
        matchRepo.enqueueAndPair(QueueEntry(user, mode, 0, clock.now.minusSeconds(30), clock.now.plus(MatchingRules.TICKET_TTL)), true, clock.now)
    }

    test("DEMO_USERS_ENABLED=false: nunca se empareja con un usuario is_demo") {
        val f = MatchFixture(flags = MatchingFlags(demoUsersEnabled = false, scenarioModeEnabled = false))
        val demo = f.person("demo", isDemo = true)
        val ana = f.person("ana")
        // El demo no puede ni entrar a la fila por la API con la bandera apagada.
        shouldThrow<ValidationFailed> { f.service.enqueue(demo, Mode.AMISTAD) }
        f.seedTicket(demo, Mode.AMISTAD)
        f.service.enqueue(ana, Mode.AMISTAD).status shouldBe TicketStatus.WAITING
        f.matchRepo.allMatches().shouldBeEmpty()
    }

    test("DEMO_USERS_ENABLED=true: se empareja con demo en Amistad, pero nunca en Pareja") {
        val f = MatchFixture(flags = MatchingFlags(demoUsersEnabled = true, scenarioModeEnabled = false))
        val demoAmistad = f.person("demo1", isDemo = true)
        val demoPareja = f.person("demo2", isDemo = true)
        val ana = f.person("ana")
        val beto = f.person("beto")
        f.seedTicket(demoPareja, Mode.PAREJA)
        f.service.enqueue(ana, Mode.PAREJA).status shouldBe TicketStatus.WAITING
        f.matchRepo.allMatches().shouldBeEmpty()
        f.seedTicket(demoAmistad, Mode.AMISTAD)
        f.service.enqueue(beto, Mode.AMISTAD).status shouldBe TicketStatus.MATCHED
        f.matchRepo.allMatches().single().let { setOf(it.userA, it.userB) } shouldBe setOf(demoAmistad, beto)
        f.service.match(beto, f.service.ticket(beto).matchId!!).other.isDemo shouldBe true
    }

    test("un ticket WAITING con más de 24 h pasa a EXPIRED, se refleja en el espejo y no cobra cupo") {
        val f = MatchFixture()
        val ana = f.person("ana")
        f.service.enqueue(ana, Mode.AMISTAD)
        f.clock.now = f.clock.now.plus(Duration.ofHours(24)).plusSeconds(1)
        f.service.ticket(ana).status shouldBe TicketStatus.EXPIRED
        f.mirror.last("uid-ana")!!.status shouldBe TicketStatus.EXPIRED
        f.usage.charges.shouldBeEmpty()
        f.notifier.sent.single().notification.kind shouldBe NotificationKinds.MATCH_EXPIRED
        // Un ticket vencido no se empareja: Beto queda esperando y puede volver a intentar.
        val beto = f.person("beto")
        f.service.enqueue(beto, Mode.AMISTAD).status shouldBe TicketStatus.WAITING
        // Ana puede volver a entrar a la fila (reemplaza su ticket vencido) y ahora sí queda con Beto.
        f.service.enqueue(ana, Mode.AMISTAD).status shouldBe TicketStatus.MATCHED
    }

    test("el barrido vence tickets y matches sin intervención de nadie") {
        val f = MatchFixture()
        val ana = f.person("ana")
        f.service.enqueue(ana, Mode.AMISTAD)
        f.clock.now = f.clock.now.plus(Duration.ofHours(25))
        f.service.sweep() shouldBe SweepResult(expiredTickets = 1, settledMatches = 0, retriedSimulations = 0)
        f.mirror.last("uid-ana")!!.status shouldBe TicketStatus.EXPIRED
    }

    test("el espejo matchTickets/{uid} refleja WAITING -> MATCHED en ambas personas") {
        val f = MatchFixture()
        val ana = f.person("ana")
        val beto = f.person("beto")
        f.service.enqueue(ana, Mode.AMISTAD)
        f.mirror.last("uid-ana")!!.status shouldBe TicketStatus.WAITING
        val t = f.service.enqueue(beto, Mode.AMISTAD)
        f.mirror.last("uid-ana")!!.run {
            status shouldBe TicketStatus.MATCHED
            matchId.toString() shouldBe t.matchId
        }
        f.mirror.last("uid-beto")!!.status shouldBe TicketStatus.MATCHED
        f.notifier.sent.map { it.userId } shouldContainExactlyInAnyOrder listOf(ana, beto)
        f.notifier.sent.all { it.notification.kind == NotificationKinds.MATCH_FOUND } shouldBe true
    }

    // ---- Elección --------------------------------------------------------------------------

    fun MatchFixture.pair(): Triple<UUID, UUID, String> {
        val ana = person("ana")
        val beto = person("beto")
        runBlocking { service.enqueue(ana, Mode.AMISTAD) }
        val matchId = runBlocking { service.enqueue(beto, Mode.AMISTAD) }.matchId!!
        return Triple(ana, beto, matchId)
    }

    test("ambos eligen SCENARIO con la bandera encendida: estado SCENARIO y se crea el chat") {
        val f = MatchFixture(flags = MatchingFlags(demoUsersEnabled = true, scenarioModeEnabled = true))
        val (ana, beto, id) = f.pair()
        f.service.choose(ana, id, MatchChoice.SCENARIO).status shouldBe MatchStatus.PENDING_CHOICE
        val done = f.service.choose(beto, id, MatchChoice.SCENARIO)
        done.status shouldBe MatchStatus.SCENARIO
        done.scenarioAvailable shouldBe true
        val chat = f.matchRepo.scenarioChats().single()
        chat.members shouldContainExactlyInAnyOrder listOf(ana, beto)
        chat.mode shouldBe Mode.AMISTAD
        chat.endsAt shouldBe f.clock.now.plusSeconds(600)
        f.matchRepo.match(UUID.fromString(id))!!.chatId shouldBe chat.id
        // E4.8: la respuesta de `choice` ya trae el id del chat (F3 lo abre con él) y el chat se replica en Firestore.
        done.chatId shouldBe chat.id.toString()
        f.service.match(ana, id).chatId shouldBe chat.id.toString()
        f.mirroredChats.toList() shouldBe listOf(chat.id)
    }

    test("un bloqueo posterior al match impide el chat SCENARIO: el match vence, se devuelve el cupo y se avisa") {
        val f = MatchFixture(flags = MatchingFlags(demoUsersEnabled = true, scenarioModeEnabled = true))
        val (ana, beto, id) = f.pair()
        f.service.choose(ana, id, MatchChoice.SCENARIO)
        f.matchRepo.block(ana, beto)
        // Beto ya no puede ni ver ni elegir el match bloqueado: se cierra sin crear chat.
        shouldThrow<NotFound> { f.service.choose(beto, id, MatchChoice.SCENARIO) }
        f.matchRepo.scenarioChats().shouldBeEmpty()
        f.matchRepo.match(UUID.fromString(id))!!.status shouldBe MatchStatus.EXPIRED
        // Ana bloqueó: no recupera el cupo; Beto, que no causó el cierre, sí.
        f.usage.refunded("$id:$ana") shouldBe false
        f.usage.refunded("$id:$beto") shouldBe true
        f.notifier.sent.count { it.notification.kind == NotificationKinds.MATCH_EXPIRED } shouldBe 2
    }

    test("el bloqueo justo antes de crear el chat SCENARIO (carrera con la elección) deja EXPIRED y sin chat") {
        val f = MatchFixture(flags = MatchingFlags(demoUsersEnabled = true, scenarioModeEnabled = true))
        val (ana, beto, id) = f.pair()
        f.service.choose(ana, id, MatchChoice.SCENARIO)
        // El bloqueo ocurre entre la lectura del match y la resolución: no pasa por `visible`.
        val record = f.matchRepo.match(UUID.fromString(id))!!
        f.matchRepo.block(beto, ana)
        f.matchRepo.updateMatch(record.id) { it.copy(choiceB = MatchChoice.SCENARIO, status = MatchStatus.SCENARIO) }
        f.matchRepo.createScenarioChat(record.id, f.clock.instant(), allowPremium = false) shouldBe ScenarioChatOutcome.Unavailable(setOf(beto))
        f.matchRepo.scenarioChats().shouldBeEmpty()
        f.matchRepo.match(record.id)!!.status shouldBe MatchStatus.EXPIRED
    }

    test("GET de un match pendiente con bloqueo posterior responde 404, lo cierra y devuelve el cupo solo a quien fue bloqueado") {
        val f = MatchFixture()
        val (ana, beto, id) = f.pair()
        f.matchRepo.block(ana, beto)
        shouldThrow<NotFound> { f.service.match(beto, id) }
        f.matchRepo.match(UUID.fromString(id))!!.status shouldBe MatchStatus.EXPIRED
        f.usage.refunded("$id:$ana") shouldBe false
        f.usage.refunded("$id:$beto") shouldBe true
        // Una segunda lectura ve el match ya cerrado y no vuelve a avisar ni a devolver cupo.
        f.service.match(ana, id).status shouldBe MatchStatus.EXPIRED
        f.notifier.sent.count { it.notification.kind == NotificationKinds.MATCH_EXPIRED } shouldBe 2
    }

    test("quien bloquea y abre el match no recupera el cupo: no se puede saltar el límite diario") {
        val f = MatchFixture()
        val (ana, beto, id) = f.pair()
        f.matchRepo.block(ana, beto)
        // Quien bloqueó es quien abre el match: 404, se cierra y su cupo sigue gastado.
        shouldThrow<NotFound> { f.service.match(ana, id) }
        f.matchRepo.match(UUID.fromString(id))!!.status shouldBe MatchStatus.EXPIRED
        f.usage.refunded("$id:$ana") shouldBe false
        f.usage.refunded("$id:$beto") shouldBe true
    }

    test("si se bloquean entre sí nadie recupera el cupo") {
        val f = MatchFixture()
        val (ana, beto, id) = f.pair()
        f.matchRepo.block(ana, beto)
        f.matchRepo.block(beto, ana)
        shouldThrow<NotFound> { f.service.match(ana, id) }
        f.usage.refunded("$id:$ana") shouldBe false
        f.usage.refunded("$id:$beto") shouldBe false
    }

    test("una persona suspendida no entra a la fila (SUSPENDED) y al vencer la suspensión sí") {
        val f = MatchFixture()
        val ana = f.person("ana")
        f.matchRepo.addPerson(f.matchRepo.person(ana)!!.copy(suspendedUntil = f.clock.instant().plusSeconds(3600)))
        shouldThrow<Suspended> { f.service.enqueue(ana, Mode.AMISTAD) }
        f.matchRepo.ticket(ana) shouldBe null
        f.clock.now = f.clock.now.plusSeconds(3601)
        f.service.enqueue(ana, Mode.AMISTAD).status shouldBe TicketStatus.WAITING
    }

    test("si una persona se borra o la suspenden el cupo vuelve solo a la otra") {
        val f = MatchFixture()
        val (ana, beto, id) = f.pair()
        f.matchRepo.addPerson(f.matchRepo.person(beto)!!.copy(suspendedUntil = f.clock.instant().plusSeconds(3600)))
        shouldThrow<NotFound> { f.service.match(ana, id) }
        f.usage.refunded("$id:$ana") shouldBe true
        f.usage.refunded("$id:$beto") shouldBe false
    }

    test("una publicación WAITING atrasada no pisa a MATCHED en matchTickets (versión monótona)") {
        val f = MatchFixture()
        val ana = f.person("ana")
        val beto = f.person("beto")
        f.service.enqueue(ana, Mode.AMISTAD)
        val staleWaiting = f.mirror.last("uid-ana")!!
        staleWaiting.status shouldBe TicketStatus.WAITING
        f.service.enqueue(beto, Mode.AMISTAD).status shouldBe TicketStatus.MATCHED
        f.mirror.last("uid-ana")!!.status shouldBe TicketStatus.MATCHED
        // La escritura WAITING de Ana llega tarde: el espejo la descarta.
        f.mirror.publish(staleWaiting)
        f.mirror.last("uid-ana")!!.status shouldBe TicketStatus.MATCHED
    }

    test("publicar relee el ticket: si el emparejamiento ocurre mientras se escribe WAITING, lo último que queda es MATCHED") {
        lateinit var f: MatchFixture
        var anaId: UUID? = null
        var betoId: UUID? = null
        var racing = false
        val inner = RecordingMatchTicketMirror()
        // Al publicar WAITING de Ana, Beto entra y se empareja antes de que la escritura de Ana termine.
        val racy = object : MatchTicketMirror by inner {
            override suspend fun publish(doc: TicketMirrorDoc) {
                if (doc.firebaseUid == "uid-ana" && doc.status == TicketStatus.WAITING && !racing) {
                    racing = true
                    f.service.enqueue(betoId!!, Mode.AMISTAD)
                }
                inner.publish(doc)
            }
        }
        f = MatchFixture(mirrorOverride = racy)
        anaId = f.person("ana")
        betoId = f.person("beto")
        f.service.enqueue(anaId, Mode.AMISTAD)
        inner.last("uid-ana")!!.status shouldBe TicketStatus.MATCHED
        inner.last("uid-beto")!!.status shouldBe TicketStatus.MATCHED
    }

    test("con la bandera apagada elegir SCENARIO es 400 VALIDATION y la tarjeta no está disponible") {
        val f = MatchFixture()
        val (ana, _, id) = f.pair()
        f.service.match(ana, id).scenarioAvailable shouldBe false
        shouldThrow<ValidationFailed> { f.service.choose(ana, id, MatchChoice.SCENARIO) }
    }

    test("uno elige SIMULATION: estado SIMULATION y una simulación RANDOM sin cobrar cupo semanal") {
        val f = MatchFixture()
        val (ana, beto, id) = f.pair()
        val chargesBefore = f.usage.charges.toList()
        val result = f.service.choose(beto, id, MatchChoice.SIMULATION)
        result.status shouldBe MatchStatus.SIMULATION
        result.myChoice shouldBe MatchChoice.SIMULATION
        val match = f.matchRepo.match(UUID.fromString(id))!!
        val sim = f.simRepo.find(match.simulationId.shouldNotBeNull())!!
        sim.origin shouldBe SimulationOrigin.RANDOM
        setOf(sim.userA, sim.userB) shouldBe setOf(ana, beto)
        // No se cobró ninguna SIMULATION: el cupo semanal sigue intacto y no hubo nuevos usos.
        f.usage.charges shouldBe chargesBefore
        f.quota.service.status(ana).simulations.used shouldBe 0
        f.quota.service.status(beto).simulations.used shouldBe 0
    }

    test("SCENARIO de uno y SIMULATION del otro: gana SIMULATION") {
        val f = MatchFixture(flags = MatchingFlags(true, true))
        val (ana, beto, id) = f.pair()
        f.service.choose(ana, id, MatchChoice.SCENARIO)
        f.service.choose(beto, id, MatchChoice.SIMULATION).status shouldBe MatchStatus.SIMULATION
    }

    test("solo uno elige y vence decide_by (10 min): SIMULATION") {
        val f = MatchFixture(flags = MatchingFlags(true, true))
        val (ana, beto, id) = f.pair()
        f.service.choose(ana, id, MatchChoice.SCENARIO)
        f.clock.now = f.clock.now.plus(MatchingRules.DECIDE_WINDOW).plusSeconds(1)
        f.service.match(beto, id).status shouldBe MatchStatus.SIMULATION
        f.matchRepo.match(UUID.fromString(id))!!.simulationId.shouldNotBeNull()
    }

    test("nadie elige: al vencer decide_by el match queda EXPIRED y se avisa") {
        val f = MatchFixture()
        val (ana, _, id) = f.pair()
        f.clock.now = f.clock.now.plus(MatchingRules.DECIDE_WINDOW).plusSeconds(1)
        f.service.match(ana, id).status shouldBe MatchStatus.EXPIRED
        f.matchRepo.match(UUID.fromString(id))!!.simulationId.shouldBeNull()
        f.notifier.sent.count { it.notification.kind == NotificationKinds.MATCH_EXPIRED } shouldBe 2
    }

    test("el barrido resuelve matches vencidos") {
        val f = MatchFixture()
        val (_, _, id) = f.pair()
        f.clock.now = f.clock.now.plus(MatchingRules.DECIDE_WINDOW).plusSeconds(1)
        f.service.sweep().settledMatches shouldBe 1
        f.matchRepo.match(UUID.fromString(id))!!.status shouldBe MatchStatus.EXPIRED
    }

    test("elegir dos veces es 409 CONFLICT") {
        val f = MatchFixture(flags = MatchingFlags(true, true))
        val (ana, _, id) = f.pair()
        f.service.choose(ana, id, MatchChoice.SCENARIO)
        shouldThrow<Conflict> { f.service.choose(ana, id, MatchChoice.SCENARIO) }
        shouldThrow<Conflict> { f.service.choose(ana, id, MatchChoice.SIMULATION) }
    }

    test("elegir después de decide_by es 409 CONFLICT") {
        val f = MatchFixture()
        val (ana, _, id) = f.pair()
        f.clock.now = f.clock.now.plus(MatchingRules.DECIDE_WINDOW).plusSeconds(1)
        shouldThrow<Conflict> { f.service.choose(ana, id, MatchChoice.SIMULATION) }
    }

    test("elegir sobre un match ya resuelto es 409; uno ajeno es 404") {
        val f = MatchFixture()
        val (ana, beto, id) = f.pair()
        f.service.choose(ana, id, MatchChoice.SIMULATION)
        shouldThrow<Conflict> { f.service.choose(beto, id, MatchChoice.SIMULATION) }
        val intruso = f.person("intruso")
        shouldThrow<NotFound> { f.service.match(intruso, id) }
        shouldThrow<NotFound> { f.service.choose(intruso, id, MatchChoice.SIMULATION) }
        shouldThrow<NotFound> { f.service.match(ana, "no-es-uuid") }
    }

    test("hay que resolver el match pendiente antes de volver a la fila") {
        val f = MatchFixture(limits = me.santiagoreina.parcheu.api.quota.QuotaLimits(freeDailyRandomMatches = 5))
        val (ana, _, _) = f.pair()
        shouldThrow<Conflict> { f.service.enqueue(ana, Mode.ESTUDIO) }
    }

    test("un bloqueo posterior hace fallar la SIMULATION: el match se cierra y el cupo vuelve solo a quien no bloqueó") {
        val f = MatchFixture(flags = MatchingFlags(demoUsersEnabled = true, scenarioModeEnabled = true))
        val (ana, beto, id) = f.pair()
        // Beto elige SCENARIO; Ana bloquea a Beto y nadie más abre el match. Al vencer decide_by el barrido
        // lo resuelve como SIMULATION y crearla falla (BLOCKED) sin pasar por la comprobación de `visible`.
        f.service.choose(beto, id, MatchChoice.SCENARIO)
        f.block(ana, beto)
        f.clock.now = f.clock.now.plus(MatchingRules.DECIDE_WINDOW).plusSeconds(1)
        f.service.sweep()
        f.matchRepo.match(UUID.fromString(id))!!.status shouldBe MatchStatus.EXPIRED
        f.simRepo.all().shouldBeEmpty()
        // Ana bloqueó: no recupera el cupo; Beto, que no causó el cierre, sí.
        f.usage.refunded("$id:$ana") shouldBe false
        f.usage.refunded("$id:$beto") shouldBe true
    }

    test("barridos concurrentes sobre un match en SIMULATION sin simulación crean una sola (sin huérfanas)") {
        val f = MatchFixture()
        val (_, _, id) = f.pair()
        f.matchRepo.updateMatch(UUID.fromString(id)) { it.copy(status = MatchStatus.SIMULATION, simulationId = null) }
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.Default) {
            (1..8).map { async { f.service.sweep() } }.forEach { it.await() }
        }
        f.simRepo.all().size shouldBe 1
        f.matchRepo.match(UUID.fromString(id))!!.simulationId shouldBe f.simRepo.all().single().id
    }

    test("si la simulación falla sin culpable (gemelo no listo) el match se cierra y el cupo vuelve a las dos") {
        val f = MatchFixture()
        val (ana, beto, id) = f.pair()
        f.simRepo.putTwin(TwinProfile(beto, 1, List(6) { 0.5 }, "Persona de beto", personaReady = false))
        f.service.choose(ana, id, MatchChoice.SIMULATION)
        f.matchRepo.match(UUID.fromString(id))!!.status shouldBe MatchStatus.EXPIRED
        f.usage.refunded("$id:$ana") shouldBe true
        f.usage.refunded("$id:$beto") shouldBe true
    }

    test("si vence sin que nadie lo abra no se devuelve nada, aunque después haya un bloqueo y se consulte") {
        val f = MatchFixture()
        val (ana, beto, id) = f.pair()
        f.clock.now = f.clock.now.plus(MatchingRules.DECIDE_WINDOW).plusSeconds(1)
        // El bloqueo llega después del vencimiento pero antes de que alguien consulte (o pase el barrido).
        f.block(ana, beto)
        f.service.match(beto, id).status shouldBe MatchStatus.EXPIRED
        f.usage.refunded("$id:$ana") shouldBe false
        f.usage.refunded("$id:$beto") shouldBe false
        f.service.match(ana, id).status shouldBe MatchStatus.EXPIRED
        f.usage.refunded("$id:$beto") shouldBe false
    }

    test("AnonProfile no contiene nombre, foto, carrera ni id interno") {
        val f = MatchFixture()
        val (ana, beto, id) = f.pair()
        val dto = f.service.match(ana, id)
        val json = ContractJson.encodeToString(MatchDto.serializer(), dto)
        listOf("Nombre Secreto", "fotos.example", beto.toString(), ana.toString(), "uid-beto", "uid-ana").forEach { json shouldNotContain it }
        dto.other.run {
            label shouldBe "Alguien de la UIS"
            ageRange shouldBe "20-22"
            university shouldBe "UIS"
            interests shouldBe listOf("musica", "cine")
            isDemo shouldBe false
            anonId.startsWith("a_") shouldBe true
        }
        // Estructura: el contrato solo permite estos campos.
        AnonProfile.serializer().descriptor.let { d -> (0 until d.elementsCount).map { d.getElementName(it) }.toSet() } shouldBe setOf("anonId", "label", "ageRange", "university", "interests", "isDemo")
        // El anonId es estable para el observador y distinto entre observadores.
        f.service.match(ana, id).other.anonId shouldBe dto.other.anonId
        f.service.match(beto, id).other.anonId shouldBe f.service.match(beto, id).other.anonId
        (f.service.match(beto, id).other.anonId == dto.other.anonId) shouldBe false
    }

    test("borrado de cuenta (E8.3): la contraparte recibe su ticket republicado y el aviso solo si el match esperaba elección") {
        val f = MatchFixture()
        val ana = f.person("ana")
        val beto = f.person("beto")
        f.service.enqueue(ana, Mode.AMISTAD)
        val matchId = UUID.fromString(f.service.enqueue(beto, Mode.AMISTAD).matchId!!)
        val before = f.notifier.sent.size
        val publishedBefore = f.mirror.docs.count { it.firebaseUid == "uid-beto" }

        f.service.onMatchesClosedByDeletion(listOf(ClosedMatch(matchId, beto, notify = true)))
        f.mirror.docs.count { it.firebaseUid == "uid-beto" } shouldBe publishedBefore + 1
        f.notifier.sent.drop(before).let { sent ->
            sent.map { it.userId } shouldBe listOf(beto)
            sent.single().notification.kind shouldBe NotificationKinds.MATCH_EXPIRED
            sent.single().notification.title shouldBe "Este parche ya no está disponible"
        }

        // Un match ya cerrado solo republica el ticket, sin aviso; una contraparte que ya no existe se ignora.
        val anaBefore = f.mirror.docs.count { it.firebaseUid == "uid-ana" }
        f.service.onMatchesClosedByDeletion(listOf(ClosedMatch(matchId, ana, notify = false), ClosedMatch(matchId, UUID.randomUUID(), notify = true)))
        f.mirror.docs.count { it.firebaseUid == "uid-ana" } shouldBe anaBefore + 1
        f.notifier.sent.size shouldBe before + 1
    }
})
