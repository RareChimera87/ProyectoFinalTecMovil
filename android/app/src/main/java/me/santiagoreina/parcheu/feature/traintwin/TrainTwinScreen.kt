package me.santiagoreina.parcheu.feature.traintwin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.contract.FeedbackPrompt
import me.santiagoreina.parcheu.contract.MemoryDto
import me.santiagoreina.parcheu.designsystem.components.base.ModeChip
import me.santiagoreina.parcheu.designsystem.components.base.ModeChipSize
import me.santiagoreina.parcheu.designsystem.components.base.OutlineButton
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUButtonSize
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUCard
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUChildTopBar
import me.santiagoreina.parcheu.designsystem.components.base.PrimaryButton
import me.santiagoreina.parcheu.designsystem.components.base.SectionHeader
import me.santiagoreina.parcheu.designsystem.components.base.TonalButton
import me.santiagoreina.parcheu.designsystem.components.compound.ErrorState
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.parche.fof.FofIcons
import me.santiagoreina.parcheu.feature.safety.ReportAiContentAction
import me.santiagoreina.parcheu.feature.simulation.forMode
import me.santiagoreina.parcheu.feature.simulation.label

/** Etiquetas de prueba de C4. */
object TrainTwinTags {
    const val ROOT = "traintwin:root"
    const val LOADING = "traintwin:loading"
    const val ERROR = "traintwin:error"
    const val MESSAGE = "traintwin:message"
    const val SEE_ALL = "traintwin:seeAll"
    const val CONTROL = "traintwin:control"
    const val PENDING_EMPTY = "traintwin:pendingEmpty"
    const val RATINGS_EMPTY = "traintwin:ratingsEmpty"

    fun controlOption(index: Int) = "traintwin:control:$index"

    fun accept(id: String) = "traintwin:accept:$id"

    fun reject(id: String) = "traintwin:reject:$id"

    fun accepted(id: String) = "traintwin:accepted:$id"

    fun rateUp(id: String) = "traintwin:rateUp:$id"

    fun rateDown(id: String) = "traintwin:rateDown:$id"

    fun report(id: String) = "traintwin:report:$id"
}

/** Acciones de C4; la ruta las conecta con el ViewModel. */
data class TrainTwinCallbacks(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onShowAll: () -> Unit = {},
    val onAccept: (MemoryDto) -> Unit = {},
    val onReject: (MemoryDto) -> Unit = {},
    val onRate: (promptId: String, positive: Boolean) -> Unit = { _, _ -> },
    val onControlAnswer: (promptId: String, optionIndex: Int) -> Unit = { _, _ -> },
    val onReport: (promptId: String) -> Unit = {}
)

/** C4 Entrena a tu gemelo. Pantalla hija de C3: sin NavBar. */
@Composable
fun TrainTwinScreen(
    state: TrainTwinUiState,
    callbacks: TrainTwinCallbacks,
    modifier: Modifier = Modifier
) {
    val colors = ParcheUTheme.colors
    Column(modifier.fillMaxSize().background(colors.bg).testTag(TrainTwinTags.ROOT)) {
        ParcheUChildTopBar(
            title = "Entrena a tu gemelo",
            navigationIcon = rememberVectorPainter(FofIcons.Back),
            navigationContentDescription = "Volver",
            onNavigationClick = callbacks.onBack
        )
        when (state) {
            TrainTwinUiState.Loading ->
                Box(
                    Modifier.fillMaxSize().testTag(TrainTwinTags.LOADING),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = colors.primary)
                }
            is TrainTwinUiState.Error ->
                ErrorState(
                    title = "No pudimos cargar el entrenamiento",
                    message = state.error.message(),
                    retryLabel = "Reintentar",
                    onRetryClick = callbacks.onRetry,
                    modifier = Modifier.testTag(TrainTwinTags.ERROR)
                )
            is TrainTwinUiState.Loaded -> LoadedContent(state.content, callbacks)
        }
    }
}

