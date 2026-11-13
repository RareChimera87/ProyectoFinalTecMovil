package me.santiagoreina.parcheu.feature.twinquestions

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import me.santiagoreina.parcheu.R
import me.santiagoreina.parcheu.contract.QuestionDto
import me.santiagoreina.parcheu.contract.TwinAnswer
import me.santiagoreina.parcheu.core.error.AI_BUDGET_MESSAGE
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUButtonSize
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUChildTopBar
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUTextButton
import me.santiagoreina.parcheu.designsystem.components.base.PrimaryButton
import me.santiagoreina.parcheu.designsystem.mascot.TwinMascot
import me.santiagoreina.parcheu.designsystem.mascot.TwinMascotSize
import me.santiagoreina.parcheu.designsystem.theme.ParcheUPalette
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.plus.formatWeekdayDate

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

/** Luminancia de una pantalla de celular encendida en blanco: el color del velo de [photoVeil]. */
private const val SCREEN_LUMA = 0.92f

/** B1 sin estado: pila de tarjetas, progreso "n de 12", barra de gestos y estados de carga, envío y error. */
@Composable
fun TwinQuestionsContent(
    state: TwinQuestionsUiState,
    onAnswer: (TwinAnswer) -> Unit,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    showClose: Boolean = true
) {
    val colors = ParcheUTheme.colors
    Column(
        modifier = modifier.fillMaxSize().background(colors.bg).testTag("twinQuestions:screen")
    ) {
        val showProgress = state.total > 0
        val progressText: @Composable () -> Unit = {
            if (showProgress) {
                Text(
                    text = stringResource(
                        R.string.twin_questions_progress,
                        state.currentNumber,
                        state.total
                    ),
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                    color = colors.mu,
                    modifier = Modifier.padding(
                        end = 8.dp
                    ).testTag("twinQuestions:progressText")
                )
            }
        }
        if (showClose) {
            ParcheUChildTopBar(
                title = "",
                navigationIcon = rememberVectorPainter(CloseIcon),
                navigationContentDescription = stringResource(R.string.twin_questions_close),
                onNavigationClick = onClose,
                action = { progressText() }
            )
        } else {
            // B1 es la raíz de la pila: no hay a dónde cerrar, así que no se muestra la X.
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                progressText()
                Spacer(Modifier.width(8.dp))
            }
        }
        if (showProgress) {
            QuestionSegments(
                total = state.total,
                answered = state.answeredCount,
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

/**
 * Un 402 sin `kind` es el tope interno de reenvíos del gemelo (B-M1), no un cupo de ParcheU+: la pantalla
 * dice que vuelva mañana (con la fecha de `resetsAt` si llegó) en vez de ofrecer reintentar a ciegas.
 */
@Composable
private fun twinQuestionsErrorMessage(error: AppError?): String = when {
    error is AppError.Network -> stringResource(R.string.twin_questions_error_network)
    error is AppError.QuotaExceeded && error.kind == null ->
        error.resetsAt?.let {
            stringResource(R.string.twin_questions_error_daily_limit_on, formatWeekdayDate(it))
        }
            ?: stringResource(R.string.twin_questions_error_daily_limit)
    error is AppError.RateLimited -> stringResource(R.string.twin_questions_error_rate_limited)
    error is AppError.AiBudget -> AI_BUDGET_MESSAGE
    else -> stringResource(R.string.twin_questions_error_generic)
}

@Composable
private fun ErrorPane(error: AppError?, onRetry: () -> Unit) {
    val colors = ParcheUTheme.colors
    val message = twinQuestionsErrorMessage(error)
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
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.mu,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("twinQuestions:errorMessage")
        )
        Spacer(Modifier.height(24.dp))
        PrimaryButton(
            text = stringResource(R.string.twin_questions_retry),
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().testTag("twinQuestions:retry")
        )
    }
}

/**
 * Barra de 12 segmentos de la dirección A: los respondidos en violeta pleno y los demás en s2. Para TalkBack
 * es una sola barra de progreso ("Pregunta 4 de 12").
 */
@Composable
private fun QuestionSegments(
    total: Int,
    answered: Int,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val colors = ParcheUTheme.colors
    // En claro s2 casi no se distingue del fondo lila: los pendientes van en el color de los bordes.
    val pending = if (colors.isDark) colors.s2 else colors.ln
    Row(
        modifier
            .fillMaxWidth()
            .height(5.dp)
            .semantics {
                this.contentDescription = contentDescription
                progressBarRangeInfo = ProgressBarRangeInfo(answered.toFloat() / total, 0f..1f)
            },
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        repeat(total) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (i < answered) colors.hero else pending, CircleShape)
            )
        }
    }
}

