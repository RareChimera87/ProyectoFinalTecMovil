plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.jib)
    application
}

group = "me.santiagoreina.parcheu"
version = "1.0.0"

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // kotlin.time.Instant (contrato) es experimental en Kotlin 2.2.
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

application {
    mainClass.set("me.santiagoreina.parcheu.api.MainKt")
}

dependencies {
    implementation("me.santiagoreina.parcheu:contract")
    implementation(platform(libs.ktor.bom))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.call.logging)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.exposed.json)
    implementation(libs.hikari)
    implementation(libs.postgresql)
    implementation(libs.cloud.sql.socket.factory)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.postgresql)

    implementation(libs.firebase.admin)
    // Cloud Tasks (E2.3, ADR-0019): encolar trabajo asíncrono hacia /internal/tasks/*.
    implementation(libs.google.cloud.tasks)
    implementation(libs.google.api.client)

    // Pasarela de IA (E0.9, ADR-0008): Gemini (Gen AI SDK en modo Vertex) y Claude en Vertex.
    implementation(libs.google.genai)
    implementation(libs.anthropic.java.vertex)
    implementation(libs.anthropic.java.client.okhttp)

    implementation(libs.logback.classic)
    runtimeOnly(libs.logstash.logback.encoder)

    testImplementation(platform(libs.ktor.bom))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    // E3.5: TwinPromptBuilderDelegationTest lee las invocaciones reales de los .class con ASM.
    testImplementation(libs.asm)
}

// Los tests con Testcontainers solo corren en CI; en local se omiten con -PsinContenedores.
val sinContenedores = providers.gradleProperty("sinContenedores").isPresent

tasks.test {
    // El humo contra Vertex real (@Tag("vertex")) es manual: solo corre con -Pvertex.
    useJUnitPlatform {
        if (!providers.gradleProperty("vertex").isPresent) excludeTags("vertex")
    }
    if (sinContenedores) systemProperty("sinContenedores", "true")
    // Semilla de la suite de privacidad (E3.5): es entrada de la tarea para que cambiarla vuelva a
    // correr los tests (si no, Gradle responde UP-TO-DATE) y se reenvía a la JVM de pruebas.
    val privacySeed = providers.environmentVariable("PRIVACY_SEED").orElse(providers.gradleProperty("privacySeed")).orElse("")
    inputs.property("privacySeed", privacySeed)
    if (privacySeed.get().isNotBlank()) systemProperty("privacy.seed", privacySeed.get())
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// Imagen sin daemon de Docker: `./gradlew jibBuildTar`. La misma imagen sirve para el servicio
// y para los Cloud Run Jobs (migrate, seed-demo, purge-demo); el modo se pasa como argumento.
jib {
    from {
        // Tag verificado en Docker Hub el 22-oct-2026 (eclipse-temurin:21-jre existe).
        image = "eclipse-temurin:21-jre"
    }
    to {
        image = providers.gradleProperty("imagen").getOrElse("parcheu-api")
        tags = setOf("latest")
    }
    container {
        mainClass = "me.santiagoreina.parcheu.api.MainKt"
        args = listOf("serve")
        ports = listOf("8080")
        environment = mapOf(
            "APP_VERSION" to (System.getenv("GITHUB_SHA") ?: providers.gradleProperty("gitSha").getOrElse("dev")),
        )
        creationTime = "USE_CURRENT_TIMESTAMP"
    }
}

// ENV es obligatorio en el backend (sin valor por defecto). `./gradlew run` es solo para desarrollo local.
tasks.named<JavaExec>("run") {
    if (System.getenv("ENV") == null) environment("ENV", "local")
}
