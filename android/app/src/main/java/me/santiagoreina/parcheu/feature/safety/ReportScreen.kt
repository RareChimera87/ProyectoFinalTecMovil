package me.santiagoreina.parcheu.feature.safety

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.contract.HelpResource
import me.santiagoreina.parcheu.contract.ReportReason
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUCheckbox
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUChildTopBar
import me.santiagoreina.parcheu.designsystem.components.base.ParcheURadioRow
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUTextField
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUTextFieldStyle
import me.santiagoreina.parcheu.designsystem.components.base.PrimaryButton
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/** Acciones de H2 y de la confirmación de reporte enviado. */
data class ReportActions(
    val onClose: () -> Unit = {},
    val onReason: (ReportReason) -> Unit = {},
    val onDetailsChange: (String) -> Unit = {},
    val onAttachChange: (Boolean) -> Unit = {},
    val onAlsoBlockChange: (Boolean) -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onDone: () -> Unit = {},
    val onCall: (String) -> Unit = {}
)

/** H2 (mockup 22b) o, ya enviado, la confirmación con las líneas de ayuda si corresponde. */
@Composable
fun ReportScreen(state: ReportUiState, actions: ReportActions, modifier: Modifier = Modifier) {
    val sent = state.sent
    if (sent != null) {
        ReportSentScreen(state.target, sent, actions, modifier)
    } else {
        ReportFormScreen(state, actions, modifier)
    }
}

@Composable
private fun ReportFormScreen(state: ReportUiState, actions: ReportActions, modifier: Modifier) {
    val colors = ParcheUTheme.colors
    val name = state.target.shortName
    Column(modifier.fillMaxSize().background(colors.bg).imePadding()) {
        ParcheUChildTopBar(
            title = "Reportar a $name",
            navigationIcon = rememberVectorPainter(CloseIcon),
            navigationContentDescription = "Cerrar",
            onNavigationClick = actions.onClose
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(REPORT_PROMISE, style = MaterialTheme.typography.bodyMedium, color = colors.mu)
            val shape = RoundedCornerShape(20.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(colors.s1, shape)
                    .padding(horizontal = 16.dp)
            ) {
                ReportReasons.forEachIndexed { index, option ->
                    if (index > 0) HorizontalDivider(color = colors.ln)
                    ParcheURadioRow(
                        label = option.label,
                        selected = state.reason == option.reason,
                        onClick = { actions.onReason(option.reason) },
                        enabled = !state.submitting,
                        modifier = Modifier.testTag(SafetyTags.REASON_PREFIX + option.reason.name)
                    )
                }
            }
            ParcheUTextField(
                value = state.details,
                onValueChange = actions.onDetailsChange,
                label = "Cuéntanos qué pasó (opcional)",
                style = ParcheUTextFieldStyle.TextArea,
                enabled = !state.submitting,
                supportingText = "${state.details.length} / $MAX_REPORT_DETAILS",
                modifier = Modifier.testTag(SafetyTags.DETAILS)
            )
            if (state.canAttach) {
                CheckRow(
                    label = "Adjuntar los últimos mensajes",
                    checked = state.attachMessages,
                    onChange = actions.onAttachChange,
                    enabled = !state.submitting,
                    tag = SafetyTags.ATTACH
                )
            }
            CheckRow(
                label = "Bloquear también a $name",
                checked = state.alsoBlock,
                onChange = actions.onAlsoBlockChange,
                enabled = !state.submitting,
                tag = SafetyTags.ALSO_BLOCK
            )
            state.error?.let { ErrorText(it) }
        }
        PrimaryButton(
            text = if (state.submitting) "Enviando…" else "Enviar reporte",
            onClick = actions.onSubmit,
            enabled = state.canSubmit,
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp)
                .testTag(SafetyTags.SUBMIT)
        )
    }
}

@Composable
private fun CheckRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean,
    tag: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ParcheUCheckbox(
            checked = checked,
            onCheckedChange = onChange,
            contentDescription = label,
            enabled = enabled,
            modifier = Modifier.testTag(tag)
        )
        Text(label, style = MaterialTheme.typography.bodyMedium, color = ParcheUTheme.colors.tx)
    }
}

@Composable
private fun ReportSentScreen(
    target: SafetyTarget,
    sent: ReportSent,
    actions: ReportActions,
    modifier: Modifier
) {
    val colors = ParcheUTheme.colors
    Column(modifier.fillMaxSize().background(colors.bg)) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                rememberVectorPainter(CheckCircleIcon),
                contentDescription = null,
                tint = colors.prt,
                modifier = Modifier.size(48.dp)
            )
            Text(
                "Recibimos tu reporte",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.tx
            )
            Text(
                "Gracias por avisarnos. Lo revisamos lo antes posible y no le diremos quién " +
                    "lo envió.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.mu
            )
            when {
                sent.blocked ->
                    Text(
                        "Bloqueaste a ${target.shortName}: ya no podrá escribirte ni verte.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.tx
                    )
                sent.blockFailed ->
                    Text(
                        "No pudimos bloquear a ${target.shortName}. Inténtalo de nuevo " +
                            "desde el menú del chat.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.coralt
                    )
            }
            if (sent.helpResources.isNotEmpty()) {
                HelpResourcesCard(
                    sent.helpResources,
                    actions.onCall
                )
            }
        }
        PrimaryButton(
            text = "Listo",
            onClick = actions.onDone,
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp)
                .testTag(SafetyTags.SENT_DONE)
        )
    }
}

/** Líneas de ayuda (ADR-0026): tocar una abre el marcador con el número, nunca llama sola. */
@Composable
fun HelpResourcesCard(
    resources: List<HelpResource>,
    onCall: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ParcheUTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.prc, shape)
            .padding(16.dp)
            .testTag(SafetyTags.HELP),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "No estás solo. Si piensas en hacerte daño o alguien corre peligro, habla ahora " +
                "con alguien:",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = colors.onprc
        )
        resources.forEach { resource ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClickLabel = "Llamar a ${resource.name}") {
                        onCall(resource.phone)
                    }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    rememberVectorPainter(CallIcon),
                    contentDescription = null,
                    tint = colors.onprc,
                    modifier = Modifier.size(22.dp)
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        "${resource.name} · ${resource.phone}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = colors.onprc
                    )
                    resource.description?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onprc)
                    }
                }
            }
        }
    }
}
