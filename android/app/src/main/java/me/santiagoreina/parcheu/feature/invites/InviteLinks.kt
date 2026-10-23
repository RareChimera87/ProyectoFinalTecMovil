package me.santiagoreina.parcheu.feature.invites

import java.net.URI
import me.santiagoreina.parcheu.BuildConfig

/**
 * Códigos y enlaces de invitación (E6.1, ADR-0018). El código es `XXXX-0000` y el QR codifica el
 * App
 * Link `https://<hosting>/i/{codigo}` del sitio de Firebase Hosting de este entorno
 * (`HOSTING_URL`).
 */
object InviteLinks {
    private val CODE = Regex("^[A-Z]{4}-[0-9]{4}$")

    /** Host del sitio de Hosting de este flavor (`parcheu-dev.web.app` o `parcheu-prod.web.app`).
     * */
    val defaultHost: String = hostOf(BuildConfig.HOSTING_URL)

    fun isValidCode(code: String): Boolean = CODE.matches(code)

    /** Lo que escribe la persona, sin espacios y en mayúsculas ("abcd-1234 " -> "ABCD-1234"). */
    fun normalize(raw: String): String = raw.trim().uppercase()

    /**
     * Código de un enlace `https://<host>/i/{codigo}` de este entorno; `null` si es de otro sitio,
     * otro
     * camino o no trae un código válido. Nunca hace nada más que leer el texto.
     */
    fun codeFromLink(raw: String?, host: String = defaultHost): String? {
        val uri = raw?.trim()?.let { runCatching { URI(it) }.getOrNull() } ?: return null
        if (uri.scheme?.lowercase() != "https" ||
            !uri.host.equals(host, ignoreCase = true)
        ) {
            return null
        }
        if (uri.port != -1 && uri.port != 443) return null
        val segments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        if (segments.size != 2 || segments[0] != "i") return null
        return normalize(segments[1]).takeIf(::isValidCode)
    }

    /**
     * Código leído de un QR: el App Link de ParcheU o el código suelto. Cualquier otro contenido
     * (otro
     * sitio, texto, un QR de pago) devuelve `null` y la pantalla dice 'Este código no es de
     * ParcheU'.
     */
    fun codeFromScan(raw: String?, host: String = defaultHost): String? {
        val text = raw?.trim() ?: return null
        return codeFromLink(text, host) ?: normalize(text).takeIf(::isValidCode)
    }

    private fun hostOf(url: String): String = runCatching { URI(url).host }.getOrNull()
        ?: url.removePrefix("https://").substringBefore('/')
}
