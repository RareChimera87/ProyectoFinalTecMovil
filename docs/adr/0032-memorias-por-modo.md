# ADR-0032: Las memorias del gemelo se aíslan por modo

- **Estado:** Aceptada · 22 oct 2026 (complementa ADR-0012 y ADR-0017)
- **Features:** E2.3, E3.5, E4.3, E9.2, E9.3

## Contexto
El gemelo aprende de los chats con consentimiento por modo. Si una memoria extraída de un chat de Pareja entra al prompt de una simulación de Amistad o Estudio con un desconocido, puede aparecer en la transcripción o el informe (F8) que ve el otro: una fuga del requisito 17 por un camino distinto del grafo.

## Decisión
- `twin_memories.mode` es obligatorio; la extracción procesa cada modo por separado.
- `TwinPromptBuilder` es el único lugar que arma el prompt de un `TwinAgent` y toma sus memorias de `MemorySelection.select` (E3.5), que solo incluye memorias aceptadas **del modo de la simulación**.
- `persona_summary` se construye **solo con las 12 respuestas**; las memorias nunca se mezclan en la persona general.
- Pruebas: captura de prompt en E4.3 y E9.2, y una propiedad en la suite E3.5 (con su variante de mutación para la sustentación).

## Alternativas consideradas
- **Memorias globales filtradas solo para Pareja:** igual filtra datos de Estudio a Amistad, que el usuario no consintió compartir en ese contexto.
- **Integrar memorias en la persona:** imposible aislarlas después.

## Consecuencias
- Un gemelo puede 'saber más' en un modo que en otro; es coherente con el consentimiento por modo.
- `twins.version` sube con cualquier memoria aceptada (invalida la caché de todas las simulaciones; costo aceptable).
