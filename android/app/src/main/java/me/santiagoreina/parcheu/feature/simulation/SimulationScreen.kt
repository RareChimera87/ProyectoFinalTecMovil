package me.santiagoreina.parcheu.feature.simulation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.designsystem.components.base.ModeSpineCard
import me.santiagoreina.parcheu.designsystem.components.base.OutlineButton
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUCard
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUChildTopBar
import me.santiagoreina.parcheu.designsystem.components.base.ParcheULinearProgress
import me.santiagoreina.parcheu.designsystem.components.base.PrimaryButton
import me.santiagoreina.parcheu.designsystem.components.compound.IconCircle
import me.santiagoreina.parcheu.designsystem.mascot.rememberAnimationsEnabled
import me.santiagoreina.parcheu.designsystem.theme.ModeColors
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/** Texto de F5 cuando el cupo se devolvió; solo se muestra con `refunded = true`. */
const val NOT_CHARGED_TEXT = "No se descontó de tu cupo."

/** Etiquetas de prueba de F4/F5. */
object SimulationTags {
    const val RUNNING = "simulation:running"
    const val FAILED = "simulation:failed"
    const val CONNECTING = "simulation:connecting"
    const val LOAD_ERROR = "simulation:loadError"
    const val RETRY = "simulation:retry"
    const val HOME = "simulation:home"
    const val NOT_CHARGED = "simulation:notCharged"
}

/** F4 (en curso), F5 (error) y los estados de conexión de la pantalla de simulación. */
@Composable
fun SimulationScreen(
    state: SimulationUiState,
    onClose: () -> Unit,
    onReload: () -> Unit,
    onRetry: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    animate: Boolean = true
) {
    val colors = ParcheUTheme.colors
    Column(modifier.fillMaxSize().background(colors.bg)) {
        ParcheUChildTopBar(
            title = "Simulación",
            navigationIcon = rememberIcon(SimulationIcons.Close),
            navigationContentDescription = "Cerrar",
            onNavigationClick = onClose
        )
        when (state) {
            SimulationUiState.Connecting -> Connecting(animate)
            is SimulationUiState.Running -> RunningContent(state, animate)
            is SimulationUiState.Failed -> FailedContent(state, onRetry, onHome)
            is SimulationUiState.Error -> LoadError(state.error, onReload)
        }
    }
}

@Composable
private fun Connecting(animate: Boolean) {
    Box(
        Modifier.fillMaxSize().testTag(SimulationTags.CONNECTING),
        contentAlignment = Alignment.Center
    ) {
        if (animate) {
            CircularProgressIndicator(color = ParcheUTheme.colors.primary)
        } else {
            TypingDots(ParcheUTheme.colors.mu, animate = false)
        }
    }
}

