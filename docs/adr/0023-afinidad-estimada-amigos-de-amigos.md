# ADR-0023: Afinidad estimada para amigos de amigos

- **Estado:** Aceptada · 22 oct 2026
- **Features:** E6.3

## Contexto
D6 muestra 'Alguien · 87 % afín' para personas a dos pasos con las que el usuario nunca simuló. Simular con todos los candidatos costaría IA y cupos.

## Decisión
- La afinidad de un candidato de amigos de amigos es una **estimación** = similitud de rasgos (`100 · (1 − ‖tA − tB‖₂ / √6)`), la misma parte determinista del puntaje de simulación.
- La UI la rotula 'afinidad estimada' y ofrece 'Simular' para obtener la real.
- El ranking es por amigos en común (`count(DISTINCT)`) y luego por la estimación.

## Alternativas consideradas
- **Embeddings y pgvector:** más infraestructura para 6 dimensiones (descartado en ADR-0003).
- **Simular en lote:** gasto de IA sin pedido del usuario.

## Consecuencias
- Cálculo en microsegundos en Kotlin; honesto porque se rotula como estimación.
- Puede diferir del puntaje real de una simulación; el texto lo explica.
