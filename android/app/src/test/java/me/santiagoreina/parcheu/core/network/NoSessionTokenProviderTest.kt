package me.santiagoreina.parcheu.core.network

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Test

class NoSessionTokenProviderTest {
    @Test
    fun `sin sesion no hay token ni uid`() = runTest {
        assertNull(NoSessionTokenProvider.currentToken())
        assertNull(NoSessionTokenProvider.refreshToken())
        assertNull(NoSessionTokenProvider.currentUserId())
    }
}
