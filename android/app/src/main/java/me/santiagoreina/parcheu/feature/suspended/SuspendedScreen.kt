package me.santiagoreina.parcheu.feature.suspended

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.designsystem.components.base.OutlineButton
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUTextButton
import me.santiagoreina.parcheu.designsystem.components.compound.InfoNote
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

object SuspendedTags {
    const val SCREEN = "suspended"
    const val EXPORT = "suspended:export"
    const val DELETE = "suspended:delete"
    const val SIGN_OUT = "suspended:signout"
}

/**
 * Pantalla de bloqueo por suspensión (nueva, sin mockup; mismo esquema que `UnderageScreen`): explica que
 * la cuenta está suspendida y deja descargar los datos, borrar la cuenta o cerrar sesión. No tiene botón
 * atrás: no hay a dónde volver.
 */
@Composable
fun SuspendedScreen(
    onExportData: () -> Unit,
    onDeleteAccount: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ParcheUTheme.colors
    val spacing = ParcheUTheme.spacing
    Column(
        modifier
            .fillMaxSize()
            .background(colors.bg)
            .testTag(SuspendedTags.SCREEN)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.gutterWide),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(
                spacing.blockGap,
                Alignment.CenterVertically
            )
        ) {
            Spacer(Modifier.height(48.dp))
            Text(
                text = "Tu cuenta está suspendida",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.tx,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = "Por ahora no puedes usar ParcheU: ni tu gemelo, ni tu parche, ni conocer " +
                    "gente.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.mu,
                textAlign = TextAlign.Center
            )
            InfoNote(
                text = "Tus datos siguen siendo tuyos: puedes descargarlos o borrar tu cuenta " +
                    "cuando quieras."
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacing.footer),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlineButton(
                text = "Descargar mis datos",
                onClick = onExportData,
                modifier = Modifier.fillMaxWidth().testTag(SuspendedTags.EXPORT)
            )
            OutlineButton(
                text = "Borrar mi cuenta",
                onClick = onDeleteAccount,
                modifier = Modifier.fillMaxWidth().testTag(SuspendedTags.DELETE)
            )
            ParcheUTextButton(
                text = "Cerrar sesión",
                onClick = onSignOut,
                modifier = Modifier.testTag(SuspendedTags.SIGN_OUT)
            )
        }
    }
}
