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
    implementation(libs.hikari)
    implementation(libs.postgresql)
    implementation(libs.cloud.sql.socket.factory)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.postgresql)

    implementation(libs.firebase.admin)
    implementation(libs.google.api.client)

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
}

// Los tests con Testcontainers solo corren en CI; en local se omiten con -PsinContenedores.
val sinContenedores = providers.gradleProperty("sinContenedores").isPresent

tasks.test {
    useJUnitPlatform()
    if (sinContenedores) systemProperty("sinContenedores", "true")
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
