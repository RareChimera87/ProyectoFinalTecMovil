package me.santiagoreina.parcheu.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.santiagoreina.parcheu.contract.MeDto
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.TraitKey
import me.santiagoreina.parcheu.contract.TwinDto
import me.santiagoreina.parcheu.core.ui.StateIllustration
import me.santiagoreina.parcheu.core.ui.stateProp
import me.santiagoreina.parcheu.designsystem.components.base.ModeChip
import me.santiagoreina.parcheu.designsystem.components.base.ModeChipSize
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUButtonSize
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUCard
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUIconButton
import me.santiagoreina.parcheu.designsystem.components.base.ParcheULinearProgress
import me.santiagoreina.parcheu.designsystem.components.base.ParcheURootTopBar
import me.santiagoreina.parcheu.designsystem.components.base.PillChip
import me.santiagoreina.parcheu.designsystem.components.base.SectionHeader
import me.santiagoreina.parcheu.designsystem.components.base.TonalButton
import me.santiagoreina.parcheu.designsystem.components.compound.ErrorState
import me.santiagoreina.parcheu.designsystem.components.compound.HeroBlockDefaults
import me.santiagoreina.parcheu.designsystem.components.compound.LineChart
import me.santiagoreina.parcheu.designsystem.components.compound.SettingsRow
import me.santiagoreina.parcheu.designsystem.mascot.TwinMascotGlyph
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.simulation.errorMessage
import me.santiagoreina.parcheu.feature.simulation.forMode
import me.santiagoreina.parcheu.feature.simulation.label
import me.santiagoreina.parcheu.feature.simulation.rememberIcon

/** Etiquetas de prueba de C3. */
object ProfileTags {
    const val ROOT = "profile:root"
    const val LOADING = "profile:loading"
    const val ERROR = "profile:error"
    const val SETTINGS = "profile:settings"
    const val EDIT = "profile:edit"
    const val TRAIN = "profile:train"
    const val SUBSCRIPTION = "profile:subscription"
    const val ADD_FRIENDS = "profile:addFriends"
    const val CHART = "profile:chart"
    const val CHART_EMPTY = "profile:chartEmpty"
    const val CHART_UNAVAILABLE = "profile:chartUnavailable"
    const val CHART_VALUE = "profile:chartValue"
    const val MODES = "profile:modes"
}

/** Texto del gráfico sin datos (criterio de la ficha E9.1). */
const val CHART_EMPTY_TEXT =
    "Tu gemelo aún está aprendiendo: califica respuestas en Entrena a tu gemelo"

/** Acciones de C3; la ruta las conecta con el ViewModel. */
data class ProfileCallbacks(
    val onSettings: () -> Unit = {},
    val onEditProfile: () -> Unit = {},
    val onTrainTwin: () -> Unit = {},
    val onSubscription: () -> Unit = {},
    val onAddFriends: () -> Unit = {},
    val onRetry: () -> Unit = {}
)

/** C3 Perfil y gemelo digital. La NavBar la pone la raíz de la app. */
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    callbacks: ProfileCallbacks,
    modifier: Modifier = Modifier
) {
    val colors = ParcheUTheme.colors
    Column(modifier.fillMaxSize().background(colors.bg).testTag(ProfileTags.ROOT)) {
        ParcheURootTopBar(
            title = "Perfil",
            actions = {
                ParcheUIconButton(
                    icon = rememberIcon(ProfileIcons.Settings),
                    contentDescription = "Ajustes",
                    onClick = callbacks.onSettings,
                    modifier = Modifier.testTag(ProfileTags.SETTINGS)
                )
            }
        )
        when (state) {
            ProfileUiState.Loading ->
                Box(
                    Modifier.fillMaxSize().testTag(ProfileTags.LOADING),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = colors.primary)
                }
            is ProfileUiState.Error ->
                ErrorState(
                    title = "No pudimos cargar tu perfil",
                    message = errorMessage(state.error),
                    retryLabel = "Reintentar",
                    onRetryClick = callbacks.onRetry,
                    illustration = { StateIllustration(state.error.stateProp(), error = true) },
                    modifier = Modifier.fillMaxSize().testTag(ProfileTags.ERROR)
                )
            is ProfileUiState.Loaded -> LoadedContent(state, callbacks)
        }
    }
}

