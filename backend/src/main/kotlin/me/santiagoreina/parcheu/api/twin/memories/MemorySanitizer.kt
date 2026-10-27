package me.santiagoreina.parcheu.api.twin.memories

import me.santiagoreina.parcheu.api.simulation.MemorySelection

/**
 * Quita datos de contacto de un texto antes de que entre al prompt de extracción o quede guardado
 * como memoria (E9.2): correos, enlaces, teléfonos (7 o más dígitos, con o sin `+57`, espacios,
 * puntos, guiones o paréntesis) y direcciones con la nomenclatura colombiana (`Calle 45 # 23-10`,
 * `Cra 27 #45-12 apto 301`, `Av. 9 No. 10-20`). Es una barrera conservadora: prefiere borrar de
 * más (una fecha `2026-10-27` también se va) a dejar pasar un número de teléfono.
 *
 * Al final aplica [MemorySelection.sanitize] (sin etiquetas, sin saltos de línea y con tope de
 * [MemorySelection.MAX_MEMORY_CHARS]), la misma limpieza que reciben las memorias al entrar al
 * prompt de un `TwinAgent`.
 */
object MemorySanitizer {
    private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}")
    private val AT_HANDLE_EMAIL = Regex("\\S+@\\S+")
    private val URL = Regex("(?i)\\b(?:https?://|www\\.)\\S+")

    /** Dirección colombiana: tipo de vía + número, con `# 23-10` y complemento opcionales. */
    private val ADDRESS = Regex(
        "(?i)\\b(?:calle|cll?|carrera|cra|kra?|cr|avenida|avda|av|transversal|trans|tv|diagonal|dg|circular|cq|autopista)" +
            "\\.?\\s*\\d+(?:\\s*[a-z]\\b)?(?:\\s*bis)?" +
            "(?:\\s*(?:#|no\\.?|n[°º]|número|numero)\\s*\\d+(?:\\s*[a-z]\\b)?(?:\\s*-\\s*\\d+)?)?" +
            "(?:\\s*(?:apto|apartamento|casa|torre|int|interior|piso|oficina|of)\\.?\\s*\\d+)*",
    )

    /** Placa de dirección suelta: `# 23-10`, `#45 - 12`. */
    private val ADDRESS_PLATE = Regex("#\\s*\\d+(?:\\s*[a-zA-Z]\\b)?\\s*-\\s*\\d+")

    /** Secuencia de dígitos con separadores típicos de un teléfono; se borra si tiene 7 o más dígitos. */
    private val DIGIT_RUN = Regex("\\+?\\(?\\d[\\d\\s().-]*\\d\\)?")
    private const val MIN_PHONE_DIGITS = 7

    private val SPACE_BEFORE_PUNCT = Regex("\\s+([,.;:!?])")
    private val REPEATED_PUNCT = Regex("([,;:])\\s*[,;:]+")
    private val WHITESPACE = Regex("\\s+")

    /** Texto sin datos de contacto, sin etiquetas y con tope de longitud. Puede quedar vacío. */
    fun sanitize(text: String): String {
        var out = text
        out = EMAIL.replace(out, " ")
        out = URL.replace(out, " ")
        // Restos con arroba que no parecen correo completo (`ana@`, `@usuario.co`) también se van.
        out = AT_HANDLE_EMAIL.replace(out, " ")
        out = ADDRESS.replace(out, " ")
        out = ADDRESS_PLATE.replace(out, " ")
        out = DIGIT_RUN.replace(out) { match -> if (match.value.count(Char::isDigit) >= MIN_PHONE_DIGITS) " " else match.value }
        out = out.replace(WHITESPACE, " ")
        out = REPEATED_PUNCT.replace(out, "$1")
        out = SPACE_BEFORE_PUNCT.replace(out, "$1")
        return MemorySelection.sanitize(out).trim(' ', ',', ';', ':', '-')
    }

    /** true si [text] tiene algo que [sanitize] quitaría como dato de contacto. */
    fun containsContactData(text: String): Boolean =
        EMAIL.containsMatchIn(text) || URL.containsMatchIn(text) || AT_HANDLE_EMAIL.containsMatchIn(text) ||
            ADDRESS.containsMatchIn(text) || ADDRESS_PLATE.containsMatchIn(text) ||
            DIGIT_RUN.findAll(text).any { it.value.count(Char::isDigit) >= MIN_PHONE_DIGITS }
}
