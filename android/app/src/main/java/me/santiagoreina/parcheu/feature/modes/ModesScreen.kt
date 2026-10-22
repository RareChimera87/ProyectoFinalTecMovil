package me.santiagoreina.parcheu.feature.modes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUCheckbox
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUChildTopBar
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUSwitch
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUTextButton
import me.santiagoreina.parcheu.designsystem.components.base.PrimaryButton
import me.santiagoreina.parcheu.designsystem.components.compound.ErrorState
import me.santiagoreina.parcheu.designsystem.components.compound.InfoBanner
import me.santiagoreina.parcheu.designsystem.components.compound.InfoNote
import me.santiagoreina.parcheu.designsystem.components.compound.LoadingDots
import me.santiagoreina.parcheu.designsystem.components.compound.ParcheUModalDialogContent
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/** Etiquetas de prueba de B2 y B3. */
object ModesTags {
    const val SCREEN = "modes"
    const val CONTINUE = "modes:continue"
    const val LOADING = "modes:loading"
    const val ERROR = "modes:error"
    const val DIALOG = "pareja:dialog"
    const val DIALOG_DATE = "pareja:date"
    const val DIALOG_CHECK = "pareja:check"
    const val DIALOG_CONFIRM = "pareja:confirm"
    const val DIALOG_CANCEL = "pareja:cancel"

    fun switch(mode: Mode) = "modes:switch:${mode.name}"
}

/** Texto de la casilla de B3 (ficha E2.5). */
const val PAREJA_CONFIRM_TEXT = "Confirmo que soy mayor de 18 y quiero activar Pareja"

/**
 * B2 'Elegir modos' con el diálogo B3 superpuesto. Sin estado propio: todo llega en [state]. El diálogo
 * se dibuja dentro de la pantalla (no en otra ventana) para que las capturas y las pruebas lo vean.
 */
@Composable
fun ModesScreen(
    state: ModesUiState,
    showBack: Boolean,
    onBack: () -> Unit,
    onToggle: (Mode, Boolean) -> Unit,
    onContinue: () -> Unit,
    onRetry: () -> Unit,
    onDialogConfirmedChange: (Boolean) -> Unit,
    onDialogConfirm: () -> Unit,
    onDialogCancel: () -> Unit,
    modifier: Modifier = Modifier,
    animateLoading: Boolean = true,
) {
    val colors = ParcheUTheme.colors
    Box(modifier.fillMaxSize().background(colors.bg).testTag(ModesTags.SCREEN)) {
        Column(Modifier.fillMaxSize()) {
            if (showBack) {
                ParcheUChildTopBar(
                    title = "",
                    navigationIcon = rememberVectorPainter(FlowIcons.Back),
                    navigationContentDescription = "Volver",
                    onNavigationClick = onBack,
                )
            } else {
                Spacer(Modifier.height(56.dp))
            }
            when {
                state.loading -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                    LoadingDots(Modifier.testTag(ModesTags.LOADING), animated = animateLoading)
                }
                state.loadError != null -> ErrorState(
                    title = "No pudimos cargar tus modos",
                    message = modesErrorMessage(state.loadError),
                    retryLabel = "Reintentar",
                    onRetryClick = onRetry,
                    modifier = Modifier.weight(1f).testTag(ModesTags.ERROR),
                )
                else -> ModesContent(state, onToggle, Modifier.weight(1f))
            }
            Column(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PrimaryButton(
                    text = if (state.saving) "Guardando…" else "Continuar",
                    onClick = onContinue,
                    enabled = state.canContinue,
                    modifier = Modifier.fillMaxWidth().testTag(ModesTags.CONTINUE),
                )
            }
        }
        state.dialog?.let { dialog ->
            ParejaDialog(dialog, onDialogConfirmedChange, onDialogConfirm, onDialogCancel)
        }
    }
}

@Composable
private fun ModesContent(state: ModesUiState, onToggle: (Mode, Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = ParcheUTheme.colors
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "¿Para qué quieres tu parche?",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.tx,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "Activa los modos que quieras. Puedes cambiarlos cuando quieras en Ajustes.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.mu,
            )
        }
        Mode.entries.forEach { mode ->
            ModeRow(mode, state.modes[mode] == true, enabled = !state.saving) { onToggle(mode, it) }
        }
        InfoBanner(
            text = "Solo te emparejamos con personas que tengan el mismo modo activo.",
            icon = rememberVectorPainter(FlowIcons.Info),
        )
        if (!state.modes.values.any { it }) {
            InfoNote("Activa al menos un modo para continuar.")
        }
        state.saveError?.let { InfoNote(modesErrorMessage(it), Modifier.testTag(ModesTags.ERROR)) }
    }
}

