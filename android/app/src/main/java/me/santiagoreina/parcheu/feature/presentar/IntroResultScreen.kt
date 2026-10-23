package me.santiagoreina.parcheu.feature.presentar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.santiagoreina.parcheu.contract.IntroductionStatus
import me.santiagoreina.parcheu.contract.ReportDto
import me.santiagoreina.parcheu.designsystem.components.base.ModeChip
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUButtonSize
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUCard
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUChildTopBar
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUTextButton
import me.santiagoreina.parcheu.designsystem.components.base.PrimaryButton
import me.santiagoreina.parcheu.designsystem.components.base.TonalButton
import me.santiagoreina.parcheu.designsystem.components.compound.LoadingDots
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.results.AffinityRing
import me.santiagoreina.parcheu.feature.results.CardOverline
import me.santiagoreina.parcheu.feature.results.LockedReport
import me.santiagoreina.parcheu.feature.results.ReportRow
import me.santiagoreina.parcheu.feature.results.ResultsIcons
import me.santiagoreina.parcheu.feature.results.rememberResultsIcon
import me.santiagoreina.parcheu.feature.simulation.SimulationMascot
import me.santiagoreina.parcheu.feature.simulation.forMode
import me.santiagoreina.parcheu.feature.simulation.label

private const val REPORT_USED_TEXT = "Ya usaste el informe completo gratis de este mes."

/** Etiquetas de prueba de E2. */
object IntroResultTags {
    const val LOADING = "introResult:loading"
    const val ERROR = "introResult:error"
    const val SIMULATING = "introResult:simulating"
    const val READY = "introResult:ready"
    const val REASONS = "introResult:reasons"
    const val UNLOCK = "introResult:unlock"
    const val SEND = "introResult:send"
    const val SKIP = "introResult:skip"
    const val SENT = "introResult:sent"
    const val CONNECTED = "introResult:connected"
    const val DECLINED = "introResult:declined"
    const val FAILED = "introResult:failed"
}

data class IntroResultActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onUnlock: () -> Unit = {},
    val onSeePlus: () -> Unit = {},
    val onSend: () -> Unit = {},
    val onSkip: () -> Unit = {},
    val onDone: () -> Unit = {},
    val onIntroduceAgain: () -> Unit = {},
    val onDismissNotice: () -> Unit = {}
)

/**
 * E2 · Resultado de sus gemelos, para quien presenta: simulando, listo (con el informe según la
 * regla del informe mensual), propuesta enviada, conectados, 'No por ahora' y fallida.
 */
@Composable
fun IntroResultScreen(
    state: IntroResultUiState,
    actions: IntroResultActions,
    modifier: Modifier = Modifier
) {
    val colors = ParcheUTheme.colors
    Column(modifier.fillMaxSize().background(colors.bg)) {
        ParcheUChildTopBar(
            title = "Presentar a dos amigos",
            navigationIcon = rememberPresentarIcon(PresentarIcons.ArrowBack),
            navigationContentDescription = "Volver",
            onNavigationClick = actions.onBack
        )
        when (state) {
            IntroResultUiState.Loading -> PresentarLoading(IntroResultTags.LOADING)
            is IntroResultUiState.Error -> PresentarLoadError(
                state.error,
                actions.onRetry,
                IntroResultTags.ERROR
            )
            is IntroResultUiState.Loaded -> Loaded(state.content, actions)
        }
    }
}

@Composable
private fun Loaded(content: IntroResultContent, actions: IntroResultActions) {
    val report = content.introduction.report
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PairHeader(content)
            when (content.status) {
                IntroductionStatus.SIMULATING -> Simulating(content)
                IntroductionStatus.FAILED -> StatusText(
                    "La simulación no se pudo completar. No se gastó tu cupo.",
                    IntroResultTags.FAILED
                )
                else -> if (report != null) Report(content, report, actions)
            }
            content.notice?.let { notice ->
                PresentarNotice(
                    when (notice) {
                        IntroResultNotice.ReportAlreadyUsed -> REPORT_USED_TEXT
                        is IntroResultNotice.Failed -> presentarErrorText(notice.error)
                    },
                    actions.onDismissNotice
                )
            }
        }
        Footer(content, actions)
    }
}

@Composable
private fun PairHeader(content: IntroResultContent) {
    val colors = ParcheUTheme.colors
    val mode = colors.forMode(content.introduction.mode)
    val (first, second) = content.names
    val score = content.introduction.report?.score
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            val failed = content.status == IntroductionStatus.FAILED
            SimulationMascot(mode.solid, 64.dp, error = failed)
            if (score != null) AffinityRing(score, size = 112.dp) else LoadingDots(animated = false)
            SimulationMascot(mode.solid, 64.dp, error = failed)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            NameLabel(first)
            NameLabel(second)
        }
        ModeChip(label = content.introduction.mode.label(), mode = mode)
    }
}

@Composable
private fun NameLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge.copy(
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        ),
        color = ParcheUTheme.colors.tx,
        maxLines = 1
    )
}

