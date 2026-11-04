package me.santiagoreina.parcheu.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.HomeDto
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.SimulationSummary
import me.santiagoreina.parcheu.contract.Suggestion
import me.santiagoreina.parcheu.contract.Usage
import me.santiagoreina.parcheu.designsystem.brand.Wordmark
import me.santiagoreina.parcheu.designsystem.components.base.ModeChip
import me.santiagoreina.parcheu.designsystem.components.base.ModeChipSize
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUButtonSize
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUCard
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUCardVariant
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUHomeTopBar
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUIconButton
import me.santiagoreina.parcheu.designsystem.components.base.ParcheULinearProgress
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUPlusBadge
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUTextButton
import me.santiagoreina.parcheu.designsystem.components.base.PillChip
import me.santiagoreina.parcheu.designsystem.components.base.PrimaryButton
import me.santiagoreina.parcheu.designsystem.components.base.SectionHeader
import me.santiagoreina.parcheu.designsystem.components.base.SegmentedQuota
import me.santiagoreina.parcheu.designsystem.components.base.TonalButton
import me.santiagoreina.parcheu.designsystem.components.compound.AvatarInitials
import me.santiagoreina.parcheu.designsystem.components.compound.AvatarSizes
import me.santiagoreina.parcheu.designsystem.mascot.TwinMascot
import me.santiagoreina.parcheu.designsystem.mascot.TwinMascotSize
import me.santiagoreina.parcheu.designsystem.mascot.TwinMascotTone
import me.santiagoreina.parcheu.designsystem.theme.ParcheUSizes
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.simulation.SimulationIcons
import me.santiagoreina.parcheu.feature.simulation.errorMessage
import me.santiagoreina.parcheu.feature.simulation.forMode
import me.santiagoreina.parcheu.feature.simulation.label
import me.santiagoreina.parcheu.feature.simulation.rememberIcon

/** Etiquetas de prueba de C1. */
object HomeTags {
    const val ROOT = "home:root"
    const val LOADING = "home:loading"
    const val ERROR = "home:error"
    const val QUOTA = "home:quota"
    const val EMPTY = "home:empty"
    const val RUNNING = "home:running"
    const val BELL = "home:bell"
    const val BELL_BADGE = "home:bellBadge"
    const val PLUS_CHIP = "home:plusChip"
    const val RANDOM_MATCH = "home:randomMatch"
    const val AVATAR = "home:avatar"
    const val ACTION_ERROR = "home:actionError"
    const val DEMO_BADGE = "home:demoBadge"

    fun ready(id: String) = "home:ready:$id"

    fun suggestion(id: String) = "home:suggestion:$id"
}

/** Acciones de C1; la ruta las conecta con la navegación y el ViewModel. */
data class HomeCallbacks(
    val onBell: () -> Unit = {},
    val onAvatar: () -> Unit = {},
    val onSeeAll: () -> Unit = {},
    val onOpenSimulation: (SimulationSummary) -> Unit = {},
    val onSimulate: (Suggestion) -> Unit = {},
    val onRandomMatch: () -> Unit = {},
    val onPickFromParche: () -> Unit = {},
    val onPlus: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onDismissError: () -> Unit = {}
)

/**
 * C1 Inicio (oscuro) e I1 (claro): cupo semanal con [SegmentedQuota], simulación en curso, resultados
 * listos, sugerencias y match al azar; estados vacío, 0 restantes y ParcheU+. La NavBar la pone la
 * raíz de la app. [now] fija el "hace X" en pruebas y capturas.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    callbacks: HomeCallbacks,
    now: Instant,
    modifier: Modifier = Modifier,
    animate: Boolean = true
) {
    val colors = ParcheUTheme.colors
    val loaded = state as? HomeUiState.Loaded
    Column(modifier.fillMaxSize().background(colors.bg).testTag(HomeTags.ROOT)) {
        ParcheUHomeTopBar(
            wordmark = { Wordmark(darkTheme = colors.isDark) },
            actions = {
                Bell(unread = loaded?.home?.unread ?: 0, onClick = callbacks.onBell)
                ProfileAvatar(name = loaded?.firstName ?: "Tú", onClick = callbacks.onAvatar)
            }
        )
        when (state) {
            HomeUiState.Loading ->
                Box(
                    Modifier.fillMaxSize().testTag(HomeTags.LOADING),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = colors.primary)
                }
            is HomeUiState.Error -> ErrorContent(state, callbacks.onRetry)
            is HomeUiState.Loaded -> LoadedContent(state, callbacks, now, animate)
        }
    }
}

/**
 * Acceso a Perfil (C3) desde la barra: el avatar mide 34 dp, pero la zona táctil es de 48 dp y TalkBack
 * lo anuncia como "Tu perfil" (no con el nombre de la persona).
 */
