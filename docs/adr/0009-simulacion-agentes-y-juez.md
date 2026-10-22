# ADR-0009: Simulación con dos agentes independientes y un juez con rúbrica

- **Estado:** Aceptada · 22 oct 2026
- **Features:** E4.3, E4.8

## Contexto
La idea central de ParcheU es que **cada gemelo es un agente** que conversa con el otro antes de que las personas se conozcan. El resultado debe ser explicable (porqué y dónde chocarían) y defendible en la sustentación.

## Decisión
- Por escenario (3 por simulación), hasta 8 turnos alternados entre dos `TwinAgent`; cada uno ve solo su persona, sus memorias aceptadas y la transcripción del escenario.
- Juez separado, con salida JSON por esquema y la rúbrica del modo guardada en `scenarios.rubric`.
- Puntaje = `round(0.35 · similitud de rasgos + 0.65 · promedio del juez)`, sin acotar.
- Caché por (par, modo, versión A, versión B) durante 7 días.
- El modo escenario (F3) usa el mismo juez sobre la conversación humana.

## Alternativas consideradas
- **Dueto (un LLM narra a ambos):** unas 4 llamadas en lugar de 25, pero contradice 'cada gemelo es un agente' y tiende a la armonía.
- **Solo similitud de rasgos:** barato pero no conversa ni explica.
- **Acotar la afinidad a [40, 98]:** maquilla el resultado y choca con el tono honesto.

## Consecuencias
- Unas 25 llamadas cortas por simulación (costo de centavos con Flash); se controla con cuotas, caché y presupuesto (ADR-0010).
- La mezcla con una parte determinista hace el puntaje más estable y explicable.
