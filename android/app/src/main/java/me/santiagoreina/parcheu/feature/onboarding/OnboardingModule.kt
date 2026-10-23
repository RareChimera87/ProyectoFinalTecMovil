package me.santiagoreina.parcheu.feature.onboarding

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Dependencias de A2-A4 (E2.2). El `AuthRepository` viene de `authModule`. */
val onboardingModule =
    module {
        viewModelOf(::OnboardingViewModel)
    }
