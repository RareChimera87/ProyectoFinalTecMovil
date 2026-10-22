# ADR-0011: Migraciones Flyway en un Cloud Run Job, versionadas por fecha

- **Estado:** Aceptada · 22 oct 2026
- **Features:** E0.4, E0.6

## Contexto
Migrar al arrancar la API provoca una carrera si arrancan varias instancias a la vez (prod tiene de 1 a 5) y alarga el arranque en frío. Los agentes trabajan en paralelo y numeraciones secuenciales (V3, V4...) chocan.

## Decisión
- El contenedor tiene un modo `migrate`; `deploy-dev` y `deploy-prod` ejecutan el Cloud Run Job `migrate` **antes** de desplegar la API; si falla, no se despliega.
- `V1__esquema.sql` y `V2__semillas.sql`; después `V<yyyyMMdd>_<HHmm>__<descripcion>.sql`.
- `outOfOrder=true` en dev y CI; un check en cada PR rechaza versiones menores que la última de `dev`, así prod siempre recibe las migraciones en orden.

## Alternativas consideradas
- **Migrar al arrancar:** carrera entre instancias y arranque lento.
- **Rangos por épica (V100, V200...):** no evitan choques dentro de una épica y rompen el orden lógico.

## Consecuencias
- Despliegues un poco más largos (un job más), pero seguros.
- Los agentes deben renombrar su migración si otra se mezcla antes con fecha posterior (el check lo indica).
