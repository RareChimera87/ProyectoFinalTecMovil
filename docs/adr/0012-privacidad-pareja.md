# ADR-0012: Privacidad de Pareja con una policy única, filtros SQL y pruebas por propiedades

- **Estado:** Aceptada · 22 oct 2026
- **Features:** E3.1, E3.5, E6.3, E6.4, E7.4

## Contexto
Requisito 17: los vínculos de modo Pareja **nunca** se ven en el grafo de terceros. Además, los usuarios HIDDEN, bloqueados y borrados no deben aparecer a terceros. Las fugas suelen aparecer al agregar vistas nuevas (fof, comunidades, sugerencias).

## Decisión
- `GraphVisibilityPolicy` es la única clase que decide qué ve un observador sobre un tercero.
- Los filtros críticos (Pareja, HIDDEN, borrados, bloqueos) se aplican **también en SQL**, no solo en Kotlin.
- Toda vista de terceros implementa `ThirdPartyView` y se registra; un test de arquitectura falla si falta el registro.
- Suite de **pruebas por propiedades** (Kotest) con grafos aleatorios y ≥ 1.000 casos por propiedad.
- Demo de **mutación a propósito** (`scripts/demo-mutacion-privacidad.sh`) para la sustentación.

## Alternativas consideradas
- Solo tests de ejemplo: no cubren combinaciones raras.
- Vista SQL única para terceros sin policy en Kotlin: no cubre el enmascarado de anónimos.

## Consecuencias
- Una sola superficie que auditar y una red de seguridad automática.
- Cada feature nueva de vistas de terceros paga un pequeño costo de registro.

## Enmienda (22 oct 2026)
La privacidad de Pareja también cubre el **contenido del gemelo**: las memorias se aíslan por modo y nunca entran a la persona general ni a simulaciones de otro modo ([ADR-0032](0032-memorias-por-modo.md)); la suite E3.5 agrega esa propiedad.
