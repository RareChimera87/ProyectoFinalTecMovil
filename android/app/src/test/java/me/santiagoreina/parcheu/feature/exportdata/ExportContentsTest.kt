package me.santiagoreina.parcheu.feature.exportdata

import org.junit.Assert.assertTrue
import org.junit.Test

/** La pantalla debe nombrar cada categoría que ya trae `ExportDto` (E8.3 y el cierre de #112). */
class ExportContentsTest {
    private val text = ExportContents.joinToString(" ").lowercase()

    @Test
    fun namesTheCategoriesAddedToTheExport() {
        listOf("notificaciones", "presentaciones", "matches", "feedback", "invitaciones", "compras")
            .forEach { assertTrue("Falta '$it' en ExportContents", it in text) }
    }

    @Test
    fun keepsTheOriginalCategories() {
        listOf(
            "perfil",
            "gemelo",
            "autorizaciones",
            "simulaciones",
            "conexiones",
            "bloqueos",
            "suscripción",
            "mensajes"
        )
            .forEach { assertTrue("Falta '$it' en ExportContents", it in text) }
    }
}
