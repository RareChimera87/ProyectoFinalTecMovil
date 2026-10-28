package me.santiagoreina.parcheu.api.premium.fixtures

import me.santiagoreina.parcheu.api.platform.Premium

/**
 * Servicio de prueba con un endpoint `@Premium` que no está en `PremiumEndpoints`: demuestra que el
 * chequeo del registro lo detecta. Vive en las clases de test, así que no contamina el chequeo de main.
 */
class UnlistedPremiumFake {
    @Premium("GET /v1/fake-premium")
    suspend fun secret(): String = "contenido premium"
}
