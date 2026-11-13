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

## Enmienda (13 nov 2026): activación manual acotada de ParcheU+
Para mostrar ParcheU+ en la demo contra producción sin Play (la app aún no está publicada) se agrega el modo
`grant-plus` del contenedor, que corre solo como Cloud Run Job (`grant-plus`, misma imagen, `sa-api`). `grant-plus` de
dev (`POST /internal/dev/grant-plus`) sigue sin existir en prod.
- **Qué hace:** busca al usuario por correo exacto o por uid de Firebase. Rechaza al inexistente, al borrado, al suspendido
  y al demo. Deja una fila `entitlements` ACTIVE con `product_id = 'manual-grant'` (el origen MANUAL) y un vencimiento
  obligatorio de 1 a 30 días (7 por defecto). Si se repite, extiende o deja igual. `revoke-plus` (o `--days=0`) la
  retira. No toca una suscripción de Play vigente ni recuperable, y una compra real que da Plus la reemplaza.
- **Por qué no es una puerta trasera:**
  - No tiene ruta HTTP ni superficie pública.
  - Ejecutarla con argumentos exige `run.jobs.runWithOverrides` en el proyecto (propietario o `run.admin`). Quien
    tiene ese permiso ya podía correr código como `sa-api` (residual aceptado en ADR-0014), así que no da ningún poder
    nuevo.
  - Con los argumentos por defecto falla.
  - Siempre vence y nunca es indefinida.
  - Queda auditada en `purchase_events` (`source = MANUAL`, sin correo ni datos de pago) y en el log
    `plus_granted_manual`, que lleva el uuid interno y no el correo.
- **Consecuencia:** el gating y los cupos no cambian. Usan el mismo `EntitlementService`, así que el Plus manual vale lo
  mismo que una compra. El cobro sigue siendo solo de Play. Tras cada demo hay que revocarlo (docs/operacion.md).
