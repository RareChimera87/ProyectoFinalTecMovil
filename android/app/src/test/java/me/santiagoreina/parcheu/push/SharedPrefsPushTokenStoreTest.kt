package me.santiagoreina.parcheu.push

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SharedPrefsPushTokenStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun clearForgetsTheTokenButKeepsThePermissionAskedFlag() {
        val store = SharedPrefsPushTokenStore(context)
        store.lastToken = "t1"
        store.registeredToken = "t1"
        store.registeredUser = "u1"
        NotificationPermission.markAsked(context)

        store.clear()

        assertNull(store.lastToken)
        assertNull(store.registeredToken)
        assertNull(store.registeredUser)
        assertTrue(NotificationPermission.wasAsked(context))
    }

    @Test
    fun clearOnAFreshStoreDoesNotMarkThePermissionAsAsked() {
        SharedPrefsPushTokenStore(context).clear()

        assertFalse(NotificationPermission.wasAsked(context))
    }
}
