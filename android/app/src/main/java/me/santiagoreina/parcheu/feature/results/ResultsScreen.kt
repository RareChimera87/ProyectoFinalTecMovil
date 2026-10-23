package me.santiagoreina.parcheu.feature.results

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.santiagoreina.parcheu.contract.Speaker
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.designsystem.components.base.ModeChip
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUButtonSize
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUCard
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUChildTopBar
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUPlusBadge
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUTextButton
import me.santiagoreina.parcheu.designsystem.components.base.PillChip
import me.santiagoreina.parcheu.designsystem.components.base.PrimaryButton
import me.santiagoreina.parcheu.designsystem.components.base.TonalButton
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.simulation.SimulationIcons
import me.santiagoreina.parcheu.feature.simulation.SimulationMascot
import me.santiagoreina.parcheu.feature.simulation.forMode
import me.santiagoreina.parcheu.feature.simulation.label
import me.santiagoreina.parcheu.feature.simulation.rememberIcon

/** Insignia de perfil demo (ADR-0027): se mantiene también en resultados. */
const val DEMO_BADGE_TEXT = "Perfil de demostración"

/** Etiquetas de prueba de F7/F8. */
object ResultsTags {
    const val LOADING = "results:loading"
    const val ERROR = "results:error"
    const val LOCKED = "results:locked"
    const val FULL = "results:full"
    const val UNLOCK = "results:unlock"
    const val SEE_PLUS = "results:seePlus"
    const val FOOTER = "results:footer"
    const val CONNECT = "results:connect"
    const val DECLINE = "results:decline"
    const val WAITING = "results:waiting"
    const val DECLINED = "results:declined"
    const val CONNECTED = "results:connected"
    const val DEMO = "results:demo"
    const val TRANSCRIPT_LINK = "results:transcriptLink"
    const val TRANSCRIPT = "results:transcript"
    const val SHARE = "results:share"
}

/** Acciones de la pantalla de resultados. */
data class ResultsActions(
    val onClose: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onUnlock: () -> Unit = {},
    val onSeePlus: () -> Unit = {},
    val onConnect: () -> Unit = {},
    val onDecline: () -> Unit = {},
    val onOpenChat: () -> Unit = {},
    val onOpenTranscript: () -> Unit = {},
    val onCloseTranscript: () -> Unit = {},
    val onDismissNotice: () -> Unit = {}
)

/**
 * F7 (gratis, bloqueado), F8/I3 (completo), 'esperando a la otra persona' y la conversación de los
 * gemelos. Sin compartir.
 */
@Composable
fun ResultsScreen(state: ResultsUiState, actions: ResultsActions, modifier: Modifier = Modifier) {
    val colors = ParcheUTheme.colors
    val content = (state as? ResultsUiState.Loaded)?.content
    if (content?.transcript != null) {
        TranscriptScreen(content, content.transcript, actions, modifier)
        return
    }
    Column(modifier.fillMaxSize().background(colors.bg)) {
        ParcheUChildTopBar(
            title = "Resultado",
            navigationIcon = rememberIcon(SimulationIcons.Close),
            navigationContentDescription = "Cerrar",
            onNavigationClick = actions.onClose,
            action = { if (content?.plus == true) ParcheUPlusBadge(Modifier.padding(end = 12.dp)) }
        )
        when (state) {
            ResultsUiState.Loading -> Box(
                Modifier.fillMaxSize().testTag(ResultsTags.LOADING),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = colors.primary)
            }
            is ResultsUiState.Error -> LoadError(state.error, actions.onRetry)
            is ResultsUiState.Loaded -> Loaded(state.content, actions)
        }
    }
}

@Composable
private fun Loaded(content: ResultsContent, actions: ResultsActions) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .testTag(if (content.full) ResultsTags.FULL else ResultsTags.LOCKED + ":screen"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Header(content)
            Text(
                content.report.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = ParcheUTheme.colors.mu,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.CenterHorizontally).widthIn(max = 280.dp)
            )
            if (content.full) FullReport(content, actions) else Locked(content, actions)
            content.notice?.let { Notice(it, actions.onDismissNotice) }
        }
        Footer(content, actions)
    }
}