@Composable
private fun Simulating(content: IntroResultContent) {
    val (first, second) = content.names
    Column(
        Modifier.fillMaxWidth().testTag(IntroResultTags.SIMULATING),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "Los gemelos de $first y $second están conversando…",
            style = MaterialTheme.typography.titleMedium,
            color = ParcheUTheme.colors.tx,
            textAlign = TextAlign.Center
        )
        Text(
            "Te avisamos cuando terminen. Ninguno de los dos sabe nada todavía.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = ParcheUTheme.colors.mu,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun Report(content: IntroResultContent, report: ReportDto, actions: IntroResultActions) {
    val colors = ParcheUTheme.colors
    Text(
        report.summary,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.mu,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().widthIn(max = 300.dp)
    )
    val reasons = report.reasons.orEmpty()
    if (!report.locked) {
        if (reasons.isNotEmpty()) {
            ParcheUCard(Modifier.fillMaxWidth().testTag(IntroResultTags.REASONS)) {
                CardOverline("Por qué funcionaría")
                reasons.forEach {
                    ReportRow(it, rememberResultsIcon(ResultsIcons.Reason), colors.prt)
                }
            }
        }
    } else {
        // Misma regla y redacción que F7: el servidor no envía las razones de un informe bloqueado.
        val busy = content.busy != null
        val message = if (report.unlockAvailable) {
            val month = content.usedMonth?.let { " de $it" } ?: " de este mes"
            "Tienes 1 informe completo gratis$month: úsalo aquí para ver " +
                "${report.reasonsCount} razones."
        } else {
            val used = content.usedMonth?.let { "de $it" } ?: "de este mes"
            val back = content.resetMonth?.let { "en $it" } ?: "el próximo mes"
            "Ya usaste tu informe gratis $used. Vuelve $back o míralo con ParcheU+."
        }
        LockedReport(
            reasonsCount = report.reasonsCount,
            frictionsCount = report.frictionsCount,
            title = "Por qué funcionaría y dónde chocarían",
            message = message
        ) {
            if (report.unlockAvailable) {
                TonalButton(
                    text = if (content.busy ==
                        IntroResultBusy.UNLOCK
                    ) {
                        "Abriendo…"
                    } else {
                        "Ver informe completo"
                    },
                    onClick = actions.onUnlock,
                    enabled = !busy,
                    size = ParcheUButtonSize.Medium,
                    modifier = Modifier.testTag(IntroResultTags.UNLOCK)
                )
            }
            ParcheUTextButton(
                text = "Ver ParcheU+",
                onClick = actions.onSeePlus,
                size = ParcheUButtonSize.Medium
            )
        }
    }
    if (content.status == IntroductionStatus.READY) {
        PresentarHint(
            "Ninguno sabrá si el otro dice que no.",
            rememberPresentarIcon(PresentarIcons.VisibilityOff)
        )
    }
}

@Composable
private fun StatusText(text: String, tag: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = ParcheUTheme.colors.mu,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().testTag(tag)
    )
}

@Composable
private fun Footer(content: IntroResultContent, actions: IntroResultActions) {
    val (first, second) = content.names
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (content.status) {
            IntroductionStatus.READY -> {
                PrimaryButton(
                    text = if (content.busy ==
                        IntroResultBusy.SEND
                    ) {
                        "Enviando…"
                    } else {
                        "Enviar propuesta a los dos"
                    },
                    onClick = actions.onSend,
                    enabled = content.busy == null,
                    icon = rememberPresentarIcon(PresentarIcons.Send),
                    modifier = Modifier.fillMaxWidth().testTag(IntroResultTags.SEND)
                )
                ParcheUTextButton(
                    text = "No por ahora",
                    onClick = actions.onSkip,
                    enabled = content.busy == null,
                    modifier = Modifier.fillMaxWidth().testTag(IntroResultTags.SKIP)
                )
            }
            IntroductionStatus.SIMULATING -> ParcheUTextButton(
                text = "Volver mientras tanto",
                onClick = actions.onDone,
                modifier = Modifier.fillMaxWidth()
            )
            IntroductionStatus.FAILED -> PrimaryButton(
                text = "Intentar de nuevo",
                onClick = actions.onIntroduceAgain,
                modifier = Modifier.fillMaxWidth()
            )
            else -> {
                val (text, tag) = when (content.status) {
                    IntroductionStatus.CONNECTED ->
                        "¡$first y $second se conectaron! Ya pueden hablar." to
                            IntroResultTags.CONNECTED
                    IntroductionStatus.DECLINED ->
                        "No enviaste la propuesta. Ninguno de los dos se enteró." to
                            IntroResultTags.DECLINED
                    else ->
                        "Propuesta enviada. Si a los dos les interesa, se abre un chat " +
                            "entre ellos." to
                            IntroResultTags.SENT
                }
                StatusText(text, tag)
                PrimaryButton(
                    text = "Listo",
                    onClick = actions.onDone,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
