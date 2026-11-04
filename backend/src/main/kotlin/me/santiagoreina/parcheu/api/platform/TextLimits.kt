package me.santiagoreina.parcheu.api.platform

/** Límites de texto compartidos entre módulos (así `simulation` no depende de `chat`). */
object TextLimits {
    /** Máximo de code points de un mensaje de chat; el juez de escenarios corta cada turno a lo mismo. */
    const val MESSAGE_CHARS = 1000
}
