# ParcheU · Plan definitivo

> Versión 1.1 del plan, 22 oct 2026 (ronda de corrección: billing en la 0.1, términos y Ley 1581, memorias por modo, calendario con holgura, retención, reportar desde todas partes; ver §10). Base: propuesta "Ruta segura" (ganadora de la evaluación), con las mejores ideas de "Mejor sustentación" y "Mejor producto" y las correcciones de los jueces.
> Documentos hermanos: [arquitectura.md](arquitectura.md) (detalle técnico), [backlog.md](backlog.md) (épicas y features), [equipo.md](equipo.md) (reparto semanal), [adr/](adr/) (decisiones), [mockups-inventario.md](mockups-inventario.md).

---

## 1. Resumen ejecutivo

- **Meta:** ParcheU publicada en Google Play y funcionando el **30 nov 2026** con las 17 funciones pedidas y ParcheU+, construida por 3 estudiantes con agentes de IA en paralelo.
- **Principio rector: pocas piezas y todas probadas.** Cada pieza nueva tiene que justificarse con una función que no se pueda cumplir sin ella.
- **Android:** el proyecto existente (AGP 9.4.1 con Kotlin integrado) + Compose/M3, MVVM con flujo unidireccional, **Koin** y **DataStore** (nada que dependa de KSP o kapt), Navigation Compose 2.9 con rutas tipadas, Retrofit, grafo dibujado en **Canvas propio** y capturas **Roborazzi** comparables con los mockups.
- **Backend:** **un solo servicio Ktor** (Kotlin, JVM 21) en Cloud Run, imagen con Jib, **Cloud SQL Postgres 16** como única fuente de verdad, migraciones Flyway en un **Cloud Run Job** antes de cada despliegue.
- **Tiempo real:** Firestore como **canal de solo lectura** escrito por el backend: chat, progreso de simulación y estado de la fila. Los clientes nunca escriben en Firestore.
- **IA:** Vertex AI. **Gemini Flash** por defecto y Claude opcional por configuración (solo después de confirmar que los créditos lo cubren). Cada gemelo es un agente independiente; un juez con rúbrica por modo puntúa. Presupuesto diario escalonado, caché y cuotas en el servidor.
- **Privacidad:** una sola `GraphVisibilityPolicy` más filtros en SQL y una **suite de pruebas por propiedades** que garantiza que Pareja, ocultos, bloqueados y borrados nunca aparecen a terceros (con demo de mutación para la sustentación).
- **Riesgo n.º 1, no técnico:** la regla de Play de **12 testers durante 14 días** en prueba cerrada, que corre desde que Play **aprueba** la versión y los testers se **inscriben**, no desde la subida. Por eso: el **28 oct** sube a mano a la pista interna un AAB con Play Billing (permite crear `parcheu_plus`); el **29 oct** la 0.1 se envía a revisión en la prueba cerrada (aprobación estimada 30-31 oct); la 0.2 con el ciclo principal sale el **9 nov** (fecha de la materia); la 0.3 con billing de punta a punta el **13 nov**; se pide acceso a producción cuando se cumplen los 14 días (**≈14 nov**); la 0.9 se envía a revisión de producción **apenas llega el acceso** y la 1.0 entra como actualización el **24-25 nov**.
- **Ley 1581 desde el primer usuario:** aceptación expresa de términos y política en A4 (versionada), aviso de IA y transferencia a EE. UU., edad pedida a todos, retención definida y exportación y borrado completos.
- **Backlog:** 11 épicas y 69 features pequeñas en 13 olas paralelizables, 28 de ellas major (con revisión obligatoria del agente y revisor par humano a posteriori), con medio día de revisión al final de cada ola.

---

## 2. Arquitectura elegida

```
 Android (Compose, Koin, Retrofit)              GCP proyecto parcheu-{dev|prod} · us-central1
 ┌──────────────────────────┐  HTTPS + ID token ┌──────────────────────────────────────────────┐
 │ UI (A..I, G0, N0)        │ ────────────────▶ │ Cloud Run "api" (Ktor 3, JVM 21, Jib)        │
 │ ViewModels (UDF)         │                   │  /v1/*       usuarios, gemelo, grafo, fila,  │
 │ Repositorios + Fakes     │                   │              simulaciones, chat, social,     │
 │  ├ Retrofit/OkHttp       │                   │              billing, privacidad, admin      │
 │  ├ Firestore (solo leer) │◀─ snapshots ──┐   │  /internal/* OIDC: Tasks, Scheduler, RTDN    │
 │  ├ DataStore             │               │   └──┬────────┬─────────┬──────────┬───────────┘
 │  ├ Play Billing 8        │               │      │        │         │          │
 │  └ FCM                   │◀─ push ────┐  │  Cloud SQL  Vertex AI  Cloud Tasks  Firebase Admin
 └──────────────────────────┘            │  │  Postgres16 Gemini /   simulations  (Auth, FCM,
                                         │  │  (verdad)   Claude     ai-light     Firestore)
                                         │  └── Firestore ◀── escribe solo el backend ──┘
                                         └───── FCM ◀────────────────────────────────┘
 Cloud Run Jobs "migrate" (Flyway, antes de cada deploy de "api"), "seed-demo", "purge-demo" (misma imagen)
 Cloud Tasks: simulations · ai-light · maintenance (tasa máxima = techo de gasto)
 Google Play ─ RTDN ─▶ Pub/Sub ─▶ /internal/billing/rtdn
 Cloud Scheduler ─▶ /internal/jobs/{communities,memories,twin-accuracy,retention}
 Firebase Hosting: /privacidad /terminos /borrar-cuenta /i/{codigo} /.well-known/assetlinks.json /admin.html
 Secret Manager · Artifact Registry · Cloud Logging/Monitoring · Terraform · GitHub Actions + WIF
```

