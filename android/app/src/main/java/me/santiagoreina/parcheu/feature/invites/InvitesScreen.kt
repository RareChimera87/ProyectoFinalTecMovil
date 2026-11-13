package me.santiagoreina.parcheu.feature.invites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.PendingInvite
import me.santiagoreina.parcheu.core.ui.StateIllustration
import me.santiagoreina.parcheu.core.ui.stateProp
import me.santiagoreina.parcheu.designsystem.brand.ParcheUAppIcon
import me.santiagoreina.parcheu.designsystem.components.base.ModeFilterChip
import me.santiagoreina.parcheu.designsystem.components.base.ModeSpineCard
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUButtonSize
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUChildTopBar
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUTextField
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUTextFieldStyle
import me.santiagoreina.parcheu.designsystem.components.base.PrimaryButton
import me.santiagoreina.parcheu.designsystem.components.base.SegmentedControl
import me.santiagoreina.parcheu.designsystem.components.compound.AvatarInitials
import me.santiagoreina.parcheu.designsystem.components.compound.ErrorState
import me.santiagoreina.parcheu.designsystem.components.compound.InfoNote
import me.santiagoreina.parcheu.designsystem.components.compound.QRCard
import me.santiagoreina.parcheu.designsystem.mascot.TwinMascotGlyph
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.designsystem.theme.SoraFontFamily
import me.santiagoreina.parcheu.feature.parche.HeroActionButton
import me.santiagoreina.parcheu.feature.safety.ReportOrBlockAction
import me.santiagoreina.parcheu.feature.simulation.forMode

object InvitesTags {
    const val SCREEN = "invites"
    const val QR = "invites:qr"
    const val CODE = "invites:code"
    const val COPY = "invites:copy"
    const val SHARE = "invites:share"
    const val INPUT = "invites:input"
    const val ADD = "invites:add"
    const val SCAN = "invites:scan"
    const val MESSAGE = "invites:message"
    const val PENDING = "invites:pending"

    fun mode(mode: Mode) = "invites:mode:${mode.name}"

    fun accept(id: String) = "invites:accept:$id"
}

data class InvitesActions(
    val onBack: () -> Unit = {},
    val onTab: (InvitesTab) -> Unit = {},
    val onCopy: (String) -> Unit = {},
    val onShare: (code: String, link: String) -> Unit = { _, _ -> },
    val onCodeChange: (String) -> Unit = {},
    val onMode: (Mode) -> Unit = {},
    val onAdd: () -> Unit = {},
    val onScan: () -> Unit = {},
    val onAccept: (PendingInvite) -> Unit = {},
    val onRetry: () -> Unit = {},
    /** E8.7: abre H1 para quien canjeó mi código y espera que lo acepte. */
    val onReportOrBlock: (PendingInvite) -> Unit = {}
)

/** C5 · Agregar amigos, QR y código (E6.1). La pestaña 'Escanear' no tiene mockup; sigue los tokens
* de C5. */
@Composable
fun InvitesScreen(state: InvitesUiState, actions: InvitesActions, modifier: Modifier = Modifier) {
    val colors = ParcheUTheme.colors
    val spacing = ParcheUTheme.spacing
    Column(modifier.fillMaxSize().background(colors.bg).testTag(InvitesTags.SCREEN)) {
        ParcheUChildTopBar(
            title = "Agregar amigos",
            navigationIcon = rememberVectorPainter(InvitesIcons.Back),
            navigationContentDescription = "Atrás",
            onNavigationClick = actions.onBack
        )
        SegmentedControl(
            options = listOf("Mi código", "Escanear"),
            selectedIndex = state.tab.ordinal,
            onSelect = { actions.onTab(InvitesTab.entries[it]) },
            modifier = Modifier.padding(horizontal = spacing.gutter).fillMaxWidth()
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.gutter)
                .padding(top = spacing.gutter, bottom = spacing.gutterWide),
            verticalArrangement = Arrangement.spacedBy(spacing.blockGap)
        ) {
            when (state.tab) {
                InvitesTab.MY_CODE -> MyCodeTab(state, actions)
                InvitesTab.SCAN -> ScanTab(actions)
            }
            OrDivider()
            CodeEntry(state, actions)
        }
    }
}

