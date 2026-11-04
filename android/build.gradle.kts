// Archivo de build raíz: declara los plugins (sin aplicarlos) y configura Spotless/ktlint para todo el proyecto.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.play.publisher) apply false
    alias(libs.plugins.spotless)
}

spotless {
    val ktlintVersion = libs.versions.ktlint.get()
    // Los Composables usan PascalCase; ktlint lo permite si la función lleva @Composable.
    val ktlintRules =
        mapOf(
            "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
            "ktlint_code_style" to "android_studio"
        )
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktlint(ktlintVersion).editorConfigOverride(ktlintRules)
    }
    kotlinGradle {
        target("*.gradle.kts", "**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(ktlintVersion).editorConfigOverride(ktlintRules)
    }
}
