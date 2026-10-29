# ADR-0026: Moderación con IA asíncrona y fail-open

- **Estado:** Aceptada · 22 oct 2026
- **Features:** E8.1, E8.5, E8.6

## Contexto
Tres personas moderan una app con chat entre desconocidos. Play exige mecanismos de reporte y bloqueo. Una moderación síncrona agregaría latencia a cada mensaje y dependería de la disponibilidad del LLM.

## Decisión
- Reportar y bloquear siempre gratis, inmediatos y desde todas partes (E8.1, E8.7), con instantánea y `admin.html`.
- Moderación con el modelo lite **después** de entregar el mensaje (cola `ai-light`): severidad alta → ocultar y reporte automático; autolesión → recursos de ayuda enviados solo al autor por aviso y push, nunca al chat compartido (Línea 192 opción 4, Línea 106 y 123; verificar vigencia y cobertura en Santander); 'posible menor' → reporte de prioridad alta.
- Si el LLM falla, el mensaje queda visible y se registra `moderation_failed` (**fail-open**).
- E8.6 es recortable: no es una de las 17 funciones.

## Alternativas consideradas
- **Fail-closed (ocultar hasta moderar):** con la IA caída, el chat dejaría de funcionar.
- **Sin moderación automática:** depende solo de reportes; se mantiene como piso.

## Consecuencias
- El chat nunca se bloquea por la IA; puede haber una ventana corta con contenido dañino visible, cubierta por reportes y bloqueo inmediato.

## Enmienda (auditoría del 28 oct 2026, B-M1): tope diario por autor con muestreo
- Cada mensaje era una llamada al LLM sin límite por persona: un bucle de mensajes gastaba el presupuesto de IA de todos.
- Cada autor tiene `MODERATION_DAILY_PER_USER` (200) mensajes moderados al día; pasado el tope se modera 1 de cada `MODERATION_SAMPLE_EVERY` (10) de sus mensajes, elegido por el id del mensaje (determinista: un reintento cae del mismo lado). Los demás quedan visibles sin revisión automática y se registra `moderation_sampled_out`. Se eligió el muestreo sobre la deduplicación o el batching por chat porque no cambia la tarea `moderate` ni su idempotencia.
- Sigue siendo fail-open: si el contador falla se modera igual, y reportar y bloquear no cambian. Las moderaciones **no** cuentan para el techo de gasto personal de su autor ([ADR-0010](0010-control-de-costo-ia.md)): si contaran, gastar el techo en simulaciones apagaría la moderación (y la ayuda ante autolesión) de esa persona hasta medianoche. Tienen su propio sub-techo diario por autor, `AI_USER_DAILY_MODERATION_USD` (0,25 USD), que al pasarse lleva al mismo muestreo 1 de cada `MODERATION_SAMPLE_EVERY` en vez de apagarla. Siguen contando para el presupuesto total.