@Composable
private fun LoadedContent(content: TrainTwinContent, callbacks: TrainTwinCallbacks) {
    val colors = ParcheUTheme.colors
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Mientras más le enseñes, mejor te representa en las simulaciones.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.mu
        )
        content.message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.coralt,
                modifier = Modifier.testTag(TrainTwinTags.MESSAGE)
            )
        }
        content.control?.let { ControlCard(it, content, callbacks) }

        SectionHeader(
            title = "Memorias pendientes",
            actionLabel = seeAllLabel(content),
            onActionClick = if (content.canExpand) callbacks.onShowAll else null,
            modifier = Modifier.testTag(TrainTwinTags.SEE_ALL)
        )
        if (content.pending.isEmpty()) {
            EmptyNote(
                "Cuando chatees con el aprendizaje activado, aquí aparecerá lo que tu gemelo aprendió de ti.",
                TrainTwinTags.PENDING_EMPTY
            )
        }
        content.visiblePending.forEach { PendingMemoryCard(it, it.id in content.busy, callbacks) }

        if (content.visibleAccepted.isNotEmpty()) {
            SectionHeader(title = "Memorias aceptadas")
            content.visibleAccepted.forEach { AcceptedMemoryCard(it) }
        }

        SectionHeader(title = "Respuestas por calificar")
        if (content.ratings.isEmpty()) {
            EmptyNote(
                "Cuando tu gemelo simule conversaciones, podrás calificar cómo respondió.",
                TrainTwinTags.RATINGS_EMPTY
            )
        }
        content.ratings.forEach { RatingCard(it, it.id in content.busy, callbacks) }
        Box(Modifier.padding(bottom = 16.dp))
    }
}

private fun seeAllLabel(content: TrainTwinContent): String? = when {
    !content.canExpand -> null
    content.showAll -> "Ver menos"
    else -> "Ver todas"
}

@Composable
private fun EmptyNote(text: String, tag: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = ParcheUTheme.colors.mu,
        modifier = Modifier.testTag(tag)
    )
}

@Composable
private fun ControlCard(
    prompt: FeedbackPrompt,
    content: TrainTwinContent,
    callbacks: TrainTwinCallbacks
) {
    val colors = ParcheUTheme.colors
    val busy = prompt.id in content.busy
    ParcheUCard(modifier = Modifier.testTag(TrainTwinTags.CONTROL)) {
        SectionHeader(title = "Pregunta de la semana")
        Text(
            "Tu gemelo ya adivinó qué responderías. Contesta tú y mira si acertó.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.mu
        )
        Text(
            prompt.text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.tx
        )
        prompt.options.orEmpty().forEachIndexed { index, option ->
            OutlineButton(
                text = option,
                onClick = { callbacks.onControlAnswer(prompt.id, index) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag(TrainTwinTags.controlOption(index))
            )
        }
    }
}

@Composable
private fun MemoryHeader(memory: MemoryDto) {
    val colors = ParcheUTheme.colors
    ModeChip(label = memory.mode.label(), mode = colors.forMode(memory.mode), size = ModeChipSize.S)
}

@Composable
private fun PendingMemoryCard(memory: MemoryDto, busy: Boolean, callbacks: TrainTwinCallbacks) {
    val colors = ParcheUTheme.colors
    ParcheUCard {
        MemoryHeader(memory)
        Text(memory.text, style = MaterialTheme.typography.bodyMedium, color = colors.tx)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlineButton(
                text = "Rechazar",
                onClick = { callbacks.onReject(memory) },
                size = ParcheUButtonSize.Small,
                enabled = !busy,
                modifier = Modifier.weight(1f).testTag(TrainTwinTags.reject(memory.id))
            )
            PrimaryButton(
                text = "Aceptar",
                onClick = { callbacks.onAccept(memory) },
                size = ParcheUButtonSize.Small,
                enabled = !busy,
                modifier = Modifier.weight(1f).testTag(TrainTwinTags.accept(memory.id))
            )
        }
    }
}

@Composable
private fun AcceptedMemoryCard(memory: MemoryDto) {
    val colors = ParcheUTheme.colors
    ParcheUCard(modifier = Modifier.testTag(TrainTwinTags.accepted(memory.id))) {
        MemoryHeader(memory)
        Text(memory.text, style = MaterialTheme.typography.bodyMedium, color = colors.tx)
    }
}

@Composable
private fun RatingCard(prompt: FeedbackPrompt, busy: Boolean, callbacks: TrainTwinCallbacks) {
    val colors = ParcheUTheme.colors
    ParcheUCard {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "“${prompt.text}”",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onprc,
                modifier =
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.prc)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
            ReportAiContentAction(
                onClick = { callbacks.onReport(prompt.id) },
                modifier = Modifier.testTag(TrainTwinTags.report(prompt.id))
            )
        }
        Text(
            "¿Así respondería tu gemelo como tú?",
            style = MaterialTheme.typography.bodySmall,
            color = colors.mu
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TonalButton(
                text = "Sí, soy yo",
                onClick = { callbacks.onRate(prompt.id, true) },
                size = ParcheUButtonSize.Small,
                enabled = !busy,
                modifier = Modifier.weight(1f).testTag(TrainTwinTags.rateUp(prompt.id))
            )
            OutlineButton(
                text = "No suena a mí",
                onClick = { callbacks.onRate(prompt.id, false) },
                size = ParcheUButtonSize.Small,
                enabled = !busy,
                modifier = Modifier.weight(1f).testTag(TrainTwinTags.rateDown(prompt.id))
            )
        }
    }
}
