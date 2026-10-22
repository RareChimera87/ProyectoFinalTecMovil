# ADR-0022: Pruebas Android en la JVM con Robolectric y Roborazzi

- **Estado:** Aceptada · 22 oct 2026
- **Features:** E0.7, E1.x, todas las features de UI, E10.4

## Contexto
Los agentes necesitan criterios de UI verificables en CI. Un emulador en GitHub Actions es lento e inestable, y el SDK 37 puede ir por delante de Robolectric.

## Decisión
- ViewModels con JUnit4, Turbine y `kotlinx-coroutines-test` contra Fakes de repositorio.
- Pantallas con Compose UI test sobre **Robolectric** (`@Config(sdk = [35])`).
- **Roborazzi** para capturas en ambos temas: es una prueba de **regresión** contra la captura anterior (`changeThreshold = 0.01`).
- La **comparación con los mockups es humana**: I2 la hace lado a lado con los PNG exportados (`docs/mockups/png/`, `scripts/lado-a-lado.sh`) con una checklist (tokens exactos, espaciados ±4dp, tipografía y jerarquía) y deja la tabla de diferencias aceptadas en el PR.
- Sin emulador en CI; verificación manual en dispositivo para login, push y compras.

## Alternativas consideradas
- **Tests instrumentados en emulador:** lentos y frágiles en CI.
- **Umbral de píxeles contra el mockup:** los mockups son de Claude Design a 360×800 con marco y fuentes renderizadas distinto; daría falsos fallos constantes.
- **Paparazzi:** buena opción, pero Roborazzi se integra mejor con Robolectric y Compose UI test en el mismo test.

## Consecuencias
- CI rápida y sin emulador; las capturas sirven de evidencia en la sustentación.
- La fidelidad a los mockups depende de la revisión de I2, que queda registrada.
