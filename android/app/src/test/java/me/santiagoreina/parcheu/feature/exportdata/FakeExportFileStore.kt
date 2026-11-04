package me.santiagoreina.parcheu.feature.exportdata

/** [ExportFileStore] en memoria: guarda lo escrito por uri y simula el `content://` para compartir. */
class FakeExportFileStore(var writeOk: Boolean = true, var shareOk: Boolean = true) :
    ExportFileStore {
    val written = mutableMapOf<String, String>()
    val shared = mutableListOf<Pair<String, String>>()
    var cleared = 0

    override suspend fun writeTo(uri: String, json: String): Boolean {
        if (writeOk) written[uri] = json
        return writeOk
    }

    override suspend fun shareable(fileName: String, json: String): String? {
        if (!shareOk) return null
        shared += fileName to json
        return "content://me.santiagoreina.parcheu.dev.exports/exports/$fileName"
    }

    override fun clear() {
        cleared++
    }
}
