package me.santiagoreina.parcheu.feature.conocer.match

import com.google.firebase.Timestamp
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.TicketStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TicketMappingTest {
    @Test
    fun mapsMatchedTicketFromFirestoreFields() {
        val ticket = ticketFromFields(
            mapOf(
                "status" to "MATCHED",
                "mode" to "ESTUDIO",
                "matchId" to "m9",
                "expiresAt" to Timestamp(1_793_800_000, 0)
            )
        )

        assertEquals(TicketStatus.MATCHED, ticket?.status)
        assertEquals(Mode.ESTUDIO, ticket?.mode)
        assertEquals("m9", ticket?.matchId)
        assertEquals(1_793_800_000L, ticket?.expiresAt?.epochSeconds)
    }

    @Test
    fun waitingTicketHasNoMatchId() {
        val ticket = ticketFromFields(
            mapOf("status" to "WAITING", "mode" to "AMISTAD", "expiresAt" to Timestamp(1, 0))
        )

        assertNull(ticket?.matchId)
    }

    @Test
    fun unknownOrIncompleteFieldsGiveNull() {
        assertNull(ticketFromFields(mapOf("status" to "OTHER", "mode" to "AMISTAD")))
        assertNull(ticketFromFields(mapOf("status" to "WAITING", "mode" to "AMISTAD")))
        assertNull(ticketFromFields(emptyMap()))
    }
}