@Composable
private fun QuestionStack(question: QuestionDto, onAnswer: (TwinAnswer) -> Unit) {
    val colors = ParcheUTheme.colors
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TwinMascot(size = TwinMascotSize.S, talking = true)
            Text(
                stringResource(R.string.twin_questions_taking_note),
                style = MaterialTheme.typography.bodySmall,
                color = colors.mu
            )
        }
        // La clave por id reinicia el desplazamiento al pasar a la siguiente pregunta.
        key(question.id) {
            SwipeableQuestionCard(
                question = question,
                onAnswer = onAnswer,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
        SwipeHintBar(onDepends = { onAnswer(TwinAnswer.DEPENDS) })
    }
}

/**
 * La pregunta y sus dos opciones. Todo el bloque se arrastra: a la izquierda responde A y a la derecha B. Sin
 * sombra ni tarjetas apiladas (dirección A): la opción hacia la que se arrastra se marca con el borde de 2 dp.
 */
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
    val images = remember(question.id) { TwinQuestionImages.forQuestion(question.id) }
    val (toneA, toneB) = questionPhotoHighlights(question.id)
    // A partir de un tercio del umbral ya se ve hacia qué opción va el gesto.
    val leaning =
        when {
            offset.value <= -thresholdPx / 3 -> TwinAnswer.A
            offset.value >= thresholdPx / 3 -> TwinAnswer.B
            else -> null
        }

    Column(
        modifier =
        modifier
            .graphicsLayer {
                translationX = offset.value
                rotationZ = (offset.value / flingPx) * 12f
            }
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
            .padding(top = 4.dp, bottom = 4.dp)
            .testTag("twinQuestions:card"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = question.text,
            style = MaterialTheme.typography.headlineSmall,
            color = colors.tx,
            modifier = Modifier.padding(bottom = 4.dp).testTag("twinQuestions:questionText")
        )
        OptionCard(
            letter = "A",
            label = stringResource(R.string.twin_questions_option_a),
            text = question.optionA,
            tag = "twinQuestions:optionA",
            image = images?.a,
            highlight = toneA,
            selected = leaning == TwinAnswer.A,
            onClick = { commit(TwinAnswer.A, -flingPx) },
            modifier = Modifier.weight(1f)
        )
        OptionCard(
            letter = "B",
            label = stringResource(R.string.twin_questions_option_b),
            text = question.optionB,
            tag = "twinQuestions:optionB",
            image = images?.b,
            highlight = toneB,
            selected = leaning == TwinAnswer.B,
            onClick = { commit(TwinAnswer.B, flingPx) },
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Opción A o B (dirección A): tarjeta s1 de radio 20 con la foto arriba a sangre, en duotono (decorativa: el
 * texto ya describe la opción), y abajo la letra en Sora 800 22 `prt` junto al texto. TalkBack lee "Opción A"
 * y el texto. Sin foto, la tarjeta deja el texto abajo como antes. La foto cede altura al texto, así que con
 * letra grande se achica en vez de recortar el texto.
 */
@Composable
private fun OptionCard(
    letter: String,
    label: String,
    text: String,
    tag: String,
    @DrawableRes image: Int?,
    highlight: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ParcheUTheme.colors
    val shape = ParcheUTheme.shapes.photo
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.s1, shape)
            .border(2.dp, if (selected) colors.prt else Color.Transparent, shape)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .testTag(tag)
    ) {
        if (image != null) {
            val painter = painterResource(image)
            val gain = photoGainFor(image)
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = b1DuotoneFilter(ParcheUPalette.Ink, highlight, gain),
                modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .photoVeil(
                        image = image,
                        intrinsic = painter.intrinsicSize,
                        color = b1DuotoneColor(ParcheUPalette.Ink, highlight, gain, SCREEN_LUMA)
                    )
                    .testTag("$tag:image")
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        Row(
            Modifier.fillMaxWidth().padding(
                start = 14.dp,
                end = 14.dp,
                top = 10.dp,
                bottom = 12.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                letter,
                style = ParcheUTheme.textStyles.hero46.copy(
                    fontSize = 22.sp,
                    lineHeight = 26.sp,
                    letterSpacing = 0.sp
                ),
                color = colors.prt,
                modifier = Modifier.widthIn(min = 18.dp).semantics { contentDescription = label }
            )
            Text(text, style = MaterialTheme.typography.titleMedium, color = colors.tx)
        }
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
