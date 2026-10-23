package me.santiagoreina.parcheu.api.billing

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Base64

/*
 * Real-time developer notifications de Google Play (ADR-0013). Llegan por la suscripción push
 * `play-rtdn-push` de Pub/Sub con un token OIDC de `sa-tasks`. No son DTOs del contrato: el formato
 * lo define Google.
 */

/** Sobre del push de Pub/Sub: `{"message": {"data": <base64>, "messageId": "..."}, "subscription": "..."}`. */
@Serializable
data class PubSubPush(
    val message: PubSubMessage? = null,
    val subscription: String? = null,
)

@Serializable
data class PubSubMessage(
    val data: String? = null,
    val messageId: String? = null,
    /** Pub/Sub manda el id en las dos grafías. */
    @SerialName("message_id") val messageIdSnake: String? = null,
    val publishTime: String? = null,
) {
    val id: String? get() = (messageId ?: messageIdSnake)?.takeIf { it.isNotBlank() }
}

/** `DeveloperNotification` decodificada del campo `data`. */
@Serializable
data class DeveloperNotification(
    val version: String? = null,
    val packageName: String? = null,
    val eventTimeMillis: String? = null,
    val subscriptionNotification: SubscriptionNotification? = null,
    val testNotification: JsonObject? = null,
)

@Serializable
data class SubscriptionNotification(
    val version: String? = null,
    val notificationType: Int,
    val purchaseToken: String,
    val subscriptionId: String? = null,
)

/** Tipos de `SubscriptionNotification.notificationType` que el servidor trata aparte. */
object RtdnTypes {
    const val SUBSCRIPTION_PURCHASED = 4
    /** Reembolso o revocación: se quita Plus de inmediato, aunque Play diga que aún no vence. */
    const val SUBSCRIPTION_REVOKED = 12
}

internal val RtdnJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/** Notificación ya decodificada: [raw] es el JSON tal cual llegó (se guarda en `purchase_events.payload`). */
data class DecodedRtdn(val messageId: String, val raw: JsonElement, val notification: DeveloperNotification?)

/**
 * Decodifica el sobre. Falla con [IllegalArgumentException] si no es un push de Pub/Sub (sin `message`
 * o sin id). Si el `data` no es una notificación legible devuelve `notification = null`: se guarda y se
 * ignora, porque reintentarla no la arreglaría.
 */
internal fun decodeRtdn(body: String): DecodedRtdn {
    val push = runCatching { RtdnJson.decodeFromString(PubSubPush.serializer(), body) }
        .getOrElse { throw IllegalArgumentException("El cuerpo no es un push de Pub/Sub") }
    val message = requireNotNull(push.message) { "El push de Pub/Sub no trae message" }
    val id = requireNotNull(message.id) { "El mensaje de Pub/Sub no trae messageId" }
    val raw = message.data?.let { data ->
        runCatching { RtdnJson.parseToJsonElement(String(Base64.getDecoder().decode(data.trim()), Charsets.UTF_8)) }.getOrNull()
    } ?: JsonObject(mapOf("unreadable" to JsonPrimitive(true)))
    val notification = runCatching { RtdnJson.decodeFromJsonElement(DeveloperNotification.serializer(), raw) }.getOrNull()
    return DecodedRtdn(id, raw, notification)
}
