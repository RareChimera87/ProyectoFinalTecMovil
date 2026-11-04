package me.santiagoreina.parcheu.e2e

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/** Lee los mensajes visibles de un chat tal como los vería la persona en la app. */
interface ChatReader {
    /** Textos de `chats/{chatId}/messages` visibles para quien presenta [idToken]. */
    fun visibleTexts(chatId: String, idToken: String): List<String>
}

/**
 * Firestore real por REST con el ID token de la persona: así el smoke comprueba también que las reglas dejan
 * leer el mensaje a un miembro del chat (la app lo lee igual, con el SDK del cliente).
 */
class FirestoreChatReader(private val projectId: String) : ChatReader {
    private val http = HttpClient.newHttpClient()

    override fun visibleTexts(chatId: String, idToken: String): List<String> {
        val url = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/chats/$chatId/messages?pageSize=100"
        val request = HttpRequest.newBuilder(URI.create(url)).header("Authorization", "Bearer $idToken").GET().build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        // 403/404 mientras el espejo no llega o las reglas aún no dejan leer: el sondeo reintenta hasta el plazo.
        if (response.statusCode() != 200) throw ApiException("GET", "firestore:chats/$chatId/messages", response.statusCode(), response.body())
        return parseVisibleTexts(response.body())
    }

    companion object {
        /** Textos de los documentos de la respuesta REST de `listDocuments`, sin los mensajes ocultos. */
        fun parseVisibleTexts(json: String): List<String> {
            val root: JsonObject = Json.parseToJsonElement(json).jsonObject
            val documents = root["documents"]?.jsonArray ?: return emptyList()
            return documents.mapNotNull { doc ->
                val fields = doc.jsonObject["fields"]?.jsonObject ?: return@mapNotNull null
                val hidden = fields["hidden"]?.jsonObject?.get("booleanValue")?.jsonPrimitive?.boolean ?: false
                if (hidden) null else fields["text"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content
            }
        }
    }
}
