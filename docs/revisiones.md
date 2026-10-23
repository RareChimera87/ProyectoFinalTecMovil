# Bitácora de revisiones

Registro de toda revisión (PLAN.md §5): la automática previa al merge no se anota aquí; sí se anotan las revisiones post-merge de features major, las de cada 3 features pequeñas y las de cierre de épica. Los hallazgos se corrigen en ramas `fix/<kebab>` antes de lanzar las features que dependen de la revisada.

El workflow `review-counter` (E0.6) actualiza el contador de abajo.

## Contador de features pequeñas

- Features pequeñas (no major) mezcladas desde la última revisión: 0
- Última revisión de 3 features pequeñas: ninguna

## Registro

| Feature | Tipo de revisión | Fecha | Revisor | Hallazgos | Rama fix |
|---|---|---|---|---|---|
| E2.2 Onboarding y login con Google (ola 6, commit 2c88d63, con interacción con E3.3 2814a90) | post-merge major | 2026-10-22 | agente | 6 hallazgos (0 altos, 3 medios, 3 bajos). Medios: (1) la 0.1 (E10.3) cortada desde dev incluiría el login y quedaría bloqueada en A4, hay que cortarla desde d7989af o usar un flag; (2) /terminos y /privacidad dan 404 en dev porque E8.4 no está mezclado, y es prerrequisito antes de registrar consents en la verificación manual; (3) allowBackup=true con la sesión de Firebase Auth en el dispositivo. Bajos: FirebaseTokenProvider convierte fallos de red en un 401 que cierra la sesión; la hoja de términos no es modal para TalkBack ni Atrás; A4 no usa PuGraph de E3.3 y E1.3 entró por #28 con #20 abierto. Suite android-ci local en verde (app 86 y designsystem 92 pruebas). | fix/revision-ola-6-0 |
| E3.3 Componente G0 en Canvas (#29, 2814a903) | post-merge major | 2026-10-22 | agente | 1 media: alignFofToParents encima fof con muchos amigos de amigos (30 fof → 17-18 pares solapados, dos en el mismo punto, hasOverlaps falso). 2 bajas: la nota de hueco de contrato en backlog E3.4 razona mal la exclusión de Pareja para viaId; zonas táctiles de nodos < 48 dp (40/38 dp; ~25 dp en miniatura). Suite completa de dev en verde (spotless, lint, 92 pruebas de designsystem y 86 de app, Roborazzi verify). Sin secretos. Alcance: PuGraph, RadialLayout copiado de #15 (idéntico), interacción con E3.1 (#31) y E2.2 (#28), mockup PU Grafo, inventario §3.7, ADR-0007/0030. | fix/revision-ola-6-1 |
| - | - | - | - | Sin revisiones todavía | - |

Tipos: `post-merge major`, `3 pequeñas`, `cierre de épica`.
