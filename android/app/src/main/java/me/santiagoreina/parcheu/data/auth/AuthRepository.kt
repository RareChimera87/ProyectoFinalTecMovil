package me.santiagoreina.parcheu.data.auth

import android.content.Context
import me.santiagoreina.parcheu.contract.SessionDto
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult

/** Resultado de "Continuar con Google" (A4). */
sealed interface SignInResult {
    /** Sesión de Firebase iniciada y `POST /v1/session` respondió. */
    data class Success(val session: SessionDto) : SignInResult

    /** La persona cerró el selector de cuentas: se vuelve a A4 sin mostrar error. */
    data object Cancelled : SignInResult

    data class Failure(val error: AppError) : SignInResult
}

/**
 * Sesión del usuario (ADR-0021): Credential Manager + `googleid` → `FirebaseAuth.signInWithCredential`
 * → `POST /v1/session`. La UI depende solo de esta interfaz; las pruebas usan `FakeAuthRepository`.
 */
interface AuthRepository {
    /**
     * `true` si hay un usuario de Firebase con sesión en el dispositivo y no se está cerrando ([signOut] en
     * curso cuenta como sesión cerrada desde su primer paso).
     */
    fun hasSession(): Boolean

    /**
     * Abre el selector de cuentas de Google sobre [activityContext] (debe ser una Activity: Credential
     * Manager muestra su hoja encima de ella) y completa el login con Firebase y la API. Si hay un [signOut]
     * en curso, espera a que termine antes de abrir el selector.
     */
    suspend fun signInWithGoogle(activityContext: Context): SignInResult

    /** `POST /v1/session` con la sesión ya existente (Splash y tras aceptar términos). */
    suspend fun refreshSession(): ApiResult<SessionDto>

    /** `POST /v1/me/terms` con la versión vigente del texto legal ([SessionDto.legalTextVersion]). */
    suspend fun acceptTerms(textVersion: String): ApiResult<Unit>

    /**
     * Cierra la sesión (E8.2, [SignOutFlow]): borra el token FCM en el servidor, los datos locales de la
     * persona, la caché de Firestore y la sesión de Firebase y de Credential Manager.
     */
    suspend fun signOut()
}

/** Borra datos locales de la persona al cerrar sesión (caché del grafo, etc.). Cada feature aporta el suyo. */
fun interface SessionCleaner {
    suspend fun clear()
}