@Composable
private fun RunningContent(state: SimulationUiState.Running, animate: Boolean) {
    val colors = ParcheUTheme.colors
    val mode = state.participants?.mode?.let { colors.forMode(it) }
    Column(
        Modifier.fillMaxSize()
            .testTag(SimulationTags.RUNNING)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ParcheUTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(ParcheUTheme.spacing.blockGapTight)
    ) {
        Spacer(Modifier.height(2.dp))
        SimulationStage(state, mode, animate)
        val stepsContent: @Composable ColumnScope.() -> Unit = {
            Text(
                "ESCENARIOS",
                style = ParcheUTheme.textStyles.overline,
                color = colors.mu,
                modifier = Modifier.semantics { heading() }
            )
            state.steps.forEach { step -> StepRow(step, mode?.text ?: colors.prt) }
        }
        if (mode != null) {
            ModeSpineCard(mode = mode, modifier = Modifier.fillMaxWidth(), content = stepsContent)
        } else {
            ParcheUCard(Modifier.fillMaxWidth(), content = stepsContent)
        }
        ParcheUCard(Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconCircle(icon = rememberIcon(SimulationIcons.Notifications))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Avisarme al terminar",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.tx
                    )
                    Text(
                        "Puedes salir; la simulación sigue.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.mu
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * Escena de F4, el protagonista: bloque tonal del modo (radio 24) con quién y en qué modo, el escenario
 * actual en Sora 800, las dos P frente a frente conversando (habla una a la vez y se turnan) y la barra
 * del color del modo con el avance.
 */
@Composable
private fun SimulationStage(state: SimulationUiState.Running, mode: ModeColors?, animate: Boolean) {
    val colors = ParcheUTheme.colors
    val container = mode?.container ?: colors.prc
    val content = mode?.onContainer ?: colors.onprc
    val other = mode?.solid ?: colors.primary
    val systemAllows = rememberAnimationsEnabled()
    var speaker by remember { mutableStateOf(TwinSpeaker.Me) }
    if (animate && systemAllows) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(TURN_MS)
                speaker = if (speaker == TwinSpeaker.Me) TwinSpeaker.Other else TwinSpeaker.Me
            }
        }
    }
    val who = state.participants?.otherLabel
    Column(
        Modifier.fillMaxWidth()
            .clip(ParcheUTheme.shapes.hero)
            .background(container)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        state.participants?.let {
            Text(
                "Tú y ${it.otherLabel} · ${it.mode.label()}",
                style = MaterialTheme.typography.bodyMedium,
                color = content
            )
        }
        Text(
            "Escenario ${state.currentScenario} de $SCENARIO_COUNT",
            style = MaterialTheme.typography.headlineLarge,
            color = content,
            modifier = Modifier.semantics { heading() }
        )
        Row(
            Modifier.fillMaxWidth()
                .padding(top = 14.dp, bottom = 10.dp)
                .clearAndSetSemantics {
                    contentDescription =
                        if (who != null) {
                            "Tu gemelo y el de $who están conversando"
                        } else {
                            "Los gemelos están conversando"
                        }
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom
        ) {
            TwinWithBubble(speaking = speaker == TwinSpeaker.Me, dots = content, animate) {
                SimulationMascot(
                    colors.primary,
                    STAGE_MASCOT,
                    talking = speaker == TwinSpeaker.Me,
                    animate = animate
                )
            }
            Spacer(Modifier.width(28.dp))
            TwinWithBubble(speaking = speaker == TwinSpeaker.Other, dots = content, animate) {
                SimulationMascot(
                    other,
                    STAGE_MASCOT,
                    mirrored = true,
                    talking = speaker == TwinSpeaker.Other,
                    animate = animate
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ParcheULinearProgress(
                progress = state.percent / 100f,
                contentDescription = "Avance de la simulación: ${state.percent} %",
                color = other,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${state.percent} %",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold
                ),
                color = content,
                modifier = Modifier.clearAndSetSemantics { }
            )
        }
    }
}

/** Un gemelo con su globo de "escribiendo" encima; el globo solo se ve sobre el que habla. */
@Composable
private fun TwinWithBubble(
    speaking: Boolean,
    dots: Color,
    animate: Boolean,
    mascot: @Composable () -> Unit
) {
    val colors = ParcheUTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.alpha(if (speaking) 1f else 0f)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.s1)
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            TypingDots(dots, animate = animate)
        }
        Spacer(Modifier.height(10.dp))
        mascot()
    }
}

private val STAGE_MASCOT = 92.dp
private const val TURN_MS = 1_800L

