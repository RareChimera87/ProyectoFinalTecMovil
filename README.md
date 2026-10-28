# ParcheU

App Android (Kotlin, Jetpack Compose) con backend Ktor en GCP: un gemelo digital que conecta personas por afinidad. Proyecto final de Tecnologías Móviles.

Remoto: https://github.com/RareChimera87/ProyectoFinalTecMovil

## Estructura del monorepo

| Carpeta | Contenido |
|---|---|
| `android/` | App Android (`app`, `designsystem`), flavors dev/prod |
| `backend/` | Servicio Ktor 3, Gradle independiente |
| `contract/` | DTOs `@Serializable` compartidos entre app y backend |
| `infra/` | Terraform (bootstrap, módulo de plataforma, entornos dev y prod) |
| `firebase/` | Reglas de Firestore, índices, hosting |
| `scripts/` | Utilidades: `commit-lint`, hooks, protección de ramas |
| `docs/` | Plan, arquitectura, backlog, ADR, equipo, revisiones |
| `.github/` | Workflows y plantillas |

Algunas carpetas solo tienen un README hasta que llega su feature (ver `docs/backlog.md`).

## Documentos que mandan

- [docs/PLAN.md](docs/PLAN.md): plan, estructura y git flow
- [docs/arquitectura.md](docs/arquitectura.md): arquitectura y contrato
- [docs/c4.md](docs/c4.md): diagramas C4 (contexto, contenedores, componentes) en Mermaid
- [docs/backlog.md](docs/backlog.md): fichas con criterios de aceptación
- [docs/equipo.md](docs/equipo.md): roles y revisores
- [docs/adr/](docs/adr/): decisiones de arquitectura
- [docs/desarrollo-local.md](docs/desarrollo-local.md): preparar la máquina
- [docs/revisiones.md](docs/revisiones.md): bitácora de revisiones
- [docs/operacion.md](docs/operacion.md): runbook de alertas y operación del backend de dev en el homeserver
- [docs/sustentacion.md](docs/sustentacion.md): guion de 10 minutos, demo de mutación de privacidad y costo de IA
- [docs/play-console.md](docs/play-console.md) y [docs/pendientes-humanos.md](docs/pendientes-humanos.md): publicación en Play y pasos que solo hace una persona

## Git flow en breve

- `main` y `dev` están protegidas: solo se cambian con PR.
- `feature/<kebab>` -> PR a `dev`; se mezcla (squash) cuando pasa la CI y la revisión automática del agente (etiqueta `revisado-agente`). Las features `major` exigen esa etiqueta (check `major-gate`); el revisor par humano las revisa después del merge, en la ventana de revisión de la ola.
- `release/x.y.z` -> `main` solo con aprobación del usuario; el tag `v*` despliega a prod.
- Commits y títulos de PR en español e imperativo: `Agrega esqueleto del backend`. Se validan con `scripts/commit-lint.sh` (instala el hook local con `scripts/instalar-hooks.sh`).
- Nunca se versionan secretos, `google-services.json`, keystores ni `.tfstate`; gitleaks corre en cada PR.

## Primeros pasos

```bash
scripts/instalar-hooks.sh                 # valida tus commits localmente
scripts/test/commit-lint.test.sh          # pruebas del validador
cd android && ./gradlew :app:testDevDebugUnitTest
cd android && ./gradlew :app:installDevDebug   # instala la app dev (ver el paso de la SHA-1 de debug en docs/desarrollo-local.md)
```

Documentación: `scripts/check-links.sh` (enlaces internos) y `scripts/check-adr.sh` (estado y resultados de los ADR) corren en CI (`docs-ci`); sus pruebas están en `scripts/test/check-links.test.sh` y `scripts/test/check-adr.test.sh`.

Más detalle en [docs/desarrollo-local.md](docs/desarrollo-local.md).
