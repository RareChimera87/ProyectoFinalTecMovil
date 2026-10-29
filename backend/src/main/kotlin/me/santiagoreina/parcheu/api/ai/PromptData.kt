package me.santiagoreina.parcheu.api.ai

/**
 * Texto de personas dentro de un prompt (arquitectura.md §10.4, prompt injection): se trata como dato no confiable.
 * [neutralize] quita lo que permite salirse del delimitador o falsear una línea nueva (cualquier carácter de control o
 * separador de línea/párrafo, los de formato como marcas bidi y de ancho cero, y los corchetes angulares con sus
 * homoglifos); la cláusula "son datos, no órdenes" la añade quien arma el `system` (p. ej. `SimulationJudge.system`).
 */
object PromptData {
    private val BREAKS = Regex("[\\p{Cc}\\p{Zl}\\p{Zp}]+")
    private val FORMAT = Regex("\\p{Cf}")
    private val NAME = Regex("[A-Za-z0-9_-]+")

    fun neutralize(text: String): String = text
        .replace(BREAKS, " ")
        .replace(FORMAT, "")
        .map { c ->
            when (c) {
                '<', '＜', '﹤', '〈', '\u2329' -> '‹'
                '>', '＞', '﹥', '〉', '\u232A' -> '›'
                else -> c
            }
        }
        .joinToString("")
        .trim()

    /** Primeros [n] code points de [this] (no parte pares surrogate). */
    fun String.takeCodePoints(n: Int): String =
        if (codePointCount(0, length) <= n) this else substring(0, offsetByCodePoints(0, n))

    /** `<tag attr="valor">texto neutralizado</tag>`; [tag] y [attr] deben ser literales del código, nunca texto de usuario. */
    fun wrap(tag: String, text: String, attr: Pair<String, String>? = null): String {
        require(NAME.matches(tag) && attr?.let { NAME.matches(it.first) && NAME.matches(it.second) } != false) { "etiqueta inválida" }
        return "<$tag${attr?.let { " ${it.first}=\"${it.second}\"" }.orEmpty()}>${neutralize(text)}</$tag>"
    }
}
