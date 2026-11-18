package me.santiagoreina.parcheu.feature.notifications

import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.NotificationDto
import me.santiagoreina.parcheu.contract.NotificationKinds
import me.santiagoreina.parcheu.contract.NotificationsDto
import me.santiagoreina.parcheu.contract.PersonRef

/** Datos de ejemplo de C2 (pruebas y capturas Roborazzi), con los avisos del mockup 05. */
object NotificationsSamples {
    /** "Ahora" de las capturas: martes 3 de noviembre de 2026, 10:00 en Bogotá. */
    val now: Instant = Instant.parse("2026-11-03T15:00:00Z")

    val resultReady =
        NotificationDto(
            id = "n-result",
            kind = NotificationKinds.SIMULATION_READY,
            title = "Resultado listo",
            body = "Tú y Daniela son 78 % afines",
            route = "parcheu://results/sim-daniela",
            actor = PersonRef(id = "u-daniela", label = "Daniela", initials = "D"),
            createdAt = Instant.parse("2026-11-03T14:00:00Z")
        )

    val introduction =
        NotificationDto(
            id = "n-intro",
            kind = NotificationKinds.INTRODUCTION_PROPOSAL,
            title = "Andrés quiere presentarte a Laura",
            body = "Míralo antes de que su simulación arranque",
            route = "parcheu://intro/intro-1",
            actor = PersonRef(id = "u-andres", label = "Andrés", initials = "A"),
            createdAt = Instant.parse("2026-11-03T12:00:00Z")
        )

    val twin =
        NotificationDto(
            id = "n-twin",
            kind = NotificationKinds.TWIN_MEMORIES,
            title = "Tu gemelo aprendió 2 cosas nuevas",
            body = "Revisa lo que aprendió de tus chats",
            route = "parcheu://train-twin",
            read = true,
            createdAt = Instant.parse("2026-11-03T10:00:00Z")
        )

    val newConnection =
        NotificationDto(
            id = "n-valentina",
            kind = NotificationKinds.NEW_CONNECTION,
            title = "Valentina aceptó tu invitación",
            body = "Ya hace parte de tu parche",
            route = "parcheu://parche",
            actor = PersonRef(id = "u-valentina", label = "Valentina", initials = "V"),
            read = true,
            createdAt = Instant.parse("2026-11-01T20:00:00Z")
        )

    /** Bloqueado para el plan gratis: sin actor (el backend no lo revela). */
    val lockedConnect =
        NotificationDto(
            id = "n-locked",
            kind = NotificationKinds.CONNECT_REQUEST,
            title = "2 personas quieren conectar contigo",
            body = "Descubre quiénes son",
            route = "parcheu://notifications",
            locked = true,
            createdAt = Instant.parse("2026-10-31T16:00:00Z")
        )

    val renewed =
        NotificationDto(
            id = "n-renew",
            kind = NotificationKinds.ACCOUNT,
            title = "Se renovaron tus 5 simulaciones",
            body = "Ya puedes volver a simular esta semana",
            read = true,
            createdAt = Instant.parse("2026-10-20T13:00:00Z")
        )

    /** Con no leídas, un bloqueado y los tres grupos. */
    val withUnread =
        NotificationsUiState.Loaded(
            NotificationsDto(
                today = listOf(resultReady, introduction, twin),
                week = listOf(newConnection, lockedConnect),
                older = listOf(renewed)
            )
        )

    /** Todo leído: 'Marcar leídas' queda deshabilitado. */
    val allRead =
        NotificationsUiState.Loaded(
            NotificationsDto(
                today = listOf(resultReady.copy(read = true), twin),
                week = listOf(newConnection)
            )
        )

    val empty = NotificationsUiState.Loaded(NotificationsDto())
}
