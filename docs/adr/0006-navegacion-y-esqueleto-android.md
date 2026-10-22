# ADR-0006: Navigation Compose 2.9 tipado y esqueleto con todo declarado

- **Estado:** Aceptada · 22 oct 2026
- **Features:** E0.7

## Contexto
Varios agentes construyen pantallas en paralelo. Si cada uno edita el `NavHost` y los módulos de DI, los conflictos de merge son constantes.

## Decisión
- Navigation Compose 2.9 con rutas `@Serializable`.
- E0.7 declara **todas** las rutas del inventario en `Routes.kt` y `AppNavHost.kt` con una pantalla stub por ruta, y todos los módulos Koin vacíos en `AppModules.kt`.
- Cada feature reemplaza solo `feature/<x>/XRoute.kt` y `XModule.kt`; `AppNavHost.kt` y `AppModules.kt` quedan congelados.
- Dos módulos Gradle (`:app`, `:designsystem`) más el build incluido `contract`.

## Alternativas consideradas
- **Navigation 3 con EntryProvider por multibinding:** elegante pero poco documentado para agentes y atado a Hilt.
- **Multimódulo por feature con build-logic:** más aislamiento, pero cada módulo nuevo multiplica la configuración de Gradle que se rompe.

## Consecuencias
- Casi cero conflictos en navegación y DI.
- Un test parametrizado garantiza que todas las rutas existen y navegan.
- `:app` crece; se mantiene ordenado por paquetes `feature/` y `data/`.
