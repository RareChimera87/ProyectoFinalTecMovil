package me.santiagoreina.parcheu.api.social.invites

import me.santiagoreina.parcheu.api.graph.EdgeEvent
import me.santiagoreina.parcheu.api.graph.EdgeService
import me.santiagoreina.parcheu.api.graph.EdgeSource
import me.santiagoreina.parcheu.api.graph.GraphFilter
import me.santiagoreina.parcheu.api.graph.GraphRepository
import me.santiagoreina.parcheu.api.graph.GraphVisibilityPolicy
import me.santiagoreina.parcheu.api.graph.AnonIdSigner
import me.santiagoreina.parcheu.api.graph.PersonRow
import me.santiagoreina.parcheu.api.graph.ViewContext
import me.santiagoreina.parcheu.api.platform.errors.Blocked
import me.santiagoreina.parcheu.api.platform.errors.NotFound
import me.santiagoreina.parcheu.api.platform.errors.ValidationFailed
import me.santiagoreina.parcheu.api.platform.notifier.Notification
import me.santiagoreina.parcheu.api.platform.notifier.Notifier
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.InviteDto
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.NotificationKinds
import me.santiagoreina.parcheu.contract.PendingInvite
import me.santiagoreina.parcheu.contract.PersonRef
import me.santiagoreina.parcheu.contract.Redeem
import me.santiagoreina.parcheu.contract.RedeemResult
import me.santiagoreina.parcheu.contract.RedeemStatus
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.time.Clock
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlin.random.asKotlinRandom
import kotlin.time.toKotlinInstant

/** Lo que las invitaciones necesitan saber de una persona: sus modos activos y si borró la cuenta. */
data class InviteParticipant(val id: UUID, val activeModes: Set<Mode>, val deleted: Boolean)

/**
 * Invitaciones por QR y código (E6.1, arquitectura.md §5.2 y §6.1).
 *
 * - `GET /v1/invites/me`: el código `XXXX-0000` de la persona (se crea la primera vez), su App Link y los
 *   canjes de su código que esperan que ella acepte.
 * - `POST /v1/invites/redeem`: quien canjea el código de otra persona pide conectar en un modo que los dos
 *   tienen activo. La arista queda PENDING (`EdgeEvent.InterestShown`) y el dueño recibe `INVITE_PENDING`.
 *   Es idempotente: repetirlo no duplica el canje ni el aviso. Si el dueño ya había canjeado el código de
 *   quien canjea en el mismo modo, el interés es mutuo y quedan CONNECTED de una vez.
 * - `POST /v1/invites/{userId}/accept`: el dueño acepta (por `users.id` o por el `anonId` que ve en
 *   [InviteDto.pending]) y la arista pasa a CONNECTED en cada modo canjeado.
 *
 * Nunca hay aristas entre personas con un bloqueo (403 BLOCKED al canjear; al aceptar no se encuentra).
 */
