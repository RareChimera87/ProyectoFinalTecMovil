# ADR-0007: Grafo G0 en Canvas con layout radial determinista

- **Estado:** Aceptada · 22 oct 2026
- **Features:** E3.2, E3.3

## Contexto
'Tu parche' es la pantalla protagonista. El diseño (PU Grafo) ubica al usuario al centro y a las conexiones en anillos por afinidad, con estilos de línea por estado y color por modo.

## Decisión
- `RadialLayout.compute()` es una función pura y determinista en `:designsystem` (anillos por afinidad, ángulo estable por modo e id, relajación para separación mínima, `hitTest`).
- `PuGraph` dibuja con `Canvas` de Compose; accesibilidad con una lista semántica paralela.
- Validación con capturas Roborazzi de todos los estados de G0.

## Alternativas consideradas
- **Layout por fuerzas:** no determinista, difícil de probar y no corresponde al diseño de anillos.
- **Librerías de grafos o WebView con D3:** dependencia poco mantenida o puente JS innecesario.

## Consecuencias
- Mismo dataset → mismo dibujo → capturas reproducibles.
- Todo el control del estilo; más código propio (acotado y testeado).
