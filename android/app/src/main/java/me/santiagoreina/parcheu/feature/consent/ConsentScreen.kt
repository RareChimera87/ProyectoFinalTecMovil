package me.santiagoreina.parcheu.feature.consent

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUCard
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUChildTopBar
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUTextButton
import me.santiagoreina.parcheu.designsystem.components.base.PrimaryButton
import me.santiagoreina.parcheu.designsystem.components.compound.InfoNote
import me.santiagoreina.parcheu.designsystem.mascot.TwinMascot
import me.santiagoreina.parcheu.designsystem.mascot.TwinMascotSize
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.modes.FlowIcons

object ConsentTags {
    const val SCREEN = "consent"
    const val ACCEPT = "consent:accept"
    const val NOT_NOW = "consent:notnow"
    const val ERROR = "consent:error"
}

/**
 * B4 'Aprender de mis chats'. Explica qué aprende el gemelo y qué nunca hace (ADR-0017). 'Acepto' y
 * 'Ahora no' reemplazan el interruptor del mockup: el consentimiento es opcional y expreso.
 */
@Composable
fun ConsentScreen(
    state: ConsentUiState,
    showBack: Boolean,
    onBack: () -> Unit,
    onAccept: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
    animateMascot: Boolean = true,
) {
    val colors = ParcheUTheme.colors
    Column(modifier.fillMaxSize().background(colors.bg).testTag(ConsentTags.SCREEN)) {
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
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                TwinMascot(size = TwinMascotSize.L, animationsEnabled = animateMascot)
            }
            Text(
                "Tus chats ayudan a que tu gemelo te conozca mejor",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.tx,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "Cuando chateas en ParcheU, tu gemelo aprende de cómo conversas para simular mejor. " +
                    "Aplica a tus chats de Amistad y Estudio; los de Pareja quedan fuera hasta que lo actives en Ajustes de privacidad.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.mu,
            )
            ListCard(
                "Qué aprende",
                colors.prt,
                listOf(
                    FlowIcons.Edit to "Tu forma de escribir",
                    FlowIcons.Star to "Temas y gustos",
                    FlowIcons.Handshake to "Cómo llegas a acuerdos",
                ),
            )
            ListCard(
                "Qué nunca hace",
                colors.mu,
                listOf(
                    FlowIcons.Block to "Mostrar tus mensajes a otras personas",
                    FlowIcons.Block to "Escribir por ti en un chat real",
                ),
            )
            state.error?.let { InfoNote(consentErrorMessage(it), Modifier.testTag(ConsentTags.ERROR)) }
        }
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PrimaryButton(
                text = if (state.submitting) "Guardando…" else "Acepto",
                onClick = onAccept,
                enabled = !state.submitting,
                modifier = Modifier.fillMaxWidth().testTag(ConsentTags.ACCEPT),
            )
            ParcheUTextButton(
                text = "Ahora no",
                onClick = onNotNow,
                enabled = !state.submitting,
                modifier = Modifier.fillMaxWidth().testTag(ConsentTags.NOT_NOW),
            )
            Text(
                "Lo puedes apagar o borrar en Ajustes de privacidad.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.mu,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ListCard(title: String, tint: Color, items: List<Pair<ImageVector, String>>) {
    val colors = ParcheUTheme.colors
    ParcheUCard(Modifier.fillMaxWidth()) {
        Text(title.uppercase(), style = ParcheUTheme.textStyles.overline, color = colors.mu)
        items.forEach { (icon, text) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    rememberVectorPainter(icon),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp),
                )
                Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.tx)
            }
        }
    }
}
