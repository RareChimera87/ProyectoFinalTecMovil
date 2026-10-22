# ADR-0003: Postgres 16 como única fuente de verdad, sin pgvector ni CTE recursivas

- **Estado:** Aceptada · 22 oct 2026
- **Features:** E0.4, E3.1, E6.3

## Contexto
La mayoría del dominio es relacional: cuotas, fila con concurrencia, decisiones mutuas, suscripciones, bloqueos. El grafo social es pequeño (cientos de nodos) y solo se consulta a 1 y 2 saltos.

## Decisión
- Cloud SQL Postgres 16; el grafo es una tabla de aristas `edges(user_low < user_high, mode)` con una vista `edges_v` en ambos sentidos.
- Amigos de amigos con dos joins; comunidades y evolución calculadas en Kotlin.
- Sin pgvector: el vector de rasgos tiene 6 dimensiones y se compara en Kotlin.

## Alternativas consideradas
- **Neo4j AuraDB:** otro proveedor, otro lenguaje, pausa por inactividad, fuera de los créditos.
- **pgvector + RAG de memorias:** sobreingeniería para pocas memorias aceptadas por usuario; más costo de embeddings.
- **CTE recursivas:** innecesarias para 2 saltos y más difíciles de probar.
- **Firestore como base principal:** sin transacciones relacionales; fan-out caro para fof.

## Consecuencias
- Transacciones y `FOR UPDATE SKIP LOCKED` disponibles para la fila y las decisiones.
- Si en el futuro se necesitara búsqueda semántica, pgvector se puede activar con una migración.
- Cloud SQL tiene costo fijo (cubierto por créditos).
