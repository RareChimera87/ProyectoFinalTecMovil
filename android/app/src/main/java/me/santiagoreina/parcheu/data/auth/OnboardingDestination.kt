package me.santiagoreina.parcheu.data.auth

import me.santiagoreina.parcheu.contract.OnboardingStep
import me.santiagoreina.parcheu.navigation.AppRoute
import me.santiagoreina.parcheu.navigation.BasicProfile
import me.santiagoreina.parcheu.navigation.Home
import me.santiagoreina.parcheu.navigation.Modes
import me.santiagoreina.parcheu.navigation.Onboarding
import me.santiagoreina.parcheu.navigation.TwinQuestions

/** Página de A4 dentro del pager de onboarding (A2 = 0, A3 = 1, A4 = 2). */
const val LOGIN_PAGE = 2

/**
 * Destino tras el login según [OnboardingStep] (arquitectura.md §2.5). `NEEDS_TERMS` vuelve a A4, que
 * pide aceptar la versión vigente con la hoja de confirmación.
 */
fun OnboardingStep.destination(): AppRoute = when (this) {
    OnboardingStep.NEEDS_TERMS -> Onboarding(page = LOGIN_PAGE)
    OnboardingStep.NEEDS_PROFILE -> BasicProfile()
    OnboardingStep.NEEDS_TWIN -> TwinQuestions
    OnboardingStep.NEEDS_MODES -> Modes()
    OnboardingStep.DONE -> Home
}
