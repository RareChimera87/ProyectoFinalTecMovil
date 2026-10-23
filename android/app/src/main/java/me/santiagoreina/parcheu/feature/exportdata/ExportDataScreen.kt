package me.santiagoreina.parcheu.feature.exportdata

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.designsystem.components.base.OutlineButton
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUChildTopBar
import me.santiagoreina.parcheu.designsystem.components.base.PrimaryButton
import me.santiagoreina.parcheu.designsystem.components.compound.InfoListCard
import me.santiagoreina.parcheu.designsystem.components.compound.InfoListItem
import me.santiagoreina.parcheu.designsystem.components.compound.InfoNote
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.deleteaccount.AccountBackIcon
import me.santiagoreina.parcheu.feature.deleteaccount.AccountDoneIcon
import me.santiagoreina.parcheu.feature.deleteaccount.DownloadIcon
import me.santiagoreina.parcheu.feature.deleteaccount.ShareIcon

object ExportDataTags {
    const val SCREEN = "export-data"
    const val DOWNLOAD = "export-data:download"
    const val SHARE = "export-data:share"
    const val STATUS = "export-data:status"
}

data class ExportDataActions(
    val onBack: () -> Unit = {},
    val onDownload: () -> Unit = {},
    val onShare: () -> Unit = {}
)

/** Lo que trae el archivo (contrato `ExportDto`, E8.3). */
internal val ExportContents =
    listOf(
        "Tu perfil, tus respuestas y tu gemelo, con sus memorias.",
        "Tus autorizaciones, con fecha y versión del texto.",
        "Tus simulaciones, conexiones, bloqueos y tu suscripción.",
        "Los mensajes que tú escribiste en tus chats."
    )

/** 'Descargar mis datos' (pantalla nueva, E8.3). */
@Composable
fun ExportDataScreen(
    state: ExportDataUiState,
    actions: ExportDataActions,
    modifier: Modifier = Modifier
) {
    val colors = ParcheUTheme.colors
    val spacing = ParcheUTheme.spacing
    Column(modifier.fillMaxSize().background(colors.bg).testTag(ExportDataTags.SCREEN)) {
        ParcheUChildTopBar(
            title = "Descargar mis datos",
            navigationIcon = rememberVectorPainter(AccountBackIcon),
            navigationContentDescription = "Atrás",
            onNavigationClick = actions.onBack
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(spacing.blockGap)
        ) {
            Text(
                "Una copia de todo lo tuyo",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.tx,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                "Te damos un archivo JSON con tus datos en ParcheU. Puedes guardarlo en " +
                    "tu teléfono o enviarlo a donde quieras.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.mu
            )
            InfoListCard(
                title = "Qué incluye",
                items = ExportContents.map {
                    InfoListItem(it, rememberVectorPainter(AccountDoneIcon))
                }
            )
            InfoNote(
                "De las otras personas solo va un identificador anónimo: nunca su nombre, " +
                    "su foto ni sus mensajes."
            )
            statusMessage(state)?.let { (text, isError) ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isError) colors.coralt else colors.prt,
                    modifier =
                    Modifier
                        .testTag(ExportDataTags.STATUS)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(spacing.footer),
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            PrimaryButton(
                text = if (state.loading ==
                    ExportTarget.SAVE
                ) {
                    "Preparando…"
                } else {
                    "Descargar mis datos"
                },
                onClick = actions.onDownload,
                enabled = !state.busy,
                icon = rememberVectorPainter(DownloadIcon),
                modifier = Modifier.fillMaxWidth().testTag(ExportDataTags.DOWNLOAD)
            )
            OutlineButton(
                text = if (state.loading == ExportTarget.SHARE) "Preparando…" else "Compartir",
                onClick = actions.onShare,
                enabled = !state.busy,
                icon = rememberVectorPainter(ShareIcon),
                modifier = Modifier.fillMaxWidth().testTag(ExportDataTags.SHARE)
            )
        }
    }
}

/** Texto de estado y si es un error. */
internal fun statusMessage(state: ExportDataUiState): Pair<String, Boolean>? {
    state.error?.let { return exportErrorMessage(it) to true }
    return when (state.outcome) {
        ExportOutcome.SAVED -> "Listo: guardamos tus datos donde elegiste." to false
        ExportOutcome.SHARED -> null
        ExportOutcome.CANCELLED -> "No se guardó nada: cancelaste antes de elegir dónde." to false
        ExportOutcome.WRITE_FAILED -> "No pudimos escribir el archivo. Inténtalo de nuevo." to true
        null -> null
    }
}

internal fun exportErrorMessage(error: AppError): String = when (error) {
    AppError.Network -> "Sin conexión. Revisa tu internet e inténtalo de nuevo."
    else -> "No pudimos preparar tus datos. Inténtalo de nuevo en un momento."
}
