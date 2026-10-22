# ADR-0019: Cloud Tasks para el trabajo asíncrono

- **Estado:** Aceptada · 22 oct 2026
- **Features:** E0.5, E4.3, E2.3, E8.3, E8.6, E9.2

## Contexto
Las simulaciones tardan decenas de segundos (unas 25 llamadas al LLM), la persona y las memorias se generan con IA y el borrado de cuenta toca varios sistemas. Nada de eso cabe en una petición HTTP; además hay que reintentar con idempotencia y poner un techo al gasto de IA.

## Decisión
- **Cloud Tasks** con tres colas: `simulations` (`simulate`, `judge-scenario`), `ai-light` (`build-persona`, `extract-memories`, `moderate`, `name-community`) y `maintenance` (`purge-user`, sin IA). Cada cola con `max_dispatches_per_second` y `max_concurrent_dispatches` por variable (techo de gasto).
- Las tareas llaman a `/internal/tasks/*` con OIDC de `sa-tasks`; el id de la entidad es el nombre de la tarea (deduplicación) y el handler es idempotente.
- Los trabajos periódicos usan **Cloud Scheduler** (`communities`, `memories`, `twin-accuracy`, `retention`).

## Alternativas consideradas
- **Corrutinas dentro de la petición o en segundo plano en Cloud Run:** la instancia puede apagarse (escalado a cero) y se pierde el trabajo; sin reintentos ni techo de tasa.
- **Pub/Sub:** entrega al menos una vez y push, pero sin control fino de tasa por cola ni deduplicación por nombre; se reserva para RTDN, que es push de Google.
- **Cloud Run Jobs para todo:** arranque lento y pensado para lotes, no para miles de tareas pequeñas.

## Consecuencias
- Reintentos y tasa máxima sin código propio; la tasa máxima es también un control de costo.
- Las pruebas usan un `FakeTaskQueue` que ejecuta en línea; los handlers deben ser idempotentes (lo exigen los criterios de E4.3 y E2.3).
