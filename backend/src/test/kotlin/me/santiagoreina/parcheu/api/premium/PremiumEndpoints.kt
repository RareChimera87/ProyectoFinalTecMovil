package me.santiagoreina.parcheu.api.premium

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.santiagoreina.parcheu.api.graph.AnonIdSigner
import me.santiagoreina.parcheu.api.notifications.center.NotificationCenterService
import me.santiagoreina.parcheu.contract.ApiError
import me.santiagoreina.parcheu.contract.Choice
import me.santiagoreina.parcheu.contract.ContractJson
import me.santiagoreina.parcheu.contract.Decision
import me.santiagoreina.parcheu.contract.Enqueue
import me.santiagoreina.parcheu.contract.ErrorCode
import me.santiagoreina.parcheu.contract.IntroRequest
import me.santiagoreina.parcheu.contract.Introduce
import me.santiagoreina.parcheu.contract.IntroductionDto
import me.santiagoreina.parcheu.contract.MatchChoice
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.StartSimulation
import me.santiagoreina.parcheu.contract.TicketDto
import java.util.UUID

/**
 * Un endpoint con contenido de ParcheU+ (función marcada con `@Premium(endpoint)` en main).
 *
 * - [hides]: lo que el servidor le oculta a un usuario gratis (campos del JSON o, entre corchetes,
 *   lo que no es un campo: una acción o un dato que solo vive en el servidor). Es el informe de la
 *   auditoría (tabla endpoint → campos ocultos del PR de E7.4).
 * - [freeGets]: qué recibe en su lugar un usuario gratis.
 * - [probe]: llama al endpoint como `ana` en un [PremiumWorld] y devuelve el subconjunto de [hides]
 *   que de verdad recibió. Gratis debe dar vacío y ParcheU+ exactamente [hides] (así la sonda no pasa
 *   en vacío si el mundo deja de tener contenido premium).
 */
class PremiumEndpoint(
    val endpoint: String,
    val hides: Set<String>,
    val freeGets: String,
    val probe: suspend PremiumProbe.() -> Set<String>,
)

/**
 * Lista declarativa de los endpoints premium (backlog E7.4). `PremiumEndpointsRegistryTest` falla si
 * una función `@Premium` de main no está aquí (o si aquí sobra uno sin marcar), y
 * `PremiumGatingContractTest` recorre la lista con un usuario gratis y con uno ParcheU+.
 *
 * Quedan fuera a propósito (no ocultan nada a gratis por plan): `POST /v1/simulations/{id}/report:unlock`
 * (gasta el informe completo del mes: 402 `QUOTA_EXCEEDED` sin cupo, nunca contenido premium gratis más
 * allá de ese cupo), `GET /v1/quota` y `GET /v1/billing/entitlement` (solo informan el plan).
 */
object PremiumEndpoints {
    private val REPORT_FIELDS = setOf("reasons", "frictions", "dimensions")

