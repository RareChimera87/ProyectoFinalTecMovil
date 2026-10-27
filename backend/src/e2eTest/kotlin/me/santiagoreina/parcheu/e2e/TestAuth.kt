package me.santiagoreina.parcheu.e2e

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.santiagoreina.parcheu.api.platform.auth.FakeTokenVerifier
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/** Entrega el ID token de un usuario de prueba. */
interface TestAuth {
    /** `uid` del usuario de prueba; el servidor crea la cuenta con `is_test` en su primer `POST /v1/session`. */
    fun idToken(uid: String): String
}

/**
 * Real: Firebase Admin firma un token personalizado con el claim `is_test` y la API de Identity Toolkit lo canjea
 * por un ID token (el mismo que mandaría la app). El claim viaja en el ID token y el servidor marca `users.is_test`.
 * El claim solo lo puede fijar quien tiene credenciales del proyecto: un cliente normal no puede marcarse como prueba.
 */
class FirebaseTestAuth(
    private val projectId: String,
    private val apiKey: String,
    private val serviceAccount: String?,
    private val credentials: () -> GoogleCredentials = { GoogleCredentials.getApplicationDefault() },
) : TestAuth {
    private val http = HttpClient.newHttpClient()

    private val auth: FirebaseAuth by lazy {
        val app = FirebaseApp.getApps().firstOrNull { it.name == APP_NAME }
            ?: FirebaseApp.initializeApp(
                FirebaseOptions.builder()
                    .setCredentials(credentials())
                    .setProjectId(projectId)
                    // Con credenciales sin llave privada (WIF) los tokens se firman con IAM signBlob de esta cuenta.
                    .apply { serviceAccount?.let { setServiceAccountId(it) } }
                    .build(),
                APP_NAME,
            )
        FirebaseAuth.getInstance(app)
    }

    override fun idToken(uid: String): String {
        val custom = auth.createCustomToken(uid, mapOf("is_test" to true))
        val body = buildJsonObject {
            put("token", custom)
            put("returnSecureToken", true)
        }.toString()
        val request = HttpRequest.newBuilder(URI.create("$SIGN_IN?key=$apiKey"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) { "signInWithCustomToken respondió ${response.statusCode()}: ${response.body().take(300)}" }
        return parseIdToken(response.body())
    }

    companion object {
        private const val APP_NAME = "e2e-smoke"
        private const val SIGN_IN = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken"

        /** Extrae `idToken` de la respuesta de `accounts:signInWithCustomToken`. */
        fun parseIdToken(json: String): String {
            val root: JsonObject = Json.parseToJsonElement(json).jsonObject
            return requireNotNull(root["idToken"]?.jsonPrimitive?.content) { "La respuesta no trae idToken" }
        }
    }
}

/** Para el autotest local contra `ENV=local`: el servidor acepta `fake-<uid>` y trata `e2e-*` como cuenta de prueba. */
object FakeTestAuth : TestAuth {
    override fun idToken(uid: String): String = FakeTokenVerifier.tokenFor(uid)
}