@Composable
private fun ProfileAvatar(name: String, onClick: () -> Unit) {
    Box(
        Modifier
            .sizeIn(minWidth = ParcheUSizes.IconButton, minHeight = ParcheUSizes.IconButton)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = "Abrir tu perfil", onClick = onClick)
            .semantics { contentDescription = "Tu perfil" }
            .testTag(HomeTags.AVATAR),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.clearAndSetSemantics {}) {
            AvatarInitials(name = name, affinity = 100, size = AvatarSizes.Home)
        }
    }
}

/**
 * Insignia de ADR-0027 para perfiles demo. [compact] cabe en las tarjetas angostas de sugerencias y
 * TalkBack la lee igual que la completa.
 */
@Composable
private fun DemoBadge(compact: Boolean = false) {
    if (compact) {
        PillChip(
            text = "Demostración",
            modifier =
            Modifier.clearAndSetSemantics {
                contentDescription = DEMO_PROFILE_BADGE
                testTag = HomeTags.DEMO_BADGE
            }
        )
    } else {
        PillChip(text = DEMO_PROFILE_BADGE, modifier = Modifier.testTag(HomeTags.DEMO_BADGE))
    }
}

@Composable
private fun Bell(unread: Int, onClick: () -> Unit) {
    val description = if (unread > 0) "Notificaciones, $unread sin leer" else "Notificaciones"
    Box(Modifier.testTag(HomeTags.BELL)) {
        ParcheUIconButton(
            icon = rememberIcon(SimulationIcons.Notifications),
            contentDescription = description,
            onClick = onClick
        )
        if (unread > 0) {
            val colors = ParcheUTheme.colors
            Box(
                modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-6).dp, y = 6.dp)
                    .sizeIn(minWidth = 16.dp, minHeight = 16.dp)
                    .clip(ParcheUTheme.shapes.pill)
                    .background(colors.error)
                    .padding(horizontal = 4.dp)
                    // El número ya va en la descripción de la campana: TalkBack no lo repite.
                    .clearAndSetSemantics { testTag = HomeTags.BELL_BADGE },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (unread > 99) "99+" else unread.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.onError
                )
            }
        }
    }
}

@Composable
private fun LoadedContent(
    state: HomeUiState.Loaded,
    callbacks: HomeCallbacks,
    now: Instant,
    animate: Boolean
) {
    val colors = ParcheUTheme.colors
    val home = state.home
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = state.firstName?.let { "¿Qué más, $it?" } ?: "¿Qué más?",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = colors.tx
        )
        QuotaCard(home, callbacks.onPlus)
        if (state.isEmpty) {
            EmptyCard(home, callbacks, animate)
        } else {
            if (home.running != null || home.ready.isNotEmpty()) {
                SectionHeader(
                    title = "Simulaciones pendientes",
                    actionLabel = "Ver todas",
                    onActionClick = callbacks.onSeeAll
                )
                PendingCard(state, callbacks.onOpenSimulation, now, animate)
            }
            SectionHeader(title = "Sugerencias para ti")
            Suggestions(state, callbacks)
        }
        state.actionError?.let { error ->
            Text(
                text = homeErrorMessage(error),
                style = MaterialTheme.typography.bodySmall,
                color = colors.error,
                modifier =
                Modifier
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Descartar",
                        onClick = callbacks.onDismissError
                    )
                    .testTag(HomeTags.ACTION_ERROR)
            )
        }
    }
}

