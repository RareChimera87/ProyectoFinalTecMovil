package me.santiagoreina.parcheu.api.platform

/**
 * Marca la función de servicio que decide qué parte de la respuesta de [endpoint] es de ParcheU+
 * (arquitectura.md §5.2 y §10.1, auditoría E7.4). Para un usuario gratis el servidor omite esos
 * campos (o responde 402 `PLUS_REQUIRED`); el borroso de la UI es solo decorativo.
 *
 * [endpoint] va como "MÉTODO /ruta" tal cual aparece en la tabla de rutas (p. ej. `GET /v1/graph/fof`).
 * Toda función marcada debe tener su entrada en `PremiumEndpoints` (backend/src/test/.../premium/):
 * `PremiumEndpointsRegistryTest` escanea las clases de main y falla si falta alguna, y
 * `PremiumGatingContractTest` comprueba con un usuario gratis que la respuesta no trae los campos premium.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Premium(val endpoint: String)
