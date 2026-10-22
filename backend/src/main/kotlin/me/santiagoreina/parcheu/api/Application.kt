package me.santiagoreina.parcheu.api

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import me.santiagoreina.parcheu.api.platform.AppContainer
import me.santiagoreina.parcheu.api.platform.auth.AuthProviders
import me.santiagoreina.parcheu.api.platform.auth.installAuth
import me.santiagoreina.parcheu.api.platform.errors.installErrorHandling
import me.santiagoreina.parcheu.api.platform.healthRoutes
import me.santiagoreina.parcheu.api.platform.logging.installCallLogging
import me.santiagoreina.parcheu.contract.ContractJson

/**
 * Módulo Ktor. FIJO desde E0.3: no se modifica al agregar features. Cada dominio expone un
 * `XRoutes.kt` (interfaz `DomainRoutes`) que se registra desde `AppContainer`.
 */
fun Application.module(container: AppContainer) {
    installCallLogging()
    install(ContentNegotiation) { json(ContractJson) }
    installErrorHandling()
    installAuth(container.tokenVerifier, container.oidcVerifier)

    routing {
        route("/v1") {
            healthRoutes(container.config.version, container.clock)
            authenticate(AuthProviders.FIREBASE) {
                container.domains.forEach { domain -> with(domain) { v1() } }
            }
        }
        authenticate(AuthProviders.OIDC_INTERNAL) {
            route("/internal") {
                container.domains.forEach { domain -> with(domain) { internal() } }
            }
        }
    }
}
