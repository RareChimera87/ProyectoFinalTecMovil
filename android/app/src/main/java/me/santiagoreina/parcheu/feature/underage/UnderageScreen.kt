package me.santiagoreina.parcheu.feature.underage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.designsystem.components.base.OutlineButton
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUTextButton
import me.santiagoreina.parcheu.designsystem.components.compound.InfoNote
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

object UnderageTags {
    const val SCREEN = "underage"
    const val DELETE = "underage:delete"
    const val SIGN_OUT = "underage:signout"
}

/**
 * Pantalla de bloqueo por edad (E2.6, nueva y sin mockup): explica por qué la cuenta no puede seguir y
 * ofrece 'Borrar mi cuenta'. No tiene botón atrás: no hay a dónde volver.
 */
@Composable
fun UnderageScreen(
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
            .testTag(UnderageTags.SCREEN)
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
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(colors.prc),
                contentAlignment = Alignment.Center
            ) {
                Text("18+", style = MaterialTheme.typography.titleLarge, color = colors.onprc)
            }
            Text(
                text = "ParcheU es solo para mayores de 18",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.tx,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = "Según la fecha de nacimiento que registraste, todavía no tienes 18 años. " +
                    "Por eso tu cuenta quedó bloqueada: no puedes crear tu gemelo, ver tu parche " +
                    "ni conocer gente.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.mu,
                textAlign = TextAlign.Center
            )
            InfoNote(
                text = "La fecha de nacimiento no se puede cambiar. Puedes borrar tu cuenta y " +
                    "todos tus datos cuando quieras."
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacing.footer),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlineButton(
                text = "Borrar mi cuenta",
                onClick = onDeleteAccount,
                modifier = Modifier.fillMaxWidth().testTag(UnderageTags.DELETE)
            )
            ParcheUTextButton(
                text = "Cerrar sesión",
                onClick = onSignOut,
                modifier = Modifier.testTag(UnderageTags.SIGN_OUT)
            )
        }
    }
}
