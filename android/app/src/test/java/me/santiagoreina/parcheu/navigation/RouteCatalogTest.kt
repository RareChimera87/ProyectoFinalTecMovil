package me.santiagoreina.parcheu.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteCatalogTest {
    @Test
    fun catalogCoversEveryDeclaredRouteExactlyOnce() {
        val declared = AppRoute::class.sealedSubclasses.toSet()
        val inCatalog = routeCatalog.map { it.route::class }
        assertEquals("rutas repetidas en el catálogo", inCatalog.size, inCatalog.toSet().size)
        assertEquals(
            declared.map {
                it.simpleName.orEmpty()
            }.sorted(),
            inCatalog.map { it.simpleName.orEmpty() }.sorted()
        )
    }

    @Test
    fun inventoryHasThirtySevenRoutes() {
        // arquitectura.md §2.4: 37 rutas. Si cambia, actualizar la tabla y este número en el mismo PR.
        assertEquals(37, routeCatalog.size)
    }
}
