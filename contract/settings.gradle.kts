// Build independiente del contrato de la API v1. Se consume desde android/ y backend/
// con includeBuild("../contract") y la dependencia "me.santiagoreina.parcheu:contract".
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "contract"
