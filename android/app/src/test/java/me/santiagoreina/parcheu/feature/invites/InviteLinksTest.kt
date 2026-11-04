package me.santiagoreina.parcheu.feature.invites

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteLinksTest {
    private val host = "parcheu-dev.web.app"

    @Test
    fun readsTheCodeOfOurInviteLink() {
        assertEquals(
            "ABCD-1234",
            InviteLinks.codeFromLink("https://parcheu-dev.web.app/i/ABCD-1234", host)
        )
        assertEquals(
            "ABCD-1234",
            InviteLinks.codeFromLink("https://PARCHEU-DEV.web.app/i/abcd-1234/", host)
        )
        assertEquals(
            "ABCD-1234",
            InviteLinks.codeFromLink(" https://parcheu-dev.web.app/i/ABCD-1234?utm=x ", host)
        )
    }

    @Test
    fun rejectsLinksThatAreNotOurInvites() {
        listOf(
            "http://parcheu-dev.web.app/i/ABCD-1234",
            "https://parcheu-prod.web.app/i/ABCD-1234",
            "https://parcheu-dev.web.app.evil.example/i/ABCD-1234",
            "https://evil.example/i/ABCD-1234",
            "https://parcheu-dev.web.app/x/ABCD-1234",
            "https://parcheu-dev.web.app/i/ABCD-1234/extra",
            "https://parcheu-dev.web.app/i/ABCD1234",
            "https://parcheu-dev.web.app:8443/i/ABCD-1234",
            "parcheu://add-friends/ABCD-1234",
            "no es un enlace",
            ""
        ).forEach { assertNull(it, InviteLinks.codeFromLink(it, host)) }
        assertNull(InviteLinks.codeFromLink(null, host))
    }

    @Test
    fun aScannedQrIsTheLinkOrTheBareCode() {
        assertEquals(
            "ABCD-1234",
            InviteLinks.codeFromScan("https://parcheu-dev.web.app/i/ABCD-1234", host)
        )
        assertEquals("ABCD-1234", InviteLinks.codeFromScan("abcd-1234", host))
        assertNull(InviteLinks.codeFromScan("https://www.google.com", host))
        assertNull(InviteLinks.codeFromScan("WIFI:S:casa;T:WPA;P:clave;;", host))
        assertNull(InviteLinks.codeFromScan("1234-ABCD", host))
    }

    @Test
    fun codeFormatIsFourLettersAndFourDigits() {
        assertTrue(InviteLinks.isValidCode("MARI-4827"))
        listOf("MARI4827", "MAR-4827", "MARI-482", "mari-4827", "M4RI-4827").forEach {
            assertFalse(it, InviteLinks.isValidCode(it))
        }
    }

    @Test
    fun theQrEncodesTheLinkWithModules() {
        val qr = QrEncoder.encode("https://parcheu-dev.web.app/i/MARI-4827")
        // Con corrección H el enlace necesita al menos la versión 3 (29 módulos), con sus tres buscadores.
        assertTrue(qr.size >= 29)
        for ((row, column) in listOf(0 to 0, 0 to qr.size - 7, qr.size - 7 to 0)) {
            assertTrue(qr[row, column])
            assertTrue(qr[row + 6, column + 6])
            assertFalse(qr[row + 1, column + 1])
        }
    }
}
