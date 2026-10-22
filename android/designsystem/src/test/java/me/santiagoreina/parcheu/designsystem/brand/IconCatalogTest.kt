package me.santiagoreina.parcheu.designsystem.brand

import me.santiagoreina.parcheu.designsystem.R
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cada ícono del inventario de mockups existe como `R.drawable.ic_*` (criterio de E1.2). */
class IconCatalogTest {
    private val names =
        listOf(
            // NavBar (inactivo y activo relleno)
            "home", "hub", "shuffle", "chat_bubble", "person",
            "home_filled", "hub_filled", "shuffle_filled", "chat_bubble_filled", "person_filled",
            // App bars y acciones
            "arrow_back", "close", "more_vert", "flag", "notifications", "search", "settings",
            "person_add", "share", "send", "add", "edit",
            "content_copy", "delete", "download", "sync",
            // Estados y selección
            "check", "check_circle", "radio_button_unchecked", "progress_activity",
            "chevron_right", "keyboard_arrow_down", "keyboard_arrow_up",
            "visibility", "visibility_off",
            // Contenido y secciones
            "favorite", "group", "groups", "handshake", "diversity_3", "school", "work", "public",
            "star", "lightbulb", "schedule", "mail", "tune",
            // Privacidad, seguridad y ajustes
            "lock", "shield", "block", "privacy_tip", "logout", "info", "warning", "help",
            "qr_code_2", "qr_code_scanner",
            // Premium (FILL 1)
            "workspace_premium", "workspace_premium_filled"
        )

    @Test
    fun everyInventoryIconExistsAsDrawable() {
        val missing =
            names.filter { name ->
                runCatching { R.drawable::class.java.getField("ic_$name") }.isFailure
            }
        assertTrue("Faltan drawables: $missing", missing.isEmpty())
    }
}