@Composable
private fun Header(content: ResultsContent) {
    val colors = ParcheUTheme.colors
    val mode = colors.forMode(content.mode)
    Column(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SimulationMascot(colors.primary, 48.dp)
            AffinityRing(content.report.score)
            SimulationMascot(mode.solid, 48.dp)
        }
        Text(
            "Tú y ${content.displayName} · afinidad ${affinityWord(content.report.score)}",
            style = MaterialTheme.typography.titleLarge,
            color = colors.tx,
            textAlign = TextAlign.Center
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModeChip(label = content.mode.label(), mode = mode)
            if (content.isDemo) PillChip(DEMO_BADGE_TEXT, Modifier.testTag(ResultsTags.DEMO))
        }
    }
}

@Composable
private fun FullReport(content: ResultsContent, actions: ResultsActions) {
    val colors = ParcheUTheme.colors
    val reasons = content.report.reasons.orEmpty()
    val frictions = content.report.frictions.orEmpty()
    if (reasons.isNotEmpty()) {
        ParcheUCard(Modifier.fillMaxWidth()) {
            CardOverline("Por qué son compatibles")
            reasons.forEach { ReportRow(it, rememberResultsIcon(ResultsIcons.Reason), colors.prt) }
        }
    }
    if (frictions.isNotEmpty()) {
        ParcheUCard(Modifier.fillMaxWidth()) {
            CardOverline("Dónde chocarían")
            frictions.forEach {
                ReportRow(it, rememberResultsIcon(ResultsIcons.Friction), colors.mu)
            }
        }
    }
    Row(
        Modifier.clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = actions.onOpenTranscript)
            .padding(vertical = 12.dp, horizontal = 4.dp)
            .testTag(ResultsTags.TRANSCRIPT_LINK),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "Ver la conversación de los gemelos",
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
            color = colors.prt
        )
        Icon(
            rememberResultsIcon(ResultsIcons.ArrowForward),
            contentDescription = null,
            tint = colors.prt,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun Locked(content: ResultsContent, actions: ResultsActions) {
    val report = content.report
    val busy = content.busy != null
    val counts = "${report.reasonsCount} razones y ${report.frictionsCount} choques"
    val message = if (report.unlockAvailable) {
        val month = content.usedMonth?.let { " de $it" } ?: " de este mes"
        "Tienes 1 informe completo gratis$month: úsalo aquí para ver $counts."
    } else {
        val used = content.usedMonth?.let { "de $it" } ?: "de este mes"
        val back = content.resetMonth?.let { "en $it" } ?: "el próximo mes"
        "Ya usaste tu informe gratis $used. Vuelve $back o míralo con ParcheU+."
    }
    LockedReport(
        reasonsCount = report.reasonsCount,
        frictionsCount = report.frictionsCount,
        title = "Informe completo: por qué son compatibles y dónde chocarían",
        message = message
    ) {
        if (report.unlockAvailable) {
            TonalButton(
                text = if (content.busy == Busy.UNLOCK) "Abriendo…" else "Ver informe completo",
                onClick = actions.onUnlock,
                enabled = !busy,
                size = ParcheUButtonSize.Medium,
                modifier = Modifier.testTag(ResultsTags.UNLOCK)
            )
            ParcheUTextButton(
                text = "Ver ParcheU+",
                onClick = actions.onSeePlus,
                size = ParcheUButtonSize.Medium,
                modifier = Modifier.testTag(ResultsTags.SEE_PLUS)
            )
        } else {
            TonalButton(
                text = "Ver ParcheU+",
                onClick = actions.onSeePlus,
                size = ParcheUButtonSize.Medium,
                modifier = Modifier.testTag(ResultsTags.SEE_PLUS)
            )
        }
    }
}

@Composable
private fun Footer(content: ResultsContent, actions: ResultsActions) {
    val colors = ParcheUTheme.colors
    when (content.decision) {
        DecisionState.PENDING -> DecisionFooter(
            note = if (content.report.otherName != null) {
                "${content.report.otherName} no sabrá si eliges «Ahora no»."
            } else {
                "La otra persona no sabrá si eliges «Ahora no»."
            },
            enabled = content.busy == null,
            onDecline = actions.onDecline,
            onConnect = actions.onConnect
        )
        DecisionState.WAITING -> ParcheUCard(
            Modifier.fillMaxWidth().padding(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 16.dp
            ).testTag(ResultsTags.WAITING)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colors.prc),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        rememberResultsIcon(ResultsIcons.Hourglass),
                        contentDescription = null,
                        tint = colors.onprc,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Esperando a la otra persona",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = colors.tx
                    )
                    Text(
                        "Te avisamos si también elige Conectar. Su nombre aparece solo si las " +
                            "dos personas conectan.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = colors.mu
                    )
                }
            }
        }
        DecisionState.DECLINED -> Text(
            if (content.report.myDecision ==
                false
            ) {
                "Elegiste «Ahora no». La otra persona no lo sabrá."
            } else {
                "Esta vez no hubo conexión."
            },
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = colors.mu,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(16.dp).testTag(ResultsTags.DECLINED)
        )
        DecisionState.CONNECTED -> Column(
            Modifier.fillMaxWidth().padding(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 16.dp
            ).testTag(ResultsTags.CONNECTED),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PrimaryButton(
                text = "Abrir chat con ${content.displayName}",
                onClick = actions.onOpenChat,
                enabled = content.chatId != null,
                icon = rememberResultsIcon(ResultsIcons.Chat),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "¡Conectaron! Ya pueden hablar.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.mu,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun Notice(notice: ResultsNotice, onDismiss: () -> Unit) {
    val text = when (notice) {
        ResultsNotice.ReportAlreadyUsed -> "Ya usaste el informe completo gratis de este mes."
        is ResultsNotice.Failed -> errorText(notice.error)
    }
    ParcheUCard(Modifier.fillMaxWidth(), onClick = onDismiss) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = ParcheUTheme.colors.error
        )
    }
}

