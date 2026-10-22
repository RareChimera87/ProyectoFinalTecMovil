package me.santiagoreina.parcheu.push

import java.net.URI
import me.santiagoreina.parcheu.contract.NotificationKinds
import me.santiagoreina.parcheu.navigation.AddFriends
import me.santiagoreina.parcheu.navigation.AppRoute
import me.santiagoreina.parcheu.navigation.Chat
import me.santiagoreina.parcheu.navigation.ChatList
import me.santiagoreina.parcheu.navigation.Communities
import me.santiagoreina.parcheu.navigation.Home
import me.santiagoreina.parcheu.navigation.IntroProposal
import me.santiagoreina.parcheu.navigation.MatchFound
import me.santiagoreina.parcheu.navigation.MatchQueue
import me.santiagoreina.parcheu.navigation.Notifications
import me.santiagoreina.parcheu.navigation.Parche
import me.santiagoreina.parcheu.navigation.Profile
import me.santiagoreina.parcheu.navigation.Results
import me.santiagoreina.parcheu.navigation.Simulation
import me.santiagoreina.parcheu.navigation.Simulations
import me.santiagoreina.parcheu.navigation.TrainTwin

/**
 * Traduce un aviso (el `route` `parcheu://...` del backend o, si falta, su `type`) a la ruta de la app.
 * La tabla de rutas está en docs/arquitectura.md §8.4; C2 (E5.4) usa esta misma tabla al tocar un aviso.
 *
 * Los IDs del enlace llegan de fuera de la app (el esquema `parcheu://` es navegable), así que solo se
 * aceptan segmentos simples y nunca se ejecuta nada: lo peor que puede pasar es abrir una pantalla.
 */
object DeepLinks {
    const val SCHEME = "parcheu"

    /** Ruta de un deep link; `null` si no es `parcheu://` o no corresponde a ninguna pantalla. */
    fun fromUri(raw: String?): AppRoute? {
        val uri = raw?.let { runCatching { URI(it.trim()) }.getOrNull() } ?: return null
        if (uri.scheme != SCHEME) return null
        val host = uri.host ?: return null
        val segments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        val id = segments.firstOrNull()?.takeIf { it.matches(ID) && segments.size == 1 }
        if (segments.isNotEmpty() && id == null) return null
        return when (host) {
            "results" -> id?.let(::Results)
            "simulation" -> id?.let(::Simulation)
            "simulations" -> if (id == null) Simulations else null
            "match" -> if (id == null) MatchQueue() else MatchFound(id)
            "intro" -> id?.let(::IntroProposal)
            "chat" -> id?.let(::Chat)
            "chats" -> if (id == null) ChatList else null
            "parche" -> if (id == null) Parche() else null
            "add-friends" -> if (id == null) AddFriends() else AddFriends(code = id)
            "train-twin" -> if (id == null) TrainTwin else null
            "communities" -> if (id == null) Communities else null
            "notifications" -> if (id == null) Notifications else null
            "profile" -> if (id == null) Profile else null
            "home" -> if (id == null) Home else null
            else -> null
        }
    }

    /** Destino cuando el aviso no trae ruta (o trae una que esta versión de la app no conoce). */
    fun fromType(type: String?): AppRoute = when (type) {
        NotificationKinds.SIMULATION_READY, NotificationKinds.SIMULATION_FAILED -> Simulations
        NotificationKinds.MATCH_FOUND, NotificationKinds.MATCH_EXPIRED -> MatchQueue()
        NotificationKinds.NEW_CONNECTION -> Parche()
        NotificationKinds.INVITE_PENDING -> AddFriends()
        NotificationKinds.CHAT_MESSAGE -> ChatList
        NotificationKinds.TWIN_MEMORIES, NotificationKinds.TWIN_FEEDBACK -> TrainTwin
        NotificationKinds.COMMUNITY -> Communities
        // CONNECT_REQUEST, INTRODUCTION_PROPOSAL, ACCOUNT y tipos nuevos: la bandeja los muestra todos.
        else -> Notifications
    }

    /** Ruta final de un aviso: primero `route`, luego `type`. Nunca devuelve `null`. */
    fun resolve(type: String?, route: String?): AppRoute = fromUri(route) ?: fromType(type)

    private val ID = Regex("[A-Za-z0-9_-]{1,64}")
}
