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
import me.santiagoreina.parcheu.api.platform.errors.Forbidden
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
import java.time.Duration
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
 *   quien canjea en el mismo modo, el interés es mutuo y quedan CONNECTED de una vez. Cada persona tiene
 *   [MAX_REDEEMS_PER_DAY] intentos por día (exista o no el código) y la respuesta nunca dice qué modos
 *   tiene activos el dueño: si no tiene el modo pedido, se responde PENDING sin crear nada ni avisarle.
 * - `POST /v1/invites/{id}/accept[?mode=]`: el dueño acepta un canje (por el [PendingInvite.id]
 *   `anonId:MODO` que ve en [InviteDto.pending], o por `anonId`/`users.id` más `mode`) y la arista pasa a
 *   CONNECTED solo en ese modo. Sin modo se acepta solo si esa persona tiene un único canje pendiente.
 *
 * Un canje solo sigue vivo mientras su modo esté activo para los dos: si alguno apagó el modo después de
 * canjear (p. ej. Pareja), `me` no lo muestra y aceptarlo responde 404 y lo descarta (`EdgeEvent.Declined`).
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
        val modes = ModesLookup(userId)
        val pending = repo.pendingFor(userId).mapNotNull { r ->
            if (r.redeemer in blocked) return@mapNotNull null
            // El modo ya no está activo para alguno de los dos: no se ofrece (aceptarlo lo descarta).
            if (!modes.activeForBoth(r)) return@mapNotNull null
            val person = graph.person(r.redeemer)?.takeIf { it.deletedAt == null } ?: return@mapNotNull null
            val ref = requesterRef(userId, person)
            PendingInvite(id = pendingId(ref.anonId!!, r.mode), other = ref, mode = r.mode, createdAt = r.createdAt.toKotlinInstant())
        }
        return InviteDto(code = code, link = links.linkFor(code), pending = pending)
    }

    /** Código de [userId], todo al azar ([InviteCodes.generate]); lo crea (único) la primera vez. */
    suspend fun codeFor(userId: UUID): String {
        repo.codeOf(userId)?.let { return it }
        repeat(MAX_ATTEMPTS) {
            val code = InviteCodes.generate(random)
            if (repo.claim(code, userId)) return code
            // Otra petición concurrente de la misma persona pudo crear su código primero.
            repo.codeOf(userId)?.let { return it }
        }
        error("No se pudo generar un código de invitación único tras $MAX_ATTEMPTS intentos")
    }

    suspend fun redeem(userId: UUID, request: Redeem): RedeemResult {
        val code = InviteCodes.normalize(request.code)
        if (!InviteCodes.isValid(code)) throw ValidationFailed("El código debe tener el formato ABCD-1234")
        // El intento cuenta antes de buscar el código: nadie puede recorrer los códigos para saber de quién son.
        val now = clock.instant()
        if (!repo.tryRecordAttempt(userId, since = now.minus(ATTEMPT_WINDOW), limit = MAX_REDEEMS_PER_DAY, now = now)) {
            throw Forbidden("Llegaste al límite de $MAX_REDEEMS_PER_DAY códigos por día. Inténtalo de nuevo mañana.")
        }
        val owner = repo.ownerOf(code) ?: throw NotFound("Ese código no existe")
        if (owner == userId) throw ValidationFailed("Ese es tu propio código: compártelo con tus amigos")
        val ownerParticipant = participants(owner)?.takeIf { !it.deleted } ?: throw NotFound("Ese código no existe")
        val me = participants(userId)?.takeIf { !it.deleted } ?: throw NotFound("Usuario no encontrado")
        val ownerPerson = graph.person(owner)?.takeIf { it.deletedAt == null } ?: throw NotFound("Ese código no existe")
        if (owner in graph.blockedEitherWay(userId)) throw Blocked()
        val mode = request.mode
        if (mode !in me.activeModes) throw ValidationFailed("Activa el modo ${mode.label()} en tu perfil para conectar en ese modo")
        // Los modos del dueño (Pareja sobre todo) son suyos: la respuesta es la misma que la de un canje
        // normal, pero no se crea arista ni canje y no se le avisa.
        if (mode !in ownerParticipant.activeModes) return RedeemResult(RedeemStatus.PENDING, ownerRefBeforeAccept(userId, ownerPerson))

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

    /**
     * El dueño del código acepta UN canje: [target] es el [PendingInvite.id] (`anonId:MODO`) o la persona
     * (`anonId` o `users.id`) y [mode] el modo. Solo ese modo pasa a CONNECTED; los demás canjes de la misma
     * persona (p. ej. Pareja) siguen PENDING hasta que el dueño los acepte uno por uno.
     */
    suspend fun accept(ownerId: UUID, target: String, mode: Mode? = null): RedeemResult {
        val (who, idMode) = parsePendingId(target)
        if (idMode != null && mode != null && idMode != mode) throw ValidationFailed("El modo no coincide con la invitación")
        val wanted = idMode ?: mode
        val blocked = graph.blockedEitherWay(ownerId)
        val modes = ModesLookup(ownerId)
        val fromPerson = repo.pendingFor(ownerId).filter { r ->
            r.redeemer.toString() == who || anonIds.anonId(ownerId, r.redeemer) == who
        }
        // Sin modo, los canjes cuyo modo ya no está activo para los dos no cuentan para la ambigüedad.
        val matching = if (wanted == null) fromPerson.filter { modes.activeForBoth(it) } else fromPerson.filter { it.mode == wanted }
        val redemption = matching.singleOrNull() ?: when {
            matching.isEmpty() -> throw NotFound("No hay una invitación pendiente de esa persona")
            else -> throw ValidationFailed("Esa persona te invitó en varios modos: indica cuál aceptas")
        }
        val redeemer = redemption.redeemer
        val person = graph.person(redeemer)?.takeIf { it.deletedAt == null }
        if (redeemer in blocked || person == null || participants(redeemer)?.deleted != false) {
            throw NotFound("No hay una invitación pendiente de esa persona")
        }
        // Alguno apagó el modo después del canje (p. ej. Pareja): nunca se conecta en un modo inactivo.
        // La arista PENDING se descarta y el canje se borra; si reactivan el modo, pueden volver a canjear.
        if (!modes.activeForBoth(redemption)) {
            edges.apply(EdgeEvent.Declined(redeemer, ownerId, redemption.mode, EdgeSource.INVITE))
            repo.removeRedemption(ownerId, redeemer, redemption.mode)
            throw NotFound("No hay una invitación pendiente de esa persona")
        }
        connect(owner = ownerId, redeemer = redeemer, mode = redemption.mode)
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

    /** Modos activos del dueño [owner] y de cada persona que canjeó su código, leídos una vez por petición. */
    private inner class ModesLookup(private val owner: UUID) {
        private val cache = mutableMapOf<UUID, Set<Mode>>()

        private suspend fun of(id: UUID): Set<Mode> =
            cache[id] ?: (participants(id)?.takeIf { !it.deleted }?.activeModes.orEmpty()).also { cache[id] = it }

        suspend fun activeForBoth(r: Redemption): Boolean = r.mode in of(owner) && r.mode in of(r.redeemer)
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

        /**
         * Canjes por persona en [ATTEMPT_WINDOW] (cuentan también los códigos inexistentes). Alcanza para
         * agregar a todo un parche en una tarde; con 26^4 * 10^4 códigos posibles hace inviable recorrerlos.
         */
        const val MAX_REDEEMS_PER_DAY = 20
        val ATTEMPT_WINDOW: Duration = Duration.ofDays(1)
        const val INVITE_ROUTE = "parcheu://add-friends"

        /** [PendingInvite.id]: un canje es una persona en un modo (`anonId:MODO`). */
        fun pendingId(anonId: String, mode: Mode): String = "$anonId:${mode.name}"

        /** Separa `anonId:MODO` en persona y modo; sin `:` (o un modo desconocido) es solo la persona. */
        fun parsePendingId(id: String): Pair<String, Mode?> {
            val who = id.substringBeforeLast(':', missingDelimiterValue = id)
            if (who == id) return id to null
            val mode = Mode.entries.firstOrNull { it.name == id.substringAfterLast(':') } ?: return id to null
            return who to mode
        }
    }
}

/** Nombre del modo para los textos ("Amistad", "Pareja", "Estudio"). */
internal fun Mode.label(): String = when (this) {
    Mode.AMISTAD -> "Amistad"
    Mode.PAREJA -> "Pareja"
    Mode.ESTUDIO -> "Estudio"
}
