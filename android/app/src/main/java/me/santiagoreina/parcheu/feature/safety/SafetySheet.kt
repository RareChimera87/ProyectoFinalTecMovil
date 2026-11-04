package me.santiagoreina.parcheu.feature.safety

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.UUID
import me.santiagoreina.parcheu.designsystem.components.base.OutlineButton
import me.santiagoreina.parcheu.designsystem.components.base.PrimaryButton
import me.santiagoreina.parcheu.designsystem.components.compound.InfoNote
import me.santiagoreina.parcheu.designsystem.components.compound.ParcheUBottomSheet
import me.santiagoreina.parcheu.designsystem.components.compound.ParcheUModalDialog
import me.santiagoreina.parcheu.designsystem.components.compound.SettingsRow
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** Etiquetas de prueba de H1 y sus confirmaciones. */
object SafetyTags {
    const val SHEET = "safety:sheet"
    const val MUTE = "safety:mute"
    const val REPORT = "safety:report"
    const val BLOCK = "safety:block"
    const val CONFIRM_BLOCK = "safety:confirm-block"
    const val CANCEL_BLOCK = "safety:cancel-block"
    const val BLOCKED_DONE = "safety:blocked-done"
    const val REASON_PREFIX = "report:reason:"
    const val DETAILS = "report:details"
    const val ATTACH = "report:attach"
    const val ALSO_BLOCK = "report:also-block"
    const val SUBMIT = "report:submit"
    const val SENT_DONE = "report:sent-done"
    const val HELP = "report:help"
}

/** Acciones de H1 que la hoja delega al [SafetySheetViewModel]. */
data class SafetySheetActions(
    val onMute: () -> Unit = {},
    val onReport: () -> Unit = {},
    val onBlock: () -> Unit = {},
    val onConfirmBlock: () -> Unit = {},
    val onCancelBlock: () -> Unit = {},
    val onDismiss: () -> Unit = {}
)

/**
 * H1 sobre la pantalla que la abre (F9 y F3 en E8.1; el resto en E8.7). La hoja tiene su propio
 * ViewModel; [onClosed] avisa por qué se cerró para que la anfitriona abra H2 ([CloseReason.Report]),
 * salga del chat ([CloseReason.Blocked]) o refleje el silencio ([CloseReason.Muted]).
 */
@Composable
fun SafetySheetHost(target: SafetyTarget, onClosed: (CloseReason, SafetySheetState) -> Unit) {
    // Cada apertura es un flujo nuevo: la clave cambia con cada composición de la anfitriona.
    val nonce = rememberSaveable { UUID.randomUUID().toString() }
    val viewModel: SafetySheetViewModel =
        koinViewModel(key = "safety:${target.id}:$nonce") { parametersOf(target) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.closedBy) {
        state.closedBy?.let { onClosed(it, state) }
    }
    SafetySheet(
        state = state,
        actions =
        SafetySheetActions(
            onMute = viewModel::toggleMute,
            onReport = viewModel::report,
            onBlock = viewModel::requestBlock,
            onConfirmBlock = viewModel::confirmBlock,
            onCancelBlock = viewModel::cancelBlock,
            onDismiss = viewModel::dismiss
        )
    )
}

/** H1 y sus diálogos según el paso. Sin estado propio. */
@Composable
fun SafetySheet(state: SafetySheetState, actions: SafetySheetActions) {
    when (state.step) {
        SheetStep.Menu ->
            ParcheUBottomSheet(
                onDismissRequest = actions.onDismiss,
                modifier = Modifier.testTag(SafetyTags.SHEET)
            ) {
                SafetySheetBody(state, actions)
            }
        SheetStep.ConfirmBlock ->
            ParcheUModalDialog(
                onDismissRequest = actions.onCancelBlock,
                dismissOnScrimClick = !state.busy
            ) {
                BlockConfirmBody(state, actions)
            }
        SheetStep.Blocked ->
            ParcheUModalDialog(onDismissRequest = actions.onDismiss) {
                BlockedBody(state.target, onDone = actions.onDismiss)
            }
        SheetStep.Closed -> Unit
    }
}

