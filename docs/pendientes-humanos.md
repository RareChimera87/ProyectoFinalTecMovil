# Pendientes humanos

Pasos que solo puede hacer una persona (consolas, pagos, términos, identidad). Cada feature agrega su sección.

## E0.1 · Monorepo y git flow

1. **Protección de `dev` con checks requeridos.** Hoy `dev` y `main` exigen PR y bloquean force push, pero no tienen checks requeridos. Tras mezclar este PR y cuando los workflows `pr-title`, `gitleaks` y `major-gate` hayan corrido una vez, ejecuta como admin del repo:

   ```bash
   CHECKS="pr-title,gitleaks,major-gate" scripts/proteger-ramas.sh dev
   ```

   Cuando E0.6 agregue `revision-agente` y las CI por ruta, vuelve a ejecutarlo incluyéndolos en `CHECKS`.
2. **Protección de `main` con tu aprobación.** `scripts/proteger-ramas.sh main` exige 1 aprobación. Al ser tú el único administrador, ten en cuenta que GitHub no deja aprobar el PR propio: los PR a `main` los abre un agente o colaborador y tú los apruebas. Decide si quieres aplicarlo ya o cuando entre un segundo colaborador.
3. **Agregar a I2 e I3 como colaboradores** del repo y reemplazar los comentarios de `.github/CODEOWNERS` con sus usuarios de GitHub.
4. **Prueba de gitleaks en rama descartable** (si no se pudo ejecutar en el PR): crea una rama con un secreto falso, abre un PR y confirma que `gitleaks` falla; luego cierra el PR y borra la rama.
