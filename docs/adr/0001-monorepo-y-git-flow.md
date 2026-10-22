# ADR-0001: Monorepo y git flow con auto-merge a dev

- **Estado:** Aceptada · 22 oct 2026
- **Features:** E0.1, E0.6

## Contexto
Tres estudiantes y varios agentes de IA trabajan en paralelo sobre app, backend, contrato e infraestructura. Las reglas de la materia exigen git flow (main, dev, feature, fix, release), CI en cada PR, despliegue automático a dev y a prod solo con release aprobado.

## Decisión
- Un solo repositorio (`ParcheU/`, remoto `RareChimera87/ProyectoFinalTecMovil`) con `android/`, `backend/`, `contract/`, `infra/`, `firebase/`, `scripts/` y `docs/`.
- `feature/<kebab>` → PR a `dev` con auto-merge squash cuando pasan CI y la revisión; `release/x.y.z` → `main` solo con aprobación del usuario; tag `v*` despliega a prod.
- Commits y títulos de PR en español e imperativo, validados en CI.
- Workflows con filtros por ruta para que un PR de Android no ejecute la CI del backend.

## Alternativas consideradas
- **Repos separados (app, backend, infra):** el contrato compartido por compilación sería imposible sin publicar artefactos; más fricción para agentes.
- **Trunk-based sin dev:** no cumple la regla de la materia.

## Consecuencias
- Un cambio de API rompe la compilación de ambos lados en el mismo PR.
- El historial viejo del remoto se reemplaza solo con aprobación explícita del usuario.
- Hay que cuidar los filtros por ruta para que la CI sea rápida.

## Enmienda (22 oct 2026, ronda de corrección)
- 'Cuando pasan CI y la revisión' se implementa así: checks requeridos en `dev` = CI aplicable + `revision-agente` (revisión automática del diff) + `major-gate` (en PR con etiqueta `major`, aprobación de un revisor par distinto del autor). La revisión de errores post-merge de las features major y la de cada 3 pequeñas (contador `review-counter`) se registran en `docs/revisiones.md`.
- Las versiones intermedias (0.1, 0.2, 0.3, 0.9) siguen el mismo camino que la 1.0: `release/0.x.0` → `main` (aprueba el usuario) → tag → `deploy-prod` y `firebase-deploy` → AAB por `android-release`.
- `commit-lint` valida formato y una lista blanca de verbos en imperativo; no analiza el español en general.