    val all: List<PremiumEndpoint> = listOf(
        PremiumEndpoint(
            endpoint = "GET /v1/simulations/{id}/report",
            hides = REPORT_FIELDS,
            freeGets = "puntaje, resumen, conteos y `locked = true` (puede gastar su informe del mes con :unlock)",
        ) {
            val json = client.get("/v1/simulations/${world.franSimulation}/report") { auth("ana") }.okJson()
            json.keys intersect REPORT_FIELDS
        },
        PremiumEndpoint(
            endpoint = "GET /v1/simulations/{id}/transcript",
            hides = setOf("scenarios"),
            freeGets = "402 PLUS_REQUIRED (salvo que haya desbloqueado ese informe)",
        ) {
            val response = client.get("/v1/simulations/${world.franSimulation}/transcript") { auth("ana") }
            response.plusRequiredOr { it.keys intersect setOf("scenarios") }
        },
        PremiumEndpoint(
            endpoint = "GET /v1/introductions/{id}",
            hides = REPORT_FIELDS.map { "report.$it" }.toSet(),
            freeGets = "el informe de la presentación con la misma regla que F7 (puntaje, resumen y conteos)",
        ) {
            val created = client.post("/v1/introductions") {
                auth("ana")
                contentType(ContentType.Application.Json)
                setBody(Introduce(Mode.AMISTAD, world.id("gabi").toString(), world.id("hugo").toString()))
            }
            check(created.status.isSuccess()) { "POST /v1/introductions → ${created.status}: ${created.bodyAsText()}" }
            val id = created.body<IntroductionDto>().id
            val report = client.get("/v1/introductions/$id") { auth("ana") }.okJson().getValue("report").jsonObject
            (report.keys intersect REPORT_FIELDS).map { "report.$it" }.toSet()
        },
        PremiumEndpoint(
            endpoint = "POST /v1/simulations",
            hides = setOf("[escenarios premium]"),
            freeGets = "simulación solo con escenarios básicos del modo",
        ) {
            // caro es ParcheU+ en los dos mundos: con ana gratis, el plan que cuenta es el de ana, no el de él.
            world.sim.plus(world.id("caro"))
            // Cuatro simulaciones (gratis tiene 5 por semana y ya gastó una con fran). Con ParcheU+ el pool
            // tiene 3 básicos y 14 premium: que las cuatro salgan solo con básicos es ~1e-11.
            val used = listOf("caro", "dani", "eva", "gabi").flatMap { other ->
                val response = client.post("/v1/simulations") {
                    auth("ana")
                    contentType(ContentType.Application.Json)
                    setBody(StartSimulation(world.id(other).toString(), Mode.AMISTAD))
                }
                check(response.status == HttpStatusCode.Accepted) { "POST /v1/simulations → ${response.status}: ${response.bodyAsText()}" }
                val id = response.okJson(HttpStatusCode.Accepted).getValue("id").jsonPrimitive.content
                world.sim.repo.find(UUID.fromString(id))!!.transcript.scenarios.map { it.scenarioId }
            }
            if (used.any { it in world.premiumScenarioIds }) setOf("[escenarios premium]") else emptySet()
        },
        PremiumEndpoint(
            endpoint = "GET /v1/graph/fof",
            hides = setOf("items"),
            freeGets = "`count`",
        ) {
            client.get("/v1/graph/fof") { auth("ana") }.okJson().keys intersect setOf("items")
        },
        PremiumEndpoint(
            endpoint = "GET /v1/communities",
            hides = setOf("items"),
            freeGets = "`count`",
        ) {
            client.get("/v1/communities") { auth("ana") }.okJson().keys intersect setOf("items")
        },
        PremiumEndpoint(
            endpoint = "GET /v1/graph",
            hides = setOf("[filtro community]"),
            freeGets = "402 PLUS_REQUIRED al filtrar por comunidad",
        ) {
            val communityId = runBlocking { world.communities.repository.ofUser(world.id("ana")) }.first().id
            val response = client.get("/v1/graph?community=$communityId") { auth("ana") }
            if (response.isPlusRequired()) emptySet()
            else if (response.okJson().getValue("nodes").jsonArray.isNotEmpty()) setOf("[filtro community]") else emptySet()
        },
        PremiumEndpoint(
            endpoint = "GET /v1/graph/history",
            hides = setOf("months", "moments"),
            freeGets = "402 PLUS_REQUIRED",
        ) {
            client.get("/v1/graph/history") { auth("ana") }.plusRequiredOr { it.keys intersect setOf("months", "moments") }
        },
        PremiumEndpoint(
            endpoint = "GET /v1/connect-requests",
            hides = setOf("items"),
            freeGets = "`count` (sin saber quién)",
        ) {
            val decided = client.post("/v1/simulations/${world.franSimulation}/decision") {
                auth("fran")
                contentType(ContentType.Application.Json)
                setBody(Decision(connect = true))
            }
            check(decided.status == HttpStatusCode.OK) { "decisión de fran → ${decided.status}" }
            val json = client.get("/v1/connect-requests") { auth("ana") }.okJson()
            check(json.getValue("count").jsonPrimitive.int == 1) { "se esperaba 1 solicitud: $json" }
            json.keys intersect setOf("items")
        },
        PremiumEndpoint(
            endpoint = "GET /v1/scenarios",
            hides = setOf("[escenarios premium sin locked]"),
            freeGets = "los premium salen con `locked = true`",
        ) {
            val list = client.get("/v1/scenarios?mode=AMISTAD") { auth("ana") }.okJsonArray()
            val premium = list.map { it.jsonObject }.filter { !it.getValue("isBasic").jsonPrimitive.boolean }
            check(premium.isNotEmpty()) { "el mundo debe tener escenarios premium" }
            if (premium.any { !it.getValue("locked").jsonPrimitive.boolean }) setOf("[escenarios premium sin locked]") else emptySet()
        },
        PremiumEndpoint(
            endpoint = "POST /v1/match/queue",
            hides = setOf("[prioridad en la fila]"),
            freeGets = "ticket con `priority = 0` en `match_queue` (la prioridad nunca viaja en el DTO)",
        ) {
            val response = client.post("/v1/match/queue") {
                auth("ana")
                contentType(ContentType.Application.Json)
                setBody(Enqueue(Mode.AMISTAD))
            }
            check(response.status == HttpStatusCode.OK) { "POST /v1/match/queue → ${response.status}: ${response.bodyAsText()}" }
            check("priority" !in response.okJson().keys) { "la prioridad no debe salir en el TicketDto" }
            val ticket = world.matchRepo.ticket(world.id("ana"))!!
            if (ticket.priority > 0) setOf("[prioridad en la fila]") else emptySet()
        },
        PremiumEndpoint(
            endpoint = "POST /v1/matches/{id}/choice",
            hides = setOf("[escenario premium en el chat]"),
            freeGets = "chat SCENARIO con un escenario básico (premium solo si los dos son ParcheU+)",
        ) {
            // ivan siempre es ParcheU+: con ana gratis el chat no puede ser premium (cuenta el plan de ambos).
            world.sim.plus(world.id("ivan"))
            suspend fun enqueue(uid: String) = client.post("/v1/match/queue") {
                auth(uid)
                contentType(ContentType.Application.Json)
                setBody(Enqueue(Mode.AMISTAD))
            }.body<TicketDto>()
            enqueue("ivan")
            val matchId = checkNotNull(enqueue("ana").matchId) { "ana e ivan debían emparejarse" }
            listOf("ana", "ivan").forEach { uid ->
                val chosen = client.post("/v1/matches/$matchId/choice") {
                    auth(uid)
                    contentType(ContentType.Application.Json)
                    setBody(Choice(MatchChoice.SCENARIO))
                }
                check(chosen.status == HttpStatusCode.OK) { "choice de $uid → ${chosen.status}: ${chosen.bodyAsText()}" }
            }
            val allowed = world.allowPremiumCalls.toList()
            check(allowed.isNotEmpty()) { "el match debía crear el chat SCENARIO" }
            if (allowed.any { it }) setOf("[escenario premium en el chat]") else emptySet()
        },
        PremiumEndpoint(
            endpoint = "GET /v1/notifications",
            hides = setOf("actor", "route", "body"),
            freeGets = "aviso `locked = true` sin actor ni ruta y con el cuerpo genérico",
        ) {
            val item = client.get("/v1/notifications") { auth("ana") }.okJson().getValue("today").jsonArray.single().jsonObject
            // Invariante en ambos planes (no es un campo que ParcheU+ vea distinto): el título viaja a gratis,
            // así que un aviso de terceros no puede traer el nombre del actor sembrado (beto).
            check(item["title"]?.jsonPrimitive?.content?.contains("beto", ignoreCase = true) != true) { "el título del aviso filtra al actor" }
            buildSet {
                if ("actor" in item) add("actor")
                if ("route" in item) add("route")
                if (item.getValue("body").jsonPrimitive.content != NotificationCenterService.LOCKED_BODY) add("body")
            }
        },
        PremiumEndpoint(
            endpoint = "POST /v1/introductions/request",
            hides = setOf("[pedir presentación]"),
            freeGets = "402 PLUS_REQUIRED",
        ) {
            val anonId = AnonIdSigner.forTests().anonId(world.id("ana"), world.id("eva"))
            val response = client.post("/v1/introductions/request") {
                auth("ana")
                contentType(ContentType.Application.Json)
                setBody(IntroRequest(anonId))
            }
            when {
                response.status == HttpStatusCode.Accepted -> setOf("[pedir presentación]")
                response.isPlusRequired() -> emptySet()
                else -> error("POST /v1/introductions/request → ${response.status}: ${response.bodyAsText()}")
            }
        },
        PremiumEndpoint(
            endpoint = "GET /v1/home",
            hides = setOf("suggestions[].mutualCount"),
            freeGets = "sugerencias sin el conteo de amigos en común",
        ) {
            val suggestions = client.get("/v1/home") { auth("ana") }.okJson().getValue("suggestions").jsonArray.map { it.jsonObject }
            check(suggestions.isNotEmpty()) { "el mundo debe tener sugerencias en Inicio" }
            if (suggestions.any { "mutualCount" in it }) setOf("suggestions[].mutualCount") else emptySet()
        },
    )

    val endpoints: Set<String> get() = all.map { it.endpoint }.toSet()
}

private suspend fun HttpResponse.okJson(expected: HttpStatusCode = HttpStatusCode.OK): JsonObject {
    val text = bodyAsText()
    check(status == expected) { "${call.request.method.value} ${call.request.url.encodedPath} → $status: $text" }
    return ContractJson.parseToJsonElement(text).jsonObject
}

private suspend fun HttpResponse.okJsonArray(): JsonArray {
    val text = bodyAsText()
    check(status == HttpStatusCode.OK) { "${call.request.url.encodedPath} → $status: $text" }
    return ContractJson.parseToJsonElement(text).jsonArray
}

private suspend fun HttpResponse.isPlusRequired(): Boolean =
    status == HttpStatusCode.PaymentRequired && ContractJson.decodeFromString(ApiError.serializer(), bodyAsText()).code == ErrorCode.PLUS_REQUIRED

/** 402 `PLUS_REQUIRED` → nada expuesto; 200 → lo que [exposed] encuentre en el cuerpo. */
private suspend fun HttpResponse.plusRequiredOr(exposed: (JsonObject) -> Set<String>): Set<String> =
    if (isPlusRequired()) emptySet() else exposed(okJson())
