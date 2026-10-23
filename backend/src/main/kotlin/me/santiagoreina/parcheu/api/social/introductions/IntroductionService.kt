package me.santiagoreina.parcheu.api.social.introductions

import kotlinx.coroutines.CancellationException
import me.santiagoreina.parcheu.api.chat.ChatService
import me.santiagoreina.parcheu.api.graph.EdgeEvent
import me.santiagoreina.parcheu.api.graph.EdgeService
import me.santiagoreina.parcheu.api.graph.EdgeSource
import me.santiagoreina.parcheu.api.graph.GraphFilter
import me.santiagoreina.parcheu.api.graph.GraphRepository
import me.santiagoreina.parcheu.api.graph.GraphVisibilityPolicy
import me.santiagoreina.parcheu.api.platform.db.tables.IntroRequestStatus
import me.santiagoreina.parcheu.api.platform.db.tables.IntroResponse
import me.santiagoreina.parcheu.api.platform.errors.Blocked
import me.santiagoreina.parcheu.api.platform.errors.Conflict
import me.santiagoreina.parcheu.api.platform.errors.Forbidden
import me.santiagoreina.parcheu.api.platform.errors.NotFound
import me.santiagoreina.parcheu.api.platform.errors.PlusRequired
import me.santiagoreina.parcheu.api.platform.errors.ValidationFailed
import me.santiagoreina.parcheu.api.platform.notifier.Notification
import me.santiagoreina.parcheu.api.platform.notifier.Notifier
import me.santiagoreina.parcheu.api.quota.QuotaService
import me.santiagoreina.parcheu.api.simulation.PersonPresenter
import me.santiagoreina.parcheu.api.simulation.SimulationRecord
import me.santiagoreina.parcheu.api.simulation.SimulationRepository
import me.santiagoreina.parcheu.api.simulation.SimulationService
import me.santiagoreina.parcheu.api.simulation.report.ReportService
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.IntroRequest
import me.santiagoreina.parcheu.contract.Introduce
import me.santiagoreina.parcheu.contract.IntroductionDto
import me.santiagoreina.parcheu.contract.IntroductionStatus
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.NotificationKinds
import me.santiagoreina.parcheu.contract.PersonRef
import me.santiagoreina.parcheu.contract.ReportDto
import me.santiagoreina.parcheu.contract.SimulationOrigin
import me.santiagoreina.parcheu.contract.SimulationStatus
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID
import me.santiagoreina.parcheu.contract.IntroResponse as IntroResponseBody

/**
 * Presentar a dos amigos (backlog E6.2, arquitectura.md §5.2).
 *
 * - **Alta (E1):** solo Amistad o Estudio (Pareja → 400). Las dos personas deben ser CONNECTED de quien
 *   presenta en ese modo (si no, 403 FORBIDDEN) y no estar conectadas entre sí en ese modo; con un bloqueo
 *   entre cualquiera de los tres → 403 BLOCKED. Crea una simulación `INTRODUCTION` con **el mismo id** que
 *   la presentación y cobra una SIMULATION a quien presenta (`usage_events.ref_id = introductionId`); a
 *   los presentados no se les cobra nada. Sin cupo → 402 QUOTA_EXCEEDED y no queda presentación.
 * - **Resultado (E2):** al terminar la simulación solo se avisa a quien presenta (READY); el informe sigue
 *   la regla del informe mensual (`ReportService`, misma redacción que F7). Quien presenta envía la
 *   propuesta ('Enviar propuesta a los dos', PROPOSED y aviso a cada uno) o la deja ('No por ahora').
 * - **Propuesta (E3):** cada persona responde una vez. Si las dos dicen 'Me interesa': arista CONNECTED,
 *   chat DIRECT y aviso de conexión a las dos. Un 'No, gracias' no avisa a nadie ni se revela: quien
 *   no lo dijo (y quien presentó) sigue viendo PROPOSED.
 * - **Pedir presentación (D6, ParcheU+):** crea un `intro_request` y avisa al amigo en común; quien pide
 *   nunca recibe el id del candidato ni de quién lo presentaría.
 */
