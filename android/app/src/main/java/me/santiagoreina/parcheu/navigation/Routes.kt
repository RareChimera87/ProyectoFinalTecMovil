package me.santiagoreina.parcheu.navigation

import kotlinx.serialization.Serializable
import me.santiagoreina.parcheu.contract.Mode

/*
 * Todas las rutas de la app (docs/arquitectura.md §2.4, ADR-0006). E0.7 las declara completas y este archivo
 * queda congelado: una feature nueva no agrega rutas aquí sin un PR propio que actualice también
 * AppNavHost.kt, RouteCatalog.kt y el test de navegación.
 */

/** Marca común de las rutas tipadas de Navigation Compose. */
sealed interface AppRoute

// A. Arranque

/** A1 · Splash. */
@Serializable
data object Splash : AppRoute

/** A2-A4 · Onboarding (A4 con 'Continuar con Google', términos y política). */
@Serializable
data class Onboarding(val page: Int = 0) : AppRoute

// B. Creación del gemelo

/** B0 · 'Tu perfil' (fecha de nacimiento, universidad, carrera, intereses; E2.6). */
@Serializable
data class BasicProfile(val edit: Boolean = false) : AppRoute

/** Bloqueo por edad (menor de 18; E2.6). */
@Serializable
data object Underage : AppRoute

/** Bloqueo por suspensión o ban (403 SUSPENDED): exportar, borrar la cuenta o cerrar sesión. */
@Serializable
data object Suspended : AppRoute

/** B1 · Preguntas del gemelo. */
@Serializable
data object TwinQuestions : AppRoute

/** B2 · Elegir modos (+ diálogo B3 de confirmación +18). */
@Serializable
data class Modes(val fromSettings: Boolean = false) : AppRoute

/** B4 · Consentimiento de aprender de chats. */
@Serializable
data object Consent : AppRoute

/** B5 · Gemelo listo. */
@Serializable
data object TwinReady : AppRoute

// C. Inicio, notificaciones, perfil

/** C1 · Inicio (I1 en claro). Raíz de la NavBar. */
@Serializable
data object Home : AppRoute

/** C2 · Notificaciones. */
@Serializable
data object Notifications : AppRoute

/** C3 · Perfil y gemelo digital. Raíz de la NavBar. */
@Serializable
data object Profile : AppRoute

/** C4 · Entrena a tu gemelo. */
@Serializable
data object TrainTwin : AppRoute

/** C5 · Agregar amigos (QR y código); `code` llega por App Link `/i/{codigo}`. */
@Serializable
data class AddFriends(val code: String? = null) : AppRoute

// D. Tu parche

/** D1-D5 · Tu parche (grafo, sheet D2, vacío, cargando, error; I2 en claro). Raíz de la NavBar. */
@Serializable
data class Parche(val community: String? = null, val month: String? = null) : AppRoute

/** D6/D7 · Amigos de amigos (desbloqueado / gratis con candado). */
@Serializable
data object FriendsOfFriends : AppRoute

/** D8 · Comunidades. */
@Serializable
data object Communities : AppRoute

/** D9 · Evolución de la red. */
@Serializable
data object Evolution : AppRoute

// E. Presentar a dos amigos

/** E1 · Elegir modo y dos amigos. */
@Serializable
data object Introduce : AppRoute

/** E2 · Resultado de sus gemelos. */
@Serializable
data class IntroResult(val id: String) : AppRoute

/** E3 · Propuesta recibida. */
@Serializable
data class IntroProposal(val id: String) : AppRoute

// F. Conocer

/** F1 · Match al azar, en la fila. Raíz 'Conocer' de la NavBar. */
@Serializable
data class MatchQueue(val mode: Mode? = null) : AppRoute

/** F2 · Match encontrado. */
@Serializable
data class MatchFound(val matchId: String) : AppRoute

/** F3 · Modo escenario, chat. */
@Serializable
data class ScenarioChat(val chatId: String) : AppRoute

/** F4/F5 · Simulación en curso / error. */
@Serializable
data class Simulation(val id: String) : AppRoute

/** F6 · Simulaciones ('Ver todas' y vacío). */
@Serializable
data object Simulations : AppRoute

/** F7/F8 · Resultados (gratis / ParcheU+; I3 en claro) y 'esperando a la otra persona'. */
@Serializable
data class Results(val simulationId: String) : AppRoute

/** Lista de chats (pantalla nueva). Raíz 'Chats' de la NavBar. */
@Serializable
data object ChatList : AppRoute

/** F9 · Chat en vivo. */
@Serializable
data class Chat(val chatId: String) : AppRoute

// G. ParcheU+

/** G1 · Paywall (I4 en claro). `source` = desde dónde se abrió (analítica y regreso). */
@Serializable
data class Paywall(val source: String) : AppRoute

/** G3 · Compra exitosa. */
@Serializable
data class PurchaseSuccess(val plan: String) : AppRoute

/** G4 · Gestionar suscripción. */
@Serializable
data object ManageSubscription : AppRoute

// H. Seguridad y privacidad

/**
 * H2 · Reportar (motivos) y la confirmación de reporte enviado. H1 es una hoja que abre la pantalla de
 * origen (`SafetySheetHost`) y navega aquí al elegir "Reportar". [userId] es el uuid o, si [anonymous],
 * el `anonId` con el que se ve a la persona; [name] es su etiqueta en esa pantalla (E8.1).
 */
@Serializable
data class Report(
    val userId: String,
    val chatId: String? = null,
    val name: String? = null,
    val anonymous: Boolean = false,
    /** Reporte de contenido de la IA (E8.7): simulación y turno; con ellos [userId] va vacío. */
    val simulationId: String? = null,
    val turnIndex: Int? = null
) : AppRoute

/** H3 · Ajustes de privacidad. */
@Serializable
data object PrivacySettings : AppRoute

/** Confirmación de borrado de cuenta (pantalla nueva). */
@Serializable
data object DeleteAccount : AppRoute

/** Descargar mis datos (pantalla nueva, E8.3). */
@Serializable
data object ExportData : AppRoute

/** Personas bloqueadas (pantalla nueva, E8.7). */
@Serializable
data object BlockedUsers : AppRoute
