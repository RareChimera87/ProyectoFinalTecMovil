package me.santiagoreina.parcheu.data.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.ClearCredentialException
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Borra el token FCM del dispositivo en el servidor (`DELETE /v1/me/fcm-token`). Falla en silencio. */
fun interface PushUnregistration {
    suspend fun unregister()
}

/** Termina Firestore y borra su caché en disco (`terminate()` + `clearPersistence()`). */
fun interface FirestoreSessionReset {
    suspend fun reset()
}

/** Cierra la sesión de Firebase Auth y borra el estado de Credential Manager. */
fun interface PlatformSignOut {
    suspend fun signOut()
}

/**
 * Cierre de sesión (E8.2), en este orden:
 * 1. `DELETE /v1/me/fcm-token`: necesita la sesión viva, por eso va primero.
 * 2. Borrado de datos locales de la persona ([SessionCleaner]: último grafo, progreso de B1...). El tema
 *    no es de la sesión y no se toca.
 * 3. Firestore: `terminate()` y `clearPersistence()`, para que la caché de chats y simulaciones no pase a
 *    la siguiente cuenta del teléfono.
 * 4. `FirebaseAuth.signOut()` y `CredentialManager.clearCredentialState()`.
 * Cada paso falla en silencio: un error de red o de caché no debe dejar la sesión abierta. Corre sin poder
 * cancelarse, porque la pantalla que lo pide navega a A4 y se destruye mientras termina.
 *
 * [inProgress] queda en `true` desde antes del paso 1 hasta el final: mientras tanto la sesión ya se da por
 * cerrada (`AuthRepository.hasSession()` es `false`), así que el 401 de `DELETE /v1/me/fcm-token` tras
 * borrar la cuenta no llega al bus global y A4 no vuelve a llamar a `POST /v1/session`.
 */
class SignOutFlow(
    private val push: PushUnregistration,
    private val cleaners: List<SessionCleaner>,
    private val firestore: FirestoreSessionReset,
    private val platform: PlatformSignOut
) {
    private val running = AtomicInteger(0)

    /** `true` mientras algún cierre de sesión está en curso (se marca antes de suspender por primera vez). */
    val inProgress: Boolean get() = running.get() > 0

    suspend operator fun invoke() {
        running.incrementAndGet()
        try {
            withContext(NonCancellable) {
                quietly { push.unregister() }
                cleaners.forEach { cleaner -> quietly { cleaner.clear() } }
                quietly { firestore.reset() }
                platform.signOut()
            }
        } finally {
            running.decrementAndGet()
        }
    }

    private suspend fun quietly(step: suspend () -> Unit) {
        try {
            step()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Ver la documentación de la clase.
        }
    }
}

/**
 * Cierre de sesión que no depende de la pantalla que lo pide (lo usa `GlobalErrorHandler`): corre en
 * [scope], de la app, y arranca sin despachar, así que al volver [start] la sesión ya figura como cerrada y
 * se puede navegar a A4 enseguida, sin esperar el `DELETE /v1/me/fcm-token` ni Firestore.
 */
class BackgroundSignOut(private val auth: AuthRepository, private val scope: CoroutineScope) {
    fun start() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { auth.signOut() }
    }
}

/** [FirestoreSessionReset] real. Sin `FirebaseApp` (CI sin google-services.json) no hay nada que borrar. */
class FirebaseFirestoreSessionReset(private val appContext: Context) : FirestoreSessionReset {
    override suspend fun reset() {
        if (FirebaseApp.getApps(appContext).isEmpty()) return
        val firestore = FirebaseFirestore.getInstance()
        // clearPersistence solo funciona con el cliente terminado; getInstance() devuelve uno nuevo después.
        firestore.terminate().await()
        firestore.clearPersistence().await()
    }
}

/** [PlatformSignOut] real: `FirebaseAuth.signOut()` y `clearCredentialState()` con el Context de la app. */
class FirebasePlatformSignOut(private val source: FirebaseAuthSource) : PlatformSignOut {
    override suspend fun signOut() {
        source.authOrNull()?.signOut()
        try {
            CredentialManager.create(source.applicationContext())
                .clearCredentialState(ClearCredentialStateRequest())
        } catch (_: ClearCredentialException) {
            // Sin proveedor de credenciales (emulador sin Play Services): no hay estado que borrar.
        }
    }
}
