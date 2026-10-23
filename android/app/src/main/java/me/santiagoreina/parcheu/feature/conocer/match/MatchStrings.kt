package me.santiagoreina.parcheu.feature.conocer.match

import androidx.compose.runtime.Composable
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.designsystem.theme.ModeColors
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/** Textos de F1 y F2 (los mockups 13a y 13b), en español como el resto de la app. */
internal object MatchStrings {
    const val QUEUE_TITLE = "Match al azar"
    const val COMPARING = "Comparando gustos con tu gemelo…"
    const val TODAY = "Este es tu match al azar de hoy"
    const val TODAY_COUNT = "1 de 1"
    const val CANCEL_SEARCH = "Cancelar búsqueda"
    const val CANCEL_NOTE = "Si cancelas no se descuenta."
    const val CLOSE = "Cerrar"
    const val RETRY = "Reintentar"
    const val SEARCH_AGAIN = "Buscar de nuevo"
    const val EXPIRED_TITLE = "No encontramos a nadie"
    const val EXPIRED_MESSAGE = "Pasaron 24 horas sin match. No se descontó tu match del día."
    const val MATCHED_MESSAGE = "¡Encontramos a alguien!"
    const val SEE_MATCH = "Ver match"

    const val FOUND = "¡ENCONTRAMOS A ALGUIEN!"
    const val DEMO_BADGE = "Perfil de demostración"
    const val HOW_TO_MEET = "¿Cómo se quieren conocer?"
    const val SCENARIO_TITLE = "Escenario"
    const val SCENARIO_BODY = "Chatean ustedes y llegan a un acuerdo"
    const val SIMULATION_TITLE = "Simulación"
    const val SIMULATION_BODY = "Sus gemelos conversan primero"
    const val START = "Empezar"
    const val STARTING = "Empezando…"
    const val WAITING_OTHER = "Esperando a la otra persona…"
    const val MATCH_EXPIRED_TITLE = "El match venció"
    const val MATCH_EXPIRED_MESSAGE = "Nadie eligió a tiempo. Puedes buscar a alguien más."
    const val LOADING = "Cargando match…"

    const val LIMIT_TITLE = "Ya usaste tu match de hoy"
    const val LIMIT_MESSAGE =
        "Con el plan gratis tienes 1 match al azar al día. Vuelve mañana o pásate a ParcheU+ " +
            "para tener más."
    const val SEE_PLUS = "Ver ParcheU+"
    const val UNDERSTOOD = "Entendido"

    fun searching(modeName: String) = "Buscando a alguien con $modeName activo…"

    fun ageRange(range: String) = "$range años"

    fun modeName(mode: Mode) = when (mode) {
        Mode.AMISTAD -> "Amistad"
        Mode.PAREJA -> "Pareja"
        Mode.ESTUDIO -> "Estudio"
    }

    fun errorMessage(error: AppError) = when (error) {
        AppError.Network -> "Sin conexión. Revisa tu internet e inténtalo de nuevo."
        AppError.AgeRequired -> "Para el modo Pareja necesitamos tu fecha de nacimiento."
        AppError.Underage -> "El modo Pareja es solo para mayores de 18 años."
        AppError.Blocked, AppError.Suspended, AppError.Forbidden ->
            "No puedes usar el match al azar en este momento."
        AppError.NotFound -> "Este match ya no está disponible."
        AppError.Conflict -> "Ya hay una elección registrada para este match."
        is AppError.AiBudget -> "Estamos al límite por hoy. Inténtalo más tarde."
        else -> "Algo salió mal. Inténtalo de nuevo."
    }
}

/** Color del modo en el tema actual. */
@Composable
internal fun Mode.colors(): ModeColors = when (this) {
    Mode.AMISTAD -> ParcheUTheme.colors.friendship
    Mode.PAREJA -> ParcheUTheme.colors.couple
    Mode.ESTUDIO -> ParcheUTheme.colors.study
}

internal val Mode.mascotTone
    get() = when (this) {
        Mode.AMISTAD -> me.santiagoreina.parcheu.designsystem.mascot.TwinMascotTone.Friendship
        Mode.PAREJA -> me.santiagoreina.parcheu.designsystem.mascot.TwinMascotTone.Couple
        Mode.ESTUDIO -> me.santiagoreina.parcheu.designsystem.mascot.TwinMascotTone.Study
    }
