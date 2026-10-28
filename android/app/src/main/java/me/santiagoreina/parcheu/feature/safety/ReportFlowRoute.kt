package me.santiagoreina.parcheu.feature.safety

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Report
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** Objetivo de H2 a partir de la ruta. */
fun Report.toTarget(): SafetyTarget = SafetyTarget(
    id = userId,
    name = name ?: if (anonymous) "Alguien" else "esta persona",
    anonymous = anonymous,
    chatId = chatId,
    simulationId = simulationId,
    turnIndex = turnIndex
)

/** Ruta para la anfitriona: abrir H2 para [target]. */
fun SafetyTarget.toReportRoute(): Report = Report(
    userId = id,
    chatId = chatId,
    name = name,
    anonymous = anonymous,
    simulationId = simulationId,
    turnIndex = turnIndex
)

/**
 * H2 y la confirmación. Al terminar vuelve atrás; si además se bloqueó a la persona de un chat, sale
 * también de ese chat (ya está cerrado).
 */
@Composable
fun ReportFlowRoute(route: Report, navigator: AppNavigator, modifier: Modifier = Modifier) {
    val target = remember(route) { route.toTarget() }
    val viewModel: ReportViewModel =
        koinViewModel(
            key = "report:${route.userId}:${route.chatId}:${route.simulationId}:${route.turnIndex}"
        ) {
            parametersOf(target)
        }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val finish = {
        navigator.back()
        if (state.sent?.blocked == true && route.chatId != null) navigator.back()
    }
    BackHandler(enabled = state.sent != null) { finish() }
    ReportScreen(
        state = state,
        actions =
        ReportActions(
            onClose = { navigator.back() },
            onReason = viewModel::selectReason,
            onDetailsChange = viewModel::onDetailsChange,
            onAttachChange = viewModel::setAttachMessages,
            onAlsoBlockChange = viewModel::setAlsoBlock,
            onSubmit = viewModel::submit,
            onDone = { finish() },
            onCall = { phone ->
                // Abre el marcador con el número; la persona decide si llama.
                try {
                    context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$phone".toUri()))
                } catch (_: ActivityNotFoundException) {
                    // Sin marcador (tablet sin teléfono): el número sigue visible en pantalla.
                }
            }
        ),
        modifier = modifier
    )
}
