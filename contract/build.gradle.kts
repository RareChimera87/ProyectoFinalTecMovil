plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

group = "me.santiagoreina.parcheu"
version = "1.0.0"

kotlin {
    jvmToolchain(17)
    explicitApi()
    compilerOptions {
        // kotlin.time.Instant es experimental en Kotlin 2.2; quien consuma el contrato
        // también necesita esta opción (android/ y backend/ la agregan en E0.7 y E0.3).
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

dependencies {
    // api: los DTOs exponen tipos de kotlinx.serialization (JsonElement, KSerializer).
    api(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
