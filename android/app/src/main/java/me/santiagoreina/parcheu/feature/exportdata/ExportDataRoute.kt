package me.santiagoreina.parcheu.feature.exportdata

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.ExportData
import org.koin.compose.viewmodel.koinViewModel

/** Tipo MIME del archivo exportado. */
const val EXPORT_MIME = "application/json"

/**
 * 'Descargar mis datos' (E8.3). 'Descargar' abre el selector del sistema (`ACTION_CREATE_DOCUMENT`) con el
 * nombre `parcheu-datos-<fecha>.json`; 'Compartir' abre la hoja del sistema con el archivo.
 */
@Composable
fun ExportDataRoute(
    @Suppress("UNUSED_PARAMETER") route: ExportData,
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    viewModel: ExportDataViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val createDocument =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(EXPORT_MIME)
        ) { uri ->
            viewModel.onDocumentCreated(uri?.toString())
        }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ExportDataEffect.CreateDocument -> createDocument.launch(effect.fileName)
                is ExportDataEffect.Share -> {
                    val send =
                        Intent(Intent.ACTION_SEND)
                            .setType(EXPORT_MIME)
                            .putExtra(Intent.EXTRA_STREAM, effect.uri.toUri())
                            .putExtra(Intent.EXTRA_TITLE, effect.fileName)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    try {
                        context.startActivity(Intent.createChooser(send, "Compartir mis datos"))
                    } catch (_: ActivityNotFoundException) {
                        // Sin apps para compartir: queda 'Descargar'.
                    }
                }
            }
        }
    }
    ExportDataScreen(
        state = state,
        actions =
        ExportDataActions(
            onBack = { navigator.back() },
            onDownload = viewModel::onDownload,
            onShare = viewModel::onShare
        ),
        modifier = modifier
    )
}
