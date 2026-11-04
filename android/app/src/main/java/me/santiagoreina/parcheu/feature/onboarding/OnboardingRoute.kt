package me.santiagoreina.parcheu.feature.onboarding

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.santiagoreina.parcheu.BuildConfig
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Onboarding
import org.koin.compose.viewmodel.koinViewModel

/**
 * A2-A4 (E2.2). [Onboarding.page] abre el pager en esa página: 0 desde el Splash sin sesión y 2 (A4) tras
 * cerrar sesión o cuando hay que volver a aceptar los Términos. Al terminar, la pila queda solo con el
 * destino del onboarding.
 */
@Composable
fun OnboardingRoute(
    route: Onboarding,
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pagerState =
        rememberPagerState(initialPage = route.page.coerceIn(0, ONBOARDING_PAGES - 1)) {
            ONBOARDING_PAGES
        }
    val scope = rememberCoroutineScope()
    // Credential Manager necesita el Context de la Activity para mostrar el selector de cuentas.
    val activityContext = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is OnboardingEffect.Navigate -> navigator.replaceAll(effect.route)
            }
        }
    }

    OnboardingScreen(
        state = state,
        pagerState = pagerState,
        termsUrl = BuildConfig.TERMS_URL,
        privacyUrl = BuildConfig.PRIVACY_URL,
        onSkip = { scope.launch { pagerState.animateScrollToPage(ONBOARDING_PAGES - 1) } },
        onNext = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
        onContinueWithGoogle = { viewModel.onContinueWithGoogle(activityContext) },
        onAcceptTerms = viewModel::onAcceptTerms,
        onDeclineTerms = viewModel::onDeclineTerms,
        modifier = modifier
    )
}
