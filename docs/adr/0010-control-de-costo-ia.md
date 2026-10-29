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

## Actualización (E10.7, 28 oct 2026): números reales y techo físico en dev
- Con los precios fijados al elegir los modelos en [ADR-0008](0008-ia-vertex-proveedores-intercambiables.md), una llamada del juez cuesta ≈ 0,0013 USD y una simulación de unas 25 llamadas, de 1 a 3 centavos; el tope diario por defecto es `AI_DAILY_BUDGET_USD=3`. El cálculo completo para la sustentación está en [sustentacion.md](../sustentacion.md#7-costo-de-la-ia).
- En dev (sin Cloud Tasks) el techo físico de gasto es la concurrencia de `InProcessTaskQueue` (simulaciones 2, IA ligera 4, mantenimiento 1) en vez de la tasa de las colas de Cloud Tasks; el `DailyBudgetGuard` y las cuotas funcionan igual.

## Actualización (auditoría del 28 oct 2026, B-M1): una persona no agota el presupuesto de todos
- **Problema:** el guard leía el gasto y luego llamaba, así que con concurrencia el gasto pasaba del tope; un timeout o un 5xx no se contaba; y una sola cuenta (reenviando las respuestas del gemelo o escribiendo en bucle, cada mensaje una moderación) podía dejar a todos con 503 `AI_BUDGET` hasta medianoche.
- **Reserva previa:** antes de cada llamada se bloquea el gasto del día (`ai_budget_global`, `SELECT ... FOR UPDATE`) y se suma el costo máximo de la llamada; al terminar se cambia por el real. Una llamada que no cabe en lo que queda se rechaza sin llamar. Los fallos sin uso reportado se cuentan con la entrada estimada.
- **Techo por persona:** `AI_USER_DAILY_BUDGET_USD` (0,75 USD, un cuarto del total) con la misma reserva (`ai_budget_user`); al pasarlo, 503 `AI_BUDGET` solo para esa persona. La simulación se cobra entera a quien la pide. La moderación no cuenta para este techo: tiene su sub-techo `AI_USER_DAILY_MODERATION_USD` (0,25 USD), que pasa al muestreo en vez de apagarla ([ADR-0026](0026-moderacion-ia-fail-open.md)).
- **Topes internos y límite por minuto:** reenvíos de respuestas del gemelo (10 gratis, 30 Plus al día → 402 `QUOTA_EXCEEDED` sin `kind`), moderaciones por autor (ver [ADR-0026](0026-moderacion-ia-fail-open.md)) y 30 peticiones por minuto y persona en las rutas que disparan IA (429). Los topes internos no tienen `QuotaKind` ni código propio en el contrato para no romper los `when` exhaustivos de Android; agregarlos es un seguimiento coordinado con la app.
- Detalle en [arquitectura.md §7.3](../arquitectura.md#73-presupuesto-y-modelos).