@Composable
private fun LoadedContent(state: ProfileUiState.Loaded, callbacks: ProfileCallbacks) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ProfileHeader(state, callbacks.onEditProfile)
        TwinHero(state.twin, state.chart, callbacks.onTrainTwin)
        TraitsCard(state.twin)
        AccuracyCard(state.chart)
        ModesRow(state.me)
        ParcheUCard {
            SettingsRow(
                title = "Suscripción",
                subtitle = if (state.me.plus) "ParcheU+ activo" else "Plan gratuito",
                icon = rememberIcon(ProfileIcons.Star),
                onClick = callbacks.onSubscription,
                modifier = Modifier.testTag(ProfileTags.SUBSCRIPTION),
                trailing = { Chevron() }
            )
            SettingsRow(
                title = "Agregar amigos",
                subtitle = "Tu código QR y enlace de invitación",
                icon = rememberIcon(ProfileIcons.PersonAdd),
                onClick = callbacks.onAddFriends,
                modifier = Modifier.testTag(ProfileTags.ADD_FRIENDS),
                trailing = { Chevron() }
            )
        }
        Box(Modifier.padding(bottom = 16.dp))
    }
}

@Composable
private fun Chevron() {
    Icon(
        painter = rememberIcon(ProfileIcons.ChevronRight),
        contentDescription = null,
        tint = ParcheUTheme.colors.mu
    )
}

/** Cabecera de C3 sin tarjeta (dirección A): el nombre como titular para que el héroe suba. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileHeader(state: ProfileUiState.Loaded, onEdit: () -> Unit) {
    val colors = ParcheUTheme.colors
    val me = state.me
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = me.displayName ?: "Tú",
            style = MaterialTheme.typography.headlineLarge,
            color = colors.tx,
            modifier = Modifier.semantics { heading() }
        )
        val detail = listOfNotNull(me.career, state.universityName).joinToString(" · ")
        if (detail.isNotEmpty()) {
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = colors.mu)
        }
        if (state.interestLabels.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                state.interestLabels.forEach { PillChip(text = it) }
            }
        }
        TonalButton(
            text = "Editar perfil",
            onClick = onEdit,
            size = ParcheUButtonSize.Small,
            icon = rememberIcon(ProfileIcons.Edit),
            modifier = Modifier.testTag(ProfileTags.EDIT)
        )
    }
}

/** Precisión del gemelo para el héroe: el último punto con datos, o `null` si aún no hay. */
private val TwinChart.latest: Int?
    get() = when (this) {
        is TwinChart.Single -> value
        is TwinChart.Line -> last
        else -> null
    }

/**
 * Protagonista de C3: héroe violeta con la precisión en Sora 800 56 ("78 %"), la P que mira en blanco
 * (ojo del color del héroe), el nivel y la experiencia, y "Entrenar a mi gemelo". Sin precisión todavía,
 * el número grande es el nivel. Todo el texto va en onHero sólido (5,15:1).
 */
@Composable
private fun TwinHero(twin: TwinDto, chart: TwinChart, onTrain: () -> Unit) {
    val colors = ParcheUTheme.colors
    val accuracy = chart.latest
    Column(
        Modifier
            .fillMaxWidth()
            .clip(ParcheUTheme.shapes.hero)
            .background(colors.hero, ParcheUTheme.shapes.hero)
            .padding(HeroBlockDefaults.ContentPadding),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = (
                        if (accuracy !=
                            null
                        ) {
                            "Qué tanto te conoce"
                        } else {
                            "Tu gemelo"
                        }
                        ).uppercase(),
                    style = ParcheUTheme.textStyles.overline.copy(letterSpacing = 0.9.sp),
                    color = colors.onHero
                )
                if (accuracy != null) {
                    Text(
                        "$accuracy %",
                        style = ParcheUTheme.textStyles.hero56,
                        color = colors.onHero,
                        modifier =
                        Modifier.testTag(ProfileTags.CHART_VALUE).semantics { heading() }
                    )
                } else {
                    Text(
                        "Nivel ${twin.level}",
                        style = ParcheUTheme.textStyles.hero46,
                        color = colors.onHero,
                        modifier = Modifier.semantics { heading() }
                    )
                }
            }
            TwinMascotGlyph(
                width = 72.dp,
                color = colors.onHero,
                eyeColor = colors.hero,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        if (accuracy != null) {
            Text(
                "Tu gemelo · Nivel ${twin.level}",
                style = MaterialTheme.typography.titleSmall,
                color = colors.onHero
            )
        }
        HeroProgress(
            progress = if (twin.xpNext > 0) twin.xp.toFloat() / twin.xpNext else 0f,
            contentDescription = "Experiencia: ${twin.xp} de ${twin.xpNext}"
        )
        Text(
            "${twin.xp}/${twin.xpNext} XP",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onHero
        )
        TonalButton(
            text = "Entrenar a mi gemelo",
            onClick = onTrain,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).testTag(ProfileTags.TRAIN)
        )
    }
}