@Composable
private fun StepRow(step: ScenarioStep, accent: Color) {
    val colors = ParcheUTheme.colors
    val (icon, tint, status) =
        when (step.state) {
            StepState.DONE -> Triple(SimulationIcons.CheckCircle, colors.friendship.text, "Listo")
            StepState.IN_PROGRESS -> Triple(null, accent, "En curso")
            StepState.PENDING -> Triple(SimulationIcons.RadioUnchecked, colors.mu, "Pendiente")
        }
    val current = step.state == StepState.IN_PROGRESS
    Row(
        Modifier.fillMaxWidth()
            .padding(vertical = 2.dp)
            .semantics(mergeDescendants = true) {
                contentDescription =
                    "Escenario ${step.number}: $status"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon == null) {
            CircularProgressIndicator(
                progress = { 0.7f },
                modifier = Modifier.size(22.dp),
                color = tint,
                trackColor = colors.s2,
                strokeWidth = 2.5.dp
            )
        } else {
            Icon(
                rememberIcon(icon),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            "Escenario ${step.number}",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = colors.tx,
            modifier = Modifier.weight(1f)
        )
        Text(
            status,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = if (current) accent else colors.mu
        )
    }
}

@Composable
private fun FailedContent(
    state: SimulationUiState.Failed,
    onRetry: () -> Unit,
    onHome: () -> Unit
) {
    val colors = ParcheUTheme.colors
    Column(
        Modifier.fillMaxSize()
            .testTag(SimulationTags.FAILED)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ParcheUTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(ParcheUTheme.spacing.blockGapTight)
    ) {
        Spacer(Modifier.height(2.dp))
        // Las dos P siguen frente a frente, pero en gris y con un corte entre ellas: se perdió la señal.
        Column(
            Modifier.fillMaxWidth()
                .clip(ParcheUTheme.shapes.hero)
                .background(colors.s2)
                .padding(horizontal = 18.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SimulationMascot(colors.primary, FAILED_MASCOT, error = true)
                SignalCut(Modifier.padding(horizontal = 14.dp))
                SimulationMascot(colors.primary, FAILED_MASCOT, error = true, mirrored = true)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "Los gemelos perdieron la señal",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.tx,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            val paused = state.pausedAtScenario?.let {
                "La simulación se pausó en el escenario $it."
            }
            Text(
                listOfNotNull(paused).joinToString(" ").ifEmpty { "La simulación se cortó." },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.tx,
                textAlign = TextAlign.Center
            )
        }
        if (state.refunded) {
            ParcheUCard(Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconCircle(
                        icon = rememberIcon(SimulationIcons.CheckCircle),
                        container = colors.friendship.container,
                        tint = colors.friendship.onContainer
                    )
                    Text(
                        NOT_CHARGED_TEXT,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = colors.tx,
                        modifier = Modifier.weight(1f).testTag(SimulationTags.NOT_CHARGED)
                    )
                }
            }
        }
        state.retryError?.let {
            Text(
                errorMessage(it),
                style = MaterialTheme.typography.bodySmall,
                color = colors.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(6.dp))
        if (state.canRetry) {
            PrimaryButton(
                text = "Reintentar",
                onClick = onRetry,
                enabled = !state.retrying,
                icon = rememberIcon(SimulationIcons.Refresh),
                modifier = Modifier.fillMaxWidth().testTag(SimulationTags.RETRY)
            )
        }
        OutlineButton(
            text = "Volver al inicio",
            onClick = onHome,
            modifier = Modifier.fillMaxWidth().testTag(SimulationTags.HOME)
        )
        Spacer(Modifier.height(16.dp))
    }
}

/** Corte de señal entre los gemelos de F5: rayas que se interrumpen. Decorativo. */
@Composable
private fun SignalCut(modifier: Modifier = Modifier) {
    val color = ParcheUTheme.colors.mu
    Row(
        modifier.clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(width = 14.dp, height = 4.dp).clip(CircleShape).background(color))
        Box(Modifier.size(width = 6.dp, height = 4.dp).clip(CircleShape).background(color))
        Box(Modifier.size(4.dp).clip(CircleShape).background(color.copy(alpha = 0.5f)))
    }
}

private val FAILED_MASCOT = 72.dp

@Composable
private fun LoadError(error: AppError, onReload: () -> Unit) {
    val colors = ParcheUTheme.colors
    Column(
        Modifier.fillMaxSize().testTag(SimulationTags.LOAD_ERROR).padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text(
            "No pudimos cargar la simulación",
            style = MaterialTheme.typography.headlineSmall,
            color = colors.tx,
            textAlign = TextAlign.Center
        )
        Text(
            errorMessage(error),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.mu,
            textAlign = TextAlign.Center
        )
        PrimaryButton(text = "Reintentar", onClick = onReload, modifier = Modifier.fillMaxWidth())
    }
}

/** Mensaje en español para un error de red o de cupo en F4/F5/F6. */
internal fun errorMessage(error: AppError): String = when (error) {
    AppError.Network -> "Revisa tu conexión e inténtalo de nuevo."
    is AppError.QuotaExceeded -> "Ya usaste tus simulaciones de la semana."
    AppError.NotFound -> "Esta simulación ya no está disponible."
    AppError.Blocked -> "No puedes simular con esta persona."
    is AppError.AiBudget -> "Estamos al límite por hoy. Prueba más tarde."
    else -> "Algo salió mal. Inténtalo de nuevo."
}