### Por qué esta arquitectura

| Capa | Decisión | Por qué | ADR |
|---|---|---|---|
| Android | Proyecto existente + Compose/M3, MVVM/UDF, Koin, DataStore, Navigation 2.9, Retrofit 3, Canvas propio | AGP 9.4.1 trae Kotlin integrado: Hilt y Room necesitan KSP y su compatibilidad es un riesgo que no paga la nota | [0005](adr/0005-android-koin-datastore-sin-ksp.md), [0006](adr/0006-navegacion-y-esqueleto-android.md) |
| Grafo | Layout radial determinista + Canvas | Coincide con el diseño de anillos, es testeable con capturas y no depende de librerías poco mantenidas | [0007](adr/0007-grafo-canvas-layout-radial.md) |
| Backend | Un servicio Ktor en Cloud Run | Mismo lenguaje que la app, contrato compartido por compilación, una unidad que desplegar | [0002](adr/0002-backend-ktor-un-servicio.md) |
| Datos | Postgres 16 como verdad única; grafo = tabla de aristas | Transacciones para cuotas, fila, decisiones mutuas y billing; amigos de amigos es un join de 2 saltos | [0003](adr/0003-postgres-fuente-de-verdad.md) |
| Migraciones | Flyway en Cloud Run Job, versiones con fecha y hora | Evita la carrera entre instancias y el arranque lento; los agentes no chocan en versiones | [0011](adr/0011-migraciones-flyway-cloud-run-job.md) |
| Tiempo real | Firestore de solo lectura para chat, simulación y fila | Offline y reconexión gratis en Android, sin sockets en Cloud Run, reglas de 15 líneas | [0004](adr/0004-firestore-canal-solo-lectura.md) |
| IA | Vertex AI; Gemini por defecto, Claude opcional | Mismo IAM y créditos; IDs de modelo solo en configuración | [0008](adr/0008-ia-vertex-proveedores-intercambiables.md), [0009](adr/0009-simulacion-agentes-y-juez.md), [0010](adr/0010-control-de-costo-ia.md) |
| Privacidad | Policy única + SQL + pruebas por propiedades | Un solo lugar que auditar y una prueba que falla si alguien rompe la regla | [0012](adr/0012-privacidad-pareja.md) |
| Consentimiento | Tabla `consents` que solo agrega filas: términos y política en A4, aprendizaje por modo | Auditable (Ley 1581 de 2012) | [0017](adr/0017-consentimiento-ley-1581.md) |
| Edad y perfil | Fecha de nacimiento para todos en B0, universidad e intereses | La app es 18+ y F2 necesita esos datos | [0031](adr/0031-edad-y-perfil-en-onboarding.md) |
| Memorias del gemelo | Solo entran a simulaciones de su mismo modo; nunca a la persona general | Pareja nunca se filtra a Amistad o Estudio | [0032](adr/0032-memorias-por-modo.md) |
| Retención | Plazos por tipo de dato, job diario de purga, borrado que limpia jsonb | Data safety, `/privacidad` y Ley 1581 | [0025](adr/0025-borrado-purga-y-retencion.md) |
| Billing | Play Billing 8 + verificación y acknowledge en servidor + RTDN | Patrón oficial; el servidor es la fuente de verdad de Plus | [0013](adr/0013-billing-servidor-rtdn.md) |
| Infra | Dos proyectos GCP, Terraform, WIF | Aislamiento real; sin llaves en el repo | [0014](adr/0014-dos-proyectos-terraform-wif.md) |
| Calendario | Prueba cerrada el 29 oct | Cumplir la regla de 12 × 14 a tiempo | [0016](adr/0016-prueba-cerrada-temprana.md) |

---

## 3. Qué se descartó y por qué

