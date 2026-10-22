# ADR-0015: App Check diferido después de la 1.0

- **Estado:** Aceptada · 22 oct 2026
- **Features:** E0.9, E4.1 (mitigaciones)

## Contexto
App Check con Play Integrity evita que scripts usen la API, pero complica emuladores, CI, tokens de debug y la build de la prueba cerrada en las primeras semanas.

## Decisión
No activar App Check en la 1.0. Mitigar el abuso con: autenticación obligatoria, cuotas en servidor, uso justo para Plus, `DailyBudgetGuard`, tasa máxima de colas, límite de tamaño de mensajes y alertas.

## Alternativas consideradas
- App Check desde el día 1: más configuración y riesgo de bloquear a testers legítimos.

## Consecuencias
- Un atacante autenticado puede consumir su propio cupo, pero no más que eso; el gasto diario tiene techo.
- Se planifica App Check para la 1.1 en modo 'monitor' primero.
