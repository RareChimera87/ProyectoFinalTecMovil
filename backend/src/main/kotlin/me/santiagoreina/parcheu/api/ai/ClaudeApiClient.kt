package me.santiagoreina.parcheu.api.ai

import com.anthropic.backends.AnthropicBackend
import com.anthropic.client.okhttp.AnthropicOkHttpClientAsync
import me.santiagoreina.parcheu.api.graph.SecretManagerReader
import me.santiagoreina.parcheu.api.platform.config.Env
import java.time.Duration

/**
 * Claude por la API directa de Anthropic (`api.anthropic.com`) con clave, `AI_PROVIDER=anthropic`
 * (ADR-0008, enmienda del 12 nov 2026). Mismo SDK y misma lógica que [ClaudeVertexClient]
 * ([ClaudeMessagesClient]); solo cambia el backend: [AnthropicBackend] con la clave en vez
 * de [com.anthropic.vertex.backends.VertexBackend]. Los datos salen de GCP hacia Anthropic (ADR-0020).
 *
 * El backend se arma explícito con la clave para no depender de la cadena de credenciales por defecto del SDK
 * (variables `ANTHROPIC_*`, perfiles locales): solo se usa la clave del secreto. Nunca se llama a `fromEnv()`.
 *
 * Cabecera: la API autentica las claves con `x-api-key`. En `anthropic-java` 3.1.0 el backend aplica toda credencial
 * estática (también `apiKey`) como `Authorization: Bearer` salvo que la petición ya traiga `X-Api-Key`
 * (`StaticAuthHeaders`), así que se fija `X-Api-Key` en el cliente y el SDK ya no añade el `Bearer`.
 *
 * [baseUrl] solo lo cambian las pruebas (servidor HTTP falso); en producción es el del SDK.
 */
class ClaudeApiClient(
    apiKey: AnthropicApiKey,
    baseUrl: String? = null,
    timeout: Duration = ClaudeMessagesClient.TIMEOUT,
    maxRetries: Int = ClaudeMessagesClient.MAX_RETRIES,
) : ClaudeMessagesClient({
    val key = apiKey.value()
    AnthropicOkHttpClientAsync.builder()
        .backend(
            AnthropicBackend.builder()
                .apiKey(key)
                .apply { if (baseUrl != null) baseUrl(baseUrl) }
                .build(),
        )
        .putHeader("X-Api-Key", key)
        .timeout(timeout)
        .maxRetries(maxRetries)
        .build()
}) {
    override val provider = AiProvider.ANTHROPIC
}

/**
 * Clave de la API de Anthropic, resuelta en el primer uso y guardada en memoria. Nunca se imprime:
 * [toString] la oculta para que no acabe en un log ni en el `toString` de [AiConfig].
 */
class AnthropicApiKey private constructor(private val source: String, supplier: () -> String) {
    private val cached = lazy(LazyThreadSafetyMode.SYNCHRONIZED, supplier)

    /** La clave. Si viene de Secret Manager y la lectura falla, el siguiente uso lo reintenta. */
    fun value(): String = cached.value

    override fun toString(): String = "AnthropicApiKey($source, ***)"

    companion object {
        /**
         * Origen de la clave según el entorno:
         * - `SECRET_ANTHROPIC_API_KEY`: nombre del secreto (`projects/<p>/secrets/anthropic-api-key`, lo crea Terraform);
         *   se lee la versión `latest` en el primer uso, como `SECRET_HMAC_ANON_KEY`. Es la única vía con `ENV=dev/prod`.
         * - `ANTHROPIC_API_KEY`: la clave directa, solo con `ENV=local` (desarrollo; nunca en Cloud Run).
         * - nada: null; [AiConfig.fromEnv] hace fallar el arranque si `AI_PROVIDER=anthropic`.
         */
        fun fromEnv(env: Env, vars: Map<String, String>): AnthropicApiKey? {
            fun v(name: String): String? = vars[name]?.trim()?.takeIf { it.isNotEmpty() }
            val direct = v("ANTHROPIC_API_KEY")
            if (direct != null && env == Env.LOCAL) return AnthropicApiKey("ANTHROPIC_API_KEY") { direct }
            val secret = v("SECRET_ANTHROPIC_API_KEY") ?: return null
            return AnthropicApiKey("SECRET_ANTHROPIC_API_KEY") { SecretManagerReader.latest(secret).decodeToString() }
        }

        /** Solo para pruebas. */
        internal fun of(value: String): AnthropicApiKey = AnthropicApiKey("prueba") { value }
    }
}
