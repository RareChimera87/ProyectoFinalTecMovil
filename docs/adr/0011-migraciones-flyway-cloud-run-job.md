# ADR-0011: Migraciones Flyway en un Cloud Run Job, versionadas por fecha

- **Estado:** Aceptada · 22 oct 2026
- **Features:** E0.4, E0.6

## Contexto
Migrar al arrancar la API provoca una carrera si arrancan varias instancias a la vez (prod tiene de 1 a 5) y alarga el arranque en frío. Los agentes trabajan en paralelo y numeraciones secuenciales (V3, V4...) chocan.

## Decisión
- El contenedor tiene un modo `migrate`; `deploy-dev` y `deploy-prod` ejecutan el Cloud Run Job `migrate` **antes** de desplegar la API; si falla, no se despliega.
- `V1__esquema.sql` y `V2__semillas.sql`; después `V<yyyyMMdd>_<HHmm>__<descripcion>.sql`.
- `outOfOrder=true` en **todos** los entornos, prod incluido. Un check en cada PR rechaza versiones menores que la última de `dev`, pero solo corre al abrir o actualizar el PR: con auto-merge, dos PR paralelos pueden mezclarse en orden inverso y, con `outOfOrder=false`, Flyway fallaría la validación en prod y cortaría el despliegue. Con `true` la migración tardía se aplica igual que en dev (donde ya se probó). El check sigue siendo útil para mantener el historial ordenado y detectar nombres mal formados.

## Alternativas consideradas
- **Migrar al arrancar:** carrera entre instancias y arranque lento.
- **Rangos por épica (V100, V200...):** no evitan choques dentro de una épica y rompen el orden lógico.

## Consecuencias
- Despliegues un poco más largos (un job más), pero seguros.
- Los agentes deben renombrar su migración si otra se mezcla antes con fecha posterior (el check lo indica).

## Actualización (E10.7, 28 oct 2026): migraciones en el homeserver
- El modo `migrate` se conserva tal cual. En el homeserver ([ADR-0033](0033-backend-en-homeserver.md)) lo ejecuta el servicio `parcheu-migrate` de Docker Compose antes de `parcheu-api` (`depends_on`), una vez por despliegue; sigue sin migrarse al arrancar la API.
- Lo aprendido: Flyway no se deshace y la reversión automática de `desplegar.sh` solo vuelve a la imagen anterior, de modo que **las migraciones deben ser compatibles hacia atrás** (columnas nuevas nulas o con valor por defecto; nada de renombrar o borrar en el mismo paso). Procedimiento de reversión y restauración en [operacion.md](../operacion.md#desplegar-y-revertir).
