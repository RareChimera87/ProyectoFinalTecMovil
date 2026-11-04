package me.santiagoreina.parcheu.api.premium

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import me.santiagoreina.parcheu.api.graph.AnonIdSigner
import me.santiagoreina.parcheu.api.platform.errors.QuotaExceeded
import me.santiagoreina.parcheu.api.startApp
import me.santiagoreina.parcheu.contract.ReportKind
import me.santiagoreina.parcheu.contract.ReportReason
import me.santiagoreina.parcheu.contract.ReportRequest
import me.santiagoreina.parcheu.contract.QuotaKind

/**
 * Reportar y bloquear nunca devuelven 402 (arquitectura.md §10.1, backlog E7.4): un usuario gratis con
 * **todos** los cupos agotados (simulaciones de la semana, match del día e informe del mes) puede
 * reportar a una persona (por uuid o por `anonId`), reportar contenido de IA (un turno o el informe),
 * bloquear (por uuid o por `anonId`), ver su lista de bloqueados y desbloquear.
 */
class SafetyNeverPaywalledTest : FunSpec({
    fun PremiumWorld.exhaustAllQuotas() {
        val quota = sim.quota.service
        val ana = id("ana")
        QuotaKind.entries.forEach { kind ->
            var n = 0
            while (runCatching { quota.ensureAvailable(ana, kind) }.isSuccess) quota.consume(ana, kind, "agotar-$kind-${n++}")
        }
        val status = quota.status(ana)
        status.plus shouldBe false
        listOf(status.simulations, status.randomMatch, status.fullReport).forEach { it.used shouldBe it.limit }
        QuotaKind.entries.forEach { kind -> shouldThrow<QuotaExceeded> { quota.ensureAvailable(ana, kind) } }
    }

    fun HttpResponse.notPaywalled(expected: HttpStatusCode, what: String) = withClue(what) {
        status shouldNotBe HttpStatusCode.PaymentRequired
        status shouldBe expected
    }

    test("gratis con todos los cupos agotados puede reportar (201) y bloquear (204), nunca 402") {
        val w = PremiumWorld(plus = false)
        w.exhaustAllQuotas()
        val franAnon = AnonIdSigner.forTests().anonId(w.id("ana"), w.id("fran"))
        testApplication {
            val client = startApp(w.container())
            suspend fun report(request: ReportRequest) = client.post("/v1/reports") {
                auth("ana")
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            report(ReportRequest(ReportKind.USER, targetUserId = w.id("beto").toString(), reason = ReportReason.HARASSMENT))
                .notPaywalled(HttpStatusCode.Created, "reportar por uuid")
            report(ReportRequest(ReportKind.USER, anonId = franAnon, reason = ReportReason.FAKE_OR_SCAM))
                .notPaywalled(HttpStatusCode.Created, "reportar por anonId")
            report(ReportRequest(ReportKind.AI_CONTENT, simulationId = w.franSimulation, turnIndex = 0, reason = ReportReason.AI_HARMFUL))
                .notPaywalled(HttpStatusCode.Created, "reportar un turno de la IA")
            report(ReportRequest(ReportKind.AI_CONTENT, simulationId = w.franSimulation, turnIndex = -1, reason = ReportReason.AI_HARMFUL))
                .notPaywalled(HttpStatusCode.Created, "reportar el informe de la IA")

            client.post("/v1/blocks/${w.id("caro")}") { auth("ana") }.notPaywalled(HttpStatusCode.NoContent, "bloquear por uuid")
            client.post("/v1/blocks/$franAnon") { auth("ana") }.notPaywalled(HttpStatusCode.NoContent, "bloquear por anonId")
            client.get("/v1/blocks") { auth("ana") }.notPaywalled(HttpStatusCode.OK, "lista de bloqueados")
            client.delete("/v1/blocks/${w.id("caro")}") { auth("ana") }.notPaywalled(HttpStatusCode.NoContent, "desbloquear")
        }
        w.safetyRepo.allReports shouldHaveSize 4
        w.safetyRepo.allBlocks shouldBe setOf(w.id("ana") to w.id("fran"))
    }
})
