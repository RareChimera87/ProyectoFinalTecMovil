package me.santiagoreina.parcheu.feature.privacysettings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Visibility
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUChildTopBar
import me.santiagoreina.parcheu.designsystem.components.base.ParcheURadioRow
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUSwitch
import me.santiagoreina.parcheu.designsystem.components.base.SectionHeader
import me.santiagoreina.parcheu.designsystem.components.compound.ErrorState
import me.santiagoreina.parcheu.designsystem.components.compound.InfoBanner
import me.santiagoreina.parcheu.designsystem.components.compound.LoadingDots
import me.santiagoreina.parcheu.designsystem.components.compound.SettingsRow
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.deleteaccount.AccountBackIcon
import me.santiagoreina.parcheu.feature.deleteaccount.DeleteForeverIcon
import me.santiagoreina.parcheu.feature.deleteaccount.DownloadIcon
import me.santiagoreina.parcheu.feature.modes.FlowIcons
import me.santiagoreina.parcheu.feature.modes.ParejaDialog
import me.santiagoreina.parcheu.feature.modes.colors
import me.santiagoreina.parcheu.feature.modes.icon
import me.santiagoreina.parcheu.feature.modes.label

/** Etiquetas de prueba de H3. */
object PrivacySettingsTags {
    const val SCREEN = "privacy-settings"
    const val LOADING = "privacy-settings:loading"
    const val ERROR = "privacy-settings:error"
    const val STATUS = "privacy-settings:status"
    const val BLOCKED = "privacy-settings:blocked"
    const val EXPORT = "privacy-settings:export"
    const val DELETE = "privacy-settings:delete"
    const val TERMS = "privacy-settings:terms"
    const val PRIVACY_POLICY = "privacy-settings:privacy-policy"
    const val SIGN_OUT = "privacy-settings:sign-out"

    fun mode(mode: Mode) = "privacy-settings:mode:${mode.name}"

    fun learning(mode: Mode) = "privacy-settings:learning:${mode.name}"

    fun visibility(visibility: Visibility) = "privacy-settings:visibility:${visibility.name}"
}

data class PrivacySettingsActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onModeToggle: (Mode, Boolean) -> Unit = { _, _ -> },
    val onVisibility: (Visibility) -> Unit = {},
    val onLearningToggle: (Mode, Boolean) -> Unit = { _, _ -> },
    val onDialogConfirmedChange: (Boolean) -> Unit = {},
    val onDialogConfirm: () -> Unit = {},
    val onDialogCancel: () -> Unit = {},
    val onBlockedUsers: () -> Unit = {},
    val onExport: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onTerms: () -> Unit = {},
    val onPrivacyPolicy: () -> Unit = {},
    val onSignOut: () -> Unit = {}
)

private val VisibilityOptions =
    listOf(
        Triple(
            Visibility.NAME,
            "Visible con mi nombre",
            "Los amigos de tus amigos ven tu nombre y foto"
        ),
        Triple(Visibility.ANONYMOUS, "Aparecer como «Alguien»", "Solo ven tu % de afinidad"),
        Triple(Visibility.HIDDEN, "Oculto", "Solo tus contactos directos te ven")
    )

/**
 * H3 'Ajustes de privacidad' (E8.2): modos activos, visibilidad en el grafo, aprendizaje de chats por modo,
 * enlaces a bloqueados, exportación y borrado (E8.3 y E8.7), términos, política y 'Cerrar sesión'. Sin estado
 * propio: todo llega en [state]. B3 se dibuja encima cuando [PrivacySettingsUiState.dialog] no es nulo.
 */
@Composable
fun PrivacySettingsScreen(
    state: PrivacySettingsUiState,
    actions: PrivacySettingsActions,
    modifier: Modifier = Modifier,
    animateLoading: Boolean = true
) {
    val colors = ParcheUTheme.colors
    Box(modifier.fillMaxSize().background(colors.bg).testTag(PrivacySettingsTags.SCREEN)) {
        Column(Modifier.fillMaxSize()) {
            ParcheUChildTopBar(
                title = "Privacidad",
                navigationIcon = rememberVectorPainter(AccountBackIcon),
                navigationContentDescription = "Atrás",
                onNavigationClick = actions.onBack
            )
            when {
                state.loading -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                    LoadingDots(
                        Modifier.testTag(PrivacySettingsTags.LOADING),
                        animated = animateLoading
                    )
                }
                state.loadError != null -> ErrorState(
                    title = "No pudimos cargar tus ajustes",
                    message = settingsLoadErrorMessage(state.loadError),
                    retryLabel = "Reintentar",
                    onRetryClick = actions.onRetry,
                    modifier = Modifier.weight(1f).testTag(PrivacySettingsTags.ERROR)
                )
                else -> Content(state, actions, Modifier.weight(1f))
            }
        }
        state.dialog?.let { dialog ->
            ParejaDialog(
                dialog,
                actions.onDialogConfirmedChange,
                actions.onDialogConfirm,
                actions.onDialogCancel
            )
        }
    }
}

