package me.santiagoreina.parcheu.feature.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.designsystem.components.base.PrimaryButton
import me.santiagoreina.parcheu.designsystem.theme.ParcheUPalette
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.onboarding.LoadingDots
import me.santiagoreina.parcheu.feature.onboarding.OnboardingAppIcon
import me.santiagoreina.parcheu.feature.onboarding.OnboardingWordmark

object SplashTags {
    const val SCREEN = "splash"
    const val LOADING = "splash:loading"
    const val ERROR = "splash:error"
    const val RETRY = "splash:retry"
}

/** Mensaje de A1 cuando no se pudo consultar la sesión. */
fun splashErrorMessage(error: AppError): String = when (error) {
    AppError.Network -> "No hay conexión. Revisa tu internet e inténtalo de nuevo."
    AppError.Suspended -> "Tu cuenta está suspendida por ahora."
    else -> "No pudimos abrir tu sesión. Inténtalo de nuevo."
}

/** A1 sin estado: ícono, wordmark 44 y lema con un brillo violeta; abajo, puntos de carga o el error. */
@Composable
fun SplashScreen(
    state: SplashUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    animateLoading: Boolean = true
) {
    val colors = ParcheUTheme.colors
    Box(
        modifier
            .fillMaxSize()
            .background(colors.bg)
            .background(
                Brush.radialGradient(
                    0f to ParcheUPalette.Violet.copy(alpha = if (colors.isDark) 0.35f else 0.18f),
                    0.6f to ParcheUPalette.Violet.copy(alpha = 0f)
                )
            ).testTag(SplashTags.SCREEN)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            OnboardingAppIcon(size = 104.dp)
            OnboardingWordmark(size = 44.dp)
            Text(
                "Tu gemelo conoce gente por ti",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.mu
            )
        }
        Column(
            modifier = Modifier.align(
                Alignment.BottomCenter
            ).fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (state) {
                SplashUiState.Loading -> LoadingDots(
                    animate = animateLoading,
                    modifier = Modifier.testTag(SplashTags.LOADING)
                )
                is SplashUiState.Error -> {
                    Text(
                        text = splashErrorMessage(state.error),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.tx,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag(SplashTags.ERROR)
                    )
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton(
                        text = "Reintentar",
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth().testTag(SplashTags.RETRY)
                    )
                }
            }
        }
    }
}
