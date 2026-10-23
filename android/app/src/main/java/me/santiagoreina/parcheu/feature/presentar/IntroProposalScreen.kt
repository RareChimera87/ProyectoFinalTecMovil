package me.santiagoreina.parcheu.feature.presentar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.santiagoreina.parcheu.contract.PersonRef
import me.santiagoreina.parcheu.designsystem.components.base.ModeChip
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUCard
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUChildTopBar
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUTextButton
import me.santiagoreina.parcheu.designsystem.components.base.PillChip
import me.santiagoreina.parcheu.designsystem.components.base.PillChipVariant
import me.santiagoreina.parcheu.designsystem.components.base.PrimaryButton
import me.santiagoreina.parcheu.designsystem.components.compound.AvatarInitials
import me.santiagoreina.parcheu.designsystem.components.compound.AvatarSizes
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.results.ReportRow
import me.santiagoreina.parcheu.feature.results.ResultsIcons
import me.santiagoreina.parcheu.feature.results.affinityWord
import me.santiagoreina.parcheu.feature.results.rememberResultsIcon
import me.santiagoreina.parcheu.feature.simulation.forMode
import me.santiagoreina.parcheu.feature.simulation.label

/** Etiquetas de prueba de E3. */
object IntroProposalTags {
    const val LOADING = "introProposal:loading"
    const val ERROR = "introProposal:error"
    const val TITLE = "introProposal:title"
    const val REASONS = "introProposal:reasons"
    const val INTERESTED = "introProposal:interested"
    const val DECLINE = "introProposal:decline"
    const val WAITING = "introProposal:waiting"
    const val DECLINED = "introProposal:declined"
    const val CONNECTED = "introProposal:connected"
}

data class IntroProposalActions(
    val onClose: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onInterested: () -> Unit = {},
    val onDecline: () -> Unit = {},
    val onOpenChat: () -> Unit = {},
    val onDismissError: () -> Unit = {}
)

/** E3 · Propuesta recibida: pendiente, 'esperando' (aceptada), rechazada y conectados. */
@Composable
fun IntroProposalScreen(
    state: IntroProposalUiState,
    actions: IntroProposalActions,
    modifier: Modifier = Modifier
) {
    val colors = ParcheUTheme.colors
    Column(modifier.fillMaxSize().background(colors.bg)) {
        ParcheUChildTopBar(
            title = "",
            navigationIcon = rememberPresentarIcon(PresentarIcons.Close),
            navigationContentDescription = "Cerrar",
            onNavigationClick = actions.onClose
        )
        when (state) {
            IntroProposalUiState.Loading -> PresentarLoading(IntroProposalTags.LOADING)
            is IntroProposalUiState.Error -> PresentarLoadError(
                state.error,
                actions.onRetry,
                IntroProposalTags.ERROR
            )
            is IntroProposalUiState.Loaded -> Loaded(state.content, actions)
        }
    }
}

@Composable
private fun Loaded(content: IntroProposalContent, actions: IntroProposalActions) {
    val colors = ParcheUTheme.colors
    val intro = content.introduction
    val report = intro.report
    val mode = colors.forMode(intro.mode)
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Avatars(intro.introducer, intro.other, report?.score ?: 0)
                Text(
                    "${firstName(intro.introducer)} quiere presentarte a ${intro.other.label}",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = colors.tx,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag(IntroProposalTags.TITLE)
                )
                Text(
                    report?.let {
                        "Sus gemelos simularon una conversación y salieron con afinidad " +
                            "${affinityWord(it.score)}."
                    } ?: "Sus gemelos simularon una conversación.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.mu,
                    textAlign = TextAlign.Center
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeChip(label = intro.mode.label(), mode = mode)
                    report?.let {
                        PillChip("${it.score} % afines", variant = PillChipVariant.Affinity)
                    }
                }
            }
            val reasons = report?.reasons.orEmpty()
            if (reasons.isNotEmpty()) {
                ParcheUCard(Modifier.fillMaxWidth().testTag(IntroProposalTags.REASONS)) {
                    reasons.forEach {
                        ReportRow(it, rememberResultsIcon(ResultsIcons.Reason), colors.prt)
                    }
                }
            } else if (report != null && report.reasonsCount > 0) {
                PresentarHint(
                    "Con ParcheU+ o tu informe del mes ves las ${report.reasonsCount} razones " +
                        "de por qué funcionaría.",
                    rememberPresentarIcon(PresentarIcons.Info)
                )
            }
            content.error?.let { PresentarNotice(presentarErrorText(it), actions.onDismissError) }
        }
        Footer(content, actions)
    }
}

@Composable
private fun Avatars(introducer: PersonRef, other: PersonRef, score: Int) {
    val colors = ParcheUTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        AvatarInitials(introducer.label, 100, size = AvatarSizes.Lg)
        Box(
            Modifier.offset(x = (-12).dp)
                .clip(CircleShape)
                .border(3.dp, colors.bg, CircleShape)
                .padding(3.dp)
        ) {
            AvatarInitials(
                other.label,
                score,
                size = AvatarSizes.Hero,
                anonymous = other.id == null && other.label.startsWith("Alguien")
            )
        }
    }
}

private fun firstName(person: PersonRef): String = person.label.substringBefore(' ')

@Composable
private fun Footer(content: IntroProposalContent, actions: IntroProposalActions) {
    val colors = ParcheUTheme.colors
    val other = content.introduction.other.label
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (content.state) {
            ProposalState.PENDING -> {
                PrimaryButton(
                    text = "Me interesa",
                    onClick = actions.onInterested,
                    enabled = !content.busy,
                    modifier = Modifier.fillMaxWidth().testTag(IntroProposalTags.INTERESTED)
                )
                ParcheUTextButton(
                    text = "No, gracias",
                    onClick = actions.onDecline,
                    enabled = !content.busy,
                    modifier = Modifier.fillMaxWidth().testTag(IntroProposalTags.DECLINE)
                )
                FootNote("$other no sabrá si dices que no.")
            }
            ProposalState.WAITING -> ParcheUCard(
                Modifier.fillMaxWidth().testTag(IntroProposalTags.WAITING)
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
                            "Te interesó. Falta $other",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = colors.tx
                        )
                        Text(
                            "Si también le interesa, se abre un chat entre los dos y te avisamos.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                            color = colors.mu
                        )
                    }
                }
            }
            ProposalState.DECLINED -> Text(
                "Elegiste «No, gracias». $other no lo sabrá.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = colors.mu,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(
                    vertical = 8.dp
                ).testTag(IntroProposalTags.DECLINED)
            )
            ProposalState.CONNECTED -> Column(
                Modifier.fillMaxWidth().testTag(IntroProposalTags.CONNECTED),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PrimaryButton(
                    text = "Abrir chat con $other",
                    onClick = actions.onOpenChat,
                    enabled = content.chatId != null,
                    icon = rememberResultsIcon(ResultsIcons.Chat),
                    modifier = Modifier.fillMaxWidth()
                )
                FootNote("¡Conectaron! Ya pueden hablar.")
            }
        }
    }
}

@Composable
private fun FootNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
        color = ParcheUTheme.colors.mu,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}
