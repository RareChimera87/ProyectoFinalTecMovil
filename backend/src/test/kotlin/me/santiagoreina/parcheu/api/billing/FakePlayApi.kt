package me.santiagoreina.parcheu.api.billing

import com.google.api.client.http.LowLevelHttpRequest
import com.google.api.client.json.Json
import com.google.api.client.testing.http.MockHttpTransport
import com.google.api.client.testing.http.MockLowLevelHttpRequest
import com.google.api.client.testing.http.MockLowLevelHttpResponse
import java.net.URLDecoder
import java.util.UUID

/**
 * Fake de la API de Google Play Developer a nivel HTTP: [client] es el [GooglePlaySubscriptions] real
 * sobre un transporte que sirve las respuestas grabadas de `src/test/resources/billing/play/`. Así los
 * tests pasan por el mismo parseo y las mismas URL que en producción.
 *
 * `acknowledge` cambia la compra a `ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED`, como hace Play.
 */
class FakePlayApi(private val packageName: String = BillingService.DEFAULT_PACKAGE_NAME) {
    private val purchases = mutableMapOf<String, String>()
    private val acks = mutableMapOf<String, Int>()
    private val gets = mutableMapOf<String, Int>()

    /** Siguiente `subscriptionsv2.get` (o `acknowledge`) responde con este status de error. */
    var failNextWith: Int? = null

    val client: GooglePlaySubscriptions = GooglePlaySubscriptions.create(null, packageName, Transport())

    /** Registra la compra [token] con la respuesta grabada [fixture], hecha desde la cuenta de [owner]. */
    @Synchronized
    fun purchase(token: String, fixture: String, owner: UUID): FakePlayApi = apply {
        purchases[token] = recorded(fixture).replace("{{obfuscatedAccountId}}", obfuscatedAccountId(owner))
    }

    /** Registra [token] con un cuerpo ya armado (p. ej. una respuesta grabada con un campo agregado). */
    @Synchronized
    fun purchaseRaw(token: String, body: String, owner: UUID): FakePlayApi = apply {
        purchases[token] = body.replace("{{obfuscatedAccountId}}", obfuscatedAccountId(owner))
    }

    /** Cambia el estado de una compra ya registrada (lo que Play devolvería tras la notificación). */
    fun update(token: String, fixture: String, owner: UUID): FakePlayApi = purchase(token, fixture, owner)

    @Synchronized
    fun acknowledgeCalls(token: String): Int = acks[token] ?: 0

    @Synchronized
    fun getCalls(token: String): Int = gets[token] ?: 0

    @Synchronized
    private fun respond(method: String, url: String): MockLowLevelHttpResponse {
        failNextWith?.let { status ->
            failNextWith = null
            return json(status, """{"error":{"code":$status,"message":"Fallo simulado"}}""")
        }
        val path = url.substringAfter("/androidpublisher/v3/applications/").substringBefore('?')
        val app = path.substringBefore('/')
        if (app != packageName) return json(404, recorded("not-found"))
        val rest = path.substringAfter('/')
        return when {
            method == "GET" && rest.startsWith(GET_PREFIX) -> {
                val token = decode(rest.removePrefix(GET_PREFIX))
                gets[token] = (gets[token] ?: 0) + 1
                purchases[token]?.let { json(200, it) } ?: json(404, recorded("not-found"))
            }
            method == "POST" && rest.startsWith(ACK_PREFIX) && rest.endsWith(":acknowledge") -> {
                val token = decode(rest.removePrefix(ACK_PREFIX).substringAfter("/tokens/").removeSuffix(":acknowledge"))
                val body = purchases[token] ?: return json(404, recorded("not-found"))
                acks[token] = (acks[token] ?: 0) + 1
                purchases[token] = body.replace("ACKNOWLEDGEMENT_STATE_PENDING", "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED")
                MockLowLevelHttpResponse().setStatusCode(204)
            }
            else -> json(404, recorded("not-found"))
        }
    }

    private inner class Transport : MockHttpTransport() {
        override fun buildRequest(method: String, url: String): LowLevelHttpRequest = object : MockLowLevelHttpRequest(url) {
            override fun execute() = respond(method, url)
        }
    }

    companion object {
        private const val GET_PREFIX = "purchases/subscriptionsv2/tokens/"
        private const val ACK_PREFIX = "purchases/subscriptions/"

        fun recorded(name: String): String =
            checkNotNull(FakePlayApi::class.java.getResourceAsStream("/billing/play/$name.json")) { "Falta billing/play/$name.json" }
                .bufferedReader(Charsets.UTF_8).use { it.readText() }

        private fun decode(part: String) = URLDecoder.decode(part, Charsets.UTF_8)

        private fun json(status: Int, body: String) = MockLowLevelHttpResponse()
            .setStatusCode(status)
            .setContentType(Json.MEDIA_TYPE)
            .setContent(body)
    }
}
