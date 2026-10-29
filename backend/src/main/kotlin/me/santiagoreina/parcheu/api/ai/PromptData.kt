package me.santiagoreina.parcheu.api.ai

/**
 * Texto de personas dentro de un prompt (arquitectura.md §10.4, prompt injection): se trata como dato no confiable.
 * [neutralize] quita lo que permite salirse del delimitador o falsear una línea nueva (saltos de línea y los
 * corchetes angulares, así ninguna etiqueta puede cerrarse o abrirse desde dentro) y [DATA_CLAUSE] es la cláusula
 * que el `system` añade para que el modelo no obedezca lo que haya entre etiquetas.
 */
object PromptData {
    private val LINE_BREAKS = Regex("[\\r\\n\\u0085\\u2028\\u2029]+")

    fun neutralize(text: String): String = text
        .replace(LINE_BREAKS, " ")
        .replace('<', '‹')
        .replace('>', '›')
        .trim()

    /** `<tag attr="valor">texto neutralizado</tag>`; [attr] debe ser un literal del código, nunca texto de usuario. */
    fun wrap(tag: String, text: String, attr: Pair<String, String>? = null): String =
        "<$tag${attr?.let { " ${it.first}=\"${it.second}\"" }.orEmpty()}>${neutralize(text)}</$tag>"
}
