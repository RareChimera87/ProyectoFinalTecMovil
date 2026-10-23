package me.santiagoreina.parcheu.feature.invites

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Resultado de leer un QR. */
sealed interface ScanResult {
    /** Texto crudo del QR (puede no ser de ParcheU: lo decide [InviteLinks.codeFromScan]). */
    data class Scanned(val raw: String) : ScanResult

    /** La persona cerró el lector sin leer nada. */
    data object Cancelled : ScanResult

    /** El lector no se pudo abrir (sin Google Play services o el módulo aún no está descargado). */
    data object Unavailable : ScanResult
}

/** Lector de QR. La app usa [GmsCodeScanner]; las pruebas, un `FakeCodeScanner`. */
fun interface CodeScanner {
    suspend fun scan(): ScanResult
}

/**
 * Google Code Scanner (ADR-0018): la UI de cámara la pone Google Play services, así que la app
 * **no**
 * pide permiso de cámara. Solo lee QR.
 */
class GmsCodeScanner(private val context: Context) : CodeScanner {
    override suspend fun scan(): ScanResult = suspendCancellableCoroutine { cont ->
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        val task = runCatching { GmsBarcodeScanning.getClient(context, options).startScan() }
            .getOrElse {
                cont.resume(ScanResult.Unavailable)
                return@suspendCancellableCoroutine
            }
        task.addOnSuccessListener { barcode ->
            val raw = barcode.rawValue
            if (cont.isActive) {
                cont.resume(
                    if (raw ==
                        null
                    ) {
                        ScanResult.Cancelled
                    } else {
                        ScanResult.Scanned(raw)
                    }
                )
            }
        }
        task.addOnCanceledListener { if (cont.isActive) cont.resume(ScanResult.Cancelled) }
        task.addOnFailureListener { if (cont.isActive) cont.resume(ScanResult.Unavailable) }
    }
}