class InviteService(
    private val repo: InviteRepository,
    private val graph: GraphRepository,
    private val edges: EdgeService,
    private val anonIds: AnonIdSigner,
    private val participants: suspend (UUID) -> InviteParticipant?,
    private val notifier: Notifier,
    private val links: InviteLinks,
    private val clock: Clock,
    private val random: Random = SecureRandom().asKotlinRandom(),
) {
    private val log = LoggerFactory.getLogger(InviteService::class.java)

    suspend fun me(userId: UUID): InviteDto {
        val code = codeFor(userId)
        val blocked = graph.blockedEitherWay(userId)
        val pending = repo.pendingFor(userId).mapNotNull { r ->
            if (r.redeemer in blocked) return@mapNotNull null
            val person = graph.person(r.redeemer)?.takeIf { it.deletedAt == null } ?: return@mapNotNull null
            val ref = requesterRef(userId, person)
            PendingInvite(id = ref.anonId!!, other = ref, mode = r.mode, createdAt = r.createdAt.toKotlinInstant())
        }
        return InviteDto(code = code, link = links.linkFor(code), pending = pending)
    }

    /** Código de [userId]; lo crea (único) la primera vez. */
    suspend fun codeFor(userId: UUID): String {
        repo.codeOf(userId)?.let { return it }
        val name = graph.person(userId)?.displayName
        repeat(MAX_ATTEMPTS) { attempt ->
            // La mitad de los intentos sin el prefijo del nombre: un nombre muy común no agota los reintentos.
            val code = InviteCodes.generate(random, name, fullyRandom = attempt >= MAX_ATTEMPTS / 2)
            if (repo.claim(code, userId)) return code
            // Otra petición concurrente de la misma persona pudo crear su código primero.
            repo.codeOf(userId)?.let { return it }
        }
        error("No se pudo generar un código de invitación único tras $MAX_ATTEMPTS intentos")
    }

    suspend fun redeem(userId: UUID, request: Redeem): RedeemResult {
        val code = InviteCodes.normalize(request.code)
        if (!InviteCodes.isValid(code)) throw ValidationFailed("El código debe tener el formato ABCD-1234")
        val owner = repo.ownerOf(code) ?: throw NotFound("Ese código no existe")
        if (owner == userId) throw ValidationFailed("Ese es tu propio código: compártelo con tus amigos")
        val ownerParticipant = participants(owner)?.takeIf { !it.deleted } ?: throw NotFound("Ese código no existe")
        val me = participants(userId)?.takeIf { !it.deleted } ?: throw NotFound("Usuario no encontrado")
        val ownerPerson = graph.person(owner)?.takeIf { it.deletedAt == null } ?: throw NotFound("Ese código no existe")
        if (owner in graph.blockedEitherWay(userId)) throw Blocked()
        val mode = request.mode
        if (mode !in me.activeModes || mode !in ownerParticipant.activeModes) {
            throw ValidationFailed("El modo ${mode.label()} debe estar activo para los dos")
        }

        if (isConnected(userId, owner, mode)) {
            repo.markConnected(owner, userId, mode, clock.instant())
            return RedeemResult(RedeemStatus.CONNECTED, connectedRef(userId, ownerPerson))
        }

        // El dueño ya había canjeado el código de esta persona en el mismo modo: interés mutuo.
        if (repo.redemption(owner = userId, redeemer = owner, mode = mode)?.status == RedeemStatus.PENDING) {
            connect(owner = userId, redeemer = owner, mode = mode)
            return RedeemResult(RedeemStatus.CONNECTED, connectedRef(userId, ownerPerson))
        }

        // Primero la arista (no se crea si hay un bloqueo) y luego el canje, que decide si se avisa.
        val edge = edges.apply(EdgeEvent.InterestShown(userId, owner, mode, EdgeSource.INVITE)) ?: throw Blocked()
        val created = repo.addRedemption(owner, userId, mode, clock.instant())
        if (edge.state == EdgeState.CONNECTED) {
            repo.markConnected(owner, userId, mode, clock.instant())
            return RedeemResult(RedeemStatus.CONNECTED, connectedRef(userId, ownerPerson))
        }
        if (created) {
            val name = graph.person(userId)?.displayName?.trim()?.takeIf { it.isNotEmpty() }
            notifySafely(
                owner,
                Notification(
                    kind = NotificationKinds.INVITE_PENDING,
                    title = "Quieren agregarte",
                    body = "${name ?: "Alguien"} usó tu código para conectar en ${mode.label()}. Acéptalo en Agregar amigos.",
                    route = INVITE_ROUTE,
                    actorId = userId,
                ),
            )
        }
        return RedeemResult(RedeemStatus.PENDING, ownerRefBeforeAccept(userId, ownerPerson))
    }

    /** El dueño del código acepta a [target] (`users.id` o el `anonId` que ve en sus canjes pendientes). */
    suspend fun accept(ownerId: UUID, target: String): RedeemResult {
        val blocked = graph.blockedEitherWay(ownerId)
        val matching = repo.pendingFor(ownerId).filter { r ->
            r.redeemer.toString() == target || anonIds.anonId(ownerId, r.redeemer) == target
        }
        val redeemer = matching.firstOrNull()?.redeemer ?: throw NotFound("No hay una invitación pendiente de esa persona")
        val person = graph.person(redeemer)?.takeIf { it.deletedAt == null }
        if (redeemer in blocked || person == null || participants(redeemer)?.deleted != false) {
            throw NotFound("No hay una invitación pendiente de esa persona")
        }
        matching.forEach { connect(owner = ownerId, redeemer = redeemer, mode = it.mode) }
        return RedeemResult(RedeemStatus.CONNECTED, connectedRef(ownerId, person))
    }

    /** CONNECTED en [mode] (lo valida [EdgeService]: nunca con bloqueo), canje cerrado y aviso a quien canjeó. */
    private suspend fun connect(owner: UUID, redeemer: UUID, mode: Mode) {
        val edge = edges.apply(EdgeEvent.Connected(owner, redeemer, mode, EdgeSource.INVITE)) ?: throw Blocked()
        check(edge.state == EdgeState.CONNECTED) { "La arista no quedó conectada: ${edge.state}" }
        repo.markConnected(owner, redeemer, mode, clock.instant())
        val name = graph.person(owner)?.displayName?.trim()?.takeIf { it.isNotEmpty() }
        notifySafely(
            redeemer,
            Notification(
                kind = NotificationKinds.NEW_CONNECTION,
                title = "¡Conectaron!",
                body = "${name ?: "Tu invitación"} ${if (name != null) "aceptó tu invitación" else "fue aceptada"}: ya están conectados en ${mode.label()}.",
                route = "parcheu://parche",
                actorId = owner,
            ),
        )
    }

    private suspend fun isConnected(a: UUID, b: UUID, mode: Mode): Boolean =
        graph.egoEdges(a, GraphFilter(mode = mode)).any { it.other.id == b && it.state == EdgeState.CONNECTED }

    /** Conexión mutua: nombre, foto e `id` real (la misma regla del ego-grafo, [GraphVisibilityPolicy]). */
    private fun connectedRef(viewer: UUID, subject: PersonRow): PersonRef {
        val view = GraphVisibilityPolicy(anonIds).present(viewer, subject, connected = true, context = ViewContext.EGO)
        return PersonRef(
            id = if (view.anonymous) null else view.id,
            anonId = if (view.anonymous) view.id else null,
            label = view.label,
            initials = view.initials,
            photoUrl = view.photoUrl,
            isDemo = view.isDemo,
        )
    }

    /**
     * Quien canjea ya conoce al dueño (su nombre va debajo del QR que compartió): ve su nombre, pero no su
     * `id` ni su foto hasta que acepte.
     */
    private fun ownerRefBeforeAccept(viewer: UUID, owner: PersonRow): PersonRef = namedWithoutId(viewer, owner)

    /** El dueño ve el nombre de quien canjeó su código (pidió conectar con él), sin `id` ni foto hasta aceptar. */
    private fun requesterRef(viewer: UUID, requester: PersonRow): PersonRef = namedWithoutId(viewer, requester)

    private fun namedWithoutId(viewer: UUID, subject: PersonRow): PersonRef {
        val anonId = anonIds.anonId(viewer, subject.id)
        val name = subject.displayName?.trim()?.takeIf { it.isNotEmpty() }
        return PersonRef(
            anonId = anonId,
            label = name ?: GraphVisibilityPolicy.anonymousLabel(subject.universityShort),
            initials = name?.let(GraphVisibilityPolicy::initialsOf) ?: GraphVisibilityPolicy.ANON_INITIALS,
            isDemo = subject.isDemo,
        )
    }

    private suspend fun notifySafely(userId: UUID, notification: Notification) {
        try {
            notifier.notify(userId, notification)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // El aviso es secundario: el canje o la conexión ya quedaron guardados.
            log.warn("No se pudo avisar {} a {}", notification.kind, userId, e)
        }
    }

    companion object {
        const val MAX_ATTEMPTS = 20
        const val INVITE_ROUTE = "parcheu://add-friends"
    }
}

/** Nombre del modo para los textos ("Amistad", "Pareja", "Estudio"). */
internal fun Mode.label(): String = when (this) {
    Mode.AMISTAD -> "Amistad"
    Mode.PAREJA -> "Pareja"
    Mode.ESTUDIO -> "Estudio"
}