class IntroductionService(
    private val repo: IntroductionRepository,
    private val simulations: SimulationService,
    private val simulationRepo: SimulationRepository,
    private val reports: ReportService,
    private val graph: GraphRepository,
    private val people: IntroductionPeople,
    private val presenter: PersonPresenter,
    private val quota: QuotaService,
    private val edges: EdgeService,
    private val chat: ChatService,
    private val notifier: Notifier,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(IntroductionService::class.java)

    /** `POST /v1/introductions` (202). */
    suspend fun introduce(introducer: UUID, body: Introduce): IntroductionDto {
        if (body.mode == Mode.PAREJA) throw ValidationFailed("Pareja no está disponible para presentar: solo Amistad o Estudio")
        val a = parseUser(body.userA)
        val b = parseUser(body.userB)
        if (a == b) throw ValidationFailed("Elige a dos personas distintas")
        if (a == introducer || b == introducer) throw ValidationFailed("No puedes presentarte a ti")

        // Bloqueo con quien presenta: se revisa antes que la amistad porque el ego-grafo ya oculta a los bloqueados.
        val blocked = graph.blockedEitherWay(introducer)
        if (a in blocked || b in blocked) throw Blocked()
        val friends = graph.egoEdges(introducer, GraphFilter(mode = body.mode))
            .filter { it.state == EdgeState.CONNECTED }
            .associateBy { it.other.id }
        val personA = friends[a]?.other
        val personB = friends[b]?.other
        if (personA == null || personB == null) {
            throw Forbidden("Solo puedes presentar a amigos conectados contigo en ${body.mode.name}")
        }
        if (b in graph.blockedEitherWay(a) || simulationRepo.blockedEitherWay(a, b)) throw Blocked()
        if (!people.acceptsIntroductions(personA) || !people.acceptsIntroductions(personB)) {
            throw Forbidden("Una de las personas no recibe presentaciones")
        }
        val alreadyConnected = graph.egoEdges(a, GraphFilter(mode = body.mode))
            .any { it.other.id == b && it.state == EdgeState.CONNECTED }
        if (alreadyConnected) throw ValidationFailed("Ya están conectados en ${body.mode.name}")
        if (repo.byIntroducer(introducer, a, b, body.mode).any { isActive(it) }) {
            throw Conflict("Ya hay una presentación en curso entre estas dos personas")
        }

        val id = UUID.randomUUID()
        val record = IntroductionRecord(
            id = id,
            introducerId = introducer,
            mode = body.mode,
            userA = a,
            userB = b,
            simulationId = null,
            aResponse = null,
            bResponse = null,
            status = IntroductionStatus.SIMULATING,
            createdAt = clock.instant().truncatedTo(ChronoUnit.MICROS),
        )
        // La fila existe antes que la simulación: si el motor termina enseguida, el aviso ya sabe que es
        // una presentación y solo avisa a quien presenta.
        repo.insert(record)
        try {
            simulations.create(introducer, a, b, body.mode, SimulationOrigin.INTRODUCTION, chargeQuota = true, id = id)
        } catch (e: CancellationException) {
            repo.delete(id)
            throw e
        } catch (e: Exception) {
            repo.delete(id)
            throw e
        }
        repo.attachSimulation(id, id)
        log.info("Presentación {} creada en {}", id, body.mode)
        return dto(introducer, record.copy(simulationId = id)) ?: error("Quien presenta siempre ve su presentación")
    }

    /** `GET /v1/introductions/{id}`: 404 si no es de quien consulta o aún no le llegó. */
    suspend fun get(viewer: UUID, rawId: String): IntroductionDto {
        val record = find(rawId)
        return dto(viewer, record) ?: throw NotFound()
    }

    /** `POST /v1/introductions/{id}/response`. Repetir una respuesta → 409 CONFLICT. */
    suspend fun respond(viewer: UUID, rawId: String, body: IntroResponseBody): IntroductionDto {
        val record = find(rawId)
        if (dto(viewer, record) == null) throw NotFound()
        return if (viewer == record.introducerId) send(viewer, record, body.interested) else answer(viewer, record, body.interested)
    }

    /** E2: quien presenta envía la propuesta (o la deja en 'No por ahora'). Solo con la simulación DONE. */
    private suspend fun send(viewer: UUID, record: IntroductionRecord, interested: Boolean): IntroductionDto {
        val simulation = simulationOf(record)
        if (simulation?.status != SimulationStatus.DONE || record.status != IntroductionStatus.SIMULATING) {
            throw Conflict("La presentación no está lista para enviarse")
        }
        if (interested && simulationRepo.blockedEitherWay(record.userA, record.userB)) throw Blocked()
        val (_, after) = repo.update(record.id) { current ->
            if (current.status != IntroductionStatus.SIMULATING) throw Conflict("Ya decidiste sobre esta presentación")
            current.copy(status = if (interested) IntroductionStatus.PROPOSED else IntroductionStatus.DECLINED)
        } ?: throw NotFound()
        if (interested) {
            val introducerName = presenter.present(record.userA, record.introducerId).label
            listOf(record.userA, record.userB).forEach { user ->
                notifySafely(
                    user,
                    Notification(
                        kind = NotificationKinds.INTRODUCTION_PROPOSAL,
                        title = "$introducerName quiere presentarte a alguien",
                        body = "Sus gemelos simularon una conversación. Mira la propuesta.",
                        route = "parcheu://intro/${record.id}",
                        actorId = record.introducerId,
                    ),
                    record.id,
                )
            }
        }
        return dto(viewer, after) ?: throw NotFound()
    }

    /** E3: 'Me interesa' o 'No, gracias' de una persona presentada. */
    private suspend fun answer(viewer: UUID, record: IntroductionRecord, interested: Boolean): IntroductionDto {
        if (simulationRepo.blockedEitherWay(record.userA, record.userB)) throw Blocked()
        val response = if (interested) IntroResponse.INTERESTED else IntroResponse.DECLINED
        val (before, after) = repo.update(record.id) { current ->
            if (current.responseOf(viewer) != null) throw Conflict("Ya respondiste a esta presentación")
            if (current.status != IntroductionStatus.PROPOSED && current.status != IntroductionStatus.DECLINED) {
                throw Conflict("La presentación no admite respuestas")
            }
            val updated = if (viewer == current.userA) current.copy(aResponse = response) else current.copy(bResponse = response)
            val status = when {
                updated.aResponse == IntroResponse.DECLINED || updated.bResponse == IntroResponse.DECLINED -> IntroductionStatus.DECLINED
                updated.aResponse == IntroResponse.INTERESTED && updated.bResponse == IntroResponse.INTERESTED -> IntroductionStatus.CONNECTED
                else -> IntroductionStatus.PROPOSED
            }
            updated.copy(status = status)
        } ?: throw NotFound()
        // Solo la respuesta que completa el par llega aquí (update las serializa): una arista y un chat.
        if (after.status == IntroductionStatus.CONNECTED && before.status != IntroductionStatus.CONNECTED) connect(after)
        return dto(viewer, after) ?: throw NotFound()
    }

    private suspend fun connect(record: IntroductionRecord) {
        edges.apply(EdgeEvent.Connected(record.userA, record.userB, record.mode, EdgeSource.INTRODUCTION))
        val direct = chat.createDirectTolerant(record.userA, record.userB, record.mode)
        listOf(record.userA, record.userB).forEach { user ->
            val name = presenter.present(user, record.otherThan(user)).takeIf { it.id != null }?.label
            notifySafely(
                user,
                Notification(
                    kind = NotificationKinds.NEW_CONNECTION,
                    title = "¡Conectaron!",
                    body = name?.let { "A ti y a $it les interesó la presentación. Ya pueden hablar." }
                        ?: "Les interesó la presentación a los dos. Ya pueden hablar.",
                    route = "parcheu://chat/${direct.id}",
                    actorId = record.otherThan(user),
                ),
                record.id,
            )
        }
    }

    /**
     * `POST /v1/introductions/request` (202, ParcheU+). [IntroRequest.anonId] es el que [requester] ve en D6.
     * Idempotente: una solicitud PENDING para la misma persona no vuelve a avisar.
     */
    suspend fun request(requester: UUID, body: IntroRequest) {
        if (!quota.isPlus(requester)) throw PlusRequired("Pedir una presentación es de ParcheU+")
        val (target, mutuals) = people.resolveFof(requester, body.anonId.trim()) ?: throw NotFound("La persona no existe")
        if (repo.pendingRequest(requester, target) != null) return
        // Determinista: el amigo en común con el id menor (no se revela a quien pide).
        val via = mutuals.minBy { it.toString() }
        repo.insertRequest(
            IntroRequestRecord(UUID.randomUUID(), requester, via, target, IntroRequestStatus.PENDING, clock.instant().truncatedTo(ChronoUnit.MICROS)),
        )
        // Las dos personas son conexiones directas del amigo en común: las ve con su nombre.
        val requesterName = presenter.present(via, requester).label
        val targetName = presenter.present(via, target).label
        notifySafely(
            via,
            Notification(
                kind = NotificationKinds.INTRODUCTION_REQUEST,
                title = "Te piden una presentación",
                body = "$requesterName quiere conocer a $targetName. ¿Los presentas?",
                route = "parcheu://introduce",
                actorId = requester,
            ),
            null,
        )
        log.info("Solicitud de presentación creada")
    }

    /**
     * Lo que [viewer] ve de [record], o null si no le corresponde verla: no participa, la propuesta aún no
     * le llegó (o quien presenta la dejó), o hay un bloqueo con alguien de la presentación.
     */
    private suspend fun dto(viewer: UUID, record: IntroductionRecord): IntroductionDto? {
        val simulation = simulationOf(record)
        val base = when (record.status) {
            IntroductionStatus.SIMULATING -> when (simulation?.status) {
                SimulationStatus.DONE -> IntroductionStatus.READY
                SimulationStatus.FAILED -> IntroductionStatus.FAILED
                else -> IntroductionStatus.SIMULATING
            }
            else -> record.status
        }
        val report = simulation?.takeIf { it.status == SimulationStatus.DONE }?.let { reportFor(viewer, it) }
        return when {
            viewer == record.introducerId -> {
                val blocked = graph.blockedEitherWay(viewer)
                if (record.userA in blocked || record.userB in blocked) return null
                IntroductionDto(
                    id = record.id.toString(),
                    mode = record.mode,
                    introducer = self(viewer),
                    other = presenter.present(viewer, record.userA),
                    // 'No, gracias' de una persona presentada: quien presenta tampoco se entera.
                    status = if (base == IntroductionStatus.DECLINED && !record.declinedByIntroducer) IntroductionStatus.PROPOSED else base,
                    simulationId = record.simulationId?.toString(),
                    report = report,
                    myResponse = null,
                    introduced = listOf(presenter.present(viewer, record.userA), presenter.present(viewer, record.userB)),
                )
            }
            record.isIntroduced(viewer) -> {
                val proposed = record.status == IntroductionStatus.PROPOSED || record.status == IntroductionStatus.CONNECTED ||
                    (record.status == IntroductionStatus.DECLINED && !record.declinedByIntroducer)
                if (!proposed) return null
                val blocked = graph.blockedEitherWay(viewer)
                val otherId = record.otherThan(viewer)
                if (record.introducerId in blocked || otherId in blocked) return null
                val mine = record.responseOf(viewer)
                val status = when {
                    mine == IntroResponse.DECLINED -> IntroductionStatus.DECLINED
                    record.status == IntroductionStatus.CONNECTED -> IntroductionStatus.CONNECTED
                    // El 'No, gracias' de la otra persona nunca se revela.
                    else -> IntroductionStatus.PROPOSED
                }
                val other = people.presentOther(viewer, otherId)
                    ?: PersonRef(label = GraphVisibilityPolicy.ANON_LABEL, initials = GraphVisibilityPolicy.ANON_INITIALS)
                val chatId = if (status == IntroductionStatus.CONNECTED) chat.findDirect(viewer, otherId, record.mode)?.id?.toString() else null
                IntroductionDto(
                    id = record.id.toString(),
                    mode = record.mode,
                    introducer = presenter.present(viewer, record.introducerId),
                    other = other,
                    status = status,
                    simulationId = record.simulationId?.toString(),
                    report = report?.let { if (status == IntroductionStatus.CONNECTED) it.copy(chatId = chatId, otherName = other.label) else it },
                    myResponse = mine?.let { it == IntroResponse.INTERESTED },
                )
            }
            else -> null
        }
    }

    /** Informe con la regla del informe mensual (gratis: sin `reasons`, `frictions` ni `dimensions`). */
    private suspend fun reportFor(viewer: UUID, simulation: SimulationRecord): ReportDto? = try {
        reports.introductionReport(viewer, simulation.id)
    } catch (_: NotFound) {
        null
    }

    private suspend fun self(viewer: UUID): PersonRef {
        val me = graph.person(viewer)
        val name = me?.displayName?.trim()?.takeIf { it.isNotEmpty() } ?: "Tú"
        return PersonRef(id = viewer.toString(), label = name, initials = GraphVisibilityPolicy.initialsOf(name), photoUrl = me?.photoUrl, isDemo = me?.isDemo ?: false)
    }

    /**
     * ¿Impide presentar otra vez al mismo par (409)? Sí, salvo que quien presenta la haya dejado en
     * 'No por ahora' o que la simulación haya fallado. Un 'No, gracias' de una persona presentada la deja
     * DECLINED pero sigue contando: si el mismo POST pasara de 409 a 202, quien presenta descubriría el
     * rechazo que GET le oculta, y quien rechazó volvería a recibir la propuesta.
     */
    private suspend fun isActive(record: IntroductionRecord): Boolean = when {
        record.declinedByIntroducer -> false
        record.status == IntroductionStatus.SIMULATING -> simulationOf(record)?.status != SimulationStatus.FAILED
        else -> true
    }

    private suspend fun simulationOf(record: IntroductionRecord): SimulationRecord? =
        simulationRepo.find(record.simulationId ?: record.id)

    private suspend fun find(rawId: String): IntroductionRecord {
        val id = runCatching { UUID.fromString(rawId) }.getOrNull() ?: throw NotFound()
        return repo.find(id) ?: throw NotFound()
    }

    private fun parseUser(raw: String): UUID =
        runCatching { UUID.fromString(raw.trim()) }.getOrNull() ?: throw ValidationFailed("Elige a dos amigos de tu parche")

    private suspend fun notifySafely(user: UUID, notification: Notification, introductionId: UUID?) {
        try {
            notifier.notify(user, notification)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // El aviso no deshace la presentación: la propuesta queda en GET y en la bandeja.
            log.warn("No se pudo enviar el aviso {} de la presentación {}: {}", notification.kind, introductionId, e.message)
        }
    }
}