@Composable
private fun QuotaCard(home: HomeDto, onPlus: () -> Unit) {
    val colors = ParcheUTheme.colors
    val usage = home.quota.simulations
    val limit = usage.limit
    ParcheUCard(Modifier.fillMaxWidth().testTag(HomeTags.QUOTA)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (home.plus) "Simulaciones" else "Simulaciones de esta semana",
                style = MaterialTheme.typography.titleSmall,
                color = colors.tx,
                modifier = Modifier.weight(1f)
            )
            if (limit != null) {
                Text(
                    text = "${usage.used.coerceAtMost(limit)} de $limit",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.tx
                )
            } else {
                ParcheUPlusBadge()
            }
        }
        if (limit != null && limit > 0) {
            SegmentedQuota(
                used = usage.used,
                total = limit,
                contentDescription = "Usaste ${usage.used.coerceAtMost(
                    limit
                )} de $limit simulaciones esta semana"
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = quotaFootnote(usage),
                style = MaterialTheme.typography.bodySmall,
                color = if (usage.remaining() == 0) colors.tx else colors.mu,
                modifier = Modifier.weight(1f)
            )
            if (!home.plus) {
                Box(
                    Modifier
                        .clip(ParcheUTheme.shapes.pill)
                        .clickable(role = Role.Button, onClick = onPlus)
                        .semantics { contentDescription = "Conoce ParcheU+" }
                        .testTag(HomeTags.PLUS_CHIP)
                ) {
                    ParcheUPlusBadge()
                }
            }
        }
        if (usage.remaining() == 0) {
            TonalButton(
                text = "Simula sin límite con ParcheU+",
                onClick = onPlus,
                size = ParcheUButtonSize.Small,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PendingCard(
    state: HomeUiState.Loaded,
    onOpen: (SimulationSummary) -> Unit,
    now: Instant,
    animate: Boolean
) {
    val colors = ParcheUTheme.colors
    val running = state.home.running
    ParcheUCard(Modifier.fillMaxWidth()) {
        if (running != null) {
            RunningRow(running, state.progress, onOpen, animate)
        }
        state.home.ready.forEachIndexed { index, item ->
            if (running != null || index > 0) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.ln))
            }
            ReadyRow(item, onOpen, now)
        }
    }
}

@Composable
private fun RunningRow(
    running: SimulationSummary,
    progress: RunningProgress?,
    onOpen: (SimulationSummary) -> Unit,
    animate: Boolean
) {
    val colors = ParcheUTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onOpen(running) }
            .testTag(HomeTags.RUNNING),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row {
                TwinMascot(
                    size = TwinMascotSize.S,
                    tone = TwinMascotTone.User,
                    animationsEnabled = animate
                )
                TwinMascot(
                    size = TwinMascotSize.S,
                    tone = toneFor(running.mode),
                    animationsEnabled = animate,
                    modifier = Modifier.offset(x = (-6).dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Tú y ${running.other.label} están conversando",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.tx,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text =
                    progress?.let { "Escenario ${it.scenario} de 3 · ${running.mode.label()}" }
                        ?: "En curso · ${running.mode.label()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.mu
                )
                if (running.other.isDemo) DemoBadge()
            }
            if (progress != null) {
                Text(
                    text = "${progress.percent} %",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.prt
                )
            }
        }
        // Sin detalle del avance (GET /v1/simulations/{id} falló) no se pinta una barra al 0 % que
        // engañe a la vista y a TalkBack: basta el texto "En curso".
        if (progress != null) {
            ParcheULinearProgress(
                progress = progress.percent / 100f,
                contentDescription = "Avance de la simulación: ${progress.percent} %"
            )
        }
    }
}

@Composable
private fun ReadyRow(item: SimulationSummary, onOpen: (SimulationSummary) -> Unit, now: Instant) {
    val colors = ParcheUTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onOpen(item) }
            .testTag(HomeTags.ready(item.id)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AvatarInitials(
            name = item.other.label,
            affinity = item.score ?: 50,
            size = AvatarSizes.Md,
            anonymous = item.other.id == null
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = "Resultado listo con ${item.other.label}",
                style = MaterialTheme.typography.titleSmall,
                color = colors.tx,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val score = item.score?.let { "$it % afines · " }.orEmpty()
            Text(
                text = score + relativeTime(item.createdAt, now),
                style = MaterialTheme.typography.bodySmall,
                color = colors.mu
            )
            if (item.other.isDemo) DemoBadge()
        }
        ModeChip(
            label = item.mode.label(),
            mode = colors.forMode(item.mode),
            size = ModeChipSize.S
        )
    }
}

@Composable
private fun Suggestions(state: HomeUiState.Loaded, callbacks: HomeCallbacks) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        state.home.suggestions.forEach { suggestion ->
            SuggestionCard(
                suggestion,
                starting = state.startingId == suggestion.other.id,
                onSimulate = callbacks.onSimulate
            )
        }
        RandomMatchCard(state.home.quota.randomMatch, callbacks.onRandomMatch)
    }
}

private val SuggestionWidth = 164.dp

private const val EMPTY_BODY =
    "Agrega amigos a tu parche o prueba un match al azar para tu primera simulación."

