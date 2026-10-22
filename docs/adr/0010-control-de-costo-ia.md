# ADR-0010: Control de costo de la IA

- **Estado:** Aceptada · 22 oct 2026
- **Features:** E0.5, E0.9, E4.1, E4.3

## Contexto
Los créditos de GCP son finitos y no hay App Check en la 1.0 (ADR-0015). Un bucle o un abuso podría quemar el presupuesto.

## Decisión
- **Cuotas en el servidor** (5 simulaciones por semana, 1 match por día, 1 informe por mes para gratis; uso justo diario para Plus).
- **Caché** de simulaciones por versión de gemelo.
- **Registro** de cada llamada en `ai_usage` y agregado diario.
- **`DailyBudgetGuard` escalonado:** < 70 % normal; 70-100 % todo pasa al modelo lite; ≥ 100 % 503 `AI_BUDGET` sin cobrar cupo.
- **Tasa máxima** en las colas de Cloud Tasks como techo físico de gasto.
- **Alertas** de presupuesto de facturación al 50 % y 90 % y alerta por `AiBudgetExceeded`.

## Alternativas consideradas
- Solo alertas de facturación: llegan tarde.
- Encolar indefinidamente al superar el presupuesto: experiencia confusa; se prefiere un error explícito con `retryAt`.

## Consecuencias
- El gasto diario tiene techo conocido.
- La degradación al modelo lite puede bajar la calidad en días de mucho uso; queda registrada en `simulations.model`.
