package me.santiagoreina.parcheu.feature.twinquestions

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.santiagoreina.parcheu.R
import me.santiagoreina.parcheu.contract.QuestionDto
import me.santiagoreina.parcheu.contract.TwinAnswer
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUButtonSize
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUChildTopBar
import me.santiagoreina.parcheu.designsystem.components.base.ParcheULinearProgress
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUTextButton
import me.santiagoreina.parcheu.designsystem.components.base.PrimaryButton
import me.santiagoreina.parcheu.designsystem.mascot.TwinMascot
import me.santiagoreina.parcheu.designsystem.mascot.TwinMascotSize
import me.santiagoreina.parcheu.designsystem.theme.ParcheURadius
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/** Icono "close" 24dp; el de E1.2 reemplaza este cuando se mezcle esa rama. Se tiñe con el color del contenido. */
private val CloseIcon: ImageVector =
    ImageVector.Builder("close", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(6f, 6f)
            lineTo(18f, 18f)
            moveTo(18f, 6f)
            lineTo(6f, 18f)
        }
    }.build()

/** Distancia horizontal (dp) a partir de la cual soltar la tarjeta cuenta como respuesta. */
internal val SWIPE_THRESHOLD = 110.dp

/** B1 sin estado: pila de tarjetas, progreso "n de 12", barra de gestos y estados de carga, envío y error. */
@Composable
fun TwinQuestionsContent(
    state: TwinQuestionsUiState,
    onAnswer: (TwinAnswer) -> Unit,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ParcheUTheme.colors
    Column(
        modifier = modifier.fillMaxSize().background(colors.bg).testTag("twinQuestions:screen")
    ) {
        val showProgress = state.total > 0
        ParcheUChildTopBar(
            title = "",
            navigationIcon = rememberVectorPainter(CloseIcon),
            navigationContentDescription = stringResource(R.string.twin_questions_close),
            onNavigationClick = onClose,
            action = {
                if (showProgress) {
                    Text(
                        text = stringResource(
                            R.string.twin_questions_progress,
                            state.currentNumber,
                            state.total
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.mu,
                        modifier = Modifier.padding(
                            end = 8.dp
                        ).testTag("twinQuestions:progressText")
                    )
                }
            }
        )
        if (showProgress) {
            ParcheULinearProgress(
                progress = state.answeredCount.toFloat() / state.total,
                contentDescription =
                stringResource(
                    R.string.twin_questions_progress_description,
                    state.currentNumber,
                    state.total
                ),
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
        when (state.phase) {
            TwinQuestionsPhase.Loading -> Centered(stringResource(R.string.twin_questions_loading))
            TwinQuestionsPhase.Submitting, TwinQuestionsPhase.Done ->
                Centered(stringResource(R.string.twin_questions_sending))
            TwinQuestionsPhase.Error -> ErrorPane(state.error, onRetry)
            TwinQuestionsPhase.Answering -> state.current?.let { question ->
                QuestionStack(question = question, onAnswer = onAnswer)
            }
        }
    }
}

@Composable
private fun Centered(text: String) {
    Box(
        Modifier.fillMaxSize().testTag("twinQuestions:status"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TwinMascot(size = TwinMascotSize.M, talking = true)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = ParcheUTheme.colors.mu)
        }
    }
}

@Composable
private fun ErrorPane(error: AppError?, onRetry: () -> Unit) {
    val colors = ParcheUTheme.colors
    val message =
        if (error is AppError.Network) {
            R.string.twin_questions_error_network
        } else {
            R.string.twin_questions_error_generic
        }
    Column(
        modifier = Modifier.fillMaxSize().padding(
            horizontal = 32.dp
        ).testTag("twinQuestions:error"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TwinMascot(size = TwinMascotSize.L, error = true)
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.twin_questions_error_title),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.tx,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(message),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.mu,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        PrimaryButton(
            text = stringResource(R.string.twin_questions_retry),
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().testTag("twinQuestions:retry")
        )
    }
}

@Composable
private fun QuestionStack(question: QuestionDto, onAnswer: (TwinAnswer) -> Unit) {
    val colors = ParcheUTheme.colors
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TwinMascot(size = TwinMascotSize.S)
            Text(
                stringResource(R.string.twin_questions_taking_note),
                style = MaterialTheme.typography.bodySmall,
                color = colors.mu
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp)) {
            // Dos tarjetas decorativas detrás de la actual (rotación -3 y +2 grados).
            Box(
                Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(bottom = 12.dp)
                    .rotate(-3f).background(colors.s2, RoundedCornerShape(ParcheURadius.Hero))
            )
            Box(
                Modifier.fillMaxSize().padding(
                    horizontal = 12.dp
                ).padding(top = 6.dp, bottom = 6.dp)
                    .rotate(
                        2f
                    ).alpha(0.6f).background(colors.ln, RoundedCornerShape(ParcheURadius.Hero))
            )
            // La clave por id reinicia el desplazamiento al pasar a la siguiente pregunta.
            key(question.id) {
                SwipeableQuestionCard(
                    question = question,
                    onAnswer = onAnswer,
                    modifier = Modifier.fillMaxSize().padding(top = 12.dp)
                )
            }
        }
        SwipeHintBar(onDepends = { onAnswer(TwinAnswer.DEPENDS) })
    }
}