private fun errorText(error: AppError): String = when (error) {
    AppError.Network -> "Sin conexión. Inténtalo de nuevo."
    AppError.Blocked -> "No puedes conectar con esta persona."
    AppError.NotFound -> "Este resultado ya no está disponible."
    else -> "Algo salió mal. Inténtalo de nuevo."
}

@Composable
private fun LoadError(error: AppError, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp).testTag(ResultsTags.ERROR),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text(
            errorText(error),
            style = MaterialTheme.typography.bodyLarge,
            color = ParcheUTheme.colors.tx,
            textAlign = TextAlign.Center
        )
        if (error != AppError.NotFound) PrimaryButton("Reintentar", onClick = onRetry)
    }
}

@Composable
private fun TranscriptScreen(
    content: ResultsContent,
    transcript: TranscriptState,
    actions: ResultsActions,
    modifier: Modifier
) {
    val colors = ParcheUTheme.colors
    Column(modifier.fillMaxSize().background(colors.bg).testTag(ResultsTags.TRANSCRIPT)) {
        ParcheUChildTopBar(
            title = "Conversación de los gemelos",
            navigationIcon = rememberIcon(SimulationIcons.ArrowBack),
            navigationContentDescription = "Volver",
            onNavigationClick = actions.onCloseTranscript
        )
        when (transcript) {
            TranscriptState.Loading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = colors.primary)
            }
            is TranscriptState.Error -> LoadError(transcript.error, actions.onOpenTranscript)
            is TranscriptState.Loaded -> Column(
                Modifier.fillMaxSize().verticalScroll(
                    rememberScrollState()
                ).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                transcript.transcript.scenarios.forEachIndexed { index, scenario ->
                    CardOverline("Escenario ${index + 1} · ${scenario.title}")
                    scenario.turns.forEach { turn ->
                        val mine = turn.speaker == Speaker.ME
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
                        ) {
                            Text(
                                turn.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (mine) colors.onprc else colors.tx,
                                modifier = Modifier.widthIn(max = 280.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (mine) colors.prc else colors.s1)
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
                Text(
                    "Tu gemelo a la derecha; el de ${content.displayName} a la izquierda.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.mu,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}