| Opción | Veredicto | Motivo |
|---|---|---|
| CTE recursivas | Descartadas | Solo hay 1 y 2 saltos; dos joins son más rápidos, legibles y testeables |
| pgvector y RAG | Descartados | El vector de rasgos tiene 6 dimensiones (se compara en Kotlin) y cada gemelo tiene pocas memorias aceptadas; no hay búsqueda semántica que lo pague |
| Solo Firebase / Cloud Functions | Descartado | TypeScript u otro runtime, sin transacciones relacionales para cuotas, fila y billing; amigos de amigos caro en lecturas |
| Supabase | Descartado | Duplica auth y tiempo real (Firebase es obligatorio para Google Sign-In y FCM) y saca la IA de los créditos de GCP |
| Render (free) | Descartado | Se duerme por inactividad (arranques de decenas de segundos en la demo), fuera de la red de GCP, sin WIF hacia Vertex |
| Neo4j AuraDB | Descartado | Otro proveedor y otro lenguaje para un grafo de cientos de nodos; pausa por inactividad |
| WebSockets propios | Descartados | En Cloud Run exigen difusión entre instancias, reconexión a mano e instancias calientes |
| Hilt + KSP, Room | Descartados | Riesgo de compatibilidad con AGP 9.4 y su Kotlin integrado; Koin y DataStore cubren lo necesario |
| Navigation 3 con multibinding | Descartado | Poca documentación para agentes; Navigation 2.9 tipado basta |
| Multimódulo grande (build-logic + 25 módulos) | Descartado | Multiplica la configuración de Gradle que se rompe; se usan `:app` + `:designsystem` + `contract` y un esqueleto con todo declarado |
| Dueto (un LLM narra a los dos gemelos) | Descartado | Contradice 'cada gemelo es un agente' y sesga hacia la armonía |
| Acotar la afinidad a [40, 98] | Descartado | Choca con el tono honesto |
| App Check en el día 1 | Diferido | Complica CI y emuladores; cuotas, tope diario y tasa máxima de colas cubren el riesgo hasta la 1.0 ([0015](adr/0015-app-check-diferido.md)) |
| CameraX + ML Kit para QR | Descartado | Google Code Scanner no pide permiso de cámara ni pantallas extra ([0018](adr/0018-qr-google-code-scanner.md)) |
| Macrobenchmark, Baseline Profiles, Maestro en CI | Descartados para la 1.0 | Necesitan emulador en CI: lentos e inestables |
| Verificación de universidad, prueba de carga, outbox con WorkManager | Fuera de la 1.0 | No están en los requisitos |
| Compartir resultados | Retirado | Choca con la privacidad de Pareja y no estaba pedido |

---

## 4. Estructura del monorepo

```
ParcheU/                                  (git: github.com/RareChimera87/ProyectoFinalTecMovil)
├─ android/                               proyecto existente
│  ├─ app/                                features + datos (flavors dev/prod)
│  │  └─ src/main/java/me/santiagoreina/parcheu/
│  │     ├─ navigation/  AppNavHost.kt, Routes.kt     (se fijan en E0.7)
│  │     ├─ di/          AppModules.kt                (se fija en E0.7)
│  │     ├─ feature/<nombre>/  XRoute.kt, XScreen.kt, XViewModel.kt, XModule.kt
│  │     ├─ data/<dominio>/    XRepository.kt, XRepositoryImpl.kt (+ Fake en src/test y src/debug)
│  │     └─ push/, core/  (red, errores, sesión)
│  ├─ designsystem/                       tokens, componentes, mascota, grafo (layout + Canvas)
│  └─ gradle/libs.versions.toml
├─ backend/                               Ktor 3 (un servicio), Gradle independiente
│  └─ src/main/kotlin/me/santiagoreina/parcheu/api/<dominio>/  XRoutes.kt, XService.kt, XRepository.kt
│     src/main/resources/db/migration/    V1__esquema.sql, V2__semillas.sql, V<yyyyMMdd>_<HHmm>__x.sql
├─ contract/                              DTOs @Serializable compartidos (includeBuild desde android y backend)
├─ infra/terraform/{bootstrap, modules/platform, envs/dev, envs/prod}
├─ firebase/                              firestore.rules, índices, tests de reglas, hosting/
├─ scripts/                               commit-lint (+ verbos-imperativo.txt), check-migration-order, demo-mutacion-privacidad,
│                                         grant-admin, crear-proyectos, cargar-secretos, fetch-google-services, lado-a-lado
├─ docs/                                  PLAN, arquitectura, backlog, equipo, adr/, mockups-inventario, mockups/png, revisiones,
│                                         desarrollo-local, operacion, play-console, sustentacion, c4
└─ .github/workflows/                     android-ci, backend-ci, contract-ci, infra-ci, rules-ci, gitleaks, pr-title,
                                          migration-order, revision-agente, major-gate, review-counter, auto-merge,
                                          deploy-dev, deploy-prod, firebase-deploy, android-release
```

---

## 5. Git flow y reglas de revisión

### Ramas
- `main`: solo recibe PR desde `release/*` o `fix/*` urgentes; **lo aprueba el usuario**. Cada merge lleva tag `vX.Y.Z`.
- `dev`: integración. Los PR `feature/*` → `dev` los mezcla (squash) el **agente integrador del workflow** cuando pasan **CI** y la **revisión adversarial del agente revisor** (veredicto como comentario del PR; el integrador pone la etiqueta `revisado-agente`, que exigen los checks `revision-agente` y `major-gate`). No hay revisor humano obligatorio en feature→dev: el repo tiene una sola cuenta de GitHub y la revisión por pares del equipo (equipo.md) es una práctica, no un check. Así 'se mezclan cuando pasa CI y la revisión' es literal y verificable. *(Corregido el 22 oct: la versión inicial exigía la aprobación de otro usuario de GitHub y dejó 40 PR sin mezclar.)*
- `feature/<kebab>`: una por feature del backlog (el nombre está en cada ficha).
- `fix/<kebab>`: correcciones. `release/<versión>`: estabilización antes de `main`.
- El repositorio remoto tiene historial viejo; reemplazarlo (force push) **solo con la aprobación explícita del usuario** en E0.1.

