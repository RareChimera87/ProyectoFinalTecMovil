package me.santiagoreina.parcheu.feature.consent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    animateMascot: Boolean = true
) {
    val colors = ParcheUTheme.colors
    Column(modifier.fillMaxSize().background(colors.bg).testTag(ConsentTags.SCREEN)) {
        if (showBack) {
            ParcheUChildTopBar(
                title = "",
                navigationIcon = rememberVectorPainter(FlowIcons.Back),
                navigationContentDescription = "Volver",
                onNavigationClick = onBack
            )
        } else {
            Spacer(Modifier.height(56.dp))
        }
        Column(
            Modifier.weight(
                1f
            ).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                TwinMascot(size = TwinMascotSize.L, animationsEnabled = animateMascot)
            }
            Text(
                "Tus chats ayudan a que tu gemelo te conozca mejor",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.tx,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                "Cuando chateas en ParcheU, tu gemelo " +
                    "aprende de cómo conversas " +
                    "para simular mejor. " +
                    "Aplica a tus chats de Amistad y Estudio; " +
                    "los de Pareja quedan fuera hasta que " +
                    "lo actives en Ajustes de privacidad.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.mu
            )
            ListCard(
                "Qué aprende",
                tonal = true,
                listOf(
                    FlowIcons.Edit to "Tu forma de escribir",
                    FlowIcons.Star to "Temas y gustos",
                    FlowIcons.Handshake to "Cómo llegas a acuerdos"
                )
            )
            ListCard(
                "Qué nunca hace",
                tonal = false,
                listOf(
                    FlowIcons.Block to "Mostrar tus mensajes a otras personas",
                    FlowIcons.Block to "Escribir por ti en un chat real"
                )
            )
            state.error?.let {
                InfoNote(consentErrorMessage(it), Modifier.testTag(ConsentTags.ERROR))
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PrimaryButton(
                text = if (state.submitting) "Guardando…" else "Acepto",
                onClick = onAccept,
                enabled = !state.submitting,
                modifier = Modifier.fillMaxWidth().testTag(ConsentTags.ACCEPT)
            )
            ParcheUTextButton(
                text = "Ahora no",
                onClick = onNotNow,
                enabled = !state.submitting,
                modifier = Modifier.fillMaxWidth().testTag(ConsentTags.NOT_NOW)
            )
            Text(
                "Lo puedes apagar o borrar en Ajustes de privacidad.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.mu,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Lista de B4 con los tres pesos de la dirección A: lo que aprende va en una tarjeta tonal (prc, el acceso que
 * importa) y lo que nunca hace en una con borde de 1 dp (s1), para que no pesen igual.
 */
@Composable
private fun ListCard(title: String, tonal: Boolean, items: List<Pair<ImageVector, String>>) {
    val colors = ParcheUTheme.colors
    val shape = ParcheUTheme.shapes.card
    val text = if (tonal) colors.onprc else colors.tx
    val support = if (tonal) colors.onprc else colors.mu
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (tonal) colors.prc else colors.s1, shape)
            .then(if (tonal) Modifier else Modifier.border(1.dp, colors.ln, shape))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title.uppercase(), style = ParcheUTheme.textStyles.overline, color = support)
        items.forEach { (icon, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    rememberVectorPainter(icon),
                    contentDescription = null,
                    tint = support,
                    modifier = Modifier.size(20.dp)
                )
                Text(label, style = MaterialTheme.typography.bodyMedium, color = text)
            }
        }
    }
}
