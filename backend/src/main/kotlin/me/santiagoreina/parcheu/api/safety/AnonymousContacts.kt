package me.santiagoreina.parcheu.api.safety

import me.santiagoreina.parcheu.api.graph.fof.FofService
import me.santiagoreina.parcheu.api.social.introductions.IntroductionRepository
import me.santiagoreina.parcheu.api.social.invites.InviteRepository
import java.util.UUID

/**
 * Personas que [viewer] ve enmascaradas (con `anonId`) en pantallas que no son un chat ni el grafo (E8.7):
 * quien canjeó su código (C5), los amigos de amigos (D6) y la otra persona de una presentación (E3).
 * [SafetyService] las usa para resolver el `anonId` de un reporte o un bloqueo **solo** entre las personas
 * con las que ese observador tiene contexto: el `anonId` de otro observador sigue siendo 404.
 */
fun interface AnonymousContacts {
    suspend fun of(viewer: UUID): Set<UUID>

    companion object {
        /** Quienes canjearon el código de [viewer] y esperan que los acepte (C5). */
        fun pendingInvites(invites: InviteRepository) = AnonymousContacts { viewer ->
            invites.pendingFor(viewer).map { it.redeemer }.toSet()
        }

        /** Los candidatos de D6, la misma lista que ve el observador. */
        fun friendsOfFriends(fof: FofService) = AnonymousContacts { viewer ->
            fof.candidates(viewer).map { it.userId }.toSet()
        }

        /** La otra persona de cada presentación en la que [viewer] es una de las presentadas (E3). */
        fun introductions(introductions: IntroductionRepository) = AnonymousContacts { viewer ->
            introductions.involving(viewer, MAX_INTRODUCTIONS).map { it.otherThan(viewer) }.toSet()
        }

        private const val MAX_INTRODUCTIONS = 200
    }
}
