package me.santiagoreina.parcheu.core.observability

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class CrashReporterTest {
    private class Recorder : CrashReporter {
        val keys = mutableMapOf<String, String>()
        val errors = mutableListOf<Throwable>()

        override fun setKey(key: String, value: String) {
            keys[key] = value
        }

        override fun recordNonFatal(error: Throwable) {
            errors += error
        }
    }

    @After
    fun reset() = CrashReporting.overrideForTest(NoopCrashReporter)

    @Test
    fun `routeName deja solo el nombre corto de la ruta`() {
        assertEquals("Home", CrashKeys.routeName("me.santiagoreina.parcheu.navigation.Home"))
        assertEquals(
            "Chat",
            CrashKeys.routeName("me.santiagoreina.parcheu.navigation.Chat/{chatId}")
        )
    }

    @Test
    fun `routeName descarta argumentos con datos`() {
        assertEquals(
            "Chat",
            CrashKeys.routeName("me.santiagoreina.parcheu.navigation.Chat/abc-123-uuid")
        )
        assertEquals(
            "Invite",
            CrashKeys.routeName("me.santiagoreina.parcheu.navigation.Invite?code=SECRETO")
        )
    }

    @Test
    fun `routeName usa unknown si no hay ruta`() {
        assertEquals("unknown", CrashKeys.routeName(null))
        assertEquals("unknown", CrashKeys.routeName("  "))
    }

    @Test
    fun `setRoute publica la clave route sin argumentos`() {
        val recorder = Recorder()
        CrashReporting.overrideForTest(recorder)
        CrashReporting.setRoute("me.santiagoreina.parcheu.navigation.Chat/{chatId}")
        assertEquals("Chat", recorder.keys[CrashKeys.ROUTE])
    }

    @Test
    fun `el reporter sin Firebase no lanza`() {
        NoopCrashReporter.setKey("a", "b")
        NoopCrashReporter.recordNonFatal(IllegalStateException("x"))
        // CrashlyticsReporter tolera que Firebase no esté inicializado (Robolectric sin FirebaseApp).
        CrashlyticsReporter().setKey("a", "b")
        CrashlyticsReporter().recordNonFatal(IllegalStateException("x"))
    }
}
