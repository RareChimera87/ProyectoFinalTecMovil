package me.santiagoreina.parcheu.feature.safety

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import me.santiagoreina.parcheu.contract.PersonRef
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUIconButton
import me.santiagoreina.parcheu.navigation.AppNavigator

/**
 * Acción "Reportar o bloquear" (bandera) que las pantallas D2, F2, C5, E3 y D6 ponen en su barra o su
 * tarjeta: abre H1 con [SafetyEntryHost]. Reportar y bloquear es siempre gratis y accesible (E8.7).
 */
@Composable
fun ReportOrBlockAction(onClick: () -> Unit, modifier: Modifier = Modifier) {
    ParcheUIconButton(
        icon = rememberVectorPainter(FlagIcon),
        contentDescription = REPORT_OR_BLOCK_DESCRIPTION,
        onClick = onClick,
        modifier = modifier
    )
}

/** Acción "Reportar contenido de la IA" de F8 y la transcripción: abre H2 en modo `AI_CONTENT`. */
@Composable
fun ReportAiContentAction(onClick: () -> Unit, modifier: Modifier = Modifier) {
    ParcheUIconButton(
        icon = rememberVectorPainter(FlagIcon),
        contentDescription = REPORT_AI_CONTENT_DESCRIPTION,
        onClick = onClick,
        modifier = modifier
    )
}

/**
 * H1 sobre la pantalla anfitriona cuando [target] no es nulo. [onClear] se llama al cerrarse la hoja (la
 * anfitriona pone su objetivo en nulo); "Reportar" abre H2 y [onBlocked] avisa que se bloqueó a la
 * persona para que la anfitriona salga o recargue.
 */
@Composable
fun SafetyEntryHost(
    target: SafetyTarget?,
    navigator: AppNavigator,
    onClear: () -> Unit,
    onBlocked: () -> Unit = {}
) {
    if (target == null) return
    SafetySheetHost(target) { reason, _ ->
        onClear()
        handleSafetyClosed(reason, target, navigator, onBlocked)
    }
}

/** Qué hace la anfitriona cuando H1 se cierra: "Reportar" abre H2; un bloqueo hecho avisa a la anfitriona. */
internal fun handleSafetyClosed(
    reason: CloseReason,
    target: SafetyTarget,
    navigator: AppNavigator,
    onBlocked: () -> Unit
) {
    when (reason) {
        CloseReason.Report -> navigator.navigate(target.toReportRoute())
        CloseReason.Blocked -> onBlocked()
        CloseReason.Muted, CloseReason.Dismissed -> Unit
    }
}

/**
 * `turnIndex` reservado (igual que `SafetyService.REPORT_TURN_INDEX` en el backend): el reporte apunta
 * al informe completo de F8 y el servidor copia el informe, no un turno de la transcripción.
 */
const val REPORT_TURN_INDEX = -1

/** Objetivo del reporte de un turno de la IA: simulación [simulationId] y turno [turnIndex] (desde 0). */
fun aiContentTarget(simulationId: String, turnIndex: Int = 0) = SafetyTarget(
    id = "",
    name = "la IA",
    simulationId = simulationId,
    turnIndex = turnIndex
)

/** Abre H2 para el contenido de la IA directamente (no hay persona que bloquear, así que no pasa por H1). */
fun AppNavigator.reportAiContent(simulationId: String, turnIndex: Int = 0) =
    navigate(aiContentTarget(simulationId, turnIndex).toReportRoute())

/** Objetivo de H1 para una persona del contrato: su uuid si hay conexión mutua o, si no, su `anonId`. */
fun PersonRef.toSafetyTarget(chatId: String? = null): SafetyTarget {
    val real = id
    return SafetyTarget(
        id = real ?: anonId.orEmpty(),
        name = label,
        anonymous = real == null,
        chatId = chatId
    )
}
