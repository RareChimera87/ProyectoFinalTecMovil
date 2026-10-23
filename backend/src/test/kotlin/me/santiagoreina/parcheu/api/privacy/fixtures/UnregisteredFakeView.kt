package me.santiagoreina.parcheu.api.privacy.fixtures

import me.santiagoreina.parcheu.api.graph.ThirdPartyView

/**
 * Vista de terceros de prueba que **no** está en `ThirdPartyViews`: el test de registro comprueba
 * que el escáner la encuentra y la reporta como faltante. Vive en el source set de test, así que
 * no afecta el chequeo de las clases de `main`.
 */
class UnregisteredFakeView : ThirdPartyView {
    override val viewName = "fake-sin-registrar"
}
