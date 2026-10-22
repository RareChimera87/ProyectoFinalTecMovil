# ADR-0026: Moderación con IA asíncrona y fail-open

- **Estado:** Aceptada · 22 oct 2026
- **Features:** E8.1, E8.5, E8.6

## Contexto
Tres personas moderan una app con chat entre desconocidos. Play exige mecanismos de reporte y bloqueo. Una moderación síncrona agregaría latencia a cada mensaje y dependería de la disponibilidad del LLM.

## Decisión
- Reportar y bloquear siempre gratis, inmediatos y desde todas partes (E8.1, E8.7), con instantánea y `admin.html`.
- Moderación con el modelo lite **después** de entregar el mensaje (cola `ai-light`): severidad alta → ocultar y reporte automático; autolesión → recursos de ayuda (Línea 192 opción 4, Línea 106 y 123; verificar vigencia y cobertura en Santander); 'posible menor' → reporte de prioridad alta.
- Si el LLM falla, el mensaje queda visible y se registra `moderation_failed` (**fail-open**).
- E8.6 es recortable: no es una de las 17 funciones.

## Alternativas consideradas
- **Fail-closed (ocultar hasta moderar):** con la IA caída, el chat dejaría de funcionar.
- **Sin moderación automática:** depende solo de reportes; se mantiene como piso.

## Consecuencias
- El chat nunca se bloquea por la IA; puede haber una ventana corta con contenido dañino visible, cubierta por reportes y bloqueo inmediato.
