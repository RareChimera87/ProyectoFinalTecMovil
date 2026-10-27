package me.santiagoreina.parcheu.feature.privacysettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.santiagoreina.parcheu.BuildConfig
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.BlockedUsers
import me.santiagoreina.parcheu.navigation.DeleteAccount
import me.santiagoreina.parcheu.navigation.ExportData
import me.santiagoreina.parcheu.navigation.PrivacySettings
import org.koin.compose.viewmodel.koinViewModel

/**
 * H3 'Ajustes de privacidad' (E8.2). Los enlaces a 'Personas bloqueadas' (E8.7), 'Exportar mis datos' y
 * 'Borrar mi gemelo y mis datos' (E8.3) navegan a sus rutas; Términos y Política abren la página pública.
 * 'Cerrar sesión' deja A4 como única pantalla (`replaceAll`).
 */
@Composable
fun PrivacySettingsRoute(
    @Suppress("UNUSED_PARAMETER") route: PrivacySettings,
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    viewModel: PrivacySettingsViewModel = koinViewModel(),
    termsUrl: String = BuildConfig.TERMS_URL,
    privacyUrl: String = BuildConfig.PRIVACY_URL
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PrivacySettingsEffect.ReplaceAll -> navigator.replaceAll(effect.route)
            }
        }
    }
    PrivacySettingsScreen(
        state = state,
        actions =
        PrivacySettingsActions(
            onBack = { navigator.back() },
            onRetry = viewModel::load,
            onModeToggle = viewModel::onModeToggle,
            onVisibility = viewModel::onVisibility,
            onLearningToggle = viewModel::onLearningToggle,
            onDialogConfirmedChange = viewModel::onDialogConfirmedChange,
            onDialogConfirm = viewModel::onDialogConfirm,
            onDialogCancel = viewModel::onDialogCancel,
            onBlockedUsers = { navigator.navigate(BlockedUsers) },
            onExport = { navigator.navigate(ExportData) },
            onDelete = { navigator.navigate(DeleteAccount) },
            onTerms = { uriHandler.openUri(termsUrl) },
            onPrivacyPolicy = { uriHandler.openUri(privacyUrl) },
            onSignOut = viewModel::onSignOut
        ),
        modifier = modifier
    )
}
