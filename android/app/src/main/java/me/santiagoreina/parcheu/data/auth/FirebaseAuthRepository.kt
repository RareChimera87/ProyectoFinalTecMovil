package me.santiagoreina.parcheu.data.auth

import android.annotation.SuppressLint
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await
import me.santiagoreina.parcheu.contract.AcceptTerms
import me.santiagoreina.parcheu.contract.SessionDto
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.core.network.apiCall

/**
 * Da acceso a [FirebaseAuth] solo si Firebase está inicializado. Sin `google-services.json` (CI sin
 * secreto, pruebas) no hay `FirebaseApp` y la app se comporta como si no hubiera sesión.
 */
class FirebaseAuthSource(private val appContext: Context) {
    fun applicationContext(): Context = appContext

    fun authOrNull(): FirebaseAuth? =
        if (FirebaseApp.getApps(appContext).isEmpty()) null else FirebaseAuth.getInstance()

    /**
     * Client id web de OAuth (`default_web_client_id`), que genera el plugin de google-services cuando el
     * proveedor Google está activo en Firebase Auth. Se busca por nombre porque mientras el proveedor no esté
     * activo el recurso no existe y una referencia a `R.string` no compilaría (docs/pendientes-humanos.md, E0.8).
     */
    @SuppressLint("DiscouragedApi")
    fun webClientId(): String? {
        val id = appContext.resources.getIdentifier(
            "default_web_client_id",
            "string",
            appContext.packageName
        )
        return if (id == 0) null else appContext.getString(id).takeIf { it.isNotBlank() }
    }
}

/** Implementación real de [AuthRepository] (ADR-0021). */
class FirebaseAuthRepository(
    private val source: FirebaseAuthSource,
    private val api: SessionApi,
    private val errorMapper: ApiErrorMapper,
    private val cleaners: List<SessionCleaner> = emptyList()
) : AuthRepository {
    override fun hasSession(): Boolean = source.authOrNull()?.currentUser != null

    override suspend fun signInWithGoogle(activityContext: Context): SignInResult {
        val auth = source.authOrNull() ?: return SignInResult.Failure(NOT_CONFIGURED)
        val webClientId = source.webClientId() ?: return SignInResult.Failure(NOT_CONFIGURED)
        val idToken =
            when (val token = requestGoogleIdToken(activityContext, webClientId)) {
                is TokenResult.Token -> token.value
                is TokenResult.Done -> return token.result
            }
        try {
            auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: FirebaseNetworkException) {
            return SignInResult.Failure(AppError.Network)
        } catch (e: FirebaseAuthException) {
            return SignInResult.Failure(AppError.Unknown(message = e.errorCode))
        } catch (e: FirebaseException) {
            // FirebaseTooManyRequestsException, "internal error" genérico (CONFIGURATION_NOT_FOUND), etc.
            return SignInResult.Failure(AppError.Unknown(message = e.message))
        }
        return when (val session = refreshSession()) {
            is ApiResult.Success -> SignInResult.Success(session.value)
            is ApiResult.Failure -> SignInResult.Failure(session.error)
        }
    }

    override suspend fun refreshSession(): ApiResult<SessionDto> = apiCall(errorMapper) {
        api.session()
    }

    override suspend fun acceptTerms(textVersion: String): ApiResult<Unit> = apiCall(errorMapper) {
        api.acceptTerms(AcceptTerms(textVersion))
        Unit
    }

    override suspend fun signOut() {
        // Los datos locales de la persona se borran siempre, aunque Credential Manager falle después.
        cleaners.forEach { cleaner ->
            try {
                cleaner.clear()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Un fallo al borrar una caché no debe impedir cerrar la sesión.
            }
        }
        source.authOrNull()?.signOut()
        // clearCredentialState necesita un Context; el de la aplicación basta para borrar la selección.
        try {
            CredentialManager.create(
                source.applicationContext()
            ).clearCredentialState(ClearCredentialStateRequest())
        } catch (_: ClearCredentialException) {
            // Sin proveedor de credenciales (emulador sin Play Services): no hay estado que borrar.
        }
    }

    private suspend fun requestGoogleIdToken(context: Context, webClientId: String): TokenResult {
        val option =
            GetGoogleIdOption
                .Builder()
                .setServerClientId(webClientId)
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
                .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val response =
            try {
                CredentialManager.create(context).getCredential(context, request)
            } catch (e: CancellationException) {
                throw e
            } catch (_: GetCredentialCancellationException) {
                return TokenResult.Done(SignInResult.Cancelled)
            } catch (_: NoCredentialException) {
                return TokenResult.Done(SignInResult.Failure(NO_GOOGLE_ACCOUNT))
            } catch (_: GetCredentialInterruptedException) {
                return TokenResult.Done(SignInResult.Failure(AppError.Network))
            } catch (e: GetCredentialException) {
                return TokenResult.Done(SignInResult.Failure(AppError.Unknown(message = e.type)))
            }
        val credential = response.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return TokenResult.Done(
                SignInResult.Failure(AppError.Unknown(message = "credencial inesperada"))
            )
        }
        return try {
            TokenResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
        } catch (_: GoogleIdTokenParsingException) {
            TokenResult.Done(
                SignInResult.Failure(AppError.Unknown(message = "token de Google ilegible"))
            )
        }
    }

    private sealed interface TokenResult {
        data class Token(val value: String) : TokenResult

        data class Done(val result: SignInResult) : TokenResult
    }

    companion object {
        /** Firebase sin inicializar o proveedor Google sin activar en el proyecto (falta el client id web). */
        val NOT_CONFIGURED = AppError.Unknown(message = SignInErrors.NOT_CONFIGURED)

        /** El dispositivo no tiene cuentas de Google. */
        val NO_GOOGLE_ACCOUNT = AppError.Unknown(message = SignInErrors.NO_GOOGLE_ACCOUNT)
    }
}

/** Mensajes técnicos de [AppError.Unknown] que la UI reconoce para mostrar un texto propio. */
object SignInErrors {
    const val NOT_CONFIGURED = "login-no-configurado"
    const val NO_GOOGLE_ACCOUNT = "sin-cuenta-google"
}