@Composable
private fun SuggestionCard(
    suggestion: Suggestion,
    starting: Boolean,
    onSimulate: (Suggestion) -> Unit
) {
    val colors = ParcheUTheme.colors
    ParcheUCard(
        Modifier.width(
            SuggestionWidth
        ).testTag(HomeTags.suggestion(suggestion.other.id ?: suggestion.other.label)),
        variant = ParcheUCardVariant.Suggestion
    ) {
        AvatarInitials(
            name = suggestion.other.label,
            affinity = suggestion.estimatedAffinity ?: 50,
            size = AvatarSizes.List
        )
        Text(
            text = suggestion.other.label,
            style = MaterialTheme.typography.titleSmall,
            color = colors.tx,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = suggestionLine(suggestion),
            style = MaterialTheme.typography.bodySmall,
            color = colors.mu,
            minLines = 2,
            // Con fuente grande el motivo crece en vez de truncarse y perder contenido.
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
        ModeChip(
            label = suggestion.mode.label(),
            mode = colors.forMode(suggestion.mode),
            size = ModeChipSize.S
        )
        if (suggestion.other.isDemo) DemoBadge(compact = true)
        TonalButton(
            text = if (starting) "Iniciando…" else "Simular",
            onClick = { onSimulate(suggestion) },
            enabled = !starting,
            size = ParcheUButtonSize.Small,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun RandomMatchCard(usage: Usage, onRandomMatch: () -> Unit) {
    val colors = ParcheUTheme.colors
    ParcheUCard(
        Modifier.width(SuggestionWidth).testTag(HomeTags.RANDOM_MATCH),
        variant = ParcheUCardVariant.Suggestion
    ) {
        Box(
            Modifier.size(AvatarSizes.List).clip(CircleShape).background(colors.s2),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = rememberIcon(HomeIcons.Shuffle),
                contentDescription = null,
                tint = colors.tx,
                modifier = Modifier.size(22.dp)
            )
        }
        Text("Match al azar", style = MaterialTheme.typography.titleSmall, color = colors.tx)
        Text(
            text = randomMatchFootnote(usage),
            style = MaterialTheme.typography.bodySmall,
            color = colors.mu,
            minLines = 2,
            maxLines = 4
        )
        Spacer(Modifier.height(ParcheUSizes.ModeChipSmall))
        PrimaryButton(
            text = "Buscar",
            onClick = onRandomMatch,
            size = ParcheUButtonSize.Small,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun EmptyCard(home: HomeDto, callbacks: HomeCallbacks, animate: Boolean) {
    val colors = ParcheUTheme.colors
    ParcheUCard(Modifier.fillMaxWidth().testTag(HomeTags.EMPTY)) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TwinMascot(size = TwinMascotSize.M, animationsEnabled = animate)
            Text(
                text = "Tu gemelo está listo para conocer gente",
                style = MaterialTheme.typography.titleMedium,
                color = colors.tx,
                textAlign = TextAlign.Center
            )
            Text(
                text = EMPTY_BODY,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.mu,
                textAlign = TextAlign.Center
            )
            PrimaryButton(
                text = "Elegir de mi parche",
                onClick = callbacks.onPickFromParche,
                icon = rememberIcon(SimulationIcons.Hub),
                modifier = Modifier.fillMaxWidth()
            )
            ParcheUTextButton(
                text = "Match al azar · " + randomMatchFootnote(home.quota.randomMatch).lowercase(),
                onClick = callbacks.onRandomMatch
            )
        }
    }
}

@Composable
private fun ErrorContent(state: HomeUiState.Error, onRetry: () -> Unit) {
    val colors = ParcheUTheme.colors
    Column(
        Modifier.fillMaxSize().testTag(HomeTags.ERROR).padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        TwinMascot(size = TwinMascotSize.L, error = true, animationsEnabled = false)
        Text(
            "No pudimos cargar tu inicio",
            style = MaterialTheme.typography.headlineSmall,
            color = colors.tx,
            textAlign = TextAlign.Center
        )
        Text(
            errorMessage(state.error),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.mu,
            textAlign = TextAlign.Center
        )
        PrimaryButton(text = "Reintentar", onClick = onRetry, modifier = Modifier.fillMaxWidth())
    }
}

private fun toneFor(mode: Mode): TwinMascotTone = when (mode) {
    Mode.AMISTAD -> TwinMascotTone.Friendship
    Mode.PAREJA -> TwinMascotTone.Couple
    Mode.ESTUDIO -> TwinMascotTone.Study
}
