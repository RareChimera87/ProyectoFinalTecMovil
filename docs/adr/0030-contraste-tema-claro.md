# ADR-0030: Ajustes de contraste AA en el tema claro

- **Estado:** Aceptada · 22 oct 2026
- **Features:** E1.1, E1.4, E10.4

## Contexto
El requisito exige WCAG AA. Calculado con los hex del inventario (tema claro): `ort` #B8560F da 4,49:1 sobre bg, 4,08:1 sobre s2 y 3,99:1 sobre prc; `mintt` #0E7A65 da 4,48:1 sobre s2 y 4,37:1 sobre prc. El texto blanco sobre avatares violeta con alpha > 0,55 en claro da ≈ 2,4:1 (a 0,9 ni el blanco ni el oscuro llegan a 4,5:1).

## Decisión
- Tema claro: **ort #9E480B** (5,80 bg · 6,20 s1 · 5,27 s2 · 5,15 prc) y **mintt #0B6B59** (6,03 · 6,44 · 5,48 · 5,35). coralt (≥ 4,58) y bluet (≥ 4,82) se mantienen.
- Avatares: en oscuro, texto blanco siempre; en claro, texto #14111F si alpha ≤ 0,80 (≥ 5,0:1) y, si alpha > 0,80, el relleno se fija en alpha 1,0 con texto blanco (5,15:1).
- Se mantiene #14111F sobre #3A86FF (≈ 5,3:1).
- Un test en E1.1 recorre todos los pares declarados en ambos temas.

## Alternativas consideradas
- **Respetar los hex del mockup:** incumple AA, que es un requisito.
- **Agrandar el texto a ≥ 18sp para usar el umbral 3:1:** cambia la jerarquía de los mockups.

## Consecuencias
- Diferencia visual mínima frente al mockup, documentada en la tabla de diferencias aceptadas de E10.4.