/** Contenido de H1 (mockup 22a): Silenciar (si hay chat), Reportar y Bloquear, y la nota de gratuidad. */
@Composable
fun SafetySheetBody(state: SafetySheetState, actions: SafetySheetActions) {
    val target = state.target
    val name = target.shortName
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (target.chatId != null) {
            SettingsRow(
                title = if (state.muted) "Quitar el silencio" else "Silenciar a $name",
                subtitle =
                if (state.muted) {
                    "Vuelves a recibir notificaciones de este chat"
                } else {
                    "No recibes notificaciones de este chat"
                },
                icon = rememberVectorPainter(if (state.muted) UnmuteIcon else MuteIcon),
                onClick = actions.onMute,
                enabled = !state.busy,
                modifier = Modifier.testTag(SafetyTags.MUTE)
            )
            HorizontalDivider(color = ParcheUTheme.colors.ln)
        }
        SettingsRow(
            title = "Reportar a $name",
            subtitle =
            if (target.anonymous) {
                "Revisamos el caso. No sabrá que fuiste tú."
            } else {
                "Revisamos el caso. $name no sabrá que fuiste tú."
            },
            icon = rememberVectorPainter(FlagIcon),
            onClick = actions.onReport,
            destructive = true,
            enabled = !state.busy,
            modifier = Modifier.testTag(SafetyTags.REPORT)
        )
        HorizontalDivider(color = ParcheUTheme.colors.ln)
        SettingsRow(
            title = "Bloquear a $name",
            subtitle = "No podrá escribirte ni verte en su parche",
            icon = rememberVectorPainter(BlockIcon),
            onClick = actions.onBlock,
            destructive = true,
            enabled = !state.busy,
            modifier = Modifier.testTag(SafetyTags.BLOCK)
        )
        state.error?.let { ErrorText(it) }
        InfoNote(
            text = "Reportar y bloquear es gratis, siempre.",
            icon = rememberVectorPainter(ShieldIcon),
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/** Confirmación de bloqueo (nueva): explica qué pasa antes de hacerlo. */
@Composable
fun BlockConfirmBody(state: SafetySheetState, actions: SafetySheetActions) {
    val name = state.target.shortName
    Text(
        "¿Bloquear a $name?",
        style = MaterialTheme.typography.titleLarge,
        color = ParcheUTheme.colors.tx
    )
    Text(
        text =
        "No podrá escribirte, verte en su parche ni encontrarte en la fila. " +
            "Se cierran sus chats y no le avisamos.",
        style = MaterialTheme.typography.bodyMedium,
        color = ParcheUTheme.colors.mu
    )
    state.error?.let { ErrorText(it) }
    PrimaryButton(
        text = if (state.busy) "Bloqueando…" else "Bloquear",
        onClick = actions.onConfirmBlock,
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth().testTag(SafetyTags.CONFIRM_BLOCK)
    )
    OutlineButton(
        text = "Cancelar",
        onClick = actions.onCancelBlock,
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth().testTag(SafetyTags.CANCEL_BLOCK)
    )
}

/** Confirmación de bloqueo hecho (nueva). */
@Composable
fun BlockedBody(target: SafetyTarget, onDone: () -> Unit) {
    Icon(
        rememberVectorPainter(CheckCircleIcon),
        contentDescription = null,
        tint = ParcheUTheme.colors.prt,
        modifier = Modifier.size(40.dp)
    )
    Text(
        "Bloqueaste a ${target.shortName}",
        style = MaterialTheme.typography.titleLarge,
        color = ParcheUTheme.colors.tx
    )
    Text(
        text = "Ya no podrá escribirte ni verte. Si quieres contarnos qué pasó, también " +
            "puedes reportar.",
        style = MaterialTheme.typography.bodyMedium,
        color = ParcheUTheme.colors.mu
    )
    PrimaryButton(
        text = "Entendido",
        onClick = onDone,
        modifier = Modifier.fillMaxWidth().testTag(SafetyTags.BLOCKED_DONE)
    )
}
