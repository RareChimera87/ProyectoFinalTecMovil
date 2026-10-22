package me.santiagoreina.parcheu.api.twin

import io.ktor.server.routing.Route
import me.santiagoreina.parcheu.api.platform.DomainRoutes
import me.santiagoreina.parcheu.api.platform.stubGet
import me.santiagoreina.parcheu.api.platform.stubPost

/** Rutas del dominio twin: stubs 501 de E0.3; las rellena su feature. */
class TwinRoutes : DomainRoutes {
    override fun Route.v1() {
        stubGet("/twin/questions")
        stubPost("/twin/answers")
        stubGet("/twin")
        stubGet("/twin/evolution")
        stubGet("/twin/memories")
        stubPost("/twin/memories/{id}/accept")
        stubPost("/twin/memories/{id}/reject")
        stubGet("/twin/feedback-prompts")
        stubPost("/twin/feedback")
    }

    override fun Route.internal() {
        stubPost("/tasks/build-persona")
        stubPost("/tasks/extract-memories")
        stubPost("/jobs/memories")
        stubPost("/jobs/twin-accuracy")
    }
}
