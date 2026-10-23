package me.santiagoreina.parcheu.feature.invites

import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import me.santiagoreina.parcheu.designsystem.components.compound.QrMatrix

/**
 * Codifica el enlace de invitación con ZXing core (ADR-0018). Corrección de errores H (30 %): el
 * logo
 * del centro de `QRCard` tapa algunos módulos y el lector igual lo lee. Sin zona de silencio: la
 * tarjeta blanca de `QRCard` ya deja el margen.
 */
object QrEncoder {
    fun encode(content: String): QrMatrix {
        val qr = Encoder.encode(
            content,
            ErrorCorrectionLevel.H,
            mapOf(EncodeHintType.CHARACTER_SET to "UTF-8")
        )
        val matrix = qr.matrix
        val size = matrix.width
        val modules = BooleanArray(size * size) { i -> matrix.get(i % size, i / size).toInt() == 1 }
        return QrMatrix(size, modules)
    }
}