/** Barra de experiencia sobre el héroe: pista heroQuiet y avance onHero (la de s2 no se ve en violeta). */
@Composable
private fun HeroProgress(progress: Float, contentDescription: String) {
    val colors = ParcheUTheme.colors
    val value = progress.coerceIn(0f, 1f)
    val shape = RoundedCornerShape(3.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .height(6.dp)
            .clip(shape)
            .background(colors.heroQuiet, shape)
            .semantics {
                this.contentDescription = contentDescription
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
            }
    ) {
        Box(Modifier.fillMaxWidth(value).fillMaxHeight().background(colors.onHero, shape))
    }
}

@Composable
private fun TraitsCard(twin: TwinDto) {
    if (twin.traits.isEmpty()) return
    val colors = ParcheUTheme.colors
    ParcheUCard(Modifier.fillMaxWidth()) {
        SectionHeader(title = "Rasgos")
        twin.traits.forEach { trait ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    trait.key.label(),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.tx,
                    modifier = Modifier.weight(0.45f)
                )
                Box(Modifier.weight(0.55f)) {
                    ParcheULinearProgress(
                        progress = trait.value.toFloat(),
                        contentDescription =
                        "${trait.key.label()}: ${(trait.value * 100).toInt()} %"
                    )
                }
            }
        }
    }
}

@Composable
private fun AccuracyCard(chart: TwinChart) {
    val colors = ParcheUTheme.colors
    ParcheUCard(modifier = Modifier.fillMaxWidth().testTag(ProfileTags.CHART)) {
        SectionHeader(title = "Evolución")
        when (chart) {
            TwinChart.Empty ->
                Text(
                    CHART_EMPTY_TEXT,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.mu,
                    modifier = Modifier.testTag(ProfileTags.CHART_EMPTY)
                )
            TwinChart.Unavailable ->
                Text(
                    "No pudimos cargar la evolución. Inténtalo de nuevo más tarde.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.mu,
                    modifier = Modifier.testTag(ProfileTags.CHART_UNAVAILABLE)
                )
            is TwinChart.Single ->
                LineChart(
                    points = listOf(chart.value.toFloat()),
                    contentDescription =
                    "Tu gemelo te conoce un ${chart.value} %. Es el único registro semanal."
                )
            is TwinChart.Line ->
                LineChart(
                    points = chart.values.map { it.toFloat() },
                    contentDescription =
                    "Evolución semanal: de ${chart.values.first()} % a ${chart.last} % " +
                        "entre ${chart.startLabel} y ${chart.endLabel}.",
                    startLabel = chart.startLabel,
                    endLabel = chart.endLabel
                )
        }
    }
}

@Composable
private fun ModesRow(me: MeDto) {
    val active = Mode.entries.filter { me.modes[it] == true }
    if (active.isEmpty()) return
    val colors = ParcheUTheme.colors
    Column(Modifier.testTag(ProfileTags.MODES), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title = "Tus modos")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            active.forEach {
                ModeChip(label = it.label(), mode = colors.forMode(it), size = ModeChipSize.M)
            }
        }
    }
}

internal fun TraitKey.label(): String = when (this) {
    TraitKey.SOCIAL_ENERGY -> "Energía social"
    TraitKey.PLANNING -> "Planificación"
    TraitKey.CONFLICT_HANDLING -> "Manejo de conflictos"
    TraitKey.HUMOR -> "Humor"
    TraitKey.ACADEMIC_AMBITION -> "Ambición académica"
    TraitKey.OPENNESS -> "Apertura"
}
