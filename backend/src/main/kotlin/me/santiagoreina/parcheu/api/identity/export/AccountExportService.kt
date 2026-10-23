package me.santiagoreina.parcheu.api.identity.export

import me.santiagoreina.parcheu.api.graph.AnonIdSigner
import me.santiagoreina.parcheu.api.graph.GraphVisibilityPolicy
import me.santiagoreina.parcheu.api.identity.IdentityService
import me.santiagoreina.parcheu.api.identity.toKotlin
import me.santiagoreina.parcheu.api.platform.auth.FirebaseUser
import me.santiagoreina.parcheu.api.quota.grantsPlus
import me.santiagoreina.parcheu.api.twin.TwinState
import me.santiagoreina.parcheu.api.twin.xpNextFor
import me.santiagoreina.parcheu.contract.BlockedDto
import me.santiagoreina.parcheu.contract.ConsentState
import me.santiagoreina.parcheu.contract.EntitlementDto
import me.santiagoreina.parcheu.contract.ExportChat
import me.santiagoreina.parcheu.contract.ExportConnection
import me.santiagoreina.parcheu.contract.ExportDto
import me.santiagoreina.parcheu.contract.ExportMessage
import me.santiagoreina.parcheu.contract.MeDto
import me.santiagoreina.parcheu.contract.PersonRef
import me.santiagoreina.parcheu.contract.SimulationSummary
import me.santiagoreina.parcheu.contract.Trait
import me.santiagoreina.parcheu.contract.TraitKey
import me.santiagoreina.parcheu.contract.TwinDto
import java.time.Clock
import java.util.UUID

/**
 * `GET /v1/me/export` (E8.3, Ley 1581 de 2012): copia en JSON de los datos de la persona: perfil,
 * consentimientos (historial completo), gemelo y respuestas, memorias, simulaciones, conexiones, chats
 * con **solo sus mensajes**, bloqueos y suscripción.
 *
 * De otras personas solo sale su `anonId` (HMAC del observador, arquitectura.md §6.1): nunca su uuid,
 * nombre, correo, foto ni mensajes. Por eso tampoco se exportan los avisos de la bandeja, que citan
 * nombres y textos de terceros. La consulta no escribe nada.
 */
class AccountExportService(
    private val identity: IdentityService,
    private val repository: ExportRepository,
    private val anonIds: AnonIdSigner,
    private val twinState: (UUID) -> TwinState?,
    private val clock: Clock,
) {
    suspend fun export(firebase: FirebaseUser): ExportDto {
        val user = identity.requireUser(firebase)
        val me = identity.me(firebase)
        return build(user.id, me, repository.rows(user.id), twinState(user.id))
    }

    internal fun build(userId: UUID, me: MeDto, rows: ExportRows, twin: TwinState?): ExportDto {
        val now = clock.instant()
        fun anon(other: UUID) = anonIds.anonId(userId, other)
        return ExportDto(
            generatedAt = now.toKotlin(),
            me = me,
            consents = rows.consents.map { ConsentState(it.kind, it.granted, it.textVersion, it.createdAt.toKotlin()) },
            twin = twin?.toDto(),
            twinAnswers = rows.answers,
            memories = rows.memories,
            simulations = rows.simulations.map { sim ->
                SimulationSummary(
                    id = sim.id.toString(),
                    status = sim.status,
                    mode = sim.mode,
                    other = anonymousRef(anon(sim.otherUserId), sim.otherIsDemo),
                    createdAt = sim.createdAt.toKotlin(),
                    origin = sim.origin,
                    score = sim.score,
                )
            },
            connections = rows.connections.map {
                ExportConnection(label = anonymousLabel(anon(it.otherUserId)), mode = it.mode, state = it.state, connectedAt = it.connectedAt?.toKotlin())
            },
            chats = rows.chats.map { chat ->
                ExportChat(
                    id = chat.id.toString(),
                    type = chat.type,
                    mode = chat.mode,
                    messages = chat.ownMessages.map { ExportMessage(it.id.toString(), it.text, it.createdAt.toKotlin()) },
                )
            },
            blocks = rows.blocks.map {
                BlockedDto(
                    userId = anon(it.blockedUserId),
                    label = GraphVisibilityPolicy.ANON_LABEL,
                    initials = GraphVisibilityPolicy.ANON_INITIALS,
                    blockedAt = it.createdAt.toKotlin(),
                )
            },
            entitlement = rows.entitlement?.let {
                EntitlementDto(
                    plus = grantsPlus(it.state, it.expiresAt, now),
                    state = it.state,
                    basePlan = it.basePlan,
                    expiresAt = it.expiresAt?.toKotlin(),
                    autoRenewing = it.autoRenewing,
                )
            },
        )
    }

    private fun anonymousRef(anonId: String, isDemo: Boolean) = PersonRef(
        anonId = anonId,
        label = GraphVisibilityPolicy.ANON_LABEL,
        initials = GraphVisibilityPolicy.ANON_INITIALS,
        isDemo = isDemo,
    )

    /** Etiqueta de una conexión: el `anonId`, que distingue a cada persona sin decir quién es. */
    private fun anonymousLabel(anonId: String) = "${GraphVisibilityPolicy.ANON_LABEL} · $anonId"

    private fun TwinState.toDto() = TwinDto(
        traits = traits?.let { values -> TraitKey.entries.mapIndexed { i, key -> Trait(key, values[i]) } }.orEmpty(),
        level = level,
        xp = xp,
        xpNext = xpNextFor(xp),
        version = version,
        personaReady = personaReady,
        accuracy = accuracy,
    )
}
