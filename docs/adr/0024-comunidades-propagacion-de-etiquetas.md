# ADR-0024: Comunidades con propagación de etiquetas ponderada

- **Estado:** Aceptada · 22 oct 2026
- **Features:** E6.4

## Contexto
Las comunidades ('parches') se detectan automáticamente sobre un grafo pequeño (cientos a miles de nodos) y deben ser estables semana a semana y nunca usar aristas de Pareja.

## Decisión
- **Propagación de etiquetas ponderada** por afinidad, en Kotlin sin librerías, semilla fija, orden de visita determinista y máximo 20 iteraciones, sobre aristas CONNECTED de Amistad y Estudio entre usuarios no HIDDEN ni borrados.
- Comunidades de ≥ 3 miembros; id y nombre estables por Jaccard ≥ 0,5 con la semana anterior.
- Nombre creativo con el modelo lite a partir de rasgos promedio, sin datos de personas.

## Alternativas consideradas
- **Louvain:** mejor modularidad, pero más código o una dependencia (JGraphT) para un grafo pequeño.
- **Clustering por embeddings:** requiere pgvector (descartado).

## Consecuencias
- O(aristas) por iteración, determinista y fácil de probar (criterios de E6.4).
- La calidad con pocos usuarios es limitada; con datos demo se ve bien en la sustentación.
