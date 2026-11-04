package me.santiagoreina.parcheu.api.demo

/**
 * Regla única de la bandera `DEMO_USERS_ENABLED` para listas de personas propuestas (fila, sugerencias de
 * inicio E4.7, presentaciones): con la bandera apagada un demo no se propone a nadie (ADR-0027). La fila la
 * aplica en SQL (`ExposedMatchingRepository`); las vistas en memoria usan [withoutDisabledDemos].
 */
object DemoVisibility {
    fun proposable(isDemo: Boolean, demoUsersEnabled: Boolean): Boolean = !isDemo || demoUsersEnabled

    fun <T> List<T>.withoutDisabledDemos(demoUsersEnabled: Boolean, isDemo: (T) -> Boolean): List<T> =
        if (demoUsersEnabled) this else filterNot(isDemo)
}
