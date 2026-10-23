# Respuestas de la API de Play para los tests de billing (E7.1)

Cuerpos de `purchases.subscriptionsv2.get` (API Google Play Developer v3) que sirve `FakePlayApi`
a través del cliente real (`GooglePlaySubscriptions`). Siguen el formato documentado de
`SubscriptionPurchaseV2`; los ids de pedido y las fechas son de ejemplo (sin datos personales).

- `{{obfuscatedAccountId}}` lo reemplaza el test por `sha256(userId)` del usuario dueño de la compra.
- Fechas relativas al reloj fijo de los tests (`2026-10-25T12:00:00Z`): `active`, `in-grace-period`,
  `canceled` y `active-anual-acknowledged` vencen después; `on-hold`, `paused` y `expired`, antes.
- `not-found.json` es el error 404 que Play responde a un token desconocido.

Al probar la versión 0.3 con un license tester se puede reemplazar cada archivo por la respuesta
real (`GET .../purchases/subscriptionsv2/tokens/<token>`), quitando el token y los ids de pedido.
