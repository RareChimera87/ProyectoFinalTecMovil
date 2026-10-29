package me.santiagoreina.parcheu.push

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await
import me.santiagoreina.parcheu.contract.FcmToken
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.core.network.apiCall
import me.santiagoreina.parcheu.data.auth.SessionCleaner

/** Dónde se guarda el último token que FCM entregó y el que el backend ya conoce. */
interface PushTokenStore {
    var lastToken: String?
    var registeredToken: String?

    /** Cuenta para la que se registró [registeredToken]; si la cuenta cambia, hay que registrar de nuevo. */
    var registeredUser: String?

    /** Olvida todo (cierre de sesión): ni el token ni la cuenta para la que se registró quedan en disco. */
    fun clear()
}

/**
 * [SessionCleaner] del push (A-B3): `SignOutFlow` ya llamó a [PushTokenRegistrar.unregister] (que necesita
 * `lastToken` y por eso corre antes de los limpiadores); después no debe quedar el token ni la cuenta.
 */
fun pushTokenCleaner(store: PushTokenStore) = SessionCleaner { store.clear() }

class SharedPrefsPushTokenStore(context: Context) : PushTokenStore {
    private val prefs = context.applicationContext.getSharedPreferences(
        "push",
        Context.MODE_PRIVATE
    )

    override var lastToken: String?
        get() = prefs.getString("last_token", null)
        set(value) = prefs.edit().putString("last_token", value).apply()

    override var registeredToken: String?
        get() = prefs.getString("registered_token", null)
        set(value) = prefs.edit().putString("registered_token", value).apply()

    override var registeredUser: String?
        get() = prefs.getString("registered_user", null)
        set(value) = prefs.edit().putString("registered_user", value).apply()

    override fun clear() {
        // No se vacía el archivo: `permission_asked` (NotificationPermission) comparte "push" y debe sobrevivir.
        prefs.edit().remove(
            "last_token"
        ).remove("registered_token").remove("registered_user").apply()
    }
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
    private val currentUser: suspend () -> String? = { null }
) {
    /** Llega un token nuevo: se recuerda y se intenta registrar. */
    suspend fun onNewToken(token: String): Boolean {
        store.lastToken = token
        forgetRegistration()
        return register(token)
    }

    /**
     * Registra el token vigente si el backend aún no lo tiene para la cuenta con sesión. El registro
     * recuerda la cuenta: si otra persona inicia sesión en el mismo teléfono (la sesión anterior terminó
     * sin [unregister]), el token se registra de nuevo y el backend lo reasigna. Sin sesión no hace nada.
     */
    suspend fun sync(): Boolean {
        val user = currentUser() ?: return false
        val token = store.lastToken ?: currentToken()?.also { store.lastToken = it } ?: return false
        if (store.registeredToken == token && store.registeredUser == user) return true
        return register(token)
    }

    /** Borra el token del backend (cierre de sesión). Aunque falle, se olvida el registro local. */
    suspend fun unregister(): Boolean {
        val token = store.lastToken ?: return true
        val result = apiCall(mapper) { api.unregister(FcmToken(token)) }
        forgetRegistration()
        return result is ApiResult.Success
    }

    private suspend fun register(token: String): Boolean {
        // Sin sesión la llamada sería un 401; sync() reintenta tras el login.
        val user = currentUser() ?: return false
        val result = apiCall(mapper) { api.register(FcmToken(token)) }
        if (result is ApiResult.Success) {
            store.registeredToken = token
            store.registeredUser = user
        }
        return result is ApiResult.Success
    }

    private fun forgetRegistration() {
        store.registeredToken = null
        store.registeredUser = null
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
