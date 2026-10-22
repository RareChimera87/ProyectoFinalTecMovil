package me.santiagoreina.parcheu.push

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import me.santiagoreina.parcheu.navigation.Chat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PushNotifierTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val payload =
        PushPayload.from(
            mapOf(
                "id" to "n-1",
                "type" to "CHAT_MESSAGE",
                "channel" to "chat",
                "title" to "Beto",
                "body" to "Hola",
                "route" to "parcheu://chat/c-1",
            ),
        )!!

    @Test
    fun createsTheFourChannels() {
        PushChannels.ensureCreated(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertEquals(
            setOf("chat", "resultados", "social", "cuenta"),
            manager.notificationChannels.map {
                it.id
            }.toSet(),
        )
    }

    @Test
    fun withoutPermissionNothingIsShown() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(PushNotifier.show(context, payload))
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    fun withPermissionItPostsOnTheChatChannelAndOpensTheDeepLink() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(PushNotifier.show(context, payload))
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val posted = manager.activeNotifications.single().notification
        assertEquals("chat", posted.channelId)
        assertNotNull(posted.contentIntent)

        val intent = PushNotifier.openIntent(context, payload)
        assertEquals("parcheu://chat/c-1", intent.dataString)
        assertEquals(Chat("c-1"), DeepLinks.fromUri(intent.dataString))
    }

    @Test
    fun anUnknownRouteOpensTheInbox() {
        val intent = PushNotifier.openIntent(context, payload.copy(route = "https://evil.example"))
        assertEquals("parcheu://notifications", intent.dataString)
    }
}