@Composable
private fun Content(
    state: PrivacySettingsUiState,
    actions: PrivacySettingsActions,
    modifier: Modifier
) {
    val colors = ParcheUTheme.colors
    val spacing = ParcheUTheme.spacing
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        state.error?.let {
            Text(
                settingsErrorMessage(it),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.coralt,
                modifier = Modifier
                    .testTag(PrivacySettingsTags.STATUS)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }

        SectionHeader("Modos activos")
        Card {
            Mode.entries.forEach { mode ->
                SwitchRow(
                    leading = { ModeBox(mode) },
                    title = mode.label,
                    subtitle = null,
                    checked = state.modes[mode] == true,
                    enabled = state.interactive,
                    description = "Modo ${mode.label}",
                    onChange = { actions.onModeToggle(mode, it) },
                    tag = PrivacySettingsTags.mode(mode)
                )
            }
        }

        SectionHeader("Visibilidad en el grafo de otros", Modifier.padding(top = 8.dp))
        Card {
            VisibilityOptions.forEach { (value, label, hint) ->
                ParcheURadioRow(
                    label = label,
                    supportingText = hint,
                    selected = state.visibility == value,
                    onClick = { actions.onVisibility(value) },
                    enabled = state.interactive,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .testTag(PrivacySettingsTags.visibility(value))
                )
            }
        }
        InfoBanner(
            "Tus vínculos de Pareja nunca aparecen en el grafo de otras personas.",
            icon = rememberVectorPainter(LockIcon)
        )

        SectionHeader("Aprender de mis chats", Modifier.padding(top = 8.dp))
        Card {
            Mode.entries.forEach { mode ->
                val parejaOff = mode == Mode.PAREJA && state.modes[Mode.PAREJA] != true
                SwitchRow(
                    leading = { ModeBox(mode) },
                    title = mode.label,
                    subtitle = if (parejaOff) {
                        "Activa Pareja para permitirlo"
                    } else {
                        "Tu gemelo aprende de cómo conversas"
                    },
                    checked = state.learning[mode] == true,
                    enabled = state.interactive && !parejaOff,
                    description = "Aprender de mis chats de ${mode.label}",
                    onChange = { actions.onLearningToggle(mode, it) },
                    tag = PrivacySettingsTags.learning(mode)
                )
            }
        }

        SectionHeader("Tu cuenta", Modifier.padding(top = 8.dp))
        Card {
            SettingsRow(
                title = "Personas bloqueadas",
                icon = rememberVectorPainter(FlowIcons.Block),
                onClick = actions.onBlockedUsers,
                trailing = { Chevron() },
                modifier = Modifier.testTag(PrivacySettingsTags.BLOCKED)
            )
            SettingsRow(
                title = "Exportar mis datos",
                icon = rememberVectorPainter(DownloadIcon),
                onClick = actions.onExport,
                trailing = { Chevron() },
                modifier = Modifier.testTag(PrivacySettingsTags.EXPORT)
            )
            SettingsRow(
                title = "Borrar mi gemelo y mis datos",
                icon = rememberVectorPainter(DeleteForeverIcon),
                onClick = actions.onDelete,
                destructive = true,
                modifier = Modifier.testTag(PrivacySettingsTags.DELETE)
            )
        }

        SectionHeader("Legal", Modifier.padding(top = 8.dp))
        Card {
            SettingsRow(
                title = "Términos y condiciones",
                icon = rememberVectorPainter(TermsIcon),
                onClick = actions.onTerms,
                trailing = { Chevron() },
                modifier = Modifier.testTag(PrivacySettingsTags.TERMS)
            )
            SettingsRow(
                title = "Política de privacidad",
                subtitle = "Tratamiento de datos personales (Ley 1581)",
                icon = rememberVectorPainter(PrivacyTipIcon),
                onClick = actions.onPrivacyPolicy,
                trailing = { Chevron() },
                modifier = Modifier.testTag(PrivacySettingsTags.PRIVACY_POLICY)
            )
        }

        Card(Modifier.padding(top = 8.dp, bottom = 24.dp)) {
            SettingsRow(
                title = if (state.signingOut) "Cerrando sesión…" else "Cerrar sesión",
                icon = rememberVectorPainter(LogoutIcon),
                onClick = actions.onSignOut,
                enabled = !state.signingOut,
                modifier = Modifier.testTag(PrivacySettingsTags.SIGN_OUT)
            )
        }
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(ParcheUTheme.colors.s1, shape),
        content = { content() }
    )
}

@Composable
private fun SwitchRow(
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean,
    description: String,
    onChange: (Boolean) -> Unit,
    tag: String
) {
    val colors = ParcheUTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = colors.tx
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.mu)
            }
        }
        ParcheUSwitch(
            checked = checked,
            onCheckedChange = onChange,
            contentDescription = description,
            enabled = enabled,
            modifier = Modifier.testTag(tag)
        )
    }
}

/** Caja de 36 radio 12 con el color pleno del modo (H3). */
@Composable
private fun ModeBox(mode: Mode) {
    val modeColors = mode.colors()
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.size(36.dp).clip(shape).background(modeColors.solid, shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            mode.icon(),
            contentDescription = null,
            tint = modeColors.onSolid,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun Chevron() {
    Icon(
        rememberVectorPainter(ChevronIcon),
        contentDescription = null,
        tint = ParcheUTheme.colors.mu,
        modifier = Modifier.size(22.dp)
    )
}
