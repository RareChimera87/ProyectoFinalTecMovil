package me.santiagoreina.parcheu.api.notifications.push

import me.santiagoreina.parcheu.contract.NotificationKinds

/**
 * Canales de notificación de Android. Los IDs son parte del contrato con la app
 * (`android/.../push/PushChannels.kt`): no se renombran sin cambiar los dos lados.
 */
enum class PushChannel(val id: String) {
    CHAT("chat"),
    RESULTADOS("resultados"),
    SOCIAL("social"),
    CUENTA("cuenta"),
    ;

    companion object {
        /** Canal de cada tipo de aviso; un tipo desconocido cae en `cuenta`. */
        fun forKind(kind: String): PushChannel = when (kind) {
            NotificationKinds.CHAT_MESSAGE -> CHAT
            NotificationKinds.SIMULATION_READY,
            NotificationKinds.SIMULATION_FAILED,
            NotificationKinds.MATCH_FOUND,
            NotificationKinds.MATCH_EXPIRED,
            -> RESULTADOS
            NotificationKinds.CONNECT_REQUEST,
            NotificationKinds.NEW_CONNECTION,
            NotificationKinds.INTRODUCTION_PROPOSAL,
            NotificationKinds.INVITE_PENDING,
            NotificationKinds.COMMUNITY,
            -> SOCIAL
            else -> CUENTA
        }
    }
}