@Composable
private fun ColumnScope.MyCodeTab(state: InvitesUiState, actions: InvitesActions) {
    val colors = ParcheUTheme.colors
    val invite = state.invite
    when {
        invite == null && state.loadError != null -> ErrorState(
            title = "No pudimos cargar tu código",
            message = "Revisa tu conexión e inténtalo de nuevo.",
            retryLabel = "Reintentar",
            illustration = { StateIllustration(state.loadError.stateProp(), error = true) },
            onRetryClick = actions.onRetry
        )
        invite == null -> Box(
            Modifier.fillMaxWidth().height(260.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = colors.prt)
        }
        else -> {
            // Si alguien ya canjeó tu código, aceptarlo va primero (en la demo es el paso siguiente).
            if (state.pending.isNotEmpty()) PendingList(state, actions)
            CodeHero(
                code = invite.code,
                onCopy = { actions.onCopy(invite.code) },
                onShare = { actions.onShare(invite.code, invite.link) }
            )
            state.qr?.let { qr ->
                Text(
                    "O QUE ESCANEEN TU QR",
                    style = ParcheUTheme.textStyles.overline.copy(letterSpacing = 0.9.sp),
                    color = colors.mu,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)
                )
                Box(
                    Modifier.align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.TopCenter
                ) {
                    QRCard(
                        matrix = qr,
                        name = state.name ?: "Tu código",
                        contentDescription = "Código QR de tu invitación, ${invite.code}",
                        modifier = Modifier.testTag(InvitesTags.QR)
                    )
                    // El ícono nuevo (la P que mira) al centro del QR, sobre su recuadro blanco.
                    QrLogo(Modifier.padding(top = QR_LOGO_TOP))
                }
            }
        }
    }
}

@Composable
private fun PendingList(state: InvitesUiState, actions: InvitesActions) {
    val colors = ParcheUTheme.colors
    Column(
        Modifier.fillMaxWidth().testTag(InvitesTags.PENDING),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Quieren agregarte",
            style = MaterialTheme.typography.titleMedium,
            color = colors.tx,
            modifier = Modifier.semantics { heading() }
        )
        state.pending.forEach { pending ->
            ModeSpineCard(mode = colors.forMode(pending.mode), modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AvatarInitials(name = pending.other.label, affinity = 100)
                    Column(Modifier.weight(1f)) {
                        Text(
                            pending.other.label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.tx
                        )
                        Text(
                            "Quiere conectar en ${pending.mode.label()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.mu
                        )
                    }
                    ReportOrBlockAction(onClick = { actions.onReportOrBlock(pending) })
                }
                PrimaryButton(
                    text = if (state.accepting == pending.id) "Aceptando…" else "Aceptar",
                    onClick = { actions.onAccept(pending) },
                    size = ParcheUButtonSize.Medium,
                    enabled = state.accepting == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag(InvitesTags.accept(pending.id))
                )
            }
        }
    }
}

@Composable
private fun ScanTab(actions: InvitesActions) {
    val colors = ParcheUTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(ParcheUTheme.shapes.hero)
            .background(colors.hero)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier.size(64.dp).background(colors.onHero, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = rememberVectorPainter(InvitesIcons.Scanner),
                    contentDescription = null,
                    tint = colors.hero,
                    modifier = Modifier.size(34.dp)
                )
            }
            TwinMascotGlyph(width = 40.dp, color = colors.onHero, eyeColor = colors.hero)
        }
        Text(
            "Escanea el QR de tu amigo",
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onHero,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            "Abre el lector y apunta al QR que aparece en su pantalla de Agregar amigos.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onHero
        )
        HeroActionButton(
            text = "Abrir escáner",
            onClick = actions.onScan,
            icon = rememberVectorPainter(InvitesIcons.Scanner),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag(InvitesTags.SCAN)
        )
    }
    InfoNote(
        "No te pedimos permiso de cámara: el lector lo abre Google Play services y " +
            "solo nos pasa el código."
    )
}

@Composable
private fun OrDivider() {
    val colors = ParcheUTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(colors.ln))
        Text(
            "o ingresa el código de tu amigo",
            style = MaterialTheme.typography.titleSmall,
            color = colors.tx,
            modifier = Modifier.semantics { heading() }
        )
        Box(Modifier.weight(1f).height(1.dp).background(colors.ln))
    }
}

