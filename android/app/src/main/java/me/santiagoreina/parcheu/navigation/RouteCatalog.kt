package me.santiagoreina.parcheu.navigation

import me.santiagoreina.parcheu.contract.Mode

/**
 * Una instancia de ejemplo por ruta, con los IDs de pantalla del inventario que cubre. Lo usan el
 * catálogo de pantallas del stub de Splash (para recorrer la app en un dispositivo) y el test
 * parametrizado de navegación. Un test verifica que cubre todas las subclases de [AppRoute].
 */
data class RouteEntry(val route: AppRoute, val screens: String, val title: String)

val routeCatalog: List<RouteEntry> =
    listOf(
        RouteEntry(Splash, "A1", "Splash"),
        RouteEntry(Onboarding(page = 2), "A2-A4", "Onboarding"),
        RouteEntry(BasicProfile(), "B0", "Tu perfil"),
        RouteEntry(Underage, "Edad", "Bloqueo por edad"),
        RouteEntry(Suspended, "Suspendida", "Cuenta suspendida"),
        RouteEntry(TwinQuestions, "B1", "Preguntas del gemelo"),
        RouteEntry(Modes(), "B2-B3", "Elegir modos"),
        RouteEntry(Consent, "B4", "Consentimiento"),
        RouteEntry(TwinReady, "B5", "Gemelo listo"),
        RouteEntry(Home, "C1, I1", "Inicio"),
        RouteEntry(Notifications, "C2", "Notificaciones"),
        RouteEntry(Profile, "C3", "Perfil y gemelo"),
        RouteEntry(TrainTwin, "C4", "Entrena a tu gemelo"),
        RouteEntry(AddFriends(code = "ABCD-1234"), "C5", "Agregar amigos"),
        RouteEntry(Parche(), "D1-D5, I2", "Tu parche"),
        RouteEntry(FriendsOfFriends, "D6-D7", "Amigos de amigos"),
        RouteEntry(Communities, "D8", "Comunidades"),
        RouteEntry(Evolution, "D9", "Evolución de la red"),
        RouteEntry(Introduce, "E1", "Presentar a dos amigos"),
        RouteEntry(IntroResult(id = "intro-1"), "E2", "Resultado de sus gemelos"),
        RouteEntry(IntroProposal(id = "intro-1"), "E3", "Propuesta recibida"),
        RouteEntry(MatchQueue(mode = Mode.AMISTAD), "F1", "Match al azar"),
        RouteEntry(MatchFound(matchId = "match-1"), "F2", "Match encontrado"),
        RouteEntry(ScenarioChat(chatId = "chat-1"), "F3", "Modo escenario"),
        RouteEntry(Simulation(id = "sim-1"), "F4-F5", "Simulación"),
        RouteEntry(Simulations, "F6", "Simulaciones"),
        RouteEntry(Results(simulationId = "sim-1"), "F7-F8, I3", "Resultados"),
        RouteEntry(ChatList, "Chats", "Lista de chats"),
        RouteEntry(Chat(chatId = "chat-1"), "F9", "Chat"),
        RouteEntry(Paywall(source = "catalogo"), "G1, I4", "ParcheU+"),
        RouteEntry(PurchaseSuccess(plan = "anual"), "G3", "Compra exitosa"),
        RouteEntry(ManageSubscription, "G4", "Gestionar suscripción"),
        RouteEntry(Report(userId = "user-1", chatId = "chat-1"), "H1-H2", "Reportar o bloquear"),
        RouteEntry(PrivacySettings, "H3", "Ajustes de privacidad"),
        RouteEntry(DeleteAccount, "Borrado", "Borrar cuenta"),
        RouteEntry(ExportData, "Exportar", "Descargar mis datos"),
        RouteEntry(BlockedUsers, "Bloqueados", "Personas bloqueadas")
    )

/** Entrada del catálogo para una ruta (por clase), para títulos e IDs de pantalla en los stubs. */
fun catalogEntryFor(route: AppRoute): RouteEntry = routeCatalog.first {
    it.route::class ==
        route::class
}
