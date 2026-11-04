package me.santiagoreina.parcheu.data.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.contract.AcceptTerms
import me.santiagoreina.parcheu.contract.ConsentsDto
import me.santiagoreina.parcheu.contract.SessionDto
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * M1 de la ronda 2 de #109: el 401 abre A4 mientras el cierre de sesión sigue en segundo plano. Si la persona
 * vuelve a entrar antes de que acabe, el paso 4 del cierre viejo (`FirebaseAuth.signOut()`) cerraría la
 * sesión nueva. `signInWithGoogle` espera a que el cierre termine antes de abrir el selector de cuentas.
 *
 * Robolectric sin `FirebaseApp`: tras la espera, el login devuelve `NOT_CONFIGURED` antes de tocar Credential
 * Manager o `signInWithCredential`, así que el momento en que vuelve marca cuándo habría abierto el selector.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FirebaseAuthRepositorySignOutRaceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val events = mutableListOf<String>()
    private val platformGate = CompletableDeferred<Unit>()

    private val signOutFlow = SignOutFlow(
        PushUnregistration { events += "fcm" },
        listOf(SessionCleaner { events += "local" }),
        FirestoreSessionReset { events += "firestore" }
    ) {
        platformGate.await()
        events += "platform-signout"
    }

    private val repository = FirebaseAuthRepository(
        FirebaseAuthSource(context),
        object : SessionApi {
            override suspend fun session(): SessionDto = error("no se espera POST /v1/session")

            override suspend fun acceptTerms(body: AcceptTerms): ConsentsDto = error("no se espera")
        },
        ApiErrorMapper(),
        signOutFlow
    )

    @Test
    fun continueWithGoogleWaitsForTheBackgroundSignOutToFinish() = runTest {
        assumeTrue(FirebaseApp.getApps(context).isEmpty())
        // Como BackgroundSignOut: arranca sin despachar y queda bloqueado en el paso 4.
        launch(start = CoroutineStart.UNDISPATCHED) { repository.signOut() }
        assertFalse(repository.hasSession())

        val signIn = async {
            repository.signInWithGoogle(context).also { events += "signIn-reached-selector" }
        }
        try {
            runCurrent()
            assertFalse(signIn.isCompleted)
            assertEquals(listOf("fcm", "local", "firestore"), events)
        } finally {
            // El cierre corre con NonCancellable: sin liberarlo, un fallo dejaría colgado runTest.
            platformGate.complete(Unit)
        }
        val result = signIn.await()

        assertEquals(
            listOf("fcm", "local", "firestore", "platform-signout", "signIn-reached-selector"),
            events
        )
        assertEquals(SignInResult.Failure(FirebaseAuthRepository.NOT_CONFIGURED), result)
    }

    @Test
    fun withoutASignOutInProgressSignInDoesNotWait() = runTest {
        assumeTrue(FirebaseApp.getApps(context).isEmpty())
        val result = repository.signInWithGoogle(context)
        assertEquals(SignInResult.Failure(FirebaseAuthRepository.NOT_CONFIGURED), result)
        assertEquals(emptyList<String>(), events)
    }
}
