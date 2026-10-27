package me.santiagoreina.parcheu.data.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import me.santiagoreina.parcheu.contract.TwinAnswer
import me.santiagoreina.parcheu.feature.twinquestions.DataStoreAnswerProgressStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Criterio de E8.2 (Fakes): 'Cerrar sesión' borra el token FCM en el servidor, limpia el DataStore de sesión y
 * la caché de Firestore y cierra la sesión de Firebase, en ese orden.
 */
class SignOutFlowTest {
    private val calls = mutableListOf<String>()
    private val dir = File.createTempFile("signout", "").apply {
        delete()
        mkdirs()
    }
    private val scopes = mutableListOf<CoroutineScope>()

    @After fun tearDown() {
        scopes.forEach { it.cancel() }
        dir.deleteRecursively()
    }

    private fun progressStore(): DataStoreAnswerProgressStore {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes += it }
        val ds = PreferenceDataStoreFactory.create(scope = scope) { File(dir, "p.preferences_pb") }
        return DataStoreAnswerProgressStore(ds)
    }

    private fun flow(
        push: PushUnregistration = PushUnregistration { calls += "fcm" },
        cleaners: List<SessionCleaner> = listOf(SessionCleaner { calls += "local" }),
        firestore: FirestoreSessionReset = FirestoreSessionReset { calls += "firestore" }
    ) = SignOutFlow(push, cleaners, firestore) { calls += "firebase" }

    @Test
    fun runsEveryStepInOrderWithTheSessionStillAliveForTheFcmCall() = runBlocking {
        flow()()
        assertEquals(listOf("fcm", "local", "firestore", "firebase"), calls)
    }

    @Test
    fun clearsTheSessionDataStoreButNotAnythingElse() = runBlocking {
        val store = progressStore()
        store.save(mapOf(1 to TwinAnswer.A, 2 to TwinAnswer.B))
        flow(cleaners = listOf(SessionCleaner { store.clear() }))()
        assertEquals(emptyMap<Int, TwinAnswer>(), store.load())
        assertEquals(listOf("fcm", "firestore", "firebase"), calls)
    }

    @Test
    fun aFailingStepNeverKeepsTheSessionOpen() = runBlocking {
        flow(
            push = PushUnregistration { error("sin red") },
            cleaners = listOf(
                SessionCleaner { error("disco lleno") },
                SessionCleaner { calls += "second-cleaner" }
            ),
            firestore = FirestoreSessionReset { error("terminate falló") }
        )()
        assertEquals(listOf("second-cleaner", "firebase"), calls)
    }
}
