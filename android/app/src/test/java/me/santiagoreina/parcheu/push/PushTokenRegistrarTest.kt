package me.santiagoreina.parcheu.push

import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.contract.ContractJson
import me.santiagoreina.parcheu.contract.FcmToken
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.data.auth.SignOutFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PushTokenRegistrarTest {
    private class FakeApi(var failing: Boolean = false) : PushApi {
        val registered = mutableListOf<String>()
        val unregistered = mutableListOf<String>()

        override suspend fun register(token: FcmToken) {
            if (failing) error("401")
            registered += token.token
        }

        override suspend fun unregister(token: FcmToken) {
            if (failing) error("500")
            unregistered += token.token
        }
    }

    private class MemoryStore : PushTokenStore {
        override var lastToken: String? = null
        override var registeredToken: String? = null
        override var registeredUser: String? = null

        override fun clear() {
            lastToken = null
            registeredToken = null
            registeredUser = null
        }
    }

    private var user: String? = "ana"

    private fun registrar(api: PushApi, store: PushTokenStore, current: String? = null) =
        PushTokenRegistrar(api, ApiErrorMapper(ContractJson), store, { current }, { user })

    @Test
    fun newTokenIsRegisteredAndRemembered() = runTest {
        val api = FakeApi()
        val store = MemoryStore()
        assertTrue(registrar(api, store).onNewToken("tok-1"))
        assertEquals(listOf("tok-1"), api.registered)
        assertEquals("tok-1", store.registeredToken)
    }

    @Test
    fun tokenBeforeLoginIsKeptAndRegisteredOnSync() = runTest {
        val api = FakeApi(failing = true)
        val store = MemoryStore()
        val registrar = registrar(api, store)
        assertFalse(registrar.onNewToken("tok-1"))
        assertNull(store.registeredToken)

        api.failing = false
        assertTrue(registrar.sync())
        assertEquals(listOf("tok-1"), api.registered)
    }

    @Test
    fun syncDoesNotRepeatARegisteredToken() = runTest {
        val api = FakeApi()
        val registrar = registrar(api, MemoryStore())
        registrar.onNewToken("tok-1")
        assertTrue(registrar.sync())
        assertEquals(1, api.registered.size)
    }

    @Test
    fun syncAsksFirebaseWhenThereIsNoStoredToken() = runTest {
        val api = FakeApi()
        assertTrue(registrar(api, MemoryStore(), current = "tok-fb").sync())
        assertEquals(listOf("tok-fb"), api.registered)
        assertFalse(registrar(api, MemoryStore(), current = null).sync())
    }

    @Test
    fun unregisterDeletesTheTokenAndForgetsTheRegistration() = runTest {
        val api = FakeApi()
        val store = MemoryStore()
        val registrar = registrar(api, store)
        registrar.onNewToken("tok-1")
        assertTrue(registrar.unregister())
        assertEquals(listOf("tok-1"), api.unregistered)
        assertNull(store.registeredToken)
    }

    @Test
    fun anotherAccountOnTheSamePhoneRegistersTheTokenAgain() = runTest {
        val api = FakeApi()
        val store = MemoryStore()
        val registrar = registrar(api, store)
        registrar.onNewToken("tok-1")
        // La sesión de Ana terminó sin unregister (expirada o sin red); Beto inicia sesión.
        user = "beto"
        assertTrue(registrar.sync())
        assertEquals(listOf("tok-1", "tok-1"), api.registered)
        assertEquals("beto", store.registeredUser)
        // La misma cuenta no repite el registro.
        assertTrue(registrar.sync())
        assertEquals(2, api.registered.size)
    }

    @Test
    fun withoutSessionNothingIsRegisteredUntilLogin() = runTest {
        val api = FakeApi()
        val store = MemoryStore()
        val registrar = registrar(api, store)
        user = null
        assertFalse(registrar.onNewToken("tok-1"))
        assertFalse(registrar.sync())
        assertTrue(api.registered.isEmpty())
        user = "ana"
        assertTrue(registrar.sync())
        assertEquals(listOf("tok-1"), api.registered)
    }

    @Test
    fun signOutUnregistersFromTheServerAndThenLeavesNothingOnDisk() = runTest {
        val api = FakeApi()
        val store = MemoryStore()
        val registrar = registrar(api, store, current = "fcm-1")
        registrar.sync()
        val signOut = SignOutFlow(
            push = { registrar.unregister() },
            cleaners = listOf(pushTokenCleaner(store)),
            firestore = { },
            platform = { }
        )

        signOut()

        assertEquals(listOf("fcm-1"), api.unregistered)
        assertNull(store.lastToken)
        assertNull(store.registeredToken)
        assertNull(store.registeredUser)
    }
}
