# ADR-0013: Billing con verificación y acknowledge en el servidor, y RTDN

- **Estado:** Aceptada · 22 oct 2026
- **Features:** E7.1, E7.2

## Contexto
ParcheU+ es una suscripción ($5.000 COP/mes o $45.000 COP/año). Se exige verificación en servidor, notificaciones RTDN y pruebas sin cobro.

## Decisión
- Una suscripción `parcheu_plus` con planes base `mensual` y `anual`.
- Cliente con Play Billing Library 8; `obfuscatedAccountId = sha256(userId)`.
- Servidor: `subscriptionsv2.get`, validación del account id, `entitlements` y **acknowledge en el servidor**.
- RTDN por Pub/Sub push con OIDC, idempotente por `messageId`, siempre reconsultando Play.
- Pruebas con license testers en la pista cerrada y respuestas grabadas; `grant-plus` solo en dev.

## Alternativas consideradas
- Acknowledge en el cliente: si la app muere antes, Play reembolsa la compra a los 3 días.
- Confiar en el estado del cliente: inseguro.
- RevenueCat u otro intermediario: un proveedor más, fuera del alcance de la materia.

## Consecuencias
- El servidor es la fuente de verdad de Plus; la UI nunca decide sola.
- Requiere cuenta de servicio con acceso a la API de Play y perfil de pagos desde el sprint 1.
