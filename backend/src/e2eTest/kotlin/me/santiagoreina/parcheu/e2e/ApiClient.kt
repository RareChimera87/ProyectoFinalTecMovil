package me.santiagoreina.parcheu.e2e

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import me.santiagoreina.parcheu.contract.ContractJson
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Respuesta cruda de la API. */
data class ApiResponse(val status: Int, val body: String) {
    val ok: Boolean get() = status in 200..299

    inline fun <reified T> decode(): T = ContractJson.decodeFromString(serializer<T>(), body)
}

/** Un status inesperado de la API: lleva el cuerpo para que el fallo del smoke diga qué respondió el servidor. */
class ApiException(val method: String, val path: String, val status: Int, val body: String) :
    RuntimeException("$method $path respondió $status: ${body.take(500)}")

/**
 * Cliente mínimo de la API con `java.net.http` (sin dependencias nuevas) y el JSON del contrato compartido.
 * Las llamadas bloquean: el smoke es secuencial y cada paso espera su respuesta.
 */
class ApiClient(private val baseUrl: String, private val timeout: Duration = Duration.ofSeconds(60)) {
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()

    fun call(method: String, path: String, token: String? = null, body: String? = null): ApiResponse {
        val builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(timeout)
        if (token != null) builder.header("Authorization", "Bearer $token")
        if (body != null) builder.header("Content-Type", "application/json")
        builder.method(method, if (body == null) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(body))
        val response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        return ApiResponse(response.statusCode(), response.body().orEmpty())
    }

    /** Llama y exige 2xx; si no, lanza [ApiException]. */
    fun expectOk(method: String, path: String, token: String? = null, body: String? = null): ApiResponse =
        call(method, path, token, body).also { if (!it.ok) throw ApiException(method, path, it.status, it.body) }

    inline fun <reified T> get(path: String, token: String?): T = expectOk("GET", path, token).decode()

    inline fun <reified T> post(path: String, token: String?): T = expectOk("POST", path, token).decode()

    inline fun <reified B, reified T> post(path: String, token: String?, body: B): T =
        expectOk("POST", path, token, encode(body)).decode()

    inline fun <reified B, reified T> put(path: String, token: String?, body: B): T =
        expectOk("PUT", path, token, encode(body)).decode()

    inline fun <reified B> encode(body: B): String = ContractJson.encodeToString(serializer<B>() as KSerializer<B>, body)
}
