package me.santiagoreina.parcheu.feature.simulation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.designsystem.components.base.OutlineButton
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUCard
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUChildTopBar
import me.santiagoreina.parcheu.designsystem.components.base.ParcheULinearProgress
import me.santiagoreina.parcheu.designsystem.components.base.PrimaryButton
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
    val other = state.participants?.mode?.let { colors.forMode(it).solid } ?: colors.primary
    Column(
        Modifier.fillMaxSize()
            .testTag(SimulationTags.RUNNING)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            state.participants?.let {
                Text(
                    "Tú y ${it.otherLabel} · ${it.mode.label()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.mu
                )
            }
            Text(
                "Escenario ${state.currentScenario} de $SCENARIO_COUNT",
                style = MaterialTheme.typography.titleMedium,
                color = colors.tx
            )
        }
        Box(
            Modifier.fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(colors.s1)
        ) {
            SimulationMascot(
                colors.primary,
                80.dp,
                Modifier.align(Alignment.BottomStart).padding(start = 22.dp, bottom = 26.dp)
            )
            SimulationMascot(
                other,
                80.dp,
                Modifier.align(Alignment.BottomEnd).padding(end = 22.dp, bottom = 26.dp)
            )
            TypingDots(colors.mu, Modifier.align(Alignment.TopCenter).padding(top = 28.dp), animate)
        }
        ParcheULinearProgress(
            progress = state.percent / 100f,
            contentDescription = "Avance de la simulación: ${state.percent} %"
        )
        ParcheUCard {
            state.steps.forEach { step -> StepRow(step) }
        }
        ParcheUCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    rememberIcon(SimulationIcons.Notifications),
                    contentDescription = null,
                    tint = colors.prt,
                    modifier = Modifier.size(24.dp)
                )
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

@Composable
private fun StepRow(step: ScenarioStep) {
    val colors = ParcheUTheme.colors
    val (icon, tint, status) =
        when (step.state) {
            StepState.DONE -> Triple(SimulationIcons.CheckCircle, colors.friendship.text, "Listo")
            StepState.IN_PROGRESS -> Triple(null, colors.prt, "En curso")
            StepState.PENDING -> Triple(SimulationIcons.RadioUnchecked, colors.mu, "Pendiente")
        }
    Row(
        Modifier.fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics(mergeDescendants = true) {
                contentDescription =
                    "Escenario ${step.number}: $status"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon == null) {
            CircularProgressIndicator(progress = {
                0.7f
            }, modifier = Modifier.size(22.dp), color = tint, strokeWidth = 2.5.dp)
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
            style = MaterialTheme.typography.bodyLarge,
            color = colors.tx,
            modifier = Modifier.weight(1f)
        )
        Text(
            status,
            style = MaterialTheme.typography.bodySmall,
            color = if (step.state ==
                StepState.IN_PROGRESS
            ) {
                colors.prt
            } else {
                colors.mu
            }
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
        Modifier.fillMaxSize().testTag(SimulationTags.FAILED).padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        SimulationMascot(colors.primary, 84.dp, error = true)
        Text(
            "Los gemelos perdieron la señal",
            style = MaterialTheme.typography.headlineSmall,
            color = colors.tx,
            textAlign = TextAlign.Center
        )
        val paused = state.pausedAtScenario?.let { "La simulación se pausó en el escenario $it." }
        Text(
            listOfNotNull(paused).joinToString(" ").ifEmpty { "La simulación se cortó." },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.mu,
            textAlign = TextAlign.Center
        )
        if (state.refunded) {
            Text(
                NOT_CHARGED_TEXT,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.tx,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(SimulationTags.NOT_CHARGED)
            )
        }
        state.retryError?.let {
            Text(
                errorMessage(it),
                style = MaterialTheme.typography.bodySmall,
                color = colors.error,
                textAlign = TextAlign.Center
            )
        }
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
    }
}

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
