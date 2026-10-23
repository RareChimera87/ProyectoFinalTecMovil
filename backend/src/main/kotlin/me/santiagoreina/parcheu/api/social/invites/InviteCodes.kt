package me.santiagoreina.parcheu.api.social.invites

import me.santiagoreina.parcheu.api.platform.config.AppConfig
import me.santiagoreina.parcheu.api.platform.config.Env
import java.text.Normalizer
import kotlin.random.Random

/**
 * Códigos de invitación `XXXX-0000` (E6.1): cuatro letras A-Z, guion y cuatro dígitos. Las letras salen
 * del nombre de la persona cuando se puede ("Mariana" -> `MARI-4827`, como en el mockup C5) y se
 * completan al azar; los dígitos siempre son al azar. La unicidad la garantiza la clave primaria de
 * `invites`: [InviteService] reintenta con otro código si choca.
 */
object InviteCodes {
    val FORMAT = Regex("^[A-Z]{4}-[0-9]{4}$")

    private const val LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    fun isValid(code: String): Boolean = FORMAT.matches(code)

    /** Normaliza lo que escribe o escanea una persona: sin espacios y en mayúsculas ("abcd-1234 " -> "ABCD-1234"). */
    fun normalize(raw: String): String = raw.trim().uppercase()

    /**
     * Código nuevo. Con [name] usa sus primeras letras (sin tildes ni símbolos); con [fullyRandom] ignora
     * el nombre, para salir de un prefijo cuyos 10.000 números ya estén casi llenos.
     */
    fun generate(random: Random, name: String? = null, fullyRandom: Boolean = false): String {
        val fromName = if (fullyRandom) "" else lettersOf(name).take(4)
        val letters = fromName + buildString { repeat(4 - fromName.length) { append(LETTERS[random.nextInt(LETTERS.length)]) } }
        val digits = random.nextInt(10_000).toString().padStart(4, '0')
        return "$letters-$digits"
    }

    private fun lettersOf(name: String?): String {
        if (name.isNullOrBlank()) return ""
        val plain = Normalizer.normalize(name, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return plain.uppercase().filter { it in LETTERS }
    }
}

/**
 * Enlace de invitación `https://<hosting>/i/{codigo}` (App Link de Firebase Hosting, ADR-0018 y ADR-0028).
 * La base sale de `INVITE_LINK_BASE` o, si falta, del sitio por defecto de Hosting del proyecto de Firebase
 * (`https://<proyecto>.web.app`); en local, el de dev.
 */
class InviteLinks(base: String) {
    private val base = base.trimEnd('/')

    init {
        require(this.base.startsWith("https://")) { "La base de los enlaces de invitación debe ser https: $base" }
    }

    fun linkFor(code: String): String = "$base/i/$code"

    companion object {
        const val LOCAL_BASE = "https://parcheu-dev.web.app"

        fun fromConfig(config: AppConfig, vars: Map<String, String> = System.getenv()): InviteLinks {
            val explicit = vars["INVITE_LINK_BASE"]?.trim()?.takeIf { it.isNotEmpty() }
            val project = config.firebaseProjectId?.takeIf { it.isNotBlank() }
            return InviteLinks(
                when {
                    explicit != null -> explicit
                    config.env != Env.LOCAL && project != null -> "https://$project.web.app"
                    else -> LOCAL_BASE
                },
            )
        }
    }
}
