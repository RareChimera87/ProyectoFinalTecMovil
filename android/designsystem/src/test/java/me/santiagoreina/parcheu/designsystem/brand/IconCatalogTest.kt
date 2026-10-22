package me.santiagoreina.parcheu.designsystem.brand

import me.santiagoreina.parcheu.designsystem.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cada ícono del inventario de mockups existe como `R.drawable.ic_*` (criterio de E1.2).
 *
 * [designIcons] sale de los nombres de Material Symbols que usan los archivos de Claude Design
 * (PU NavBar y PU Seccion A a I). Se extrajeron buscando el patrón
 * `font-family:'Material Symbols Rounded' ...>nombre<` y los arreglos de chips en los scripts
 * (p. ej. `apps` en el filtro del grafo). Quedan fuera los de la barra de estado del teléfono simulado
 * (`signal_cellular_alt`, `wifi`, `battery_full`), que dibuja el sistema. `_filled` es la variante FILL 1.
 * Al agregar una pantalla nueva, se añade aquí el ícono y su SVG.
 */
class IconCatalogTest {
    private val designIcons =
        listOf(
            "home", "hub", "shuffle", "chat_bubble", "person", "home_filled", "hub_filled",
            "shuffle_filled", "chat_bubble_filled", "person_filled", "apps", "add_circle",
            "all_inclusive", "arrow_back", "arrow_forward", "auto_awesome", "auto_awesome_filled",
            "autorenew", "block", "bubble_chart", "cancel", "celebration", "check", "check_circle",
            "chevron_right", "close", "cloud_off", "content_copy", "description", "diversity_3",
            "edit", "edit_note", "event_available", "expand_more", "favorite", "flag", "forum",
            "handshake", "hiking", "info", "interests", "lock", "model_training", "more_vert",
            "movie", "music_note", "notifications", "payments", "person_add", "progress_activity",
            "push_pin", "qr_code_scanner", "radio_button_unchecked", "receipt_long", "refresh",
            "replay", "restore", "schedule", "school", "send", "sentiment_satisfied", "settings",
            "share", "shield", "shield_person", "smart_toy", "swap_horiz", "swipe_left",
            "swipe_right", "thumb_down", "thumb_up", "timeline", "today", "tune", "visibility_off",
            "weekend", "workspace_premium", "workspace_premium_filled"
        )

    /** Íconos previstos para pantallas de E1.3 y siguientes que ya tienen drawable. */
    private val extraIcons =
        listOf(
            "delete", "download", "group", "groups", "keyboard_arrow_down", "keyboard_arrow_up",
            "logout", "mail", "privacy_tip", "public", "qr_code_2", "search", "star", "sync",
            "visibility", "warning", "work", "help", "lightbulb"
        )

    private fun missing(names: List<String>) =
        names.filter { name -> runCatching { R.drawable::class.java.getField("ic_$name") }.isFailure }

    @Test
    fun designListHasNoDuplicates() {
        assertEquals(designIcons.distinct(), designIcons)
    }

    @Test
    fun everyDesignIconExistsAsDrawable() {
        val missing = missing(designIcons)
        assertTrue("Faltan drawables del diseño: $missing", missing.isEmpty())
    }

    @Test
    fun everyExtraIconExistsAsDrawable() {
        val missing = missing(extraIcons)
        assertTrue("Faltan drawables: $missing", missing.isEmpty())
    }
}