@Composable
private fun CodeEntry(state: InvitesUiState, actions: InvitesActions) {
    val colors = ParcheUTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ParcheUTextField(
            value = state.codeInput,
            onValueChange = actions.onCodeChange,
            label = "Código",
            placeholder = "Ej. SEBA-1290",
            style = ParcheUTextFieldStyle.Code,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { actions.onAdd() }),
            modifier = Modifier.fillMaxWidth().testTag(InvitesTags.INPUT)
        )
        Text("¿Cómo se conocen?", style = MaterialTheme.typography.bodyMedium, color = colors.mu)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Mode.AMISTAD, Mode.ESTUDIO, Mode.PAREJA).forEach { mode ->
                ModeFilterChip(
                    label = mode.label(),
                    selected = state.mode == mode,
                    onClick = { actions.onMode(mode) },
                    dotColor = colors.forMode(mode).solid,
                    modifier = Modifier.testTag(InvitesTags.mode(mode))
                )
            }
        }
        PrimaryButton(
            text = if (state.submitting) "Agregando…" else "Agregar",
            onClick = actions.onAdd,
            enabled = state.canSubmit,
            icon = rememberVectorPainter(InvitesIcons.PersonAdd),
            modifier = Modifier.fillMaxWidth().testTag(InvitesTags.ADD)
        )
    }
    state.message?.let { message ->
        Text(
            text = message.text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (message.isError) colors.coralt else colors.prt,
            modifier = Modifier
                .testTag(InvitesTags.MESSAGE)
                .semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}

/** Código del héroe: Sora 800 de 36 con aire entre letras, para leerlo de un teléfono a otro. */
private val CODE_HERO_STYLE = TextStyle(
    fontFamily = SoraFontFamily,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 36.sp,
    lineHeight = 42.sp,
    letterSpacing = 2.sp
)

/** Distancia del borde superior de [QRCard] al recuadro del logo: 20 de relleno + 100 − 22. */
private val QR_LOGO_TOP = 98.dp

/**
 * C5 · "Mi código" como protagonista (dirección A): un héroe violeta con el código en Sora 800 de 36 y
 * las dos acciones (copiar y compartir) en píldoras blancas. Es lo que se lee en voz alta en la demo
 * con dos teléfonos. TalkBack lee el código letra por letra.
 */
@Composable
private fun CodeHero(code: String, onCopy: () -> Unit, onShare: () -> Unit) {
    val colors = ParcheUTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(ParcheUTheme.shapes.hero)
            .background(colors.hero)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            Modifier.semantics(mergeDescendants = true) {
                contentDescription = "Tu código de invitación: ${spelled(code)}"
            },
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Tu código de invitación".uppercase(),
                    style = ParcheUTheme.textStyles.overline.copy(letterSpacing = 0.9.sp),
                    color = colors.onHero,
                    modifier = Modifier.weight(1f)
                )
                TwinMascotGlyph(width = 26.dp, color = colors.onHero, eyeColor = colors.hero)
            }
            Text(
                code,
                style = CODE_HERO_STYLE,
                color = colors.onHero,
                maxLines = 1,
                modifier = Modifier.testTag(InvitesTags.CODE)
            )
            Text(
                "Díctalo o compártelo: tu amigo lo escribe abajo en su app.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onHero
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroActionButton(
                text = "Copiar",
                onClick = onCopy,
                icon = rememberVectorPainter(InvitesIcons.Copy),
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Copiar código" }
                    .testTag(InvitesTags.COPY)
            )
            HeroActionButton(
                text = "Compartir",
                onClick = onShare,
                icon = rememberVectorPainter(InvitesIcons.Share),
                modifier = Modifier
                    .weight(1.25f)
                    .semantics { contentDescription = "Compartir invitación" }
                    .testTag(InvitesTags.SHARE)
            )
        }
    }
}

/** El ícono de la app (la P que mira sobre violeta) en su recuadro blanco, para el centro del QR. */
@Composable
private fun QrLogo(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(44.dp)
            .background(Color.White, RoundedCornerShape(10.dp))
            .padding(4.dp)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center
    ) {
        ParcheUAppIcon(size = 36.dp)
    }
}

/** "MARI-4827" letra por letra para el lector de pantalla. */
private fun spelled(code: String): String = code.toList().joinToString(" ") {
    if (it ==
        '-'
    ) {
        "guion"
    } else {
        it.toString()
    }
}
