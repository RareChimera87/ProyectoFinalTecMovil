package me.santiagoreina.parcheu.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PushPayloadTest {
    @Test
    fun parsesTheDataMessageTheBackendSends() {
        val payload =
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
        assertEquals(PushChannelSpec.CHAT, payload.channel)
        assertEquals("Beto", payload.title)
        assertEquals("Hola", payload.body)
        assertEquals("parcheu://chat/c-1", payload.route)
    }

    @Test
    fun unknownChannelFallsBackToAccount() {
        assertEquals(PushChannelSpec.CUENTA, PushPayload.from(mapOf("title" to "x", "channel" to "nuevo"))!!.channel)
        assertEquals(PushChannelSpec.CUENTA, PushPayload.from(mapOf("title" to "x"))!!.channel)
    }

    @Test
    fun aMessageWithoutTextIsIgnored() {
        assertNull(PushPayload.from(emptyMap()))
        assertNull(PushPayload.from(mapOf("type" to "CHAT_MESSAGE", "title" to " ", "body" to "")))
    }

    @Test
    fun theChannelIdsMatchTheBackend() {
        assertEquals(listOf("chat", "resultados", "social", "cuenta"), PushChannelSpec.entries.map { it.id })
    }
}
