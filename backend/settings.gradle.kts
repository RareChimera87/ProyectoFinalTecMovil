// Backend Ktor de ParcheU: build independiente que consume el contrato con includeBuild.
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    // Resuelve (y descarga si hace falta) el JDK 21 del toolchain. En la máquina del equipo
    // se usa el JBR 21 de Android Studio (ver gradle.properties y docs/desarrollo-local.md).
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "backend"

// Sustituye me.santiagoreina.parcheu:contract por el módulo del monorepo.
includeBuild("../contract")
