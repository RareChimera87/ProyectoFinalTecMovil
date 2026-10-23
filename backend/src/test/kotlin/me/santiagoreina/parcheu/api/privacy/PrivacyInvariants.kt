package me.santiagoreina.parcheu.api.privacy

import me.santiagoreina.parcheu.api.graph.AnonIdSigner
import me.santiagoreina.parcheu.api.graph.ViewContext
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Visibility
import java.util.UUID

/** Arista tal como la entrega una vista: ids públicos (uuid o `anonId`) de sus dos extremos. */
data class ExposedEdge(val aId: String, val bId: String, val mode: Mode)

/** Todo lo que una vista mostró a un observador: personas (por id público) y aristas. */
data class Exposure(val personIds: List<String>, val edges: List<ExposedEdge>) {
    companion object {
        val EMPTY = Exposure(emptyList(), emptyList())
    }
}

/**
 * Las reglas de ADR-0012 comprobadas sobre lo que una vista expuso:
 * 1. ninguna arista de Pareja que no involucre al observador;
 * 2. ningún usuario HIDDEN (solo en contextos de segundo grado);
 * 3. ningún bloqueado (en cualquier sentido);
 * 4. ningún borrado;
 * además, todo id debe corresponder a una persona del mundo y el `users.id` real solo se puede
 * ver con conexión mutua (si no, debe ir el `anonId`).
 */
object PrivacyInvariants {
    private val signer = AnonIdSigner.forTests()

    fun violations(world: PrivacyWorld, viewer: UUID, exposure: Exposure, context: ViewContext): List<String> {
        val problems = mutableListOf<String>()
        val blocked = world.blockedEitherWay(viewer)

        fun resolve(publicId: String): UUID? {
            val asUuid = runCatching { UUID.fromString(publicId) }.getOrNull()
            if (asUuid != null) {
                if (world.person(asUuid) == null) return null
                if (asUuid != viewer && !world.connected(viewer, asUuid)) {
                    problems += "uuid real de $publicId expuesto sin conexión mutua"
                }
                return asUuid
            }
            return world.people.firstOrNull { signer.anonId(viewer, it.id) == publicId }?.id
        }

        fun checkSubject(publicId: String) {
            val id = resolve(publicId) ?: run {
                problems += "id $publicId no corresponde a ninguna persona del mundo"
                return
            }
            if (id == viewer) return
            val subject = world.person(id) ?: return
            if (subject.deletedAt != null) problems += "usuario borrado $publicId expuesto"
            if (id in blocked) problems += "usuario bloqueado $publicId expuesto"
            if (context == ViewContext.THIRD_PARTY && subject.visibility == Visibility.HIDDEN) {
                problems += "usuario HIDDEN $publicId expuesto en segundo grado"
            }
        }

        exposure.personIds.forEach(::checkSubject)
        exposure.edges.forEach { edge ->
            checkSubject(edge.aId)
            checkSubject(edge.bId)
            val a = resolve(edge.aId)
            val b = resolve(edge.bId)
            if (edge.mode == Mode.PAREJA && a != viewer && b != viewer) {
                problems += "arista de Pareja ${edge.aId}-${edge.bId} que no involucra al observador"
            }
        }
        return problems.distinct()
    }
}
