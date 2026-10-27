package me.santiagoreina.parcheu.data.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.ClearCredentialException
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
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
 */
class SignOutFlow(
    private val push: PushUnregistration,
    private val cleaners: List<SessionCleaner>,
    private val firestore: FirestoreSessionReset,
    private val platform: PlatformSignOut
) {
    suspend operator fun invoke() {
        withContext(NonCancellable) {
            quietly { push.unregister() }
            cleaners.forEach { cleaner -> quietly { cleaner.clear() } }
            quietly { firestore.reset() }
            platform.signOut()
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
