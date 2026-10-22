package me.santiagoreina.parcheu.api.graph

import com.google.auth.oauth2.GoogleCredentials
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.santiagoreina.parcheu.api.platform.config.Env
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * `anonId = HMAC-SHA256(clave, viewerId || userId)` (arquitectura.md §6.1): opaco, estable para el
 * mismo observador y distinto entre observadores, así que no permite cruzar identidades. Se
 * devuelve como `a_` + 22 caracteres base64url (128 bits).
 */
class AnonIdSigner(private val key: () -> ByteArray) {
    fun anonId(viewer: UUID, subject: UUID): String {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(key(), ALGORITHM))
        val input = ByteBuffer.allocate(32)
            .putLong(viewer.mostSignificantBits).putLong(viewer.leastSignificantBits)
            .putLong(subject.mostSignificantBits).putLong(subject.leastSignificantBits)
            .array()
        val digest = mac.doFinal(input).copyOf(16)
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    companion object {
        const val PREFIX = "a_"
        private const val ALGORITHM = "HmacSHA256"

        /** Clave fija solo para `ENV=local` y pruebas: nunca se usa en dev ni en prod. */
        val LOCAL_KEY: ByteArray = "parcheu-local-anon-key-no-usar-en-la-nube".toByteArray()

        fun forTests(): AnonIdSigner = AnonIdSigner { LOCAL_KEY }

        /**
         * Origen de la clave según el entorno:
         * - `HMAC_ANON_KEY`: el valor directo (desarrollo local o CI).
         * - `SECRET_HMAC_ANON_KEY`: nombre del secreto (`projects/<p>/secrets/hmac-anon-key`, lo define E0.5);
         *   se lee la versión `latest` con la API de Secret Manager en el primer uso y se guarda en memoria,
         *   para que el servicio arranque aunque el secreto aún no tenga valor.
         * - nada: clave local fija si `ENV=local`; en dev o prod falla al primer uso (nunca una clave débil).
         */
        fun fromEnv(env: Env, vars: Map<String, String> = System.getenv()): AnonIdSigner {
            val direct = vars["HMAC_ANON_KEY"]?.trim()?.takeIf { it.isNotEmpty() }
            if (direct != null) return AnonIdSigner { direct.toByteArray() }
            val secret = vars["SECRET_HMAC_ANON_KEY"]?.trim()?.takeIf { it.isNotEmpty() }
            if (secret != null) {
                val cached = lazy(LazyThreadSafetyMode.SYNCHRONIZED) { SecretManagerReader.latest(secret) }
                return AnonIdSigner { cached.value }
            }
            if (env == Env.LOCAL) return forTests()
            return AnonIdSigner { error("Falta la clave de anonId: define SECRET_HMAC_ANON_KEY (o HMAC_ANON_KEY)") }
        }
    }
}

/** Lectura mínima de Secret Manager por REST con las credenciales por defecto (sin otra dependencia). */
internal object SecretManagerReader {
    private val http = HttpClient.newHttpClient()

    fun latest(secretName: String): ByteArray {
        val credentials = GoogleCredentials.getApplicationDefault()
            .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))
        credentials.refreshIfExpired()
        val token = checkNotNull(credentials.accessToken) { "Sin token de acceso para Secret Manager" }.tokenValue
        val name = secretName.trimEnd('/').let { if ("/versions/" in it) it else "$it/versions/latest" }
        val request = HttpRequest.newBuilder(URI.create("https://secretmanager.googleapis.com/v1/$name:access"))
            .header("Authorization", "Bearer $token")
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) { "Secret Manager respondió ${response.statusCode()} al leer $name" }
        val data = Json.parseToJsonElement(response.body()).jsonObject["payload"]!!.jsonObject["data"]!!.jsonPrimitive.content
        val value = Base64.getDecoder().decode(data).decodeToString().trim()
        check(value.isNotEmpty()) { "El secreto $name está vacío" }
        return value.toByteArray()
    }
}
