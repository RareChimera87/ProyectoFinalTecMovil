package me.santiagoreina.parcheu.api.social.invites

import me.santiagoreina.parcheu.api.platform.config.AppConfig
import me.santiagoreina.parcheu.api.platform.config.Env
import kotlin.random.Random

/**
 * Códigos de invitación `XXXX-0000` (E6.1): cuatro letras A-Z, guion y cuatro dígitos, todo al azar con
 * `SecureRandom` (26^4 * 10^4 ≈ 4.570 millones de códigos). Las letras ya no salen del nombre (el mockup C5
 * muestra `MARI-4827`): un prefijo predecible dejaba solo 10.000 códigos por nombre y, junto al canje, servía
 * para averiguar quién tenía cuál. La unicidad la garantiza la clave primaria de `invites`: [InviteService]
 * reintenta con otro código si choca.
 */
object InviteCodes {
    val FORMAT = Regex("^[A-Z]{4}-[0-9]{4}$")

    private const val LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    fun isValid(code: String): Boolean = FORMAT.matches(code)

    /** Normaliza lo que escribe o escanea una persona: sin espacios y en mayúsculas ("abcd-1234 " -> "ABCD-1234"). */
    fun normalize(raw: String): String = raw.trim().uppercase()

    /** Código nuevo, todo al azar. */
    fun generate(random: Random): String {
        val letters = buildString { repeat(4) { append(LETTERS[random.nextInt(LETTERS.length)]) } }
        val digits = random.nextInt(10_000).toString().padStart(4, '0')
        return "$letters-$digits"
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