### Límites del check `revision-agente` y pasos humanos (auditoría del 28 oct, C-A1/C-A2)
- `revision-agente` y `major-gate` solo comprueban una **etiqueta**; con una sola cuenta de GitHub, quien abre el PR puede ponérsela. Mitigaciones en código: ambos corren con `pull_request_target` (el YAML sale de `dev`, un PR no puede reescribirlo para aprobarse; no hacen checkout), los checks requeridos de `dev` se fijan al `app_id` de GitHub Actions (`scripts/proteger-ramas.sh`) y un PR que toque los gates, `auto-merge.yml`, `CODEOWNERS` o `proteger-ramas.sh` falla sin la etiqueta `gobernanza`, que revisa una persona.
- **Pendiente humano (C-A2, no es código):** usar una cuenta o GitHub App aparte para los agentes (que no sea administradora) y, cuando exista, activar en la protección de `dev` *Require approvals* con *Prevent self-review* (hoy `required_approving_review_count: 0` por la cuenta única). Pasos en [pendientes-humanos.md](pendientes-humanos.md#gobernanza-de-ci-c-a1c-a2-c-m3).

### Commits y PR
- Commits en **español e imperativo**: "Agrega motor de simulación", "Corrige cálculo de cuota semanal". `pr-title`/`commit-lint` valida el formato y que el verbo inicial esté en una lista blanca de imperativos (`scripts/verbos-imperativo.txt`); no pretende analizar el español en general, así que el revisor par también lo mira.
- Un PR por feature, con la plantilla: qué hace, criterios de aceptación marcados, capturas Roborazzi si hay UI, cómo probar.
- Secretos nunca en el repo (gitleaks en cada PR). `google-services.json`, keystore y credenciales de Play viven en secretos de GitHub o Secret Manager; GCP se autentica con WIF.

### CI en cada PR
lint + tests de la parte tocada (filtros por ruta): `android-ci`, `backend-ci` (Testcontainers solo en CI), `contract-ci`, `infra-ci` (plan de dev comentado), `rules-ci`, `migration-order`, `gitleaks`, `pr-title`.

### Despliegue
- Push a `dev` → `deploy-dev`: Jib → Artifact Registry → job `migrate` → Cloud Run dev → smoke e2e → AAB dev como artefacto.
- Tag `v*` en `main` → `deploy-prod` con el environment `production` (aprobación del usuario). El `terraform apply` de prod lo ejecuta **solo el usuario**.

### Revisiones obligatorias
1. **Antes del merge, en todo PR:** revisión adversarial del agente revisor (hasta 2 rondas de corrección), registrada con la etiqueta `revisado-agente`. En features major, el revisor par humano de [equipo.md](equipo.md#revisores-par) la mira después del merge, en la ventana de revisión de la ola.
2. **Tras cada feature major** (28, listadas en backlog.md §5): revisión en busca de errores sobre `dev` con el diff mezclado, antes de que arranque cualquier feature que dependa de ella.
3. **Cada 3 features pequeñas** (no major) mezcladas: el workflow `review-counter` lleva la cuenta en `docs/revisiones.md` y, al llegar a 3, abre un issue de revisión asignado al dueño de la ola; se hace en la ventana de revisión de esa ola.
4. **Al cerrar cada épica mayor** (E0, E1, E2, E3, E4, E7, E8, E10): revisión de la épica completa.
5. **Revisión final completa** en `release/1.0.0` antes del PR a `main`.

**Hueco en el calendario:** el último medio día de cada ola está reservado para estas revisiones y sus `fix/*` (fechas en backlog.md §3). Toda revisión queda registrada en `docs/revisiones.md` (feature, tipo, fecha, revisor, hallazgos, rama de corrección). Los hallazgos se corrigen en ramas `fix/<kebab>` antes de continuar con features dependientes.

### Releases intermedios (0.1, 0.2, 0.3, 0.9) y 1.0
Todas las versiones que llegan a Play siguen el mismo camino, para que el proceso de la 1.0 ya esté probado:
1. `release/0.x.0` desde `dev` (solo `fix/*` entran a esa rama).
2. PR a `main` → **aprueba el usuario** → merge → tag `v0.x.0`.
3. `deploy-prod` (environment `production`, aprobación del usuario): migración y API en prod (desde la 0.2; la 0.1 no usa backend) y `firebase-deploy` prod (reglas, índices y Hosting).
4. `android-release` (E10.2) construye el AAB `prod` y lo sube a la pista cerrada con notas en es-419. Excepción: la **primera** subida (AAB 0.0.1 del 28 oct, E7.0) es manual desde la consola, como exige Play.
5. `release/*` se mezcla de vuelta a `dev`.

---

## 6. Calendario por sprint

| Sprint | Fechas | Olas | Entregables | Hitos |
|---|---|---|---|---|
| 1 | 22 oct – 2 nov | 1-6 | E0 completo, E1 completo, E2.1-E2.4, E3.1-E3.3, E4.1, E5.3, E7.0, E8.4, E10.1-E10.3 | **22-23 oct:** el usuario crea los proyectos GCP y Firebase; I3 verifica el tipo de cuenta de Play y recluta 15-20 testers. **25 oct:** spike de serialización (ADR-0005). **27 oct:** el usuario aplica Terraform de prod. **28 oct:** AAB 0.0.1 a mano a la pista interna y suscripción `parcheu_plus` creada (E7.0). **29 oct: 0.1 enviada a revisión en prueba cerrada**; aprobación estimada 30-31 oct → testers inscritos ese día (arranca el reloj). **30 oct:** spike de IA (ADR-0008) |
| 2 | 3 – 9 nov | 6-9 | E2.5, E2.6, E3.4, E3.5, E4.2-E4.7, E5.1, E5.2, E7.2, E8.1; si caben: E4.9, E8.3 | **6 nov:** primera simulación real en dev. **8 nov:** `release/0.2.0`. **9 nov: 0.2 con el ciclo principal (login → términos → perfil → gemelo → match → simulación → resultados → conectar → chat → reportar) en prueba cerrada**; primera verificación de login desde Play |
| 3 | 10 – 23 nov | 9-12 | E6 completo, E7.1, E7.3, E7.4, E4.8, E5.4, E8.2, E8.5-E8.8, E9 completo, E10.4-E10.6, E10.8; lo que quedó de la ola 9 | **13 nov:** 0.3 con billing de punta a punta; **14 nov:** compra con license tester. **≈14 nov:** 14 días cumplidos → solicitud de acceso a producción. **18 nov:** decisión de contingencia. **20 nov:** 0.9 (candidata) en prueba cerrada y, apenas llegue el acceso, **enviada a revisión de producción** con publicación administrada. **23 nov: congelamiento de funciones** |
| 4 | 24 – 30 nov | 13 | E10.7, E10.9; solo `fix/*` | **24-25 nov:** `release/1.0.0` → `main` → `v1.0.0` → producción (actualización sobre la 0.9 si ya estaba en revisión) con despliegue escalonado. **30 nov:** en producción y sustentación |

**Tiempos de Play que asume el calendario** (verificar en la consola el día que aplique): revisión de la primera versión de una app nueva en prueba cerrada: horas a ~3 días; respuesta a la solicitud de acceso a producción: hasta ~7 días; revisión de la primera versión de producción de una app con chat entre desconocidos, contenido generado por usuarios y suscripciones: hasta ~7 días; actualizaciones posteriores: normalmente menos. Por eso la 0.9 no espera al congelamiento: se envía a producción en cuanto haya acceso, con **publicación administrada** (verificar) para controlar cuándo sale, y la 1.0 la reemplaza como actualización.

### Contingencia del 9 nov (versión 0.2)
La fecha del 9 nov es de la materia y no se mueve. Lo **imprescindible** de la 0.2: E2.1, E2.2, E2.4, E2.5, E2.6, E4.2-E4.6, E5.1, E5.2, E8.1 (Play pide reportar y bloquear en apps con chat). Si al **6 nov** (fin de la ola 7) hay retraso, se recorta en este orden, sin quitar funciones de la 1.0 (pasan a la 0.3 del 13 nov):
1. E4.9 usuarios demo (la fila se prueba con testers y cuentas `is_test`).
2. E8.3 borrado en la app (la URL `/borrar-cuenta` con solicitud por correo cubre la declaración de Play mientras tanto).
3. E4.7 inicio simplificado: solo cupos y simulación en curso.
4. E3.4 solo D1 con D2 conectado (D3-D5 y las otras variantes de D2 en la 0.3).
5. Modo escenario ya está oculto en la 0.2 (`SCENARIO_MODE_ENABLED=false`, F2 solo ofrece Simulación).
6. E7.2 (billing en el cliente) pasa a la 0.3 junto con E7.1 y E7.3.
Si al 8 nov falta algo imprescindible, la 0.2 sale con lo que haya del ciclo y la nota a testers lo dice; la fecha de subida no se mueve.

Si se atrasa el **sprint 1** (por ejemplo, Terraform o el esqueleto): la 0.1 no depende del backend (E0.7 + E7.0 + E8.4), así que su fecha se protege; I1 ya no carga con E4.7 (API), E4.9, E8.1, E10.2 ni E10.6 (son de I3); además puede posponer E0.9 → E2.3 (la persona puede generarse con el Fake hasta el 3 nov).

### Contingencia del 18 nov (sin quitar ninguna de las 17 funciones)
Si al **18 nov** hay retraso:
- D9 (evolución) se entrega derivada solo de aristas, sin 'Momentos'.
- Comunidades corre a demanda (botón 'recalcular' para admin) en lugar de semanal.
- C4 se limita a aceptar o rechazar memorias; calificaciones y pregunta de control pasan a 1.0.1 (la métrica usa solo memorias aceptadas).
- E8.6 (moderación con IA) se recorta: no es una de las 17 funciones; queda el flujo de reportes con admin.
- E8.7 se reduce a D2, F2 y F8 (las demás entradas a H1 en 1.0.1) y la lista de bloqueados se mantiene (la exige 'siempre accesible').
- Si el acceso a producción no llega antes del 26 nov, o la revisión de producción no termina antes del 30 nov, se sustenta con la app en prueba cerrada (instalada desde Play), la solicitud o el envío registrados y la 1.0 lista para promover.

---

## 7. Riesgos y mitigaciones

| # | Riesgo | Prob. | Impacto | Mitigación |
|---|---|---|---|---|
| 1 | Regla de 12 testers × 14 días (cuenta personal creada después de nov 2023); el reloj corre desde la aprobación y la inscripción | Alta | Bloquea producción | Verificar tipo de cuenta el 22 oct (E10.1); 0.1 enviada el 29 oct (E10.3); enlace de inscripción enviado el mismo día de la aprobación; 15-20 testers y seguimiento diario de inscritos |
| 2 | Revisión de Play tarda o rechaza (primera versión, acceso a producción, UGC, chat con desconocidos, suscripciones, Data safety, edad) | Media | Retraso | Reportar/bloquear desde la 0.2 y desde todas partes en la 0.9; términos, política, retención y borrado listos antes del envío; público 18+ con edad pedida a todos; instrucciones y cuenta para revisores; **0.9 enviada a producción apenas llega el acceso** con publicación administrada; la 1.0 como actualización |
| 2b | Retraso del sprint 1 o 2 frente a la fecha fija del 9 nov | Media | La 0.2 sale incompleta | Medio día de revisión por ola; lista de recortes del 9 nov (§6); la 0.1 no depende del backend; carga de I1 repartida hacia I3 |
| 3 | SHA-1 de Play App Signing sin registrar → Google Sign-In falla en Play | Media | Login roto | Registrar SHA de App Signing en E10.3 y probar con una instalación desde Play |
| 4 | Perfil de pagos o suscripción tardan en estar disponibles | Media | Billing tarde | Perfil de pagos en sprint 1; AAB con Billing subido a mano el 28 oct (E7.0) para crear `parcheu_plus`; E7.2 en el sprint 2; compra con license tester en la 0.3 (13-14 nov); `grant-plus` en dev |
| 5 | Toolchain nuevo (AGP 9.4.1, SDK 37) | Media | Builds rotos | Sin KSP ni kapt; spike de kotlinx.serialization el día 1 (E0.7); Robolectric con sdk 35 |
| 6 | IDs de modelo cambian o se retiran; créditos no cubren Claude | Media | IA rota o cara | IDs solo en configuración, verificados en E0.9; Gemini por defecto; Claude solo si los créditos lo cubren |
| 7 | Gasto de IA (abuso o bucles) | Media | Créditos | Cuotas en servidor, caché por versión del gemelo, `DailyBudgetGuard` escalonado, tasa máxima en Cloud Tasks, alertas de presupuesto |
| 8 | Fila vacía en la demo | Alta | Demo pobre | Fila de 24 h con push, QR para conectar al curso, usuarios semilla `is_demo` en dev y prueba cerrada (E4.9) |
| 9 | Fuga de privacidad (Pareja, ocultos, bloqueados) | Baja | Grave | Policy única + SQL + suite por propiedades (E3.5) + auditoría premium (E7.4) + revisión en features major |
| 10 | Conflictos entre agentes | Media | Tiempo | DTOs completos en E0.2, esqueletos con todo declarado (E0.3, E0.7), migraciones con fecha y check de orden |
| 11 | Testcontainers no corre en local (usuario fuera del grupo docker) | Alta | Fricción | Tests de base de datos solo en CI; `-PsinContenedores` en local; documentar `sudo usermod -aG docker $USER` (lo decide el usuario) |
| 12 | Moderación con 3 personas | Media | Política de Play | Bloqueo inmediato, alertas por correo, `admin.html`, moderación con IA opcional, texto honesto en H2 |
| 13 | Arranque en frío de la JVM | Media | Latencia en demo | `min-instances=1` en prod; migraciones fuera del arranque |
| 14 | Desfase UI ↔ API | Media | Bugs de integración | Contrato compartido por compilación, Fakes, smoke e2e en cada deploy |
| 15 | Incumplir la Ley 1581 con testers reales (sin aceptación ni aviso de IA) | Media | Legal y de Play | Aceptación versionada de términos y política en A4 desde la 0.2 (la 0.1 no trata datos), aviso de IA y de transferencia a EE. UU., exportación y borrado, retención publicada ([0017](adr/0017-consentimiento-ley-1581.md), [0020](adr/0020-region-y-transferencia-internacional.md), [0025](adr/0025-borrado-purga-y-retencion.md)) |
| 16 | Memorias de Pareja filtradas a simulaciones de otros modos | Baja | Grave (requisito 17) | Memorias con modo obligatorio, `TwinPromptBuilder` único, tests de captura de prompt y propiedad en la suite E3.5 ([0032](adr/0032-memorias-por-modo.md)) |
| 17 | Usuarios demo percibidos como cuentas falsas | Media | Confianza y revisión de Play | Insignia visible, nunca en Pareja, no escriben, declarados a los testers, purgados antes de la 1.0 ([0027](adr/0027-usuarios-demo.md)) |
| 18 | Entorno local (Java 17 + JBR 21, sin `google-services.json`) frena a los agentes | Media | Fricción | Toolchains con foojay y `installations.paths`, `scripts/fetch-google-services.sh`, `docs/desarrollo-local.md` (E0.1, E0.3, E0.7) |
| 19 | Versiones '(verificar)' que no existen o no compilan juntas | Media | Builds rotos | E0.3 y E0.7 las fijan con build verde y actualizan arquitectura.md §13; avisos sobre Testcontainers 2.x, Exposed 1.0 y Kotest 6 |

---

## 8. Integración de las ideas de las otras propuestas

| Idea | De | Dónde quedó |
|---|---|---|
| Flyway como Cloud Run Job + `outOfOrder` en dev + check de orden | Mejor sustentación | E0.4, E0.6, ADR-0011 |
| Suite de invariantes de privacidad por propiedades + demo de mutación | Mejor sustentación | E3.5, ADR-0012 |
| ADRs y diagrama C4 | Mejor sustentación | docs/adr/, E10.7 |
| Roborazzi para comparar con mockups | Ambas | E0.7 y todas las features de UI |
| Reparto humano explícito por feature | Mejor producto | backlog.md (campo Dueño), equipo.md |
| Rúbricas por modo y caché por (par, modo, versiones) | Mejor producto | E0.4 (semillas), E4.3 |
| `DailyBudgetGuard` escalonado y tasa máxima de colas | Mejor producto | E0.9, E0.5 |
| Línea 106 y 192 opción 4; Ley 1581 | Mejor producto | E8.1, E8.4, E2.5 |
| Progreso de simulación y fila por Firestore | Ambas | E4.2, E4.3, ADR-0004 |
| Usuarios semilla `is_demo` | Ambas | E4.9 |
| Acknowledge en servidor y `obfuscatedAccountId = sha256(userId)` | Ambas | E7.1, E7.2 |
| Tabla `consents` que solo agrega filas, consentimiento por modo | Ambas | E2.1, E2.5, E8.2 |
| Exportación de datos | Mejor producto | E8.3 |
| Moderación ligera y `/admin` mínimo | Mejor producto | E8.5, E8.6 |
| 'Qué tanto te conoce' con pregunta de control | Mejor producto | E9.3 |
| App Links `/i/{codigo}` con assetlinks | Mejor producto | E6.1 |
| Filtrar HIDDEN y borrados en SQL además de la policy | Corrección de los jueces | E3.1, E6.3 |
| No afirmar que Claude se paga con los créditos | Corrección de los jueces | E0.9, ADR-0008 |

---

## 10. Correcciones de la revisión crítica (ronda 1)

| Hueco detectado | Severidad | Corrección | Dónde |
|---|---|---|---|
| La 0.1 prometía Billing y login sin feature que los entregara | Alta | Nueva **E7.0** (billing mínimo y subida manual del AAB 0.0.1 el 28 oct, crea `parcheu_plus`); la 0.1 ya no promete login; E7.2 pasa al sprint 2 y E7.3 a la ola 10; la **0.3 del 13 nov** trae billing de punta a punta y la compra con license tester es el 14 nov | backlog E7.0, E7.2, E7.3, E10.3; §6; ADR-0016 |
| Consentimiento TERMS/PRIVACY sin uso; Ley 1581 | Alta | Aceptación expresa en A4 con `POST /v1/me/terms`, versión de texto, 403 `TERMS_REQUIRED`; enlaces a la política desde la 0.1; aviso de IA y transferencia | E0.7, E2.1, E2.2, E8.4; ADR-0017 (enmienda), ADR-0020 |
| Memorias de Pareja podían entrar a Amistad o Estudio | Alta | Memorias con modo obligatorio; solo del mismo modo; nunca en la persona general; tests en E4.3, E9.2 y propiedad en E3.5 | ADR-0032; arquitectura §7.1-7.2 |
| Calendario del 9 nov sin holgura ni recortes; I1 sobrecargado | Alta | Olas re-fechadas con medio día de revisión; E0.5 con 3 días; lista de recortes del 9 nov; I3 asume E4.7 (API), E4.9, E8.1, E8.5, E10.2, E10.6 y E7.0; revisores par definidos | backlog §2-§3; §6; equipo.md |
| Tiempos de revisión de Play | Media | El reloj corre desde aprobación e inscripción; envío de la 0.9 a producción apenas llega el acceso, con publicación administrada | §6, ADR-0016 (enmienda), E10.3, E10.8, E10.9 |
| Datos del match anónimo sin fuente; edad solo para Pareja | Media | Pantalla nueva **B0** con fecha para todos, universidad, carrera e intereses (**E2.6**); edición desde C3 | ADR-0031; E2.5, E4.2, E9.1 |
| Nadie desplegaba reglas, índices ni Hosting | Media | Workflow `firebase-deploy` en E0.8 (dev en push, prod con tag) | E0.8, E8.4, arquitectura §8 y §12 |
| Dependencias implícitas | Media | E4.6 → E5.1; E4.7 → E4.3, E3.1; criterio de fof y presentaciones pasado a E6.3 y E6.2; escenario oculto por bandera hasta E4.8; E7.4 → E4.2, E4.8; el chip 'Comunidad' se oculta si no hay comunidades | backlog |
| Nombres inconsistentes | Media | `senderUid` en todos lados; secretos `WIF_SERVICE_ACCOUNT_{DEV,PROD}`; lista única de 'Momentos'; alcances de E5.3 y E5.4 separados; regla de merge aclarada | arquitectura §8.2, E0.6, E5.1, E6.5, E5.3/E5.4, §5 |
| Dueños de backend no claros | Media | Regla de revisor par por capa; E8.2 vertical; tabla de revisores par | backlog 'Cómo leer', equipo.md |
| AA del tema claro | Media | ort → #9E480B, mintt → #0B6B59, regla de texto sobre avatares | ADR-0030; E1.1, E10.4 |
| Retención y datos en jsonb | Media | Tabla de retención, job diario (**E8.8**), registro `PersonalJsonPaths`, simulaciones compartidas y Firestore en la purga | arquitectura §10.2; E8.3; ADR-0025 |
| Reportar y bloquear con alcance corto | Media | **E8.7**: H1 desde D2, F2, C5, E3, D6 y F8, reportes de contenido de IA, lista de bloqueados; 'Silenciar' y criterios completos en E8.1 | backlog E8.1, E8.7, E9.3 |
| Cerrar sesión y exportar sin feature | Media | Cerrar sesión en E8.2; 'Descargar mis datos' en E8.3 | backlog; arquitectura §2.5 |
| Criterios faltantes | Media | Agregados en E7.1, E7.2, E4.2, E6.1, E6.2, E2.3, E4.5, E5.4, E8.4, E9.1, E10.7, E10.9 y E7.3; Roborazzi = regresión y comparación con mockups = revisión humana con checklist | backlog; ADR-0022 |
| ADR faltantes | Media | ADR-0019 a ADR-0032; fechas límite de los spikes en ADR-0005 (25 oct) y ADR-0008 (30 oct) | adr/ |
| Usuarios demo engañosos | Media | Insignia, fuera de Pareja, no escriben, declarados | ADR-0027; E4.9 |
| Jobs, secretos, Firebase y colas | Media | E0.5 crea los tres Jobs, las tres colas con mapa de tareas y los contenedores de secretos; valores por script; proyecto Firebase por el usuario si hace falta | E0.5; arquitectura §12 |
| Versiones '(verificar)' | Media | Criterio de build verde con versiones fijadas en E0.3 y E0.7 | arquitectura §13 |
| JDK local y `google-services.json` | Baja | Toolchains, script de descarga y `docs/desarrollo-local.md` | E0.1, E0.3, E0.7 |
| Proceso de revisión y releases intermedios | Baja | `revision-agente`, `major-gate`, `review-counter`, `docs/revisiones.md`; releases 0.x por el mismo camino que la 1.0; commit-lint con lista blanca | §5; E0.1, E0.6 |
| Evolución del gemelo con 0-1 puntos | Baja | Precisión semanal (`twin_accuracy`, job `twin-accuracy`); estados 0, 1 y ≥ 2 puntos en C3 | arquitectura §7.1; E9.1, E9.3 |

---

## 9. Checklist de publicación en Play

### Cuenta y app (E10.1, desde el 22 oct)
- [ ] Tipo de cuenta (personal u organización) y fecha de creación verificados; regla de 12 × 14 confirmada o descartada.
- [ ] Identidad del desarrollador verificada.
- [ ] App creada: `me.santiagoreina.parcheu`, idioma predeterminado es-419, categoría Social, gratis con compras.
- [ ] Play App Signing activo; llave de subida generada y guardada como secreto de GitHub (nunca en el repo).
- [ ] Cuenta de servicio con permisos de publicación para CI.
- [ ] Perfil de pagos verificado.

### Pista interna y suscripción (E7.0, 28 oct)
- [ ] AAB 0.0.1 con Play Billing subido **a mano** a la pista interna.
- [ ] Suscripción `parcheu_plus` con planes base `mensual` ($5.000 COP, P1M) y `anual` ($45.000 COP, P1Y); ≥ 3 license testers.
- [ ] Pub/Sub `play-rtdn` de prod configurado como tema de notificaciones en tiempo real.

### Prueba cerrada (E10.3, 29 oct)
- [ ] AAB 0.1 enviado a revisión en la pista cerrada (esqueleto, Billing, enlaces a términos y política); fecha de envío y de aprobación registradas.
- [ ] Enlace de inscripción enviado el día de la aprobación; ≥ 12 inscritos ese día (objetivo 15-20); seguimiento diario en `docs/play-console.md` y fecha calculada de los 14 días.
- [ ] Instrucciones a testers: versión temprana, perfiles de demostración marcados, cómo reportar.
- [ ] SHA-1 y SHA-256 de App Signing registrados en Firebase prod; login probado desde una instalación de Play con la 0.2 (9 nov).
- [ ] Compra con license tester verificada en servidor con la 0.3 (14 nov).

### Contenido de la app
- [ ] URL de política de privacidad (`/privacidad`, menciona IA, transferencia a EE. UU., la tabla de retención y la Ley 1581) con la misma versión de texto que acepta A4.
- [ ] Borrado de cuenta: en la app (H3) y URL web (`/borrar-cuenta`).
- [ ] Data safety: datos recopilados (nombre, email, foto, fecha de nacimiento, universidad, carrera, intereses, mensajes, respuestas, identificadores, compras), finalidad (incluido el procesamiento con IA), cifrado en tránsito, borrado a pedido y retención según arquitectura §10.2; cruzado con el esquema.
- [ ] Contenido generado por IA: reporte desde la app (F8 y C4) declarado.
- [ ] Clasificación de contenido (IARC) con interacción entre usuarios y contenido generado por usuarios.
- [ ] Público objetivo: 18+.
- [ ] Anuncios: no.
- [ ] Acceso a la app: instrucciones y cuenta de prueba para revisores.
- [ ] Declaración de contenido generado por usuarios: reportar, bloquear y moderación disponibles.

### Ficha (E10.8)
- [ ] Nombre, descripción corta (≤ 80) y completa (≤ 4.000) en es-419, con tono honesto.
- [ ] Icono 512×512, gráfico destacado 1024×500, ≥ 4 capturas de teléfono.
- [ ] Datos de contacto y sitio web (Hosting).

### Producción
- [ ] 14 días de prueba cerrada cumplidos con ≥ 12 testers (≈14 nov) y solicitud de acceso a producción enviada el mismo día.
- [ ] Apenas llega el acceso: 0.9 enviada a revisión de producción con publicación administrada (verificar la opción en la consola).
- [ ] `release/1.0.0` aprobada por el usuario, tag `v1.0.0`, backend prod desplegado y migrado.
- [ ] `DEMO_USERS_ENABLED=false` y `purge-demo` ejecutado en prod.
- [ ] Revisión final completa sin hallazgos abiertos.
- [ ] AAB 1.0.0 enviado a producción con despliegue escalonado 20 % → 100 %.
- [ ] Crashlytics y alertas activos; runbook en `docs/operacion.md`.
