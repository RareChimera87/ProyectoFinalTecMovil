package me.santiagoreina.parcheu.feature.exportdata

import android.content.Context
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File
import java.io.IOException
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Nombre del archivo de 'Descargar mis datos': `parcheu-datos-<yyyy-MM-dd>.json` (fecha de Bogotá). */
fun exportFileName(date: String): String = "parcheu-datos-$date.json"

/** Hoy en Bogotá como `yyyy-MM-dd` (sin `java.time`: la app soporta API 24 sin desugaring). */
fun todayInBogota(): String {
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("America/Bogota"))
    return String.format(
        Locale.ROOT,
        "%04d-%02d-%02d",
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.DAY_OF_MONTH)
    )
}

/** Dónde se escribe el JSON exportado. Las pruebas usan `FakeExportFileStore`. */
interface ExportFileStore {
    /** Escribe [json] en el documento que la persona eligió con `ACTION_CREATE_DOCUMENT`. */
    suspend fun writeTo(uri: String, json: String): Boolean

    /** Copia [json] a un archivo privado y devuelve un `content://` para compartirlo, o null si falla. */
    suspend fun shareable(fileName: String, json: String): String?

    /** Borra las copias para compartir (al salir de la pantalla). */
    fun clear()
}

/**
 * Implementación con `ContentResolver` y un `FileProvider` propio (`<paquete>.exports`, solo la carpeta
 * `cache/exports/`). El archivo para compartir vive en la caché privada y se borra al salir.
 */
class AndroidExportFileStore(private val context: Context) : ExportFileStore {
    private val dir: File get() = File(context.cacheDir, EXPORT_DIR)

    override suspend fun writeTo(uri: String, json: String): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri.toUri(), "wt")?.use {
                it.write(json.toByteArray(Charsets.UTF_8))
                true
            } ?: false
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    override suspend fun shareable(fileName: String, json: String): String? =
        withContext(Dispatchers.IO) {
            try {
                clear()
                dir.mkdirs()
                val file = File(dir, fileName).apply { writeText(json, Charsets.UTF_8) }
                FileProvider.getUriForFile(context, authority(context), file).toString()
            } catch (_: IOException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }

    override fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    companion object {
        const val EXPORT_DIR = "exports"

        /** Autoridad del `FileProvider` declarado en el manifiesto. */
        fun authority(context: Context): String = "${context.packageName}.exports"
    }
}