@Composable
private fun ModeRow(mode: Mode, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val colors = ParcheUTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(colors.s1, shape).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ModeIconBox(mode)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    mode.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.tx,
                )
                if (mode == Mode.PAREJA) AdultBadge()
            }
            Text(mode.description, style = MaterialTheme.typography.bodySmall, color = colors.mu)
        }
        ParcheUSwitch(
            checked = checked,
            onCheckedChange = onChange,
            contentDescription = "Modo ${mode.label}",
            enabled = enabled,
            modifier = Modifier.testTag(ModesTags.switch(mode)),
        )
    }
}

/** Cuadro de 48 radio 16 con el color pleno del modo. */
@Composable
internal fun ModeIconBox(mode: Mode) {
    val modeColors = mode.colors()
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier.size(48.dp).clip(shape).background(modeColors.solid, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(mode.icon(), contentDescription = null, tint = modeColors.onSolid, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun AdultBadge() {
    val colors = ParcheUTheme.colors
    Text(
        "+18",
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = colors.tx,
        modifier = Modifier
            .border(1.dp, colors.ln, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/** B3: diálogo obligatorio al encender Pareja. La fecha es de solo lectura. */
@Composable
private fun ParejaDialog(
    dialog: ParejaDialogState,
    onConfirmedChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = ParcheUTheme.colors
    BackHandler(onBack = onCancel)
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.scrim)
            // Tocar el fondo equivale a Cancelar (Pareja queda apagado).
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onCancel),
        contentAlignment = Alignment.Center,
    ) {
        ParcheUModalDialogContent(
            Modifier
                .testTag(ModesTags.DIALOG)
                .semantics { paneTitle = "Activar modo Pareja" }
                .clickable(remember { MutableInteractionSource() }, indication = null) {},
        ) {
            ModeIconBox(Mode.PAREJA)
            Text(
                "Activar modo Pareja",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.tx,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                if (dialog.birthDate != null) {
                    "Este modo es solo para mayores de 18 años. Esta es la fecha de nacimiento que verificamos en tu perfil."
                } else {
                    "Este modo es solo para mayores de 18 años y aún no verificamos tu fecha de nacimiento. " +
                        "Complétala en tu perfil para activarlo."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.mu,
            )
            if (dialog.birthDate != null) ReadOnlyBirthDate(dialog.birthDate, dialog.age)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ParcheUCheckbox(
                    checked = dialog.confirmed,
                    onCheckedChange = onConfirmedChange,
                    contentDescription = PAREJA_CONFIRM_TEXT,
                    enabled = dialog.birthDate != null,
                    modifier = Modifier.testTag(ModesTags.DIALOG_CHECK),
                )
                Text(
                    PAREJA_CONFIRM_TEXT,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (dialog.birthDate != null) colors.tx else colors.mu,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ParcheUTextButton(
                    text = "Cancelar",
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).testTag(ModesTags.DIALOG_CANCEL),
                )
                PrimaryButton(
                    text = "Confirmar",
                    onClick = onConfirm,
                    enabled = dialog.canConfirm,
                    modifier = Modifier.weight(1.3f).testTag(ModesTags.DIALOG_CONFIRM),
                )
            }
        }
    }
}

/** Campo de fecha de 56dp radio 14 en solo lectura: muestra la fecha verificada y la edad. */
@Composable
private fun ReadOnlyBirthDate(date: String, age: Int?) {
    val colors = ParcheUTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Fecha de nacimiento (verificada)",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.prt,
        )
        Row(
            Modifier.fillMaxWidth().height(56.dp).clip(shape).background(colors.s2, shape)
                .border(1.dp, colors.ln, shape).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                date,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.tx,
                modifier = Modifier.weight(1f).testTag(ModesTags.DIALOG_DATE),
            )
            if (age != null) {
                Text("$age años", style = MaterialTheme.typography.bodySmall, color = colors.mu)
            }
        }
    }
}
