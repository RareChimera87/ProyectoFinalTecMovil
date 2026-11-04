package me.santiagoreina.parcheu.designsystem.components.compound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrMatrixTest {
    @Test
    fun rechazaUnaMatrizQueNoEsCuadrada() {
        val result = runCatching { QrMatrix(3, BooleanArray(8)) }
        assertTrue(result.isFailure)
    }

    @Test
    fun leeLosModulosPorFilaYColumna() {
        val matrix = QrMatrix(2, booleanArrayOf(true, false, false, true))
        assertTrue(matrix[0, 0])
        assertTrue(matrix[1, 1])
        assertEquals(false, matrix[0, 1])
    }

    @Test
    fun laMuestraTieneMarcadoresDeEsquina() {
        val qr = sampleQr()
        assertTrue(qr[0, 0] && qr[0, 24] && qr[24, 0])
    }
}
