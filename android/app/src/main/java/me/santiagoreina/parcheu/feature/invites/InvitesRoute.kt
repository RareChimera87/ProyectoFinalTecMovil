package me.santiagoreina.parcheu.feature.invites

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.santiagoreina.parcheu.navigation.AddFriends
import me.santiagoreina.parcheu.navigation.AppNavigator
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * C5 (E6.1). [AddFriends.code] llega del App Link `https://<hosting>/i/{codigo}` o de
 * `parcheu://add-friends/{codigo}`
 * y queda precargado en el campo. 'Compartir invitación' abre la hoja del sistema con el enlace y
 * 'Escanear' abre
 * Google Code Scanner sobre esta Activity.
 */
@Composable
fun InvitesRoute(
    route: AddFriends,
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    viewModel: InvitesViewModel =
        koinViewModel(key = "invites:${route.code}") { parametersOf(route.code) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scanner = remember(context) { GmsCodeScanner(context) }
    InvitesScreen(
        state = state,
        actions = InvitesActions(
            onBack = { navigator.back() },
            onTab = viewModel::onTab,
            onCopy = { code -> copyToClipboard(context, code) },
            onShare = { code, link -> shareInvite(context, code, link) },
            onCodeChange = viewModel::onCodeChange,
            onMode = viewModel::onMode,
            onAdd = viewModel::onAdd,
            onScan = { viewModel.onScan(scanner) },
            onAccept = viewModel::onAccept,
            onRetry = viewModel::load
        ),
        modifier = modifier
    )
}

/** Texto que se comparte: el código y el App Link (que abre C5 con el código precargado). */
internal fun shareText(code: String, link: String): String =
    "¡Agrégame en ParcheU! Mi código es $code. Abre este enlace desde tu teléfono: $link"

private fun shareInvite(context: Context, code: String, link: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, shareText(code, link))
    try {
        context.startActivity(Intent.createChooser(send, "Compartir invitación"))
    } catch (_: ActivityNotFoundException) {
        // Sin apps para compartir: queda copiar el código.
    }
}

private fun copyToClipboard(context: Context, code: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Código de ParcheU", code))
}