@Composable
private fun SwipeableQuestionCard(
    question: QuestionDto,
    onAnswer: (TwinAnswer) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ParcheUTheme.colors
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val density = LocalDensity.current
    val thresholdPx = with(density) { SWIPE_THRESHOLD.toPx() }
    val flingPx = thresholdPx * 4
    val answered = remember { booleanArrayOf(false) }

    fun commit(answer: TwinAnswer, target: Float) {
        if (answered[0]) return
        answered[0] = true
        scope.launch {
            offset.animateTo(target)
            onAnswer(answer)
        }
    }

    LaunchedEffect(question.id) { offset.snapTo(0f) }

    Column(
        modifier =
        modifier
            .graphicsLayer {
                translationX = offset.value
                rotationZ = (offset.value / flingPx) * 12f
            }
            .shadow(18.dp, RoundedCornerShape(ParcheURadius.Hero), clip = false)
            .background(colors.s1, RoundedCornerShape(ParcheURadius.Hero))
            .pointerInput(question.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            offset.value <= -thresholdPx -> commit(TwinAnswer.A, -flingPx)
                            offset.value >= thresholdPx -> commit(TwinAnswer.B, flingPx)
                            else -> scope.launch { offset.animateTo(0f) }
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f) } },
                    onHorizontalDrag = { change, delta ->
                        change.consume()
                        if (!answered[0]) scope.launch { offset.snapTo(offset.value + delta) }
                    }
                )
            }
            .padding(horizontal = 18.dp, vertical = 22.dp)
            .testTag("twinQuestions:card"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = question.text,
            style = MaterialTheme.typography.headlineSmall,
            color = colors.tx,
            modifier = Modifier.testTag("twinQuestions:questionText")
        )
        OptionCard(
            label = stringResource(R.string.twin_questions_option_a),
            text = question.optionA,
            tag = "twinQuestions:optionA",
            onClick = { commit(TwinAnswer.A, -flingPx) },
            modifier = Modifier.weight(1f)
        )
        OptionCard(
            label = stringResource(R.string.twin_questions_option_b),
            text = question.optionB,
            tag = "twinQuestions:optionB",
            onClick = { commit(TwinAnswer.B, flingPx) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun OptionCard(
    label: String,
    text: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ParcheUTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .background(colors.s1, shape)
            .border(2.dp, colors.ln, shape)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .padding(16.dp)
            .testTag(tag),
        verticalArrangement = Arrangement.Bottom
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.prt)
        Spacer(Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = colors.tx)
    }
}

@Composable
private fun SwipeHintBar(onDepends: () -> Unit) {
    val colors = ParcheUTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "← " + stringResource(R.string.twin_questions_option_a),
            style = MaterialTheme.typography.bodySmall,
            color = colors.mu
        )
        ParcheUTextButton(
            text = stringResource(R.string.twin_questions_depends),
            onClick = onDepends,
            size = ParcheUButtonSize.Medium,
            modifier = Modifier.testTag("twinQuestions:depends")
        )
        Text(
            stringResource(R.string.twin_questions_option_b) + " →",
            style = MaterialTheme.typography.bodySmall,
            color = colors.mu
        )
    }
}
