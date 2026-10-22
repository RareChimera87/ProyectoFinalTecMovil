# Pendientes humanos

Pasos que solo puede hacer una persona (consolas, pagos, términos, identidad). Cada feature agrega su sección.

## E0.1 · Monorepo y git flow

1. **Protección de `dev` con checks requeridos.** Hoy `dev` y `main` exigen PR y bloquean force push, pero no tienen checks requeridos. Tras mezclar este PR y cuando los workflows `pr-title`, `gitleaks` y `major-gate` hayan corrido una vez, ejecuta como admin del repo:

   ```bash
   CHECKS="pr-title,gitleaks,major-gate" scripts/proteger-ramas.sh dev
   ```

   Cuando E0.6 agregue `revision-agente` y las CI por ruta, vuelve a ejecutarlo incluyéndolos en `CHECKS`.
2. **Protección de `main` con tu aprobación.** `scripts/proteger-ramas.sh main` exige 1 aprobación pero deja `enforce_admins: false`. Decisión: al ser tú la única cuenta (autora y admin), GitHub no deja aprobar el PR propio y con `enforce_admins: true` nadie podría mezclar. Los agentes abren el PR `release/*` → `main` y tú lo mezclas con `gh pr merge --admin`, que cuenta como tu aprobación explícita. Cuando entre un segundo colaborador (I2/I3) pasa `enforce_admins` a `true`. En `dev` se usa `strict: false` para que los PR paralelos con auto-merge no queden detrás esperando un `update-branch`.
3. **Agregar a I2 e I3 como colaboradores** del repo y reemplazar los comentarios de `.github/CODEOWNERS` con sus usuarios de GitHub.

## E0.3 · Esqueleto del backend

No hay pasos exclusivos de una persona para esta feature. Dos cosas a tener en cuenta:

1. **Dependencias del PR.** E0.3 se construyó sobre E0.1 (PR #2) y E0.2 (PR #4), que al momento de implementarla seguían abiertos; la rama las integra con dos merges. Mezcla antes esos PR: el diff de E0.3 se reduce a `backend/` y los docs de la ficha.
2. **CI del backend.** El workflow `backend-ci` (con Testcontainers) lo crea E0.6; hasta entonces `MigrateContainerTest` no ha corrido en ningún entorno (en esta máquina Docker no funciona) y debe revisarse cuando exista esa CI.

