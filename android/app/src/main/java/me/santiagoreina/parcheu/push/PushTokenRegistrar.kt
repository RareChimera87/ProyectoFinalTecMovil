package me.santiagoreina.parcheu.push

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await
import me.santiagoreina.parcheu.contract.FcmToken
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.core.network.apiCall

/** Dónde se guarda el último token que FCM entregó y el que el backend ya conoce. */
interface PushTokenStore {
    var lastToken: String?
    var registeredToken: String?
}

class SharedPrefsPushTokenStore(context: Context) : PushTokenStore {
    private val prefs = context.applicationContext.getSharedPreferences("push", Context.MODE_PRIVATE)

    override var lastToken: String?
        get() = prefs.getString("last_token", null)
        set(value) = prefs.edit().putString("last_token", value).apply()

    override var registeredToken: String?
        get() = prefs.getString("registered_token", null)
        set(value) = prefs.edit().putString("registered_token", value).apply()
}

/**
 * Mantiene el token FCM del dispositivo registrado en el backend. `onNewToken` puede dispararse antes
 * del login (la llamada falla con 401), así que el token se guarda y [sync] se vuelve a llamar tras
 * iniciar sesión (E2.2) y al conceder el permiso. [unregister] la usa el cierre de sesión (E8.2).
 */
class PushTokenRegistrar(
    private val api: PushApi,
    private val mapper: ApiErrorMapper,
    private val store: PushTokenStore,
    private val currentToken: suspend () -> String? = ::firebaseToken,
) {
    /** Llega un token nuevo: se recuerda y se intenta registrar. */
    suspend fun onNewToken(token: String): Boolean {
        store.lastToken = token
        store.registeredToken = null
        return register(token)
    }

    /** Registra el token vigente si el backend aún no lo tiene. `true` si quedó registrado. */
    suspend fun sync(): Boolean {
        val token = store.lastToken ?: currentToken()?.also { store.lastToken = it } ?: return false
        if (store.registeredToken == token) return true
        return register(token)
    }

    /** Borra el token del backend (cierre de sesión). Aunque falle, se olvida el registro local. */
    suspend fun unregister(): Boolean {
        val token = store.lastToken ?: return true
        val result = apiCall(mapper) { api.unregister(FcmToken(token)) }
        store.registeredToken = null
        return result is ApiResult.Success
    }

    private suspend fun register(token: String): Boolean {
        val result = apiCall(mapper) { api.register(FcmToken(token)) }
        if (result is ApiResult.Success) store.registeredToken = token
        return result is ApiResult.Success
    }
}

private suspend fun firebaseToken(): String? = try {
    FirebaseMessaging.getInstance().token.await()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    // Sin google-services.json (builds locales) FirebaseApp no está inicializado.
    null
}
