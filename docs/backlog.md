# ParcheU · Backlog de épicas y features

> Fuente de verdad del trabajo. Orden de ejecución por **olas**: las features de una misma ola no dependen entre sí y pueden ir en paralelo, cada una en su worktree y su rama `feature/<kebab>`. Fechas de referencia del plan del 22 oct 2026. Los tamaños son S (≤ 1 día de agente + revisión), M (1-2 días) y L (2-3 días).

## Cómo leer este backlog

- **Major** = feature decisiva: al mezclarse a `dev` dispara una revisión obligatoria en busca de errores (además de la revisión cada 3 features pequeñas y la revisión final).
- **Dueño** = integrante que revisa, prueba en dispositivo y aprueba. I1 = Santiago (backend, IA, datos, infra), I2 = interfaz y experiencia, I3 = social y publicación. Los agentes implementan.
- **Vertical** = la feature toca backend y Android a la vez (excepción permitida a la regla de no mezclar capas).
- Los DTOs ya existen desde E0.2: una feature de UI **no espera** a su backend; trabaja contra el Fake del repositorio y la integración la valida el smoke e2e (E10.6).
- Reglas para agentes: un PR por feature; solo se tocan los archivos listados en 'Archivos'; dependencias nuevas solo en `libs.versions.toml` (Android) o `backend/gradle/libs.versions.toml`; cambios de DTO solo en `contract/` y en un PR propio si afectan a otra feature; todo repositorio Android nuevo trae su Fake; toda migración nueva se llama `V<yyyyMMdd>_<HHmm>__<descripcion>.sql`. Única excepción a 'no tocar docs': E0.3 y E0.7 actualizan la tabla de versiones de arquitectura.md §13 con las versiones que fijaron.
- **Cómo se mezcla un PR** (ADR-0001, enmienda): `feature/*` → `dev` se mezcla solo (auto-merge squash) cuando están en verde **(1)** la CI de las rutas tocadas y **(2)** el check `revision-agente` (revisión automática en busca de errores sobre el diff del PR). Si el PR lleva la etiqueta `major`, además exige **(3)** la aprobación de un revisor par humano distinto del dueño (check `major-gate`; la tabla de pares está en [equipo.md](equipo.md#revisores-par)). Después del merge, la revisión de errores 'post-merge' de la feature major (y la de cada 3 features pequeñas) se hace sobre `dev` antes de lanzar las features que dependen de ella.
- **Revisor par por capa:** todo PR que toca `backend/` lo aprueba I1 aunque el dueño sea otro; todo PR que toca `android/app/.../feature/` o `android/designsystem/` lo aprueba I2 si el dueño es otro; todo PR que toca `firebase/`, `docs/play-console.md` o el pipeline de release lo aprueba I3 si el dueño es otro.
- **Holgura de revisión:** el último medio día de cada ola está reservado para las revisiones obligatorias y las ramas `fix/*` que salgan de ellas; las features de la ola siguiente arrancan cuando sus dependencias están mezcladas **y revisadas**, no por fecha.

**Totales:** 11 épicas, 69 features, 28 features major.

## 1. Épicas

| Épica | Nombre | Épica mayor | Features | Descripción |
|---|---|---|---|---|
| E0 | Fundaciones | Sí | 9 | Monorepo, contrato, backend base, esquema, infraestructura, CI/CD, esqueleto Android, Firebase y pasarela de IA. |
| E1 | Sistema de diseño | Sí | 5 | Tokens, iconos, componentes base y compuestos, y mascota, fieles al inventario de mockups. |
| E2 | Cuenta y gemelo | Sí | 6 | Sesión, términos y política, onboarding con Google, perfil básico y edad, preguntas del gemelo, modos y consentimiento. |
| E3 | Tu parche | Sí | 5 | API del grafo, layout radial, componente G0, pantalla D1-D5 y suite de invariantes de privacidad. |
| E4 | Conocer | Sí | 9 | Cuotas, fila de match, motor de simulación, resultados, decisión mutua, inicio, modo escenario y usuarios semilla. |
| E5 | Chat y notificaciones | No | 4 | API de chat con espejo en Firestore, UI de chat y lista, push FCM y centro de notificaciones. |
| E6 | Red social | No | 6 | QR y código, presentar a dos amigos, amigos de amigos, comunidades, evolución y 'quién quiere conectar contigo'. |
| E7 | ParcheU+ | Sí | 5 | Billing mínimo para Play, billing en servidor y cliente, paywall y auditoría del contenido premium. |
| E8 | Seguridad y privacidad | Sí | 8 | Reportar y bloquear (desde todas partes, incluido contenido de IA), ajustes de privacidad, cierre de sesión, borrado y exportación, retención, páginas web legales, moderación. |
| E9 | Perfil y gemelo | No | 3 | Perfil C3, extracción de memorias con consentimiento y entrenamiento C4. |
| E10 | Publicación y calidad | Sí | 9 | Play Console, pipeline de release, prueba cerrada temprana, tema claro y accesibilidad, observabilidad, e2e, ficha, docs y release 1.0. |

## 2. Orden de ejecución (tabla)

| Ola | Fechas | Sprint | ID | Feature | Rama | Tam. | Major | Dueño | Depende de |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 22 oct | 1 | E0.1 | Monorepo y git flow | `feature/monorepo-gitflow` | S | No | I1 | - |
| 1 | 22-28 oct | 1 | E10.1 | Configuración de Play Console | `feature/play-console-setup` | S | No | I3 (tarea humana) | - |
| 2 | 23-25 oct | 1 | E0.2 | Módulo de contrato compartido | `feature/contract-module` | M | **Sí** | I1 | E0.1 |
| 2 | 23-25 oct | 1 | E0.5 | Infraestructura con Terraform | `feature/infra-terraform` | L | **Sí** | I1 | E0.1 |
| 3 | 25-27 oct | 1 | E0.3 | Esqueleto del backend | `feature/backend-skeleton` | M | No | I1 | E0.2 |
| 3 | 25-27 oct | 1 | E0.7 | Esqueleto Android | `feature/android-skeleton` | M | **Sí** | I2 | E0.2 |
| 3 | 25-27 oct | 1 | E0.8 | Configuración de Firebase | `feature/firebase-setup` | S | No | I3 | E0.5 |
| 4 | 27-29 oct | 1 | E0.4 | Esquema de base de datos v1 | `feature/db-schema-v1` | M | **Sí** | I1 | E0.3 |
| 4 | 27-29 oct | 1 | E0.6 | Pipelines de CI/CD | `feature/ci-cd-pipelines` | M | **Sí** | I1 | E0.3, E0.5, E0.7 |
| 4 | 27-29 oct | 1 | E1.1 | Tokens de diseño | `feature/design-tokens` | M | **Sí** | I2 | E0.7 |
| 4 | 27-29 oct | 1 | E1.2 | Iconos y marca | `feature/icons-brand` | S | No | I2 | E0.7 |
| 4 | 27-29 oct | 1 | E3.2 | Layout radial del grafo | `feature/graph-layout` | S | No | I2 | E0.7 |
| 4 | 27-29 oct | 1 | E7.0 | Billing mínimo y primera subida | `feature/billing-bootstrap` | S | **Sí** | I3 | E0.7, E10.1 |
| 4 | 27-29 oct | 1 | E8.4 | Páginas web legales | `feature/privacy-web` | S | No | I3 | E0.8 |
| 5 | 29-31 oct | 1 | E0.9 | Pasarela de IA | `feature/ai-gateway` | M | **Sí** | I1 | E0.4 |
| 5 | 29-31 oct | 1 | E1.3 | Componentes base | `feature/components-base` | M | No | I2 | E1.1, E1.2 |
| 5 | 29-31 oct | 1 | E1.5 | Mascota del gemelo | `feature/twin-mascot` | S | No | I2 | E1.1 |
| 5 | 29-31 oct | 1 | E2.1 | API de sesión y usuarios | `feature/users-session-api` | S | No | I1 | E0.4 |
| 5 | 29-31 oct | 1 | E4.1 | Servicio de cuotas | `feature/quota-service` | S | **Sí** | I1 | E0.4 |
| 5 | 29-31 oct | 1 | E10.2 | Pipeline de release Android | `feature/android-release-pipeline` | M | **Sí** | I3 | E0.6, E0.7, E7.0 |
| 5 | 29-31 oct | 1 | E10.3 | Prueba cerrada temprana (0.1) | `feature/early-closed-test` | S | **Sí** | I3 | E7.0, E8.4, E0.8 |
| 6 | 1-3 nov | 1-2 | E1.4 | Componentes compuestos | `feature/components-compound` | M | No | I2 | E1.3 |
| 6 | 1-3 nov | 1-2 | E2.2 | Onboarding y login con Google | `feature/onboarding-login` | M | **Sí** | I2 | E1.3, E2.1, E0.8 |
| 6 | 1-3 nov | 1-2 | E2.3 | API del gemelo | `feature/twin-profile-api` | M | No | I1 | E0.4, E0.9 |
| 6 | 1-3 nov | 1-2 | E2.4 | UI de preguntas del gemelo | `feature/twin-questions-ui` | M | No | I2 | E1.3, E1.5 |
| 6 | 1-3 nov | 1-2 | E3.1 | API del grafo | `feature/graph-api` | M | **Sí** | I1 | E0.4, E2.1 |
| 6 | 1-3 nov | 1-2 | E3.3 | Componente G0 en Canvas | `feature/graph-canvas` | M | **Sí** | I2 | E3.2, E1.1 |
| 6 | 1-3 nov | 1-2 | E5.3 | Push con FCM | `feature/push-fcm` | S | No | I3 | E2.1, E0.8 |
| 7 | 3-6 nov | 2 | E2.5 | Modos, +18 y consentimiento | `feature/modes-age-consent` | M | **Sí** | I2 (UI) + I1 (API) | E2.1, E1.4 |
| 7 | 3-6 nov | 2 | E2.6 | Perfil básico y edad | `feature/basic-profile-age` | M | **Sí** | I2 (UI) + I1 (API) | E2.1, E1.4 |
| 7 | 3-6 nov | 2 | E3.5 | Suite de invariantes de privacidad | `feature/privacy-invariants-suite` | S | **Sí** | I1 | E3.1 |
| 7 | 3-6 nov | 2 | E4.3 | Motor de simulación | `feature/simulation-engine` | L | **Sí** | I1 | E0.9, E2.3, E4.1, E3.1 |
| 7 | 3-6 nov | 2 | E4.4 | UI de match al azar | `feature/match-ui` | M | No | I2 | E1.4, E1.5 |
| 7 | 3-6 nov | 2 | E4.5 | UI de simulación | `feature/simulation-ui` | M | No | I2 | E1.4, E1.5 |
| 7 | 3-6 nov | 2 | E5.1 | API de chat | `feature/chat-api` | M | **Sí** | I1 | E3.1, E0.8 |
| 7 | 3-6 nov | 2 | E5.2 | UI de chat y lista de chats | `feature/chat-ui` | M | No | I2 | E1.4, E0.8 |
| 8 | 5-8 nov | 2 | E3.4 | Pantalla Tu parche | `feature/parche-screen` | M | No | I2 | E3.3, E1.4 |
| 8 | 5-8 nov | 2 | E4.2 | API de fila de match | `feature/match-queue-api` | M | No | I1 | E4.1, E2.5, E2.6 |
| 8 | 5-8 nov | 2 | E4.6 | Resultados y decisión mutua | `feature/results-decision` | M | **Sí** | I2 (UI) + I1 (API) | E4.3, E4.5, E5.1 |
| 8 | 5-8 nov | 2 | E4.7 | Pantalla de inicio | `feature/home-screen` | M | No | I2 (UI) + I3 (API) | E4.1, E4.3, E3.1, E1.4, E1.5 |
| 8 | 5-8 nov | 2 | E7.2 | Billing en el cliente | `feature/billing-client` | M | **Sí** | I3 | E7.0, E0.7 |
| 8 | 5-8 nov | 2 | E8.1 | Reportar y bloquear | `feature/report-block` | M | **Sí** | I3 (UI y API) | E5.1, E1.4 |
| 9 | 8-10 nov | 2-3 | E4.9 | Usuarios semilla de demo | `feature/demo-seed` | S | No | I3 | E4.2, E4.3 |
| 9 | 8-10 nov | 2-3 | E8.3 | Borrado y exportación de cuenta | `feature/account-deletion` | M | **Sí** | I1 | E5.1, E3.1 |
| 10 | 10-12 nov | 3 | E5.4 | Centro de notificaciones | `feature/notifications-center` | S | No | I3 | E5.3, E1.4 |
| 10 | 10-12 nov | 3 | E6.1 | Invitaciones por QR y código | `feature/invites-qr` | M | No | I3 | E3.1, E1.4 |
| 10 | 10-12 nov | 3 | E6.2 | Presentar a dos amigos | `feature/introductions` | M | No | I3 (UI) + I1 (API) | E4.3, E5.1, E8.1 |
| 10 | 10-12 nov | 3 | E6.3 | Amigos de amigos | `feature/friends-of-friends` | M | **Sí** | I1 (API) + I3 (UI) | E3.1, E3.5 |
| 10 | 10-12 nov | 3 | E7.1 | Billing en el servidor | `feature/billing-api` | M | **Sí** | I1 | E4.1, E0.5 |
| 10 | 10-12 nov | 3 | E7.3 | Pantallas de ParcheU+ | `feature/paywall-screens` | M | No | I2 | E7.2, E1.4 |
| 10 | 10-12 nov | 3 | E8.2 | Ajustes de privacidad | `feature/privacy-settings` | M | No | I2 (UI) + I1 (API) | E2.5, E1.4 |
| 10 | 10-12 nov | 3 | E8.5 | Administración de reportes | `feature/admin-reports` | S | No | I3 (UI y API) | E8.1 |
| 10 | 10-12 nov | 3 | E9.1 | Perfil y gemelo | `feature/profile-twin` | M | No | I2 | E2.3, E2.6, E1.4 |
| 10 | 10-12 nov | 3 | E9.2 | Extracción de memorias | `feature/memory-extraction` | M | **Sí** | I1 | E5.1, E0.9, E2.3 |
| 10 | 10-12 nov | 3 | E10.5 | Observabilidad | `feature/observability` | S | No | I1 | E0.5, E4.3 |
| 10 | 10-12 nov | 3 | E10.6 | Smoke e2e | `feature/e2e-smoke` | M | No | I3 | E4.6, E5.1 |
| 11 | 13-17 nov | 3 | E4.8 | Modo escenario | `feature/scenario-mode` | M | No | I1 (API) + I2 (UI) | E4.2, E4.3, E5.1, E5.2 |
| 11 | 13-17 nov | 3 | E6.4 | Comunidades | `feature/communities` | M | No | I1 (job) + I3 (UI) | E3.5, E0.9 |
| 11 | 13-17 nov | 3 | E6.5 | Evolución de la red | `feature/network-evolution` | S | No | I3 | E3.1, E3.3 |
| 11 | 13-17 nov | 3 | E6.6 | Quién quiere conectar contigo | `feature/connect-requests` | S | No | I3 | E4.6, E5.4 |
| 11 | 13-17 nov | 3 | E8.6 | Moderación con IA | `feature/ai-moderation` | S | No | I1 | E8.5, E0.9 |
| 11 | 13-17 nov | 3 | E8.7 | Reportar y bloquear desde todas partes | `feature/report-block-everywhere` | M | No | I3 | E8.1, E8.2, E3.4, E4.4, E4.6 |
| 11 | 13-17 nov | 3 | E8.8 | Retención y purga de datos | `feature/data-retention` | S | No | I1 | E8.3 |
| 11 | 13-17 nov | 3 | E9.3 | Entrena a tu gemelo | `feature/train-twin-ui` | M | No | I2 (UI) + I1 (API) | E9.2, E9.1 |
| 11 | 13-17 nov | 3 | E10.8 | Ficha de Play y Data safety | `feature/store-listing-data-safety` | S | No | I3 | E8.3, E8.4 |
| 12 | 18-21 nov | 3 | E7.4 | Auditoría del contenido premium | `feature/premium-gating-audit` | S | **Sí** | I1 | E7.1, E6.3, E6.4, E6.5, E6.6, E4.6, E4.2, E4.8 |
| 12 | 18-21 nov | 3 | E10.4 | Tema claro y accesibilidad | `feature/light-theme-a11y` | S | No | I2 | E3.4, E4.7, E4.6, E7.3 |
| 13 | 24-27 nov | 4 | E10.7 | Documentación final | `feature/docs-final` | S | No | Todos | E7.4, E10.4 |
| 13 | 24-27 nov | 4 | E10.9 | Release 1.0 | `feature/release-1-0` | S | **Sí** | I1 (aprueba el usuario) | E10.4, E7.4, E10.8 |

**Hitos de publicación que dependen de las olas** (detalle en PLAN.md §6):
- **28 oct:** AAB 0.0.1 subido a mano a la pista interna (E7.0) → se crea la suscripción `parcheu_plus`.
- **29 oct:** `v0.1.0` enviada a revisión en la prueba cerrada (E10.3). El reloj de 14 días corre desde que Play la aprueba y ≥ 12 testers están inscritos (estimado 30-31 oct).
- **9 nov:** `v0.2.0` con el ciclo principal (olas 6-8; lo de la ola 9 entra solo si está mezclado y revisado el 8 nov).
- **13 nov:** `v0.3.0` con billing de punta a punta (E7.1, E7.2, E7.3); **14 nov:** compra con license tester verificada.
- **≈14 nov:** solicitud de acceso a producción (E10.8). **20 nov:** `v0.9.0` candidata. **23 nov:** congelamiento. **24-25 nov:** `v1.0.0`.

## 3. Mapa de paralelismo

Cada ola puede lanzarse completa en paralelo (un agente por feature, cada uno en su worktree). Puntos de contacto conocidos y cómo se evitan:

- `android/gradle/libs.versions.toml`: dos features de la misma ola que agregan dependencias pueden chocar en una línea; se resuelve con rebase trivial. E0.7 ya declara las dependencias previstas de todo el plan para minimizarlo.
- `AppNavHost.kt` y `AppModules.kt`: no se tocan después de E0.7; cada feature reemplaza su propio `XRoute.kt` y `XModule.kt`.
- `Application.kt` del backend: no se toca después de E0.3; cada dominio rellena su `XRoutes.kt`.
- Migraciones: nombre con fecha y hora y check de orden en CI (E0.4).
- `contract/`: un archivo por dominio; si dos features de la misma ola necesitan cambiar el mismo archivo, la segunda espera al merge de la primera.

- **Ola 1 (22 oct, sprint 1):** E0.1 Monorepo y git flow, E10.1 Configuración de Play Console
- **Ola 2 (23-25 oct, sprint 1):** E0.2 Módulo de contrato compartido, E0.5 Infraestructura con Terraform
- **Ola 3 (25-27 oct, sprint 1):** E0.3 Esqueleto del backend, E0.7 Esqueleto Android, E0.8 Configuración de Firebase
- **Ola 4 (27-29 oct, sprint 1):** E0.4 Esquema de base de datos v1, E0.6 Pipelines de CI/CD, E1.1 Tokens de diseño, E1.2 Iconos y marca, E3.2 Layout radial del grafo, E7.0 Billing mínimo y primera subida, E8.4 Páginas web legales
- **Ola 5 (29-31 oct, sprint 1):** E0.9 Pasarela de IA, E1.3 Componentes base, E1.5 Mascota del gemelo, E2.1 API de sesión y usuarios, E4.1 Servicio de cuotas, E10.2 Pipeline de release Android, E10.3 Prueba cerrada temprana (0.1)
- **Ola 6 (1-3 nov, sprints 1-2):** E1.4 Componentes compuestos, E2.2 Onboarding y login con Google, E2.3 API del gemelo, E2.4 UI de preguntas del gemelo, E3.1 API del grafo, E3.3 Componente G0 en Canvas, E5.3 Push con FCM
- **Ola 7 (3-6 nov, sprint 2):** E2.5 Modos, +18 y consentimiento, E2.6 Perfil básico y edad, E3.5 Suite de invariantes de privacidad, E4.3 Motor de simulación, E4.4 UI de match al azar, E4.5 UI de simulación, E5.1 API de chat, E5.2 UI de chat y lista de chats
- **Ola 8 (5-8 nov, sprint 2):** E3.4 Pantalla Tu parche, E4.2 API de fila de match, E4.6 Resultados y decisión mutua, E4.7 Pantalla de inicio, E7.2 Billing en el cliente, E8.1 Reportar y bloquear
- **Ola 9 (8-10 nov, sprints 2-3):** E4.9 Usuarios semilla de demo, E8.3 Borrado y exportación de cuenta
- **Ola 10 (10-12 nov, sprint 3):** E5.4 Centro de notificaciones, E6.1 Invitaciones por QR y código, E6.2 Presentar a dos amigos, E6.3 Amigos de amigos, E7.1 Billing en el servidor, E7.3 Pantallas de ParcheU+, E8.2 Ajustes de privacidad, E8.5 Administración de reportes, E9.1 Perfil y gemelo, E9.2 Extracción de memorias, E10.5 Observabilidad, E10.6 Smoke e2e
- **Ola 11 (13-17 nov, sprint 3):** E4.8 Modo escenario, E6.4 Comunidades, E6.5 Evolución de la red, E6.6 Quién quiere conectar contigo, E8.6 Moderación con IA, E8.7 Reportar y bloquear desde todas partes, E8.8 Retención y purga de datos, E9.3 Entrena a tu gemelo, E10.8 Ficha de Play y Data safety
- **Ola 12 (18-21 nov, sprint 3):** E7.4 Auditoría del contenido premium, E10.4 Tema claro y accesibilidad
- **Ola 13 (24-27 nov, sprint 4):** E10.7 Documentación final, E10.9 Release 1.0

**Ventanas de revisión** (último medio día de cada ola): ola 1: 22 oct (sin major); ola 2: 25 oct: E0.2 y E0.5; ola 3: 27 oct: E0.7; ola 4: 29 oct: E0.4, E0.6, E1.1, E7.0; ola 5: 31 oct: E0.9, E4.1, E10.2, E10.3; cierre de E0; ola 6: 3 nov: E2.2, E3.1, E3.3; cierre de E1; ola 7: 6 nov: E2.5, E2.6, E3.5, E4.3, E5.1; cierre de E2; ola 8: 8 nov: E4.6, E7.2, E8.1; cierre de E3; ola 9: 10 nov: E8.3; cierre de E4 (sin E4.8); ola 10: 12 nov: E6.3, E7.1, E9.2; ola 11: 17 nov: revisión de las pequeñas de E6, E8 y E9; ola 12: 21 nov: E7.4; cierre de E7 y E8; ola 13: 27 nov: E10.9 y revisión final completa.

## 4. Fichas por feature

### E0 · Fundaciones (épica mayor)

Monorepo, contrato, backend base, esquema, infraestructura, CI/CD, esqueleto Android, Firebase y pasarela de IA.

#### E0.1 · Monorepo y git flow

- **Rama:** `feature/monorepo-gitflow`
- **Tamaño:** S · **Épica:** E0 Fundaciones · **Major:** No
- **Ola:** 1 (22 oct, sprint 1) · **Dueño:** I1
- **Depende de:** nada
- **En paralelo con:** E10.1
- **Pantallas del inventario:** -
- **Archivos y módulos:** /, .github/ (pr-title, gitleaks, major-gate), scripts/, docs/revisiones.md, docs/desarrollo-local.md

**Descripción.** Crea la estructura del monorepo (android/, backend/, contract/, infra/, firebase/, scripts/, docs/, .github/), el README, .gitignore raíz (google-services.json, *.jks, *.keystore, *.tfstate*, .env*, local.properties), .editorconfig, CODEOWNERS, plantilla de PR con checklist y los workflows mínimos `pr-title` (español imperativo) y `gitleaks`. Configura ramas main y dev, protección de ramas (en `dev`: checks requeridos de CI, `revision-agente` y `major-gate`; en `main`: PR con aprobación del usuario) y auto-merge con `gh`. Crea `docs/revisiones.md` (bitácora de revisiones: feature, tipo, fecha, hallazgos, rama fix) y `docs/desarrollo-local.md` (JDK 17 y 21, toolchains, `google-services.json`, Testcontainers solo en CI). Conecta el remoto https://github.com/RareChimera87/ProyectoFinalTecMovil; reemplazar el historial viejo (force push) solo con aprobación explícita del usuario.

**Criterios de aceptación (verificables):**

- [ ] Existe un commit inicial en `main` y la rama `dev` creada a partir de él.
- [ ] `main` exige PR con aprobación del usuario; `dev` exige checks verdes y permite auto-merge squash (verificado con `gh api repos/.../branches/{main,dev}/protection`).
- [ ] `scripts/commit-lint.sh` valida el **formato** (mayúscula inicial, sin punto final, ≤ 72 caracteres) y que la primera palabra esté en una **lista blanca** de verbos en imperativo de 3.ª persona (`scripts/verbos-imperativo.txt`: Agrega, Corrige, Elimina, Actualiza, Mueve, Renombra, Documenta, Configura, Implementa, Refactoriza, Prueba, Ajusta...). No pretende analizar el español en general. Acepta 'Agrega esqueleto del backend', rechaza 'Added backend', 'agregado backend' y 'Agregando backend'; lo prueba `scripts/test/commit-lint.bats` o un test en shell equivalente.
- [ ] `gitleaks` corre en cada PR y falla con un secreto de prueba en una rama descartable.
- [ ] Ningún archivo listado en .gitignore aparece en `git ls-files`.

#### E0.2 · Módulo de contrato compartido

- **Rama:** `feature/contract-module`
- **Tamaño:** M · **Épica:** E0 Fundaciones · **Major:** Sí
- **Ola:** 2 (23-25 oct, sprint 1) · **Dueño:** I1
- **Depende de:** E0.1
- **En paralelo con:** E0.5
- **Pantallas del inventario:** -
- **Archivos y módulos:** contract/**

**Descripción.** Proyecto Gradle independiente `contract/` (Kotlin JVM, toolchain 17, kotlinx.serialization) con **todos** los DTOs de la API v1 definidos en arquitectura.md §5, un archivo por dominio (session, twin, graph, match, simulation, chat, social, notifications, billing, safety, home, admin), los enums (Mode, Visibility, EdgeState, SimulationStatus, ErrorCode...) y `ApiError`. Se consume con `includeBuild("../contract")` desde android/ y backend/. Definirlo completo al inicio permite que UI y backend avancen en paralelo con Fakes.

**Criterios de aceptación (verificables):**

- [ ] `./gradlew -p contract test` pasa: round-trip JSON de cada DTO con un ejemplo canónico (archivos en `contract/src/test/resources/examples/*.json`).
- [ ] Test que verifica que cada `ErrorCode` tiene un status HTTP asignado (PLUS_REQUIRED y QUOTA_EXCEEDED=402, TERMS_REQUIRED/AGE_REQUIRED/UNDERAGE/BLOCKED/SUSPENDED/FORBIDDEN=403, VALIDATION=400, NOT_FOUND=404, CONFLICT=409, SCENARIO_ENDED=409, AI_BUDGET=503).
- [ ] Los campos opcionales tienen valor por defecto y `explicitNulls = false`; un JSON con campos desconocidos se deserializa sin error (`ignoreUnknownKeys`).
- [ ] Un archivo por dominio, para que los cambios posteriores de distintos agentes no choquen.

#### E0.3 · Esqueleto del backend

- **Rama:** `feature/backend-skeleton`
- **Tamaño:** M · **Épica:** E0 Fundaciones · **Major:** No
- **Ola:** 3 (25-27 oct, sprint 1) · **Dueño:** I1
- **Depende de:** E0.2
- **En paralelo con:** E0.7, E0.8
- **Pantallas del inventario:** -
- **Archivos y módulos:** backend/** (excepto db/migration), docs/arquitectura.md §13 (solo la tabla de versiones), docs/desarrollo-local.md

**Descripción.** Servicio Ktor 3 (Netty, JVM 21) en `backend/`: configuración por variables de entorno, `StatusPages` que traduce excepciones a `ApiError`, plugin de autenticación con ID token de Firebase (interfaz `TokenVerifier` con implementación Firebase Admin y Fake), verificador OIDC para `/internal/*`, `/v1/health` con versión (SHA), logs JSON con `traceId` tomado de `X-Cloud-Trace-Context`, DI manual por constructores. Declara **todos** los routers por dominio como stubs que responden 501, para que cada feature solo rellene su archivo. Imagen con Jib (`eclipse-temurin:21-jre`, verificar tag) y `main` con cuatro modos: `serve`, `migrate`, `seed-demo` y `purge-demo` (la misma imagen sirve para todos los Cloud Run Jobs). **Toolchains:** `settings.gradle.kts` aplica el plugin `org.gradle.toolchains.foojay-resolver-convention` (verificar versión) y `gradle.properties` documenta `org.gradle.java.installations.paths=/opt/android-studio/jbr` para usar el JBR 21 local sin descargas; el procedimiento queda en `docs/desarrollo-local.md`. **Versiones:** fija en `backend/gradle/libs.versions.toml` cada librería de arquitectura.md §13 marcada '(verificar)' que use el backend y actualiza esa tabla en el mismo PR (excepción permitida).

**Criterios de aceptación (verificables):**

- [ ] `testApplication`: GET /v1/health → 200 con `version`; ruta /v1/me sin token → 401; con token Fake → no 401.
- [ ] Una excepción de dominio `QuotaExceeded(resetsAt)` produce 402 con cuerpo `ApiError{code=QUOTA_EXCEEDED, resetsAt}`.
- [ ] `/internal/tasks/simulate` sin token OIDC válido → 403; con el token Fake de la cuenta de tareas → no 403.
- [ ] `./gradlew -p backend jibBuildTar` genera la imagen sin daemon de Docker.
- [ ] Cada dominio tiene su `XRoutes.kt` registrado en `Application.kt`; agregar lógica no requiere tocar `Application.kt`.
- [ ] `./gradlew -p backend build` es verde en una máquina con solo Java 17 en el PATH y el JBR 21 en `/opt/android-studio/jbr` (toolchain resuelto sin intervención), y en CI.
- [ ] Ninguna versión del backend queda como '(verificar)' en arquitectura.md §13: cada una está fijada y es la que compila; para Testcontainers se usa la serie y los nombres de artefacto vigentes (2.x: `testcontainers-postgresql`, `testcontainers-junit-jupiter`, verificar) y para Exposed una sola serie (no se mezclan paquetes de 0.x y 1.x).

#### E0.4 · Esquema de base de datos v1

- **Rama:** `feature/db-schema-v1`
- **Tamaño:** M · **Épica:** E0 Fundaciones · **Major:** Sí
- **Ola:** 4 (27-29 oct, sprint 1) · **Dueño:** I1
- **Depende de:** E0.3
- **En paralelo con:** E0.6, E1.1, E1.2, E3.2, E7.0, E8.4
- **Pantallas del inventario:** -
- **Archivos y módulos:** backend/src/main/resources/db/migration/, backend/.../db/, scripts/check-migration-order.sh

**Descripción.** Migraciones Flyway: `V1__esquema.sql` con todas las tablas, vistas e índices de arquitectura.md §4, y `V2__semillas.sql` con las 12 preguntas (pesos por rasgo), los catálogos `universities` e `interests` (para B0, E2.6) y los escenarios por modo (al menos 2 básicos y 2 premium por modo) con su `rubric` jsonb. Tablas Exposed por dominio. Fábrica de conexión (Cloud SQL Java Connector con IAM en GCP, URL JDBC en local/CI). Configuración Flyway: `outOfOrder=true` en dev y CI; las siguientes migraciones se nombran `V<yyyyMMdd>_<HHmm>__<descripcion>.sql`. Script `scripts/check-migration-order.sh` que falla si un PR agrega una versión menor que la última de `dev`.

**Criterios de aceptación (verificables):**

- [ ] Testcontainers (solo en CI) aplica V1 y V2 sobre Postgres 16 limpio sin errores.
- [ ] Insertar una arista con `user_low >= user_high` viola el CHECK.
- [ ] Las semillas contienen 12 preguntas, ≥ 8 universidades más 'Otra', ≥ 20 intereses y, para cada modo, ≥2 escenarios básicos y ≥2 premium, cada uno con rúbrica de ≥4 dimensiones.
- [ ] `check-migration-order.sh` rechaza un archivo con versión menor a la máxima de `origin/dev` (probado con fixtures en `scripts/test/`).
- [ ] `./gradlew -p backend test -PsinContenedores` corre localmente omitiendo los tests de Testcontainers (el usuario no está en el grupo docker).

#### E0.5 · Infraestructura con Terraform

- **Rama:** `feature/infra-terraform`
- **Tamaño:** L · **Épica:** E0 Fundaciones · **Major:** Sí
- **Ola:** 2 (23-25 oct, sprint 1) · **Dueño:** I1
- **Depende de:** E0.1
- **En paralelo con:** E0.2
- **Pantallas del inventario:** -
- **Archivos y módulos:** infra/terraform/**, scripts/crear-proyectos.sh, scripts/cargar-secretos.sh, scripts/check-plan.sh

**Descripción.** Terraform 1.15 (provider google y google-beta 7.x, verificar). `infra/terraform/bootstrap` (bucket de estado versionado, pool y provider WIF restringidos al repo de GitHub, cuenta de servicio de despliegue), `modules/platform` (APIs, Artifact Registry, Cloud Run `api` y los Cloud Run Jobs `migrate`, `seed-demo` y `purge-demo` con la misma imagen y distinto argumento, Cloud SQL Postgres 16, Secret Manager con los **contenedores** de secretos `hmac-anon-key` y `play-sa` (sin valor), colas Cloud Tasks `simulations`, `ai-light` y `maintenance` con tasa máxima, Cloud Scheduler (`communities`, `memories`, `twin-accuracy`, `retention`), Pub/Sub `play-rtdn` con suscripción push OIDC, Firestore nativo en nam5, `google_firebase_project` (google-beta) sobre cada proyecto, cuentas de servicio de mínimo privilegio, presupuesto de facturación 50/90 %, uptime check) y `envs/{dev,prod}`. Mapa de colas: `simulations` ← `simulate`, `judge-scenario`; `ai-light` ← `build-persona`, `extract-memories`, `moderate`, `name-community`; `maintenance` ← `purge-user` (sin IA). El `apply` de dev lo hace I1 o CI; el de prod **solo el usuario** (27 oct).

**Prerrequisitos humanos (el usuario, 22-23 oct):** crear los proyectos `parcheu-dev` y `parcheu-prod` con `scripts/crear-proyectos.sh` (usa su ADC: `gcloud projects create` + vincular facturación) y, si `google_firebase_project` falla por términos sin aceptar, agregar Firebase a cada proyecto desde la consola (una vez, 5 min) e importarlo al estado. Los **valores** de los secretos los carga `scripts/cargar-secretos.sh` (`openssl rand -base64 32` → `gcloud secrets versions add`): I1 en dev, el usuario en prod; nunca pasan por Terraform ni por el repo.

**Criterios de aceptación (verificables):**

- [ ] `terraform fmt -check -recursive` y `terraform validate` pasan en ambos entornos.
- [ ] `terraform plan` de dev no tiene errores en CI vía WIF (sin llaves JSON).
- [ ] La cuenta de servicio de `api` no tiene roles de propietario ni editor (test con `conftest` o script `scripts/check-iam.sh` sobre el plan en JSON).
- [ ] Cola `simulations` con `max_dispatches_per_second` y `max_concurrent_dispatches` definidos por variable (techo de gasto).
- [ ] Dev con `min_instance_count=0`; prod con 1 y máximo 5.
- [ ] El plan contiene los tres Cloud Run Jobs, las tres colas con el mapa de tareas documentado en `infra/terraform/README.md` y los secretos sin `secret_data` (test con `scripts/check-plan.sh` sobre `terraform show -json`).
- [ ] `sa-deployer` tiene los roles para desplegar reglas, índices y Hosting de Firebase (`firebaserules.admin`, `datastore.indexAdmin`, `firebasehosting.admin`; verificar nombres) que usa E0.8.

#### E0.6 · Pipelines de CI/CD

- **Rama:** `feature/ci-cd-pipelines`
- **Tamaño:** M · **Épica:** E0 Fundaciones · **Major:** Sí
- **Ola:** 4 (27-29 oct, sprint 1) · **Dueño:** I1
- **Depende de:** E0.3, E0.5, E0.7
- **En paralelo con:** E0.4, E1.1, E1.2, E3.2, E7.0, E8.4
- **Pantallas del inventario:** -
- **Archivos y módulos:** .github/workflows/**

**Descripción.** Workflows de GitHub Actions con filtros por ruta: `android-ci` (ktlint, lint, tests JVM, verificación Roborazzi), `backend-ci` (tests con Testcontainers, `jibBuildTar`), `contract-ci`, `infra-ci` (fmt, validate, plan de dev comentado en el PR), `rules-ci` (emulador de Firestore), `migration-order`, `auto-merge` (`gh pr merge --auto --squash` para PR a dev; los checks requeridos son la CI aplicable, `revision-agente` y `major-gate`), `revision-agente` (lanza la revisión automática en busca de errores sobre el diff y publica el resultado como check), `major-gate` (pasa si el PR no tiene la etiqueta `major` o si tiene una aprobación de alguien distinto del autor), `review-counter` (al mezclar a dev cuenta las features no major mezcladas desde la última revisión en `docs/revisiones.md` y, al llegar a 3, abre un issue 'Revisión de 3 features pequeñas' asignado al dueño de la ola), `deploy-dev` (push a dev: Jib → Artifact Registry → ejecutar job `migrate` → desplegar `api` → smoke de /v1/health) y `deploy-prod` (tag `v*`, environment `production` con aprobación del usuario).

**Criterios de aceptación (verificables):**

- [ ] Un PR de prueba muestra todos los checks aplicables en verde y se mezcla solo a dev.
- [ ] Un PR que rompe un test de Android queda bloqueado.
- [ ] Tras mezclar a dev, `/v1/health` de dev responde con el SHA del commit.
- [ ] El job `migrate` corre **antes** del despliegue de la API; si falla, la API no se despliega.
- [ ] No hay llaves JSON en secretos de GitHub para GCP: solo identificadores de WIF, sin credenciales: `WIF_PROVIDER`, `WIF_PROVIDER_PROD`, `WIF_SERVICE_ACCOUNT_DEV`, `WIF_SERVICE_ACCOUNT_PROD`, `TF_PLAN_SERVICE_ACCOUNT_DEV` y `TF_SERVICE_ACCOUNT_DEV` (mismos nombres que arquitectura.md §12; hay un pool WIF por proyecto, ADR-0014).
- [ ] Un PR con etiqueta `major` y CI verde **no** se mezcla hasta que un revisor distinto del autor lo aprueba; uno sin la etiqueta se mezcla con CI y `revision-agente` en verde.
- [ ] Tras mezclar 3 PR no major, `review-counter` abre exactamente un issue de revisión (probado con un fixture de `docs/revisiones.md`).

#### E0.7 · Esqueleto Android

- **Rama:** `feature/android-skeleton`
- **Tamaño:** M · **Épica:** E0 Fundaciones · **Major:** Sí
- **Ola:** 3 (25-27 oct, sprint 1) · **Dueño:** I2
- **Depende de:** E0.2
- **En paralelo con:** E0.3, E0.8
- **Pantallas del inventario:** N0 (stub)
- **Archivos y módulos:** android/app/**, android/designsystem/ (vacío), android/gradle/libs.versions.toml, android/settings.gradle.kts, scripts/fetch-google-services.sh, docs/arquitectura.md §13 (solo la tabla de versiones)

**Descripción.** Sobre el proyecto existente: Java 17, flavors `dev` (`.dev`, backend dev) y `prod`, `BuildConfig.API_URL`, R8 activado en release con reglas para kotlinx.serialization, `dynamicColor=false`, módulo `:designsystem` vacío, `includeBuild("../contract")`, Koin, Navigation Compose 2.9 con **todas** las rutas del inventario como `@Serializable` y una pantalla stub por ruta (cada feature reemplaza solo su archivo `feature/<x>/XRoute.kt`), módulos Koin vacíos por feature declarados en `AppModules.kt`, Retrofit 3 + OkHttp 5 + `AuthInterceptor` y `Authenticator` (refresco ante 401), mapeo de `ApiError` a `AppError`, base `UiState`, Robolectric (`@Config(sdk=[35])`) y Roborazzi configurados, Spotless/ktlint. El stub de A4 ya muestra los enlaces 'Términos' y 'Política de privacidad' (URLs de Hosting en `BuildConfig`) para que la 0.1 los tenga. Versiones: fija en `libs.versions.toml` cada librería Android de arquitectura.md §13 (incluida `billing-ktx`, que agrega E7.0) y actualiza esa tabla en el mismo PR. `scripts/fetch-google-services.sh` descarga el `google-services.json` del flavor con `firebase apps:sdkconfig` y el ADC del desarrollador (los agentes lo ejecutan en su worktree; en CI sale de un secreto). Spike del día 1 (**fecha límite 25 oct**): confirmar que el plugin de kotlinx.serialization funciona con el Kotlin integrado de AGP 9.4.1 y anotar el resultado en ADR-0005.

**Criterios de aceptación (verificables):**

- [ ] `./gradlew :app:assembleDevDebug :app:testDevDebugUnitTest :app:lintDevDebug` verde.
- [ ] `./gradlew :app:assembleProdRelease` compila con R8 activado.
- [ ] Test Robolectric parametrizado: cada ruta del inventario (A1..H3, lista de chats, confirmaciones) se puede navegar y muestra su stub.
- [ ] Test: un 402 con `QUOTA_EXCEEDED` se mapea a `AppError.QuotaExceeded(kind, resetsAt)` y un 402 `PLUS_REQUIRED` a `AppError.PlusRequired`.
- [ ] Test: ante un 401 el `Authenticator` pide un token nuevo una sola vez y reintenta.
- [ ] Roborazzi graba y verifica una captura de ejemplo en CI con `changeThreshold` documentado (0,01).
- [ ] Ninguna versión Android queda como '(verificar)' en arquitectura.md §13 y el build está verde con esas versiones.
- [ ] En un worktree limpio, `scripts/fetch-google-services.sh dev` deja el archivo y `assembleDevDebug` compila; `git status` no lo muestra (está en .gitignore).

#### E0.8 · Configuración de Firebase

- **Rama:** `feature/firebase-setup`
- **Tamaño:** S · **Épica:** E0 Fundaciones · **Major:** No
- **Ola:** 3 (25-27 oct, sprint 1) · **Dueño:** I3
- **Depende de:** E0.5
- **En paralelo con:** E0.3, E0.7
- **Pantallas del inventario:** -
- **Archivos y módulos:** firebase/**, .github/workflows/firebase-deploy.yml

**Descripción.** Carpeta `firebase/` con `firebase.json`, `.firebaserc` (alias dev y prod), `firestore.rules` (todo denegado por defecto; lectura de `chats/{id}` y `chats/{id}/messages` solo para miembros; `simulations/{id}` solo para participantes; `matchTickets/{uid}` solo para el dueño; **ninguna escritura de clientes**), índices, tests de reglas con `@firebase/rules-unit-testing` en el emulador, registro de las apps Android dev y prod, SHA-1/SHA-256 de debug registrados y Hosting vacío. Workflow `firebase-deploy` (WIF, cuenta `sa-deployer`): en push a `dev` con cambios en `firebase/**` despliega `firestore:rules`, `firestore:indexes` y `hosting` al proyecto dev; con tag `v*` (environment `production`, aprobación del usuario) hace lo mismo en prod. Documento de mensaje de Firestore: `senderUid` (uid de Firebase; `null` si es del sistema), `system`, `text`, `createdAt`, `hidden` (arquitectura.md §8.2). `google-services.json` se inyecta desde secretos de CI, nunca se versiona.

**Criterios de aceptación (verificables):**

- [ ] Tests de reglas: un no miembro no lee mensajes; un miembro sí; cualquier escritura de cliente (incluido `typing`) es denegada; el dueño lee `matchTickets/{uid}` y otro usuario no.
- [ ] `rules-ci` corre los tests en el emulador en cada PR que toca `firebase/`.
- [ ] Tras mezclar a dev, `firebase-deploy` publica reglas, índices y Hosting en dev (la página vacía responde 200); un tag de prueba `v0.0.0-rc` en una rama descartable pide la aprobación de `production`.
- [ ] La app dev inicia sesión con Google en un emulador con el SHA-1 de debug registrado (verificación manual documentada en el PR).

#### E0.9 · Pasarela de IA

- **Rama:** `feature/ai-gateway`
- **Tamaño:** M · **Épica:** E0 Fundaciones · **Major:** Sí
- **Ola:** 5 (29-31 oct, sprint 1) · **Dueño:** I1
- **Depende de:** E0.4
- **En paralelo con:** E1.3, E1.5, E2.1, E4.1, E10.2, E10.3
- **Pantallas del inventario:** -
- **Archivos y módulos:** backend/.../ai/**

**Descripción.** Interfaz `LlmClient` (texto y JSON con esquema), `GeminiVertexClient` (SDK `com.google.genai:google-genai` en modo Vertex, verificar versión), `ClaudeVertexClient` (SDK Java de Anthropic con backend Vertex, verificar artefacto), `FakeLlmClient`. Configuración `AI_PROVIDER`, `AI_MODEL_CHAT`, `AI_MODEL_JUDGE`, `AI_MODEL_LITE`, `AI_DAILY_BUDGET_USD` y tabla de precios por modelo. Registro de cada llamada en `ai_usage` (propósito, modelo, tokens, costo) y agregado en `ai_usage_daily`. `DailyBudgetGuard` escalonado: normal < 70 %, degradado (todo pasa a `AI_MODEL_LITE`) entre 70 y 100 %, bloqueado ≥ 100 % (`AiBudgetExceeded` → 503 `AI_BUDGET`). **Spike:** fijar en configuración los IDs GA vigentes de Gemini Flash y Flash-Lite en Vertex y verificar si los créditos promocionales cubren modelos de socios (Claude); anotar el resultado en ADR-0008. Gemini queda como predeterminado. **Fecha límite del spike: 30 oct.**

**Criterios de aceptación (verificables):**

- [ ] Tests del guard: con 69 % de gasto usa el modelo configurado; con 70 % usa el lite; con 100 % lanza `AiBudgetExceeded` sin llamar al proveedor.
- [ ] Test: `AI_PROVIDER=claude` instancia `ClaudeVertexClient`; `gemini` instancia `GeminiVertexClient`; valor inválido falla al arrancar.
- [ ] Test: cada llamada del Fake registra una fila en `ai_usage` con costo calculado de la tabla de precios.
- [ ] Test de humo etiquetado `@Tag("vertex")` (manual, no en CI por defecto) obtiene una respuesta JSON válida de Vertex dev.
- [ ] ADR-0008 actualizado con los IDs de modelo verificados y la respuesta sobre créditos para Claude.

### E1 · Sistema de diseño (épica mayor)

Tokens, iconos, componentes base y compuestos, y mascota, fieles al inventario de mockups.

#### E1.1 · Tokens de diseño

- **Rama:** `feature/design-tokens`
- **Tamaño:** M · **Épica:** E1 Sistema de diseño · **Major:** Sí
- **Ola:** 4 (27-29 oct, sprint 1) · **Dueño:** I2
- **Depende de:** E0.7
- **En paralelo con:** E0.4, E0.6, E1.2, E3.2, E7.0, E8.4
- **Pantallas del inventario:** S0, I1-I4 (base)
- **Archivos y módulos:** android/designsystem/src/main/.../theme/**, res/font/**

**Descripción.** En `:designsystem`: `darkColorScheme` y `lightColorScheme` M3 con los tokens del inventario §3, extensión `ParcheUColors` (s1, s2, ln, mu, prt, prc, onprc, colores y tints de modo, acento, degradados, scrim) y token `error` separado de coralt. Tipografía con Sora e Inter empaquetadas en `res/font` (OFL), formas, espaciado y la función `affinityAlpha(a) = clamp(.22 + .78·(a-50)/45, .22, 1)`. **Ajustes AA del tema claro (ADR-0030):** `ort` pasa de #B8560F a **#9E480B** y `mintt` de #0E7A65 a **#0B6B59** (los hex del inventario no llegan a 4.5:1 sobre s2 y prc); `onAvatarColor(alpha, tema)` elige el texto de las iniciales: en oscuro siempre blanco; en claro #14111F si `alpha ≤ 0.80` y, si `alpha > 0.80`, el relleno se fija en `alpha = 1.0` con texto blanco (5,15:1).

**Criterios de aceptación (verificables):**

- [ ] Test: `affinityAlpha(50)=0.22`, `affinityAlpha(95)=1.0`, `affinityAlpha(72)≈0.601` (±0.001), `affinityAlpha(30)=0.22`.
- [ ] Test de contraste: #14111F sobre #3A86FF ≥ 4.5:1 (≈5.3) y blanco sobre #3A86FF < 4.5:1, lo que documenta la decisión de mantener texto oscuro en Estudio.
- [ ] Test: todos los pares texto/fondo declarados en ambos temas cumplen AA (4.5:1 texto normal, 3:1 texto ≥ 18sp), incluidos ort, mintt, coralt y bluet sobre bg, s1, s2 y prc del tema claro (con los valores corregidos).
- [ ] Test: para alpha en {0.22, 0.5, 0.8, 0.85, 0.9, 1.0} y ambos temas, el texto de `AvatarInitials` sobre el relleno resultante tiene contraste ≥ 4.5:1.
- [ ] Roborazzi de una hoja de tokens (equivalente a S0) en oscuro y claro.

#### E1.2 · Iconos y marca

- **Rama:** `feature/icons-brand`
- **Tamaño:** S · **Épica:** E1 Sistema de diseño · **Major:** No
- **Ola:** 4 (27-29 oct, sprint 1) · **Dueño:** I2
- **Depende de:** E0.7
- **En paralelo con:** E0.4, E0.6, E1.1, E3.2, E7.0, E8.4
- **Pantallas del inventario:** S0, A1
- **Archivos y módulos:** android/designsystem/src/main/res/drawable/**, android/app/src/main/res/mipmap-*/**

**Descripción.** Unos 45 Material Symbols Rounded como vector drawables (licencia Apache 2.0, sin `material-icons-extended`), composable `Wordmark` (U en #FF8A3D; tamaños 20, 22 y 44), icono adaptativo de lanzador a partir del AppIcon y splash con `core-splashscreen` sobre #14111F.

**Criterios de aceptación (verificables):**

- [ ] Roborazzi del Wordmark en los 3 tamaños y en ambos temas.
- [ ] Lint sin advertencias de recursos faltantes; el icono adaptativo tiene capa monocroma.
- [ ] Test: cada ícono usado en el inventario existe como `R.drawable.ic_*` (lista en un test).

#### E1.3 · Componentes base

- **Rama:** `feature/components-base`
- **Tamaño:** M · **Épica:** E1 Sistema de diseño · **Major:** No
- **Ola:** 5 (29-31 oct, sprint 1) · **Dueño:** I2
- **Depende de:** E1.1, E1.2
- **En paralelo con:** E0.9, E1.5, E2.1, E4.1, E10.2, E10.3
- **Pantallas del inventario:** N0 y componentes de A-I
- **Archivos y módulos:** android/designsystem/.../components/base/**

**Descripción.** Botones (Primary, Tonal, Outline, Text, GoogleSignIn), ModeChip M (28) y S (24), ModeFilterChip, SecondaryFilterChip, controles segmentados, InterestChip, PillChip, Switch, Checkbox, RadioRow, campos de texto, Card y variantes, SectionHeader, TopAppBar (variantes inicio, raíz, hija y chat), NavBar con badge de no leídos, LinearProgress, SegmentedQuota y ParcheUPlusBadge.

**Criterios de aceptación (verificables):**

- [ ] Roborazzi por componente y estado (habilitado, deshabilitado, seleccionado) en ambos temas.
- [ ] Test de semántica: todo elemento interactivo mide ≥ 48dp y tiene rol y descripción.
- [ ] Test: NavBar resalta el ítem activo y muestra badge solo si `unread > 0`.

#### E1.4 · Componentes compuestos

- **Rama:** `feature/components-compound`
- **Tamaño:** M · **Épica:** E1 Sistema de diseño · **Major:** No
- **Ola:** 6 (1-3 nov, sprints 1-2) · **Dueño:** I2
- **Depende de:** E1.3
- **En paralelo con:** E2.2, E2.3, E2.4, E3.1, E3.3, E5.3
- **Pantallas del inventario:** Componentes de B-H
- **Archivos y módulos:** android/designsystem/.../components/compound/**

**Descripción.** BottomSheet, ModalDialog con scrim, LockedOverlay (blur en API ≥ 31 y scrim opaco en < 31), LockedFeatureBanner, AvatarInitials (alpha por afinidad y variante anónima), StackedAvatars, AffinityRing, ChatBubble y ChatInputBar, InfoBanner/InfoNote/ConsentBanner, InfoListCard, EmptyState/ErrorState/GraphSkeleton, SettingsRow (destructiva con el token error), NotificationRow, CelebrationBackground con confeti, DecisionFooter, LoadingDots, PageIndicator, StatCard, QRCard y LineChart.

**Criterios de aceptación (verificables):**

- [ ] Roborazzi de cada componente en ambos temas.
- [ ] LockedOverlay: Robolectric con `sdk=30` dibuja scrim opaco y con `sdk=35` aplica blur (dos capturas distintas).
- [ ] Test: LockedOverlay no recibe datos reales; su API solo acepta un `placeholder` decorativo.
- [ ] Test: AvatarInitials anónimo no muestra iniciales y tiene descripción 'Alguien'.

#### E1.5 · Mascota del gemelo

- **Rama:** `feature/twin-mascot`
- **Tamaño:** S · **Épica:** E1 Sistema de diseño · **Major:** No
- **Ola:** 5 (29-31 oct, sprint 1) · **Dueño:** I2
- **Depende de:** E1.1
- **En paralelo con:** E0.9, E1.3, E2.1, E4.1, E10.2, E10.3
- **Pantallas del inventario:** A2, A3, B1, B5, C1, C3, F1-F8
- **Archivos y módulos:** android/designsystem/.../mascot/**

**Descripción.** `TwinMascot` dibujado en Canvas (blob, antena, ojos y boca), color por rol (violeta usuario, color de modo para el otro), proporción 1:1.125, tamaños S 30, M 56, L 80 y XL 116, estado de error (opacidad .5 y escala de grises) y animaciones de 'conversando'.

**Criterios de aceptación (verificables):**

- [ ] Roborazzi de los 4 tamaños, 4 colores y estado de error.
- [ ] Test: alto = 1.125 × ancho para cada tamaño.
- [ ] Las animaciones respetan `Settings.Global.ANIMATOR_DURATION_SCALE = 0` (test con reglas de Compose).

### E2 · Cuenta y gemelo (épica mayor)

Sesión, términos y política, onboarding con Google, perfil básico y edad, preguntas del gemelo, modos y consentimiento.

#### E2.1 · API de sesión y usuarios

- **Rama:** `feature/users-session-api`
- **Tamaño:** S · **Épica:** E2 Cuenta y gemelo · **Major:** No
- **Ola:** 5 (29-31 oct, sprint 1) · **Dueño:** I1
- **Depende de:** E0.4
- **En paralelo con:** E0.9, E1.3, E1.5, E4.1, E10.2, E10.3
- **Pantallas del inventario:** -
- **Archivos y módulos:** backend/.../identity/**

**Descripción.** `POST /v1/session` (alta o login idempotente por `firebase_uid`, devuelve `MeDto` y estado del onboarding: `NEEDS_TERMS`, `NEEDS_PROFILE`, `NEEDS_TWIN`, `NEEDS_MODES` o `DONE`), `POST /v1/me/terms` (registra **TERMS y PRIVACY** con la versión del texto vigente; Ley 1581: autorización previa, expresa e informada), `GET/PATCH /v1/me`, `POST/DELETE /v1/me/fcm-token`, `POST /v1/me/consent` y `GET /v1/me/consents` sobre la tabla `consents` que **solo agrega filas** con versión del texto. Mientras no haya TERMS y PRIVACY vigentes, cualquier ruta salvo `session`, `terms`, `GET /v1/me` y `DELETE /v1/me` responde 403 `TERMS_REQUIRED`; si cambia la versión del texto (`LEGAL_TEXT_VERSION`), el usuario vuelve a `NEEDS_TERMS`.

**Criterios de aceptación (verificables):**

- [ ] Test: dos `POST /v1/session` con el mismo token crean un solo usuario.
- [ ] Test: PATCH con nombre > 40 caracteres → 400 VALIDATION.
- [ ] Test: revocar un consentimiento inserta una fila nueva con `granted=false`; nunca hay UPDATE ni DELETE en `consents` (verificado contando filas).
- [ ] Test: `onboarding` devuelve `NEEDS_TERMS`, `NEEDS_PROFILE`, `NEEDS_TWIN`, `NEEDS_MODES` o `DONE` según el estado.
- [ ] Test: sin aceptar términos, `POST /v1/twin/answers` → 403 TERMS_REQUIRED; tras `POST /v1/me/terms` hay dos filas (TERMS y PRIVACY) con `text_version` = `LEGAL_TEXT_VERSION`; subir la versión devuelve `NEEDS_TERMS`.

#### E2.2 · Onboarding y login con Google

- **Rama:** `feature/onboarding-login`
- **Tamaño:** M · **Épica:** E2 Cuenta y gemelo · **Major:** Sí
- **Ola:** 6 (1-3 nov, sprints 1-2) · **Dueño:** I2
- **Depende de:** E1.3, E2.1, E0.8
- **En paralelo con:** E1.4, E2.3, E2.4, E3.1, E3.3, E5.3
- **Pantallas del inventario:** A1, A2, A3, A4
- **Archivos y módulos:** android/app/.../feature/onboarding/**, data/auth/**

**Descripción.** A1 Splash (sin sesión → A2; con sesión y gemelo → C1; con sesión sin gemelo → B1), A2-A4 en pager con 'Saltar' que lleva a A4, Credential Manager + `googleid` → `FirebaseAuth.signInWithCredential` → `POST /v1/session`, estados de cargando y error (no están en los mockups) y regreso a A4 tras cerrar sesión. A4 agrega bajo el botón el texto 'Al continuar aceptas los Términos y la Política de privacidad (Ley 1581 de 2012). Tus respuestas y mensajes se procesan con IA de Google Cloud en EE. UU.' con enlaces a Hosting; tras el login, si el estado es `NEEDS_TERMS`, la app llama `POST /v1/me/terms` antes de seguir (una hoja de confirmación lo pide de nuevo si cambió la versión). Destinos tras el login: `NEEDS_PROFILE` → B0 (E2.6), `NEEDS_TWIN` → B1.

**Criterios de aceptación (verificables):**

- [ ] ViewModel con `FakeAuthRepository`: éxito navega según `onboarding`; cancelación vuelve a A4 sin error; error de red muestra mensaje y botón reintentar.
- [ ] Test de navegación: los tres caminos de A1 llegan a su destino.
- [ ] Roborazzi de A1-A4, cargando y error en ambos temas.
- [ ] Test: tras un login con `NEEDS_TERMS` se llama a `POST /v1/me/terms` exactamente una vez antes de navegar; los enlaces de A4 abren las URLs de `BuildConfig`.
- [ ] Verificación manual en el PR: login real en el flavor dev.

#### E2.3 · API del gemelo

- **Rama:** `feature/twin-profile-api`
- **Tamaño:** M · **Épica:** E2 Cuenta y gemelo · **Major:** No
- **Ola:** 6 (1-3 nov, sprints 1-2) · **Dueño:** I1
- **Depende de:** E0.4, E0.9
- **En paralelo con:** E1.4, E2.2, E2.4, E3.1, E3.3, E5.3
- **Pantallas del inventario:** -
- **Archivos y módulos:** backend/.../twin/**

**Descripción.** `GET /v1/twin/questions`, `POST /v1/twin/answers` (exactamente 12), `TraitEngine` determinista (6 rasgos), construcción de persona asíncrona por Cloud Tasks (`/internal/tasks/build-persona`, LLM lite, unas 120 palabras, sin datos identificables), `GET /v1/twin` (rasgos, nivel, xp, versión, `personaReady`) y `GET /v1/twin/evolution` (puntos semanales de `twin_accuracy` y nivel; lista vacía si no hay datos). La persona se construye **solo** con las 12 respuestas: las memorias nunca se mezclan en `persona_summary` (se inyectan por modo en cada simulación, ADR-0032).

**Criterios de aceptación (verificables):**

- [ ] Tests golden de `TraitEngine`: 3 juegos de respuestas → vectores esperados.
- [ ] Test: 11 respuestas → 400 VALIDATION.
- [ ] Test con FakeLlm: el prompt de persona no contiene email, nombre completo ni fecha de nacimiento.
- [ ] Test: ejecutar dos veces la tarea `build-persona` con el mismo id no duplica versiones.
- [ ] Test: `GET /v1/twin/evolution` devuelve `[]` sin datos y, con 3 filas de `twin_accuracy`, 3 puntos ordenados por semana.
- [ ] Test: el prompt de `build-persona` no contiene ninguna memoria aunque el usuario tenga memorias aceptadas.

#### E2.4 · UI de preguntas del gemelo

- **Rama:** `feature/twin-questions-ui`
- **Tamaño:** M · **Épica:** E2 Cuenta y gemelo · **Major:** No
- **Ola:** 6 (1-3 nov, sprints 1-2) · **Dueño:** I2
- **Depende de:** E1.3, E1.5
- **En paralelo con:** E1.4, E2.2, E2.3, E3.1, E3.3, E5.3
- **Pantallas del inventario:** B1
- **Archivos y módulos:** android/app/.../feature/twin/questions/**

**Descripción.** B1 con pila de tarjetas deslizables, opciones A, B y 'Depende', progreso 'n de 12', guardado parcial en DataStore para retomar, envío al terminar y estado de error.

**Criterios de aceptación (verificables):**

- [ ] ViewModel: deslizar a la izquierda o derecha registra A o B; 'Depende' registra DEPENDS; al responder la 12 se envía una sola vez.
- [ ] Test: cerrar y reabrir retoma desde la última respuesta guardada.
- [ ] Roborazzi de B1 en pregunta 1, 4 y 12, y error.

#### E2.5 · Modos, +18 y consentimiento

- **Rama:** `feature/modes-age-consent`
- **Tamaño:** M · **Épica:** E2 Cuenta y gemelo · **Major:** Sí · **Vertical:** Sí
- **Ola:** 7 (3-6 nov, sprint 2) · **Dueño:** I2 (UI) + I1 (API)
- **Depende de:** E2.1, E1.4
- **En paralelo con:** E2.6, E3.5, E4.3, E4.4, E4.5, E5.1, E5.2
- **Pantallas del inventario:** B2, B3, B4, B5
- **Archivos y módulos:** backend/.../identity/modes/**, backend/.../identity/consent/**, android/app/.../feature/twin/modes/**

**Descripción.** Vertical. API: `PUT /v1/me/modes` (Pareja exige `age_verified_at`, que fija E2.6 en B0, y la confirmación explícita `pareja_confirmed_at`) y consentimiento de aprendizaje por modo (B4 activa Amistad y Estudio; Pareja queda apagado y se activa en H3). UI: B2 (todos los modos apagados por defecto, validación de al menos uno), B3 (muestra la fecha ya verificada en solo lectura y pide la casilla 'Confirmo que soy mayor de 18 y quiero activar Pareja'; cancelar deja Pareja apagado), B4 ('Acepto' y 'Ahora no'), B5 (celebración).

**Criterios de aceptación (verificables):**

- [ ] Servidor: activar Pareja sin `age_verified_at` → 403 AGE_REQUIRED; sin confirmación explícita → 400 VALIDATION; con ambas → OK.
- [ ] Servidor: B4 'Acepto' inserta filas `CHAT_LEARNING_AMISTAD` y `CHAT_LEARNING_ESTUDIO` con `granted=true` y ninguna de Pareja.
- [ ] UI: con 0 modos el botón Continuar está deshabilitado; encender Pareja abre B3; cancelar lo deja apagado.
- [ ] Roborazzi de B2-B5 (B3 con la fecha en solo lectura).

#### E2.6 · Perfil básico y edad

- **Rama:** `feature/basic-profile-age`
- **Tamaño:** M · **Épica:** E2 Cuenta y gemelo · **Major:** Sí · **Vertical:** Sí
- **Ola:** 7 (3-6 nov, sprint 2) · **Dueño:** I2 (UI) + I1 (API)
- **Depende de:** E2.1, E1.4
- **En paralelo con:** E2.5, E3.5, E4.3, E4.4, E4.5, E5.1, E5.2
- **Pantallas del inventario:** B0 'Tu perfil' (pantalla nueva entre A4 y B1); datos de F2 y C3
- **Archivos y módulos:** backend/.../identity/profile/**, android/app/.../feature/twin/profile/**

**Descripción.** Vertical. Pantalla nueva **B0 'Tu perfil'** entre A4 y B1, construida con el sistema de diseño (ADR-0031): fecha de nacimiento (**obligatoria para todos**: la app se publica 18+), universidad (lista cerrada `universities`: UIS, UNAB, UPB, UDES, UTS, USTA, UCC, UNIPAZ y 'Otra'; verificar la lista con el equipo), carrera (texto ≤ 60) e intereses (1 a 5 chips de un catálogo fijo `interests` sembrado en E0.4). API: `POST /v1/me/age-verification` (edad calculada en el servidor con zona America/Bogota; fecha inmutable una vez guardada; menor de 18 → 403 UNDERAGE, `users.underage_at` y la cuenta queda bloqueada con una pantalla que explica por qué y ofrece 'Borrar mi cuenta') y `PATCH /v1/me` con `career`, `universityId` e `interests`. El `AnonProfile` del match (F2) sale de aquí: `ageRange` (18-19, 20-22, 23-25, 26+), universidad e intereses. C3 reutiliza B0 en modo edición (E9.1).

**Criterios de aceptación (verificables):**

- [ ] Servidor con reloj fijo: cumple 18 hoy a las 23:30 hora Bogotá → OK; le falta un día → 403 UNDERAGE y `underage_at` fijado; después, toda ruta salvo `GET /v1/me` y `DELETE /v1/me` → 403 UNDERAGE.
- [ ] Servidor: cambiar la fecha de nacimiento una segunda vez → 409 CONFLICT.
- [ ] Test: `ageRange` para 18, 20, 22, 23 y 26 años da '18-19', '20-22', '20-22', '23-25' y '26+'.
- [ ] Test: más de 5 intereses, un interés fuera del catálogo o una universidad desconocida → 400 VALIDATION.
- [ ] UI: 'Continuar' deshabilitado sin fecha o sin universidad; un menor de edad ve la pantalla de bloqueo con 'Borrar mi cuenta'.
- [ ] Roborazzi de B0 (vacío, completo, modo edición, error de menor) en ambos temas.

### E3 · Tu parche (épica mayor)

API del grafo, layout radial, componente G0, pantalla D1-D5 y suite de invariantes de privacidad.

#### E3.1 · API del grafo

- **Rama:** `feature/graph-api`
- **Tamaño:** M · **Épica:** E3 Tu parche · **Major:** Sí
- **Ola:** 6 (1-3 nov, sprints 1-2) · **Dueño:** I1
- **Depende de:** E0.4, E2.1
- **En paralelo con:** E1.4, E2.2, E2.3, E2.4, E3.3, E5.3
- **Pantallas del inventario:** -
- **Archivos y módulos:** backend/.../graph/**

**Descripción.** `EdgeService` (alta y actualización de aristas desde simulaciones, decisiones, invitaciones y presentaciones), vista `edges_v`, `GraphVisibilityPolicy` y `GET /v1/graph?mode=&minAffinity=&community=`, `GET /v1/graph/nodes/{id}`. El filtrado de bloqueados, usuarios borrados (`deleted_at`) y `HIDDEN` en contextos de terceros se hace **en SQL** y además en la policy. El ego-grafo incluye las aristas de Pareja del propio usuario. 'Conexiones' cuenta solo CONNECTED.

**Criterios de aceptación (verificables):**

- [ ] Test: el dueño ve sus aristas de Pareja; un bloqueo en cualquier sentido oculta el nodo; un usuario borrado no aparece.
- [ ] Test: `stats.connections` cuenta solo CONNECTED (fixture con 5 CONNECTED, 3 SIMULATED y 1 PENDING → 5).
- [ ] Test: antes de la conexión mutua el nodo SIMULATED de un match al azar se muestra como 'Alguien'.
- [ ] Test: filtros `mode` y `minAffinity` devuelven el subconjunto esperado.

#### E3.2 · Layout radial del grafo

- **Rama:** `feature/graph-layout`
- **Tamaño:** S · **Épica:** E3 Tu parche · **Major:** No
- **Ola:** 4 (27-29 oct, sprint 1) · **Dueño:** I2
- **Depende de:** E0.7
- **En paralelo con:** E0.4, E0.6, E1.1, E1.2, E7.0, E8.4
- **Pantallas del inventario:** G0
- **Archivos y módulos:** android/designsystem/.../graph/layout/**

**Descripción.** Función pura `RadialLayout.compute(nodes, size, fof)` en `:designsystem/graph`: anillo por afinidad (≥85, 70-84, <70), radios 62/102/140·k (con fof 48/80/108 + 140) y k = size/328, ángulo estable ordenado por modo e id, separación mínima por relajación y `hitTest`.

**Criterios de aceptación (verificables):**

- [ ] Test: la misma entrada produce exactamente la misma salida (determinismo).
- [ ] Test: afinidad 85 cae en el anillo 1, 84 en el 2 y 69 en el 3.
- [ ] Test: con 30 nodos ningún par queda a menos de 2·r + 2 dp.
- [ ] Test: `hitTest` devuelve el nodo más cercano dentro de su radio y null fuera.

#### E3.3 · Componente G0 en Canvas

- **Rama:** `feature/graph-canvas`
- **Tamaño:** M · **Épica:** E3 Tu parche · **Major:** Sí
- **Ola:** 6 (1-3 nov, sprints 1-2) · **Dueño:** I2
- **Depende de:** E3.2, E1.1
- **En paralelo con:** E1.4, E2.2, E2.3, E2.4, E3.1, E5.3
- **Pantallas del inventario:** G0
- **Archivos y módulos:** android/designsystem/.../graph/PuGraph.kt

**Descripción.** `PuGraph` en Canvas: anillos punteados, aristas por estado (sólida, punteada, tenue) y color por modo, nodos con `affinityAlpha`, halo de selección, atenuación por filtro, capa de amigos de amigos, variante miniatura (size < 200), toque con `hitTest` y una lista semántica paralela para TalkBack.

**Criterios de aceptación (verificables):**

- [ ] Roborazzi de G0 en los estados todos, filtrado, seleccionado, fof, miniatura y count reducido, en ambos temas.
- [ ] Test de semántica: cada nodo expone 'Laura P., 87 % afín, Estudio, conectados' (o 'Alguien, ...').
- [ ] Test: tocar las coordenadas de un nodo dispara `onNodeClick(id)`.

#### E3.4 · Pantalla Tu parche

- **Rama:** `feature/parche-screen`
- **Tamaño:** M · **Épica:** E3 Tu parche · **Major:** No
- **Ola:** 8 (5-8 nov, sprint 2) · **Dueño:** I2
- **Depende de:** E3.3, E1.4
- **En paralelo con:** E4.2, E4.6, E4.7, E7.2, E8.1
- **Pantallas del inventario:** D1, D2, D3, D4, D5, I2
- **Archivos y módulos:** android/app/.../feature/parche/**

**Descripción.** D1 con grafo, chips de filtro funcionales (modo, afinidad 70 %+, comunidad), StatCards, banner bloqueado para gratis, D2 bottom sheet con variantes conectados, simularon y pendiente ('Simular de nuevo' → F4, 'Chatear' → F9), D3 vacío, D4 skeleton, D5 error con reintentar, I2 claro. Pinta al instante el último grafo guardado en DataStore.

**Criterios de aceptación (verificables):**

- [ ] ViewModel con `FakeGraphRepository`: Loading → Loaded, Empty y Error; reintentar vuelve a Loading.
- [ ] Test: el filtro de modo atenúa los nodos de otros modos; el de afinidad oculta los < 70.
- [ ] Roborazzi de D1-D5, las tres variantes de D2 e I2.

#### E3.5 · Suite de invariantes de privacidad

- **Rama:** `feature/privacy-invariants-suite`
- **Tamaño:** S · **Épica:** E3 Tu parche · **Major:** Sí
- **Ola:** 7 (3-6 nov, sprint 2) · **Dueño:** I1
- **Depende de:** E3.1
- **En paralelo con:** E2.5, E2.6, E4.3, E4.4, E4.5, E5.1, E5.2
- **Pantallas del inventario:** -
- **Archivos y módulos:** backend/src/test/.../privacy/**, scripts/demo-mutacion-privacidad.sh

**Descripción.** Pruebas por propiedades con Kotest: genera grafos aleatorios con usuarios de todas las visibilidades, aristas de los tres modos, bloqueos y borrados, y para cada observador y cada vista de terceros registrada en `ThirdPartyViews` (fof, comunidades, presentaciones, sugerencias de inicio, nodos de terceros) comprueba: ninguna arista de Pareja que no involucre al observador, ningún HIDDEN, ningún bloqueado y ningún borrado. Propiedad adicional (ADR-0032): para usuarios con memorias aleatorias en los tres modos y simulaciones aleatorias, el prompt que arma `TwinPromptBuilder` para una simulación de modo M solo contiene memorias de modo M (en particular, ninguna de Pareja fuera de Pareja). Un test de arquitectura falla si un servicio que implementa `ThirdPartyView` no está registrado. Incluye `scripts/demo-mutacion-privacidad.sh`, que aplica un parche que quita el filtro de Pareja y muestra la suite fallando (para la sustentación).

**Criterios de aceptación (verificables):**

- [ ] ≥ 1.000 casos generados por propiedad, con semilla impresa para reproducir.
- [ ] `scripts/demo-mutacion-privacidad.sh` termina con código ≠ 0 con la mutación y 0 sin ella, y deja el árbol limpio.
- [ ] El test de registro falla si se agrega una clase `ThirdPartyView` sin registrarla (probado con una clase de prueba).
- [ ] La propiedad de memorias por modo corre ≥ 1.000 casos y falla si `TwinPromptBuilder` deja de filtrar por modo (variante de la demo de mutación).

### E4 · Conocer (épica mayor)

Cuotas, fila de match, motor de simulación, resultados, decisión mutua, inicio, modo escenario y usuarios semilla.

#### E4.1 · Servicio de cuotas

- **Rama:** `feature/quota-service`
- **Tamaño:** S · **Épica:** E4 Conocer · **Major:** Sí
- **Ola:** 5 (29-31 oct, sprint 1) · **Dueño:** I1
- **Depende de:** E0.4
- **En paralelo con:** E0.9, E1.3, E1.5, E2.1, E10.2, E10.3
- **Pantallas del inventario:** -
- **Archivos y módulos:** backend/.../quota/**

**Descripción.** `QuotaService` con límites gratis (5 simulaciones por semana ISO con corte el lunes 00:00 America/Bogota, 1 match al azar por día, 1 informe completo por mes calendario) y uso justo configurable para ParcheU+. Registro en `usage_events` con `ref_id` único (idempotente) y devolución (`refunded`). `GET /v1/quota`. Usa la interfaz `EntitlementService` con un stub 'gratis' hasta E7.1 y `/internal/dev/grant-plus` en dev.

**Criterios de aceptación (verificables):**

- [ ] Test con reloj fijo: la 6.ª simulación de la semana → 402 QUOTA_EXCEEDED con `resetsAt` = próximo lunes 00:00 -05:00.
- [ ] Test: devolver un uso restaura el cupo; repetir el mismo `ref_id` no cobra dos veces.
- [ ] Test: un usuario Plus no tiene límite semanal pero sí el uso justo diario.
- [ ] Test: el límite mensual del informe se reinicia el día 1 a las 00:00 hora Bogotá.

#### E4.2 · API de fila de match

- **Rama:** `feature/match-queue-api`
- **Tamaño:** M · **Épica:** E4 Conocer · **Major:** No
- **Ola:** 8 (5-8 nov, sprint 2) · **Dueño:** I1
- **Depende de:** E4.1, E2.5, E2.6
- **En paralelo con:** E3.4, E4.6, E4.7, E7.2, E8.1
- **Pantallas del inventario:** -
- **Archivos y módulos:** backend/.../matching/**

**Descripción.** `POST/GET/DELETE /v1/match/queue` (el modo debe estar activo; Pareja exige edad verificada), emparejamiento con `FOR UPDATE SKIP LOCKED` (mismo modo, no bloqueados, no conectados ya, no uno mismo; prioridad para Plus), vencimiento a 24 h, espejo de solo lectura en Firestore `matchTickets/{uid}`, aviso por la interfaz `Notifier`, `GET /v1/matches/{id}` y `POST /v1/matches/{id}/choice` (reglas en arquitectura.md §7.5). El cupo diario se consume al encontrar match, no al entrar a la fila. `MatchDto.other` es un `AnonProfile{label, ageRange, university, interests, isDemo}` armado con los datos de B0 (E2.6). `MatchDto.scenarioAvailable` refleja la bandera `SCENARIO_MODE_ENABLED` (false hasta que E4.8 se mezcle): con la bandera apagada, elegir SCENARIO → 400 VALIDATION y F2 oculta esa tarjeta. Los usuarios demo nunca entran a la fila de Pareja.

**Criterios de aceptación (verificables):**

- [ ] Test: dos usuarios del mismo modo quedan emparejados; de modos distintos no; bloqueados no.
- [ ] Test de concurrencia: 10 usuarios encolados en paralelo producen 5 matches sin duplicados.
- [ ] Test: cancelar no crea `usage_events`.
- [ ] Test con el emulador de Firestore: `matchTickets/{uid}` refleja WAITING → MATCHED.
- [ ] Test: con `DEMO_USERS_ENABLED=false` nunca se empareja con un usuario `is_demo`; con la bandera en true tampoco en modo Pareja.
- [ ] Test (reloj fijo): ambos eligen SCENARIO → estado SCENARIO y se crea el chat (con la bandera encendida); uno elige SIMULATION → SIMULATION y una simulación RANDOM sin cobrar cupo semanal; solo uno elige y vence `decide_by` (10 min) → SIMULATION; nadie elige → EXPIRED.
- [ ] Test: elegir dos veces → 409 CONFLICT; elegir después de `decide_by` → 409 CONFLICT.
- [ ] Test: un ticket WAITING con más de 24 h pasa a EXPIRED (`matchTickets/{uid}` también) y no cobra cupo.
- [ ] Test: `AnonProfile` no contiene nombre, foto, carrera ni id interno.

#### E4.3 · Motor de simulación

- **Rama:** `feature/simulation-engine`
- **Tamaño:** L · **Épica:** E4 Conocer · **Major:** Sí
- **Ola:** 7 (3-6 nov, sprint 2) · **Dueño:** I1
- **Depende de:** E0.9, E2.3, E4.1, E3.1
- **En paralelo con:** E2.5, E2.6, E3.5, E4.4, E4.5, E5.1, E5.2
- **Pantallas del inventario:** -
- **Archivos y módulos:** backend/.../simulation/**

**Descripción.** `POST /v1/simulations` (valida modo común, bloqueos y cupo, responde 202) → Cloud Task `/internal/tasks/simulate`. Elige 3 escenarios del modo (solo básicos si es gratis). Por escenario, hasta 8 turnos alternados entre dos `TwinAgent` independientes (cada uno solo ve su persona, sus memorias aceptadas **del mismo modo de la simulación** y la transcripción del escenario; ≤ 120 tokens por turno). `TwinPromptBuilder` es el único lugar que arma el prompt y filtra memorias por modo (ADR-0032). La tarea va a la cola `simulations`. Juez con salida JSON y la rúbrica del modo (`scenarios.rubric`). Puntaje = round(.35·similitud de rasgos + .65·promedio del juez). Caché 7 días por (par, modo, versiónA, versiónB). Estados QUEUED → RUNNING → DONE/FAILED, progreso en Firestore `simulations/{id}`, devolución del cupo si falla, idempotencia por id, arista SIMULATED, aviso al terminar. `GET /v1/simulations`, `GET /v1/simulations/{id}`, `GET .../transcript`.

**Criterios de aceptación (verificables):**

- [ ] Test con FakeLlm: una ejecución completa termina en DONE con puntaje entre 0 y 100 y 3 escenarios.
- [ ] Test: si el juez falla 3 veces → FAILED y el uso queda `refunded=true`.
- [ ] Test: reintentar la misma tarea no duplica turnos ni cobros.
- [ ] Test: un acierto de caché devuelve el resultado sin llamar al LLM (contador del Fake = 0).
- [ ] Test: el prompt del gemelo A no contiene la persona ni las memorias de B.
- [ ] Test (captura del prompt con FakeLlm): un usuario con memorias aceptadas de Pareja, Amistad y Estudio; en una simulación de Amistad el prompt contiene solo las de Amistad, y ninguna de Pareja aparece en el transcript ni en el informe guardados.
- [ ] Test: un usuario gratis solo recibe escenarios básicos; en Pareja el prompt incluye las reglas de contenido.
- [ ] Test: con el presupuesto agotado → 503 AI_BUDGET y no se consume cupo.

#### E4.4 · UI de match al azar

- **Rama:** `feature/match-ui`
- **Tamaño:** M · **Épica:** E4 Conocer · **Major:** No
- **Ola:** 7 (3-6 nov, sprint 2) · **Dueño:** I2
- **Depende de:** E1.4, E1.5
- **En paralelo con:** E2.5, E2.6, E3.5, E4.3, E4.5, E5.1, E5.2
- **Pantallas del inventario:** F1, F2
- **Archivos y módulos:** android/app/.../feature/conocer/match/**

**Descripción.** F1 (selector de modo, animación de búsqueda, cancelar) con listener de Firestore sobre `matchTickets/{uid}`, F2 (match encontrado anónimo: 'Alguien de la UIS · 20-22 años', intereses, tarjetas Escenario o Simulación; la tarjeta Escenario solo aparece si `scenarioAvailable`; si el otro es demo, insignia 'Perfil de demostración') y hoja de límite de match diario.

**Criterios de aceptación (verificables):**

- [ ] ViewModel con `FakeMatchRepository`: WAITING → MATCHED navega a F2; cancelar llama DELETE.
- [ ] Test: `QUOTA_EXCEEDED(kind=RANDOM_MATCH)` abre la hoja de límite correcta.
- [ ] Test: con `scenarioAvailable=false` F2 muestra solo la tarjeta Simulación; con `isDemo=true` muestra la insignia.
- [ ] Roborazzi de F1, F2 (con y sin escenario, demo) y la hoja de límite.

#### E4.5 · UI de simulación

- **Rama:** `feature/simulation-ui`
- **Tamaño:** M · **Épica:** E4 Conocer · **Major:** No
- **Ola:** 7 (3-6 nov, sprint 2) · **Dueño:** I2
- **Depende de:** E1.4, E1.5
- **En paralelo con:** E2.5, E2.6, E3.5, E4.3, E4.4, E5.1, E5.2
- **Pantallas del inventario:** F4, F5, F6
- **Archivos y módulos:** android/app/.../feature/conocer/simulation/**

**Descripción.** F4 (progreso en vivo por listener de `simulations/{id}`, lista de 3 escenarios, se puede salir y volver por push), F5 (error que aclara que no se descontó el cupo), F6 (vacío) y la lista 'Ver todas las simulaciones'.

**Criterios de aceptación (verificables):**

- [ ] ViewModel: mapea `progress.scenarioIndex/turn` a la lista de pasos; DONE navega a resultados; FAILED muestra F5.
- [ ] Test: si el listener de Firestore no entrega nada en 10 s, el ViewModel consulta `GET /v1/simulations/{id}` una vez (respaldo) y sigue escuchando; salir y volver reanuda el estado.
- [ ] Test: F5 muestra 'No se descontó de tu cupo' solo si el resumen trae `refunded=true`.
- [ ] Roborazzi de F4 (0 %, 60 %), F5, F6 y la lista.

#### E4.6 · Resultados y decisión mutua

- **Rama:** `feature/results-decision`
- **Tamaño:** M · **Épica:** E4 Conocer · **Major:** Sí · **Vertical:** Sí
- **Ola:** 8 (5-8 nov, sprint 2) · **Dueño:** I2 (UI) + I1 (API)
- **Depende de:** E4.3, E4.5, E5.1
- **En paralelo con:** E3.4, E4.2, E4.7, E7.2, E8.1
- **Pantallas del inventario:** F7, F8, I3
- **Archivos y módulos:** backend/.../simulation/report/**, android/app/.../feature/conocer/results/**

**Descripción.** Vertical. API: `GET /v1/simulations/{id}/report` (redactado en el servidor: gratis recibe puntaje, resumen y conteos; completo si es Plus o lo desbloqueó), `POST .../report:unlock` (gasta el informe mensual), `POST .../decision` (si ambos conectan: arista CONNECTED, chat DIRECT y push; el nombre se revela solo entonces). UI: F7 (borroso con candado y estado 'informe disponible este mes'), F8, I3, estado 'esperando a la otra persona', DecisionFooter y transcripción (Plus o informe desbloqueado). Sin botón de compartir. Si el otro es demo, la insignia 'Perfil de demostración' se mantiene también aquí. El chat DIRECT se crea con `ChatService` (E5.1).

**Criterios de aceptación (verificables):**

- [ ] Test: la respuesta gratis no contiene las claves `reasons`, `frictions` ni `transcript` (comparación de claves del JSON).
- [ ] Test: el primer unlock del mes funciona; el segundo → 402 QUOTA_EXCEEDED(kind=FULL_REPORT).
- [ ] Test de concurrencia: dos decisiones simultáneas `connect=true` crean exactamente un chat y una arista CONNECTED.
- [ ] Test: 'Ahora no' no genera notificación.
- [ ] Roborazzi de F7 (consumido y disponible), F8, I3 y 'esperando'.

#### E4.7 · Pantalla de inicio

- **Rama:** `feature/home-screen`
- **Tamaño:** M · **Épica:** E4 Conocer · **Major:** No · **Vertical:** Sí
- **Ola:** 8 (5-8 nov, sprint 2) · **Dueño:** I2 (UI) + I3 (API)
- **Depende de:** E4.1, E4.3, E3.1, E1.4, E1.5
- **En paralelo con:** E3.4, E4.2, E4.6, E7.2, E8.1
- **Pantallas del inventario:** C1, I1
- **Archivos y módulos:** backend/.../home/**, android/app/.../feature/home/**

**Descripción.** Vertical. `GET /v1/home` (cupos de `QuotaService`, simulación en curso y resultados listos de E4.3, sugerencias a partir del grafo de E3.1 y conteo de no leídas; agregador sin lógica propia). Recortable para la 0.2: si se atrasa, C1 sale solo con cupos y simulación en curso. C1 con SegmentedQuota, tarjeta en curso, resultado listo, sugerencias (para gratis: re-simular con conexiones directas; el conteo de amigos en común solo para Plus), estados vacío, 0 restantes y Plus, campana con badge. I1 claro.

**Criterios de aceptación (verificables):**

- [ ] Test API: `home` refleja el cupo de `QuotaService` y la simulación RUNNING del usuario.
- [ ] Test: para gratis las sugerencias no incluyen `mutualCount`.
- [ ] Roborazzi de C1 (3 de 5, en curso, resultado listo, vacío, 0 restantes, Plus) e I1.

#### E4.8 · Modo escenario

- **Rama:** `feature/scenario-mode`
- **Tamaño:** M · **Épica:** E4 Conocer · **Major:** No · **Vertical:** Sí
- **Ola:** 11 (13-17 nov, sprint 3) · **Dueño:** I1 (API) + I2 (UI)
- **Depende de:** E4.2, E4.3, E5.1, E5.2
- **En paralelo con:** E6.4, E6.5, E6.6, E8.6, E8.7, E8.8, E9.3, E10.8
- **Pantallas del inventario:** F3
- **Archivos y módulos:** backend/.../scenario/**, android/app/.../feature/conocer/scenario/**

**Descripción.** Vertical. Chat SCENARIO con `ends_at` (temporizador) y presupuesto del escenario, tarjeta fijada arriba, `POST /v1/scenario-sessions/{id}/agreement` (ambos marcan 'Llegamos a un acuerdo') o vencimiento del tiempo → el mismo juez evalúa la conversación humana → mismo DTO de resultado → F7/F8. Bandera directa a H1. Al mezclarse, enciende `SCENARIO_MODE_ENABLED` en dev (y en prod desde la 0.9).

**Criterios de aceptación (verificables):**

- [ ] Test: el acuerdo requiere a los dos; con uno solo el estado es WAITING_OTHER.
- [ ] Test: después de `ends_at` enviar un mensaje → 409 SCENARIO_ENDED y se dispara el juez una sola vez.
- [ ] Test: el juez recibe solo la transcripción del chat (FakeLlm captura el prompt).
- [ ] Roborazzi de F3 con temporizador y presupuesto, y estado de acuerdo.

#### E4.9 · Usuarios semilla de demo

- **Rama:** `feature/demo-seed`
- **Tamaño:** S · **Épica:** E4 Conocer · **Major:** No
- **Ola:** 9 (8-10 nov, sprints 2-3) · **Dueño:** I3
- **Depende de:** E4.2, E4.3
- **En paralelo con:** E8.3
- **Pantallas del inventario:** -
- **Archivos y módulos:** backend/.../demo/**

**Descripción.** Comando `seed-demo` (Cloud Run Job `seed-demo` creado en E0.5) que crea 20 usuarios `is_demo=true` con respuestas, gemelo y modos **Amistad y Estudio únicamente** (nunca Pareja); los demo deciden conectar con una probabilidad configurable. **Honestidad (ADR-0027):** todo perfil demo se muestra con la insignia 'Perfil de demostración' (F2, D2, resultados, chat); un chat con un demo tiene un banner fijo 'Esta es una cuenta de demostración: no responde mensajes' y los demo nunca escriben; los mensajes del tester en esos chats siguen las reglas normales de consentimiento (solo sus propios mensajes). Las instrucciones de la prueba cerrada en Play y `docs/play-console.md` lo declaran. Bandera `DEMO_USERS_ENABLED` (true en dev y durante la prueba cerrada, false en producción 1.0) y comando `purge-demo`. Recortable de la 0.2 (pasa a la 0.3).

**Criterios de aceptación (verificables):**

- [ ] Test: tras `purge-demo` ninguna tabla referencia a un usuario `is_demo` (consulta sobre todas las columnas de usuario).
- [ ] Test: con la bandera en false los demo no entran a la fila ni aparecen en sugerencias.
- [ ] Test: `seed-demo` es idempotente (dos ejecuciones → 20 usuarios) y ningún demo tiene Pareja activo.
- [ ] Test: `MatchDto`, `NodeDto` y `ChatSummary` de un demo traen `isDemo=true`; `POST /v1/chats/{id}/messages` nunca se genera desde un demo.

### E5 · Chat y notificaciones

API de chat con espejo en Firestore, UI de chat y lista, push FCM y centro de notificaciones.

#### E5.1 · API de chat

- **Rama:** `feature/chat-api`
- **Tamaño:** M · **Épica:** E5 Chat y notificaciones · **Major:** Sí
- **Ola:** 7 (3-6 nov, sprint 2) · **Dueño:** I1
- **Depende de:** E3.1, E0.8
- **En paralelo con:** E2.5, E2.6, E3.5, E4.3, E4.4, E4.5, E5.2
- **Pantallas del inventario:** -
- **Archivos y módulos:** backend/.../chat/**

**Descripción.** `ChatService` (crea chats DIRECT y SCENARIO para E4.6, E4.8 y E6.2), `GET /v1/chats`, `POST /v1/chats/{id}/messages` (miembro, no bloqueado, no suspendido, ≤ 1.000 caracteres, idempotente por `client_id`; `learnable` se fija según el consentimiento del autor para el modo en ese momento), escritura en Firestore con Admin, aviso por `Notifier`, `POST .../read`, `POST .../mute`.

**Criterios de aceptación (verificables):**

- [ ] Test: un no miembro → 403; un bloqueado → 403 BLOCKED.
- [ ] Test: repetir `client_id` devuelve el mismo mensaje sin duplicar en Postgres ni en Firestore (emulador).
- [ ] Test: con el consentimiento de Pareja apagado, un mensaje en un chat de Pareja queda `learnable=false`.
- [ ] Test: el documento de Firestore tiene `senderUid` (uid de Firebase del autor), `system=false`, `text`, `createdAt` y `hidden=false`, igual que arquitectura.md §8.2 y las reglas de E0.8.

#### E5.2 · UI de chat y lista de chats

- **Rama:** `feature/chat-ui`
- **Tamaño:** M · **Épica:** E5 Chat y notificaciones · **Major:** No
- **Ola:** 7 (3-6 nov, sprint 2) · **Dueño:** I2
- **Depende de:** E1.4, E0.8
- **En paralelo con:** E2.5, E2.6, E3.5, E4.3, E4.4, E4.5, E5.1
- **Pantallas del inventario:** F9, lista de chats
- **Archivos y módulos:** android/app/.../feature/chat/**

**Descripción.** Lista de chats (pantalla nueva con componentes existentes) y F9 con listener de Firestore (caché offline), envío por la API con estado pendiente y reintento, banner de consentimiento, menú → H1, silenciar, mensajes ocultos por moderación ('Mensaje oculto').

**Criterios de aceptación (verificables):**

- [ ] ViewModel con `FakeChatRepository`: el mensaje aparece como pendiente y pasa a enviado; un fallo muestra reintentar.
- [ ] Test: un mensaje con `hidden=true` se renderiza como 'Mensaje oculto'.
- [ ] Roborazzi de F9, lista con chats y lista vacía.

#### E5.3 · Push con FCM

- **Rama:** `feature/push-fcm`
- **Tamaño:** S · **Épica:** E5 Chat y notificaciones · **Major:** No
- **Ola:** 6 (1-3 nov, sprints 1-2) · **Dueño:** I3
- **Depende de:** E2.1, E0.8
- **En paralelo con:** E1.4, E2.2, E2.3, E2.4, E3.1, E3.3
- **Pantallas del inventario:** -
- **Archivos y módulos:** backend/.../notifications/push/** (`FcmNotifier`, tokens), android/app/.../push/** (servicio, canales, deep links). No toca `notifications/center/**`.

**Descripción.** Backend: `FcmNotifier` (implementa `Notifier`; mensajes de datos; canales chat, resultados, social y cuenta), persistencia en `notifications`, limpieza de tokens inválidos. Android: `FirebaseMessagingService`, canales, permiso POST_NOTIFICATIONS (API 33+) pedido tras B5, deep links a cada ruta.

**Criterios de aceptación (verificables):**

- [ ] Test backend: `notify()` inserta una fila en `notifications` y llama al cliente FCM Fake; un token `UNREGISTERED` se borra.
- [ ] Test Android: cada tipo de notificación se traduce a la ruta correcta (tabla de casos).
- [ ] Verificación manual en el PR: push real en el flavor dev.

#### E5.4 · Centro de notificaciones

- **Rama:** `feature/notifications-center`
- **Tamaño:** S · **Épica:** E5 Chat y notificaciones · **Major:** No
- **Ola:** 10 (10-12 nov, sprint 3) · **Dueño:** I3
- **Depende de:** E5.3, E1.4
- **En paralelo con:** E6.1, E6.2, E6.3, E7.1, E7.3, E8.2, E8.5, E9.1, E9.2, E10.5, E10.6
- **Pantallas del inventario:** C2
- **Archivos y módulos:** backend/.../notifications/center/** (rutas de bandeja), android/app/.../feature/notifications/** (excepto `requests/`, que es de E6.6). No toca `notifications/push/**`.

**Descripción.** `GET /v1/notifications` (agrupado Hoy y Esta semana con fecha de Bogotá), `POST /v1/notifications/read`. C2 con no leídas, ítem bloqueado 'quieren conectar contigo' para gratis, 'Marcar leídas' y estado vacío. Badge en la campana.

**Criterios de aceptación (verificables):**

- [ ] Test API: una notificación de las 23:50 hora Bogotá del día anterior cae en 'Esta semana', no en 'Hoy'.
- [ ] Test API: `POST /v1/notifications/read` sin ids marca todas como leídas y el conteo `unread` de `GET /v1/home` pasa a 0.
- [ ] Test UI: tocar una notificación navega a su ruta (misma tabla de E5.3) y la marca como leída.
- [ ] Roborazzi de C2 (con no leídas, bloqueado y vacío).

### E6 · Red social

QR y código, presentar a dos amigos, amigos de amigos, comunidades, evolución y 'quién quiere conectar contigo'.

#### E6.1 · Invitaciones por QR y código

- **Rama:** `feature/invites-qr`
- **Tamaño:** M · **Épica:** E6 Red social · **Major:** No
- **Ola:** 10 (10-12 nov, sprint 3) · **Dueño:** I3
- **Depende de:** E3.1, E1.4
- **En paralelo con:** E5.4, E6.2, E6.3, E7.1, E7.3, E8.2, E8.5, E9.1, E9.2, E10.5, E10.6
- **Pantallas del inventario:** C5
- **Archivos y módulos:** backend/.../social/invites/**, android/app/.../feature/invites/**, firebase/hosting/.well-known/**

**Descripción.** `GET /v1/invites/me` (código `XXXX-0000` único), `POST /v1/invites/redeem` (crea arista PENDING en un modo común; el dueño del código acepta → CONNECTED). C5 con pestañas 'Mi código' (QR con ZXing) y 'Escanear' (Google Code Scanner, sin permiso de cámara) e ingreso manual, compartir enlace `https://<hosting>/i/{codigo}` como App Link con `assetlinks.json` (SHA-256 de Play App Signing y de subida).

**Criterios de aceptación (verificables):**

- [ ] Test: el código cumple `^[A-Z]{4}-[0-9]{4}$` y es único.
- [ ] Test: canjear el propio código → 400; canjear dos veces es idempotente; con bloqueo → 403 BLOCKED.
- [ ] Test Robolectric: el intent `https://.../i/ABCD-1234` abre C5 con el código precargado.
- [ ] Test: canje → arista PENDING y notificación al dueño; `POST /v1/invites/{userId}/accept` del dueño → CONNECTED en ese modo; aceptar algo inexistente → 404.
- [ ] Test con un `FakeCodeScanner`: un QR leído con la URL de invitación precarga el código; un QR ajeno muestra 'Este código no es de ParcheU'.
- [ ] Roborazzi de C5 en ambas pestañas.

#### E6.2 · Presentar a dos amigos

- **Rama:** `feature/introductions`
- **Tamaño:** M · **Épica:** E6 Red social · **Major:** No · **Vertical:** Sí
- **Ola:** 10 (10-12 nov, sprint 3) · **Dueño:** I3 (UI) + I1 (API)
- **Depende de:** E4.3, E5.1, E8.1
- **En paralelo con:** E5.4, E6.1, E6.3, E7.1, E7.3, E8.2, E8.5, E9.1, E9.2, E10.5, E10.6
- **Pantallas del inventario:** E1, E2, E3
- **Archivos y módulos:** backend/.../social/introductions/**, android/app/.../feature/presentar/**

**Descripción.** `POST /v1/introductions` (solo Amistad o Estudio; ambos deben ser CONNECTED del presentador en ese modo y no estar conectados entre sí; consume una simulación del presentador) → simulación con origen INTRODUCTION → propuesta a cada uno; `GET /v1/introductions/{id}`, `POST .../response` (ambos 'Me interesa' → CONNECTED y chat), `POST /v1/introductions/request` ('Pedir presentación' desde D6). UI E1 (0, 1 y 2 elegidos, límite), E2 (informe con la regla del informe mensual), E3 (pendiente, aceptado, rechazado).

**Criterios de aceptación (verificables):**

- [ ] Test: modo Pareja → 400 VALIDATION; un no amigo → 403.
- [ ] Test: ambos aceptan → un chat y una arista CONNECTED; uno rechaza → nadie recibe aviso del rechazo.
- [ ] Test: E2 para gratis no incluye `reasons` (misma redacción que F7).
- [ ] Test: presentar consume 1 simulación del presentador (`usage_events` con `ref_id = introductionId`) y nada de los presentados; sin cupo → 402 QUOTA_EXCEEDED.
- [ ] Test: `POST /v1/introductions/request` (Plus) crea un `intro_request` y notifica al amigo en común sin revelarle al solicitante el id del candidato; gratis → 402 PLUS_REQUIRED.
- [ ] Test: no se puede presentar a dos personas si hay un bloqueo entre ellas o con el presentador (403 BLOCKED), y un bloqueado nunca aparece en la lista de E1.
- [ ] Roborazzi de E1-E3 con sus estados.

#### E6.3 · Amigos de amigos

- **Rama:** `feature/friends-of-friends`
- **Tamaño:** M · **Épica:** E6 Red social · **Major:** Sí · **Vertical:** Sí
- **Ola:** 10 (10-12 nov, sprint 3) · **Dueño:** I1 (API) + I3 (UI)
- **Depende de:** E3.1, E3.5
- **En paralelo con:** E5.4, E6.1, E6.2, E7.1, E7.3, E8.2, E8.5, E9.1, E9.2, E10.5, E10.6
- **Pantallas del inventario:** D6, D7
- **Archivos y módulos:** backend/.../graph/fof/**, android/app/.../feature/parche/fof/**

**Descripción.** `GET /v1/graph/fof` con la consulta de 2 saltos de arquitectura.md §6.2 (count DISTINCT, sin Pareja, sin HIDDEN, sin borrados, sin bloqueos, excluye cualquier arista existente con el observador); ANONYMOUS enmascarado con `anonId` opaco por observador (HMAC); afinidad mostrada como **estimación** por similitud de rasgos. Gratis recibe solo el conteo. Se registra en `ThirdPartyViews`. UI D6, D7 (LockedOverlay) y capa fof del G0.

**Criterios de aceptación (verificables):**

- [ ] Test SQL: un amigo en común conectado en dos modos cuenta una sola vez.
- [ ] Test: la pareja del observador nunca aparece como sugerencia; un HIDDEN o borrado nunca aparece.
- [ ] Test: el `anonId` es estable para el mismo observador y distinto entre observadores.
- [ ] Test: la respuesta gratis no tiene lista de candidatos; la suite E3.5 pasa con la vista registrada.
- [ ] Test: tras un bloqueo en cualquier sentido, el bloqueado desaparece de fof del observador (criterio trasladado desde E8.1).
- [ ] Roborazzi de D6 y D7.

#### E6.4 · Comunidades

- **Rama:** `feature/communities`
- **Tamaño:** M · **Épica:** E6 Red social · **Major:** No · **Vertical:** Sí
- **Ola:** 11 (13-17 nov, sprint 3) · **Dueño:** I1 (job) + I3 (UI)
- **Depende de:** E3.5, E0.9
- **En paralelo con:** E4.8, E6.5, E6.6, E8.6, E8.7, E8.8, E9.3, E10.8
- **Pantallas del inventario:** D8
- **Archivos y módulos:** backend/.../graph/communities/**, android/app/.../feature/parche/communities/**

**Descripción.** Job semanal (Scheduler → `/internal/jobs/communities`, lunes 03:00 Bogotá) con propagación de etiquetas ponderada por afinidad y semilla fija sobre aristas CONNECTED sin Pareja ni HIDDEN; mínimo 3 miembros; IDs estables por Jaccard con la semana anterior; nombre creativo con LLM lite a partir de rasgos agregados, sin nombres. `GET /v1/communities` (Plus; gratis ve el conteo bloqueado). Se registra en `ThirdPartyViews`. UI D8 (con comunidades, bloqueado, vacío) → D1 filtrado.

**Criterios de aceptación (verificables):**

- [ ] Test: dos cliques unidos por una arista débil producen dos comunidades.
- [ ] Test: misma entrada y semilla → mismas comunidades; una arista de Pareja no cambia el resultado.
- [ ] Test: el prompt de nombre no contiene nombres ni correos (FakeLlm).
- [ ] Roborazzi de D8 en sus 3 estados.

#### E6.5 · Evolución de la red

- **Rama:** `feature/network-evolution`
- **Tamaño:** S · **Épica:** E6 Red social · **Major:** No · **Vertical:** Sí
- **Ola:** 11 (13-17 nov, sprint 3) · **Dueño:** I3
- **Depende de:** E3.1, E3.3
- **En paralelo con:** E4.8, E6.4, E6.6, E8.6, E8.7, E8.8, E9.3, E10.8
- **Pantallas del inventario:** D9
- **Archivos y módulos:** backend/.../graph/history/**, android/app/.../feature/parche/evolution/**

**Descripción.** `GET /v1/graph/history` (Plus): snapshots mensuales derivados de `edges.created_at/updated_at/connected_at` y 'Momentos' (primera conexión, primera comunidad, 10, 25 y 50 conexiones, afinidad más alta; lista única en arquitectura.md §6.4). UI D9 (con historial, < 2 meses, bloqueado) → D1 con el snapshot del mes.

**Criterios de aceptación (verificables):**

- [ ] Test: con un fixture de aristas de 3 meses, el historial devuelve 3 meses con los conteos esperados.
- [ ] Test: gratis → 402 PLUS_REQUIRED.
- [ ] Roborazzi de D9 en sus 3 estados.

#### E6.6 · Quién quiere conectar contigo

- **Rama:** `feature/connect-requests`
- **Tamaño:** S · **Épica:** E6 Red social · **Major:** No · **Vertical:** Sí
- **Ola:** 11 (13-17 nov, sprint 3) · **Dueño:** I3
- **Depende de:** E4.6, E5.4
- **En paralelo con:** E4.8, E6.4, E6.5, E8.6, E8.7, E8.8, E9.3, E10.8
- **Pantallas del inventario:** C2 (ítem), C1
- **Archivos y módulos:** backend/.../social/requests/**, android/app/.../feature/notifications/requests/**

**Descripción.** `GET /v1/connect-requests` (Plus): personas que decidieron conectar contigo y a quienes aún no respondes. Gratis recibe solo el conteo (ítem bloqueado de C2). Sección en C2 y acceso desde C1.

**Criterios de aceptación (verificables):**

- [ ] Test: gratis recibe `count` sin lista; Plus recibe la lista.
- [ ] Test: tras decidir, la persona desaparece de la lista.
- [ ] Roborazzi de la sección para gratis y Plus.

### E7 · ParcheU+ (épica mayor)

Billing mínimo para que Play permita crear la suscripción, billing en servidor y cliente, paywall y auditoría del contenido premium.

#### E7.0 · Billing mínimo y primera subida

- **Rama:** `feature/billing-bootstrap`
- **Tamaño:** S · **Épica:** E7 ParcheU+ · **Major:** Sí
- **Ola:** 4 (27-29 oct, sprint 1) · **Dueño:** I3
- **Depende de:** E0.7, E10.1
- **En paralelo con:** E0.4, E0.6, E1.1, E1.2, E3.2, E8.4
- **Pantallas del inventario:** -
- **Archivos y módulos:** android/app/build.gradle.kts (dependencia `billing-ktx`), android/app/.../data/billing/BillingClientProvider.kt, android/app/src/test/.../billing/**, docs/play-console.md

**Descripción.** Prerrequisito de Play para crear suscripciones: la app debe tener un AAB subido que incluya Play Billing Library (agrega el permiso `com.android.vending.BILLING` por fusión del manifiesto). Agrega `billing-ktx` (versión fijada en E0.7, verificar) y un `BillingClientProvider` que solo conecta, reconecta y consulta `ProductDetails` (sin UI de compra; la compra llega en E7.2 y E7.3). I3 construye el AAB `prod` firmado con la llave de subida (artefacto de CI o build local) y lo **sube a mano a la pista interna** (la primera subida de una app nueva se hace desde la consola; las siguientes las hace E10.2 por API). Con ese AAB crea en Play Console la suscripción `parcheu_plus` con planes base `mensual` (P1M, $5.000 COP) y `anual` (P1Y, $45.000 COP), agrega ≥ 3 license testers y configura el tema RTDN `play-rtdn` del proyecto prod.

**Criterios de aceptación (verificables):**

- [ ] Test (tarea sobre el manifiesto fusionado de `prodRelease`): contiene `com.android.vending.BILLING`.
- [ ] Test con un `FakeBillingClient`: `BillingClientProvider` reintenta la conexión con backoff exponencial (máx. 3) y expone los `ProductDetails` de `parcheu_plus` cuando existen.
- [ ] AAB 0.0.1 en la pista interna a más tardar el **28 oct** (fecha registrada en `docs/play-console.md`).
- [ ] `parcheu_plus` existe con los dos planes base activos, ≥ 3 license testers y RTDN apuntando a `play-rtdn` (captura en el PR, sin datos personales).

#### E7.1 · Billing en el servidor

- **Rama:** `feature/billing-api`
- **Tamaño:** M · **Épica:** E7 ParcheU+ · **Major:** Sí
- **Ola:** 10 (10-12 nov, sprint 3) · **Dueño:** I1
- **Depende de:** E4.1, E0.5
- **En paralelo con:** E5.4, E6.1, E6.2, E6.3, E7.3, E8.2, E8.5, E9.1, E9.2, E10.5, E10.6
- **Pantallas del inventario:** -
- **Archivos y módulos:** backend/.../billing/**

**Descripción.** `POST /v1/billing/verify` (purchaseToken → `purchases.subscriptionsv2.get`, valida `obfuscatedAccountId = sha256(userId)`, actualiza `entitlements` y hace **acknowledge en el servidor**), `GET /v1/billing/entitlement`, RTDN en `/internal/billing/rtdn` (idempotente por `messageId`, reconsulta Play), implementación real de `EntitlementService`, `/internal/dev/grant-plus` (404 en prod).

**Criterios de aceptación (verificables):**

- [ ] Tests con respuestas grabadas: ACTIVE → Plus; IN_GRACE_PERIOD → Plus; ON_HOLD → gratis; CANCELED antes del vencimiento → Plus; EXPIRED → gratis.
- [ ] Test: `obfuscatedAccountId` distinto → 403.
- [ ] Test: la misma RTDN dos veces se procesa una sola vez.
- [ ] Test: con `ENV=prod`, `/internal/dev/grant-plus` → 404.
- [ ] Test: una compra con `acknowledgementState=PENDING` llama a `acknowledge` en el servidor exactamente una vez (Fake de la API de Play); una ya reconocida no lo llama.
- [ ] Test: RTDN `SUBSCRIPTION_REVOKED` (reembolso) → REVOKED y sin Plus de inmediato.
- [ ] Test: el mismo `purchaseToken` verificado desde otra cuenta → 403 FORBIDDEN y no cambia el entitlement de la primera.

#### E7.2 · Billing en el cliente

- **Rama:** `feature/billing-client`
- **Tamaño:** M · **Épica:** E7 ParcheU+ · **Major:** Sí
- **Ola:** 8 (5-8 nov, sprint 2) · **Dueño:** I3
- **Depende de:** E7.0, E0.7
- **En paralelo con:** E3.4, E4.2, E4.6, E4.7, E8.1
- **Pantallas del inventario:** -
- **Archivos y módulos:** android/app/.../data/billing/**

**Descripción.** `BillingRepository` sobre Play Billing Library 8 (`billing-ktx`, versión fijada en E0.7; parte del `BillingClientProvider` de E7.0): conexión y reconexión, `queryProductDetails` de `parcheu_plus` (planes `mensual` y `anual`), `launchBillingFlow` con `obfuscatedAccountId = sha256(userId)`, compra → `verify` → refrescar entitlement, compras pendientes, restaurar con `queryPurchasesAsync` y deep link a la gestión de suscripciones de Play. Fake para tests.

**Criterios de aceptación (verificables):**

- [ ] Test: el precio mensual equivalente del anual se calcula desde los micros de `ProductDetails` ($45.000 → $3.750/mes) y nunca está escrito a mano.
- [ ] Test: cancelación del usuario no muestra error; compra PENDING muestra 'verificación pendiente'.
- [ ] Test: restaurar llama a `verify` por cada compra activa.
- [ ] Test: `obfuscatedAccountId` es `sha256(userId)` en hex minúscula (vector conocido) y nunca el userId en claro.
- [ ] Verificación manual con un license tester en la versión **0.3 (13 nov)** de la pista cerrada, junto con E7.1 y E7.3; antes, la conexión y `ProductDetails` se prueban con la 0.2.

#### E7.3 · Pantallas de ParcheU+

- **Rama:** `feature/paywall-screens`
- **Tamaño:** M · **Épica:** E7 ParcheU+ · **Major:** No
- **Ola:** 10 (10-12 nov, sprint 3) · **Dueño:** I2
- **Depende de:** E7.2, E1.4
- **En paralelo con:** E5.4, E6.1, E6.2, E6.3, E7.1, E8.2, E8.5, E9.1, E9.2, E10.5, E10.6
- **Pantallas del inventario:** G1, G2, G3, G4, I4
- **Archivos y módulos:** android/app/.../feature/plus/**

**Descripción.** G1 (anual por defecto, tabla comparativa, cargando precios, error), G2 (hojas de límite de simulaciones, match diario e informe mensual), G3 (anual, mensual y verificación pendiente), G4 (activa, cancelada, en gracia y mensual; restaurar; cancelar → Play) e I4. Tono honesto, sin contadores falsos. `PLUS_REQUIRED` abre G1 y `QUOTA_EXCEEDED(kind)` abre la G2 correspondiente.

**Criterios de aceptación (verificables):**

- [ ] Test de navegación: cada `AppError` premium abre la pantalla correcta.
- [ ] Test: el texto de ahorro del plan anual se calcula desde `ProductDetails` (mensual $5.000 y anual $45.000 → '$3.750/mes' y el ahorro según ADR-0029, que el usuario confirma antes del 10 nov); ningún precio está escrito a mano.
- [ ] Test de tono honesto: un test recorre los `strings.xml` de `feature/plus` y falla si encuentra patrones de urgencia falsa ('solo quedan', 'últimas', 'oferta termina', 'personas viendo', contadores con cifras) y la UI de G1-G4 no usa temporizadores (lista blanca de composables).
- [ ] Roborazzi de todas las variantes de G1-G4 e I4.

#### E7.4 · Auditoría del contenido premium

- **Rama:** `feature/premium-gating-audit`
- **Tamaño:** S · **Épica:** E7 ParcheU+ · **Major:** Sí
- **Ola:** 12 (18-21 nov, sprint 3) · **Dueño:** I1
- **Depende de:** E7.1, E6.3, E6.4, E6.5, E6.6, E4.6, E4.2, E4.8
- **En paralelo con:** E10.4
- **Pantallas del inventario:** -
- **Archivos y módulos:** backend/src/test/.../premium/**

**Descripción.** Test de contrato parametrizado sobre todos los endpoints premium (informe, transcripción, fof, comunidades, evolución, connect-requests, escenarios premium, prioridad de fila): para un usuario gratis la respuesta no contiene campos premium. Comprueba también que reportar y bloquear nunca devuelven 402.

**Criterios de aceptación (verificables):**

- [ ] El test recorre la lista `PremiumEndpoints` y falla si un endpoint nuevo marcado `@Premium` no está en ella.
- [ ] Test: un usuario gratis con todos los cupos agotados puede reportar y bloquear (200/201).
- [ ] Informe de la auditoría en el PR (tabla endpoint → campos ocultos).

### E8 · Seguridad y privacidad (épica mayor)

Reportar y bloquear desde todas partes (incluido contenido de IA), ajustes de privacidad y cierre de sesión, borrado y exportación, retención, páginas web legales y moderación.

#### E8.1 · Reportar y bloquear

- **Rama:** `feature/report-block`
- **Tamaño:** M · **Épica:** E8 Seguridad y privacidad · **Major:** Sí · **Vertical:** Sí
- **Ola:** 8 (5-8 nov, sprint 2) · **Dueño:** I3 (UI y API)
- **Depende de:** E5.1, E1.4
- **En paralelo con:** E3.4, E4.2, E4.6, E4.7, E7.2
- **Pantallas del inventario:** H1, H2
- **Archivos y módulos:** backend/.../safety/**, android/app/.../feature/safety/**

**Descripción.** Vertical. `POST /v1/reports` (motivo, detalle, chat; adjunta una instantánea de los últimos 20 mensajes del chat en `snapshot`), `POST/DELETE /v1/blocks/{userId}` (bloqueo inmediato: cierra chats, oculta en grafo, fila y chat, cancela pendientes; las vistas de fof y presentaciones aplican el bloqueo en E6.3 y E6.2). UI H1 (incluida la opción **Silenciar**, que llama `POST /v1/chats/{id}/mute`), H2 y confirmaciones de bloqueo y de reporte enviado (nuevas). Puntos de entrada en esta feature: F9 y F3; el resto llega en E8.7. Si el motivo es autolesión la respuesta trae `helpResources` y se muestran (Línea 192 opción 4, nacional; Línea 106; 123 emergencias; verificar vigencia y cobertura en Santander antes de la 0.2, ADR-0026). Texto de H2: 'Lo revisamos lo antes posible; el bloqueo es inmediato'. Log `report_created` que dispara la alerta por correo de E10.5.

**Criterios de aceptación (verificables):**

- [ ] Test de integración: tras bloquear, el bloqueado desaparece del grafo y de la fila, y el chat se cierra (envío → 403 BLOCKED).
- [ ] Test: un usuario gratis con cupos en 0 puede reportar y bloquear.
- [ ] Test: el reporte guarda en `snapshot` los últimos 20 mensajes del chat (o todos si hay menos), en orden, sin mensajes de otros chats.
- [ ] Test: motivo `SELF_HARM` → `helpResources` no vacío; otros motivos → vacío.
- [ ] Test: crear un reporte escribe el log estructurado `report_created` (sin texto de mensajes) que usa la métrica de alerta.
- [ ] Test UI: 'Silenciar' en H1 cambia `muted` y cierra la hoja sin bloquear.
- [ ] Roborazzi de H1, H2, confirmaciones y recursos de ayuda.

#### E8.2 · Ajustes de privacidad

- **Rama:** `feature/privacy-settings`
- **Tamaño:** M · **Épica:** E8 Seguridad y privacidad · **Major:** No · **Vertical:** Sí
- **Ola:** 10 (10-12 nov, sprint 3) · **Dueño:** I2 (UI) + I1 (API)
- **Depende de:** E2.5, E1.4
- **En paralelo con:** E5.4, E6.1, E6.2, E6.3, E7.1, E7.3, E8.5, E9.1, E9.2, E10.5, E10.6
- **Pantallas del inventario:** H3
- **Archivos y módulos:** backend/.../identity/privacy/**, android/app/.../feature/settings/** (excepto `delete/` y `export/`, de E8.3), android/app/.../data/auth/SignOut.kt

**Descripción.** Vertical (API de I1, UI de I2). `GET/PATCH /v1/me/privacy`. H3: modos (activar Pareja pasa por B3), visibilidad NAME / ANONYMOUS ('Alguien', por defecto) / HIDDEN, aprender de chats por modo (Pareja apagado por defecto), enlaces a 'Personas bloqueadas' (pantalla de E8.7), borrar cuenta y descargar mis datos (E8.3), términos y política de privacidad (Ley 1581) y **Cerrar sesión**. Cerrar sesión: `DELETE /v1/me/fcm-token`, `FirebaseAuth.signOut()`, `CredentialManager.clearCredentialState()`, borrado de DataStore (último grafo, progreso de B1, preferencias de sesión; se conserva el tema), `FirebaseFirestore.terminate()` + `clearPersistence()` y navegación a A4 sin historial.

**Criterios de aceptación (verificables):**

- [ ] Test: apagar el aprendizaje de un modo inserta una fila en `consents` y borra las memorias PENDING de ese modo.
- [ ] Test: activar Pareja desde H3 sin edad verificada abre B3.
- [ ] Test (Fakes): 'Cerrar sesión' borra el token FCM en el servidor, limpia el DataStore de sesión y la caché de Firestore, y deja A4 como única entrada del back stack.
- [ ] Roborazzi de H3 (incluida la fila 'Cerrar sesión').

#### E8.3 · Borrado y exportación de cuenta

- **Rama:** `feature/account-deletion`
- **Tamaño:** M · **Épica:** E8 Seguridad y privacidad · **Major:** Sí
- **Ola:** 9 (8-10 nov, sprints 2-3) · **Dueño:** I1
- **Depende de:** E5.1, E3.1
- **En paralelo con:** E4.9
- **Pantallas del inventario:** H3 (borrado y descarga de datos; pantallas nuevas)
- **Archivos y módulos:** backend/.../identity/deletion/**, backend/.../identity/export/**, android/app/.../feature/settings/delete/**, android/app/.../feature/settings/export/**

**Descripción.** `DELETE /v1/me`: borrado lógico inmediato (`deleted_at`, invisible en todo) y tarea `purge-user` (cola `maintenance`) que sigue la tabla de arquitectura.md §10.2: filas personales en Postgres; mensajes en chats compartidos anonimizados ('Usuario eliminado') en Postgres y Firestore; **simulaciones compartidas**: se conserva para el otro solo el puntaje y la fecha, con `transcript` y `report` puestos en `null` y el participante como 'Usuario eliminado'; documentos Firestore `simulations/{id}` y `matchTickets/{uid}` borrados; datos personales en **jsonb** limpiados según el registro `PersonalJsonPaths` (`reports.snapshot`, `simulations.transcript/report`, `notifications.payload`, `purchase_events.payload`); tokens FCM y usuario de Firebase Auth. Se conserva lo que exige la ley (consentimientos y eventos de compra sin datos de contacto, ADR-0025). Aviso de que la suscripción se cancela en Play. `GET /v1/me/export` (JSON con perfil, respuestas, memorias, consentimientos, conexiones y mensajes propios). En la app: confirmación de borrado y **'Descargar mis datos'** (descarga el JSON y lo guarda con `ACTION_CREATE_DOCUMENT` o lo comparte con la hoja del sistema).

**Criterios de aceptación (verificables):**

- [ ] Test: tras la purga, ninguna tabla con columna de usuario contiene el id (consulta generada desde el catálogo) y ninguna columna jsonb registrada en `PersonalJsonPaths` contiene el id, el nombre ni un mensaje 'canario' del usuario (búsqueda en `col::text`); un test de arquitectura falla si aparece una columna jsonb nueva sin clasificar.
- [ ] Test: una simulación compartida queda para el otro con puntaje y sin `transcript` ni `report`; los documentos Firestore `simulations/{id}` y `matchTickets/{uid}` del usuario ya no existen (emulador).
- [ ] Test: los mensajes del usuario en chats compartidos quedan anonimizados y los del otro intactos.
- [ ] Test: la exportación no incluye datos privados de otros usuarios (solo su id anónimo).
- [ ] Test UI (Fakes): 'Descargar mis datos' pide el JSON y lanza el intent de crear documento con nombre `parcheu-datos-<fecha>.json`.
- [ ] Roborazzi de la confirmación de borrado y de 'Descargar mis datos'.

#### E8.4 · Páginas web legales

- **Rama:** `feature/privacy-web`
- **Tamaño:** S · **Épica:** E8 Seguridad y privacidad · **Major:** No
- **Ola:** 4 (27-29 oct, sprint 1) · **Dueño:** I3
- **Depende de:** E0.8
- **En paralelo con:** E0.4, E0.6, E1.1, E1.2, E3.2, E7.0
- **Pantallas del inventario:** -
- **Archivos y módulos:** firebase/hosting/public/** (no toca `firebase.json` ni workflows), scripts/test/legal-pages.sh, docs/play-console.md

**Descripción.** Contenido de Firebase Hosting (lo despliega el workflow `firebase-deploy` de E0.8): `/privacidad` (responsable, datos recolectados, finalidades, procesamiento con IA en Vertex AI, **transferencia internacional a EE. UU.** (ADR-0020), **tabla de retención** copiada de arquitectura.md §10.2, Ley 1581 de 2012, derechos de consulta, actualización, supresión y revocación, canal de contacto), `/terminos` (18+, conducta, contenido generado por usuarios y por IA, suscripción), `/borrar-cuenta` (pasos en la app y solicitud por correo para quien ya no tiene la app), página de respaldo para `/i/{codigo}` y `/.well-known/assetlinks.json` (se completa en E6.1). Versión de los textos = `LEGAL_TEXT_VERSION` de E2.1. Necesarias para la prueba cerrada del 29 oct.

**Criterios de aceptación (verificables):**

- [ ] `scripts/test/legal-pages.sh` corre en el PR con el emulador de Hosting y verifica que cada página responde 200 y contiene sus secciones obligatorias (incluidas 'Retención', 'Transferencia internacional' y 'Ley 1581').
- [ ] Tras mezclar, la URL de dev responde 200 (desplegada por `firebase-deploy`); con `v0.1.0` la de prod también.
- [ ] La URL de `/privacidad` de prod queda registrada en docs/play-console.md y la versión del texto coincide con `LEGAL_TEXT_VERSION`.

#### E8.5 · Administración de reportes

- **Rama:** `feature/admin-reports`
- **Tamaño:** S · **Épica:** E8 Seguridad y privacidad · **Major:** No
- **Ola:** 10 (10-12 nov, sprint 3) · **Dueño:** I3 (UI y API)
- **Depende de:** E8.1
- **En paralelo con:** E5.4, E6.1, E6.2, E6.3, E7.1, E7.3, E8.2, E9.1, E9.2, E10.5, E10.6
- **Pantallas del inventario:** -
- **Archivos y módulos:** backend/.../admin/**, firebase/hosting/admin.html, scripts/grant-admin.sh

**Descripción.** Vertical con dueño único I3 (I1 aprueba la parte de `backend/`). API `/v1/admin/reports` (filtra por `kind`: USER o AI_CONTENT) y `POST /v1/admin/reports/{id}/resolve` (descartar, ocultar mensaje, suspender 24 h, banear), protegida por el claim `admin`. Página estática `admin.html` en Hosting con Firebase Auth JS. Script `scripts/grant-admin.sh` para dar el claim a los 3 integrantes.

**Criterios de aceptación (verificables):**

- [ ] Test: sin claim admin → 403.
- [ ] Test: suspender pone `suspended_until` y el usuario recibe 403 SUSPENDED al escribir mensajes o entrar a la fila.
- [ ] Verificación manual: resolver un reporte desde `admin.html` en dev.

#### E8.6 · Moderación con IA

- **Rama:** `feature/ai-moderation`
- **Tamaño:** S · **Épica:** E8 Seguridad y privacidad · **Major:** No
- **Ola:** 11 (13-17 nov, sprint 3) · **Dueño:** I1
- **Depende de:** E8.5, E0.9
- **En paralelo con:** E4.8, E6.4, E6.5, E6.6, E8.7, E8.8, E9.3, E10.8
- **Pantallas del inventario:** -
- **Archivos y módulos:** backend/.../safety/moderation/**

**Descripción.** Moderación asíncrona por la cola `ai-light` con el modelo lite: categorías acoso, sexual, odio, amenaza, autolesión, spam, datos personales y posible menor. Severidad alta → mensaje oculto (Postgres y Firestore) y reporte automático. Autolesión → mensaje del sistema al autor con recursos de ayuda. 'Posible menor' → reporte automático de prioridad alta (la app es 18+; un admin decide si suspende). Decisión fail-open en ADR-0026. Si el LLM falla, el mensaje queda visible y se registra (fail-open). Recortable si el presupuesto de IA se aprieta (no es una de las 17 funciones).

**Criterios de aceptación (verificables):**

- [ ] Test con FakeLlm: severidad alta oculta el mensaje y crea un reporte AUTO.
- [ ] Test: autolesión inserta el mensaje de recursos; severidad baja no hace nada.
- [ ] Test: error del LLM → mensaje visible y log `moderation_failed`.

#### E8.7 · Reportar y bloquear desde todas partes

- **Rama:** `feature/report-block-everywhere`
- **Tamaño:** M · **Épica:** E8 Seguridad y privacidad · **Major:** No · **Vertical:** Sí
- **Ola:** 11 (13-17 nov, sprint 3) · **Dueño:** I3
- **Depende de:** E8.1, E8.2, E3.4, E4.4, E4.6
- **En paralelo con:** E4.8, E6.4, E6.5, E6.6, E8.6, E8.8, E9.3, E10.8
- **Pantallas del inventario:** H1 desde D2, F2, C5, E3, D6 y F8; 'Personas bloqueadas' (pantalla nueva desde H3)
- **Archivos y módulos:** backend/.../safety/reports/** (ampliación), backend/.../safety/blocks/** (listado), android/app/.../feature/safety/blocked/**, y solo el punto de entrada (menú o ícono) en feature/parche/NodeSheet*, feature/conocer/match/MatchFound*, feature/invites/**, feature/presentar/proposal/**, feature/parche/fof/** y feature/conocer/results/transcript/**

**Descripción.** Vertical con dueño único I3 (I1 aprueba `backend/`, I2 aprueba los cambios de pantallas). Cumple 'reportar y bloquear siempre gratis y accesible' y la política de Play sobre contenido generado por IA. Agrega la acción 'Reportar o bloquear' (abre H1) en D2 (hoja del nodo), F2 (match anónimo, ícono de bandera), C5 (invitación PENDING recibida), E3 (propuesta de presentación) y D6 (amigo de amigo, por `anonId`), y 'Reportar contenido de la IA' en F8 y la transcripción, que crea un reporte `kind=AI_CONTENT` con `simulationId` y turno (C4 lo agrega E9.3 con esta misma API). API: `POST /v1/reports` acepta `targetUserId`, `anonId` (el servidor lo resuelve con la HMAC del observador y nunca devuelve el id real) o `simulationId` + `turnIndex`; `GET /v1/blocks` alimenta la pantalla nueva 'Personas bloqueadas' (desde H3) con 'Desbloquear' (`DELETE /v1/blocks/{userId}`).

**Criterios de aceptación (verificables):**

- [ ] Test de UI (Robolectric): en D2, F2, C5 (pendiente), E3 y D6 existe una acción con descripción 'Reportar o bloquear' que navega a `Report`; en F8 existe 'Reportar contenido de la IA'.
- [ ] Test: reportar por `anonId` crea el reporte contra el usuario correcto sin exponer su id; un `anonId` generado para otro observador → 404.
- [ ] Test: un reporte `AI_CONTENT` guarda `simulationId`, `turnIndex` y el texto del turno en `snapshot` y aparece en `admin.html` con su tipo.
- [ ] Test: bloquear desde F2 cancela el match y los tickets de ambos; desbloquear no restaura conexiones ni chats.
- [ ] Roborazzi de 'Personas bloqueadas' (con ítems y vacía) y de los nuevos puntos de entrada.

#### E8.8 · Retención y purga de datos

- **Rama:** `feature/data-retention`
- **Tamaño:** S · **Épica:** E8 Seguridad y privacidad · **Major:** No
- **Ola:** 11 (13-17 nov, sprint 3) · **Dueño:** I1
- **Depende de:** E8.3
- **En paralelo con:** E4.8, E6.4, E6.5, E6.6, E8.6, E8.7, E9.3, E10.8
- **Pantallas del inventario:** -
- **Archivos y módulos:** backend/.../identity/retention/**

**Descripción.** Implementa la política de retención de arquitectura.md §10.2 (ADR-0025) con un job diario (Scheduler → `/internal/jobs/retention`, 03:30 Bogotá): notificaciones con más de 90 días; `ai_usage.user_id` a `null` a los 90 días (los agregados diarios se conservan); documentos Firestore `simulations/{id}` de simulaciones terminadas hace más de 30 días y `matchTickets` vencidos; `reports.snapshot` a `null` 180 días después de resolverse; chats SCENARIO cerrados hace más de 30 días; `transcript` de simulaciones con más de 12 meses; `match_queue` y `matches` vencidos hace más de 7 días; cuentas con `deleted_at` cuya purga falló, reintentadas. Idempotente, con métrica `retention_purged_total{regla}`.

**Criterios de aceptación (verificables):**

- [ ] Test con reloj fijo: para cada regla, un registro justo antes del plazo se conserva y uno justo después se purga o anonimiza.
- [ ] Test: ejecutar el job dos veces seguidas no cambia nada la segunda vez.
- [ ] Test con el emulador de Firestore: borra los `simulations/{id}` vencidos y conserva los recientes.

### E9 · Perfil y gemelo

Perfil C3, extracción de memorias con consentimiento y entrenamiento C4.

#### E9.1 · Perfil y gemelo

- **Rama:** `feature/profile-twin`
- **Tamaño:** M · **Épica:** E9 Perfil y gemelo · **Major:** No
- **Ola:** 10 (10-12 nov, sprint 3) · **Dueño:** I2
- **Depende de:** E2.3, E2.6, E1.4
- **En paralelo con:** E5.4, E6.1, E6.2, E6.3, E7.1, E7.3, E8.2, E8.5, E9.2, E10.5, E10.6
- **Pantallas del inventario:** C3
- **Archivos y módulos:** android/app/.../feature/profile/** (excepto `train/`, de E9.3)

**Descripción.** C3: perfil (carrera, universidad, intereses) con 'Editar perfil' que reutiliza B0 de E2.6 en modo edición (la fecha de nacimiento no se edita), gemelo (nivel, xp, rasgos), gráfico 'Qué tanto te conoce' con puntos **semanales** de `GET /v1/twin/evolution`, chips de modos, entradas a C4, H3 (engranaje), G4 (suscripción) y C5 (agregar amigos). Estados del gráfico: 0 puntos → 'Tu gemelo aún está aprendiendo: califica respuestas en Entrena a tu gemelo'; 1 punto → valor único sin línea; ≥ 2 → línea. El mockup de '4 meses de historial' solo se reproduce con datos demo (dev).

**Criterios de aceptación (verificables):**

- [ ] ViewModel con Fakes: 0 puntos → estado vacío; 1 punto → valor único; 3 puntos → línea.
- [ ] Test: 'Editar perfil' abre B0 en modo edición con los datos actuales y guarda con `PATCH /v1/me`; la fecha no es editable.
- [ ] Roborazzi de C3 con 0, 1 y 16 puntos (equivalente a 4 meses, datos demo).

#### E9.2 · Extracción de memorias

- **Rama:** `feature/memory-extraction`
- **Tamaño:** M · **Épica:** E9 Perfil y gemelo · **Major:** Sí
- **Ola:** 10 (10-12 nov, sprint 3) · **Dueño:** I1
- **Depende de:** E5.1, E0.9, E2.3
- **En paralelo con:** E5.4, E6.1, E6.2, E6.3, E7.1, E7.3, E8.2, E8.5, E9.1, E10.5, E10.6
- **Pantallas del inventario:** -
- **Archivos y módulos:** backend/.../twin/memories/**

**Descripción.** Job nocturno (Scheduler → `/internal/jobs/memories`) que encola una tarea por usuario: toma solo los mensajes **propios** con `learnable=true` y consentimiento vigente para el modo, desde la última ejecución; el modelo lite extrae ≤ 5 memorias candidatas saneadas (sin teléfonos, correos ni direcciones) en estado PENDING, **cada una con el modo del chat de origen** (`twin_memories.mode`, no nulo); los mensajes de chats de modos distintos se procesan en lotes separados para que una memoria nunca mezcle modos. Cola `ai-light`. `GET /v1/twin/memories`, `POST .../{id}/accept|reject`; aceptar incrementa la versión de la persona (invalida la caché de simulaciones).

**Criterios de aceptación (verificables):**

- [ ] Test: los mensajes de otras personas nunca entran al prompt (FakeLlm captura el prompt).
- [ ] Test: con el consentimiento revocado no se extrae nada aunque haya mensajes `learnable=true`.
- [ ] Test: el saneador elimina '3001234567' y 'a@b.co'.
- [ ] Test: aceptar una memoria sube `twins.version` en 1.
- [ ] Test (captura del prompt): con mensajes propios en un chat de Pareja y otro de Estudio, cada llamada de extracción contiene mensajes de un solo modo y cada memoria resultante queda con el modo de su chat.
- [ ] Test: `GET /v1/twin/memories` devuelve el modo de cada memoria y C4 lo muestra como chip (contrato).

#### E9.3 · Entrena a tu gemelo

- **Rama:** `feature/train-twin-ui`
- **Tamaño:** M · **Épica:** E9 Perfil y gemelo · **Major:** No · **Vertical:** Sí
- **Ola:** 11 (13-17 nov, sprint 3) · **Dueño:** I2 (UI) + I1 (API)
- **Depende de:** E9.2, E9.1
- **En paralelo con:** E4.8, E6.4, E6.5, E6.6, E8.6, E8.7, E8.8, E10.8
- **Pantallas del inventario:** C4
- **Archivos y módulos:** backend/.../twin/feedback/**, android/app/.../feature/profile/train/**

**Descripción.** C4: memorias pendientes y aceptadas ('Ver todas'), calificar respuestas simuladas del propio gemelo y una pregunta de control semanal (el gemelo predice la respuesta antes de que el usuario conteste). `GET /v1/twin/feedback-prompts`, `POST /v1/twin/feedback`. Métrica 'Qué tanto te conoce' = 50 % calificaciones positivas + 50 % predicciones acertadas, calculada **cada semana** por el job `twin-accuracy` (Scheduler, domingos 23:00 Bogotá → `/internal/jobs/twin-accuracy`) y guardada en `twin_accuracy(user_id, week, accuracy)`; sin datos de la semana no se guarda punto. C4 incluye 'Reportar contenido de la IA' sobre una respuesta del gemelo (crea un reporte `AI_CONTENT`, API de E8.7).

**Criterios de aceptación (verificables):**

- [ ] Test unitario de la métrica con un fixture (8 de 10 positivas y 3 de 4 aciertos → 77,5 %).
- [ ] Test: la predicción se guarda antes de mostrar la pregunta al usuario.
- [ ] Test (reloj fijo): el job `twin-accuracy` escribe un punto por usuario con datos en la semana y ninguno para usuarios sin datos; ejecutarlo dos veces no duplica.
- [ ] Roborazzi de C4 con memoria pendiente, aceptada y respuesta por calificar.

### E10 · Publicación y calidad (épica mayor)

Play Console, pipeline de release, prueba cerrada temprana, tema claro y accesibilidad, observabilidad, e2e, ficha, docs y release 1.0.

#### E10.1 · Configuración de Play Console

- **Rama:** `feature/play-console-setup`
- **Tamaño:** S · **Épica:** E10 Publicación y calidad · **Major:** No
- **Ola:** 1 (22-28 oct, sprint 1) · **Dueño:** I3 (tarea humana)
- **Depende de:** nada
- **En paralelo con:** E0.1
- **Pantallas del inventario:** -
- **Archivos y módulos:** docs/play-console.md

**Descripción.** Tarea humana desde el día 1: verificar si la cuenta de desarrollador es personal u organización y su fecha de creación (define la regla de 12 testers × 14 días), verificación de identidad, crear la app `me.santiagoreina.parcheu`, Play App Signing, generar la llave de subida (guardada como secreto de GitHub en base64, nunca en el repo), cuenta de servicio para la API de Play, perfil de pagos, grupo de Google con 15-20 testers y license testers. Documentar en `docs/play-console.md` (sin secretos).

**Criterios de aceptación (verificables):**

- [ ] `docs/play-console.md` registra tipo de cuenta, fecha, estado de verificación, testers inscritos y license testers.
- [ ] La cuenta de servicio puede subir a la pista interna (lo valida E10.2).
- [ ] ≥ 15 correos de testers confirmados antes del 28 oct.

#### E10.2 · Pipeline de release Android

- **Rama:** `feature/android-release-pipeline`
- **Tamaño:** M · **Épica:** E10 Publicación y calidad · **Major:** Sí
- **Ola:** 5 (29-31 oct, sprint 1) · **Dueño:** I3
- **Depende de:** E0.6, E0.7, E7.0
- **En paralelo con:** E0.9, E1.3, E1.5, E2.1, E4.1, E10.3
- **Pantallas del inventario:** -
- **Archivos y módulos:** .github/workflows/android-release.yml, android/app/build.gradle.kts (signing)

**Descripción.** Firma de release desde variables de entorno (secretos de CI), `versionCode` derivado del número de ejecución, construcción del AAB prod y subida a las pistas interna y cerrada con Gradle Play Publisher (verificar compatibilidad con AGP 9; si no la tiene, plan B la acción `r0adkll/upload-google-play`, verificar versión), notas de versión en es-419. La **primera** subida de la app la hizo I3 a mano en E7.0 (Play exige que la primera vez sea desde la consola); este pipeline hace todas las siguientes. Se dispara con un tag `v*` (después de `deploy-prod`) y a mano para la pista interna.

**Criterios de aceptación (verificables):**

- [ ] Un workflow manual construye un AAB firmado y lo sube a la pista interna.
- [ ] Test: `versionCode` es estrictamente creciente entre dos ejecuciones.
- [ ] Ningún keystore ni contraseña aparece en el repo (gitleaks).

#### E10.3 · Prueba cerrada temprana (0.1)

- **Rama:** `feature/early-closed-test`
- **Tamaño:** S · **Épica:** E10 Publicación y calidad · **Major:** Sí
- **Ola:** 5 (29-31 oct, sprint 1) · **Dueño:** I3
- **Depende de:** E7.0, E8.4, E0.8
- **En paralelo con:** E0.9, E1.3, E1.5, E2.1, E4.1, E10.2
- **Pantallas del inventario:** -
- **Archivos y módulos:** docs/play-console.md

**Descripción.** Enviar la versión 0.1 a **revisión en la prueba cerrada el 29 oct**. Contenido: esqueleto navegable de E0.7, enlaces a Términos y Política en A4, y Play Billing de E7.0 (la suscripción `parcheu_plus` ya existe desde el 28 oct). **No incluye login** (E2.2 llega en la ola 6); por eso la 0.1 no trata datos personales. Declaraciones de contenido (URL de privacidad, sin anuncios, clasificación de contenido, público 18+, borrador de Data safety, instrucciones para testers que dicen que es una versión temprana y que habrá perfiles de demostración). Registrar en Firebase prod el SHA-1 y SHA-256 de Play App Signing. **El reloj de 14 días corre desde que Play aprueba la versión y los testers se inscriben (opt-in), no desde la subida:** I3 envía el enlace de inscripción apenas llegue la aprobación (estimada 30-31 oct) y registra la fecha real. El proceso de release es el de PLAN.md §5 ('Releases intermedios'): `release/0.1.0` → `main` (aprueba el usuario) → tag `v0.1.0` → `firebase-deploy` prod (Hosting) → AAB.

**Criterios de aceptación (verificables):**

- [ ] Fechas registradas en `docs/play-console.md`: envío a revisión, aprobación, primer día con ≥ 12 testers inscritos y fecha calculada de los 14 días; ≥ 12 inscritos el mismo día de la aprobación.
- [ ] SHA-1 y SHA-256 de Play App Signing registrados en la app Android de Firebase prod (captura en el PR). El login desde una instalación de Play se verifica en la **0.2** (criterio de E10.8).
- [ ] Una instalación de la 0.1 desde la pista cerrada abre la app y los enlaces de A4 llevan a `/terminos` y `/privacidad` de prod.

#### E10.4 · Tema claro y accesibilidad

- **Rama:** `feature/light-theme-a11y`
- **Tamaño:** S · **Épica:** E10 Publicación y calidad · **Major:** No
- **Ola:** 12 (18-21 nov, sprint 3) · **Dueño:** I2
- **Depende de:** E3.4, E4.7, E4.6, E7.3
- **En paralelo con:** E7.4
- **Pantallas del inventario:** I1, I2, I3, I4
- **Archivos y módulos:** android/designsystem/**, android/app/src/test/**, scripts/lado-a-lado.sh, docs/mockups/png/**

**Descripción.** Pasada completa de I1-I4 y del resto de pantallas en tema claro; contraste AA de todos los pares de texto; etiquetas de TalkBack; escala de fuente 1,3 sin recortes; áreas táctiles ≥ 48dp.

**Criterios de aceptación (verificables):**

- [ ] Roborazzi en claro de C1, D1, F8 y G1 (I1-I4); I2 los compara lado a lado con los PNG exportados de los mockups (`docs/mockups/png/`, generados con `scripts/lado-a-lado.sh`) y deja la tabla 'pantalla → diferencias aceptadas' en el PR. Roborazzi compara contra capturas previas (regresión, `changeThreshold` 0,01); la comparación con los mockups es una revisión humana con checklist (tokens de color exactos, espaciados ±4dp, tipografía y jerarquía), no un umbral de píxeles.
- [ ] Roborazzi con `fontScale=1.3` de C1, D1, F7 y G1 sin texto recortado.
- [ ] El test de contraste de E1.1 pasa sobre la lista final de pares, con `ort` y `mintt` corregidos (ADR-0030) y la regla de texto sobre avatares.

#### E10.5 · Observabilidad

- **Rama:** `feature/observability`
- **Tamaño:** S · **Épica:** E10 Publicación y calidad · **Major:** No
- **Ola:** 10 (10-12 nov, sprint 3) · **Dueño:** I1
- **Depende de:** E0.5, E4.3
- **En paralelo con:** E5.4, E6.1, E6.2, E6.3, E7.1, E7.3, E8.2, E8.5, E9.1, E9.2, E10.6
- **Pantallas del inventario:** -
- **Archivos y módulos:** infra/terraform/modules/platform/monitoring.tf, android/app (Crashlytics), docs/operacion.md

**Descripción.** En Terraform: métricas basadas en logs, alertas (5xx > 2 %, p95 > 2 s, `AiBudgetExceeded`, `report_created`, fallos de simulación), dashboard (peticiones, latencia, simulaciones por estado, costo de IA por día) y uptime de `/v1/health`. Crashlytics en Android con claves de ruta y flavor. Runbook en `docs/operacion.md`.

**Criterios de aceptación (verificables):**

- [ ] `terraform plan` incluye las políticas de alerta y el dashboard.
- [ ] Un error forzado en dev aparece en Error Reporting (verificación documentada).
- [ ] `docs/operacion.md` explica qué hacer ante cada alerta.

#### E10.6 · Smoke e2e

- **Rama:** `feature/e2e-smoke`
- **Tamaño:** M · **Épica:** E10 Publicación y calidad · **Major:** No
- **Ola:** 10 (10-12 nov, sprint 3) · **Dueño:** I3
- **Depende de:** E4.6, E5.1
- **En paralelo con:** E5.4, E6.1, E6.2, E6.3, E7.1, E7.3, E8.2, E8.5, E9.1, E9.2, E10.5
- **Pantallas del inventario:** -
- **Archivos y módulos:** backend/src/e2eTest/**, .github/workflows/deploy-dev.yml

**Descripción.** Dueño I3 (I1 aprueba la parte de `backend/`). Test e2e en Kotlin (etiqueta `e2e`) que corre en `deploy-dev` contra dev con dos usuarios de prueba (tokens personalizados de Firebase Admin canjeados por ID tokens): sesión → respuestas → modos → fila → simulación (Flash real, 1 por despliegue) → decisión mutua → mensaje visible en Firestore.

**Criterios de aceptación (verificables):**

- [ ] `deploy-dev` falla si el smoke falla.
- [ ] El smoke limpia sus datos al terminar (usuarios marcados `is_test`).
- [ ] Duración < 5 min.

#### E10.7 · Documentación final

- **Rama:** `feature/docs-final`
- **Tamaño:** S · **Épica:** E10 Publicación y calidad · **Major:** No
- **Ola:** 13 (24-27 nov, sprint 4) · **Dueño:** Todos
- **Depende de:** E7.4, E10.4
- **En paralelo con:** E10.9
- **Pantallas del inventario:** -
- **Archivos y módulos:** docs/**

**Descripción.** `docs/c4.md` (contexto y contenedores en Mermaid), `docs/operacion.md` completo, `docs/sustentacion.md` (guion de la demo, demo de mutación de privacidad, capturas Roborazzi frente a mockups, recorrido por ADRs, costo de IA), actualización de ADRs con lo aprendido y README.

**Criterios de aceptación (verificables):**

- [ ] Los diagramas de `docs/c4.md` se renderizan en GitHub.
- [ ] `docs/sustentacion.md` tiene un guion de 10 minutos con responsables por parte.
- [ ] Cada ADR tiene estado actualizado y los ADR con spike tienen su sección 'Resultado' completa (un script `scripts/check-adr.sh` falla si queda una sección '_Completar_').
- [ ] Los enlaces internos de docs/ resuelven (script de enlaces en CI).

#### E10.8 · Ficha de Play y Data safety

- **Rama:** `feature/store-listing-data-safety`
- **Tamaño:** S · **Épica:** E10 Publicación y calidad · **Major:** No
- **Ola:** 11 (13-17 nov, sprint 3) · **Dueño:** I3
- **Depende de:** E8.3, E8.4
- **En paralelo con:** E4.8, E6.4, E6.5, E6.6, E8.6, E8.7, E8.8, E9.3
- **Pantallas del inventario:** -
- **Archivos y módulos:** docs/play-console.md, docs/ficha/**

**Descripción.** Ficha es-419 (descripción corta y larga, capturas, gráfico de 1024×500, icono de 512), formulario de Data safety cruzado con el esquema de datos, clasificación IARC, público 18+, declaraciones de contenido generado por usuarios, instrucciones de acceso para revisores (cuenta de prueba con Plus de prueba vía license tester) y **solicitud de acceso a producción el día en que se cumplen los 14 días** (estimado 13-15 nov según la fecha real de inscripción registrada en E10.3). Data safety usa la tabla de retención de arquitectura.md §10.2 y declara el procesamiento con IA y la transferencia a EE. UU. Verifica además, con la 0.2 instalada desde Play, que el login con Google funciona (SHA de App Signing).

**Criterios de aceptación (verificables):**

- [ ] `docs/play-console.md` tiene la tabla Data safety ↔ tablas/columnas del esquema.
- [ ] Solicitud de acceso a producción enviada con fecha registrada.
- [ ] La URL de borrado de cuenta está declarada.
- [ ] Login con Google probado desde una instalación de Play de la 0.2 o posterior (fecha y dispositivo en `docs/play-console.md`).

#### E10.9 · Release 1.0

- **Rama:** `feature/release-1-0`
- **Tamaño:** S · **Épica:** E10 Publicación y calidad · **Major:** Sí
- **Ola:** 13 (24-27 nov, sprint 4) · **Dueño:** I1 (aprueba el usuario)
- **Depende de:** E10.4, E7.4, E10.8
- **En paralelo con:** E10.7
- **Pantallas del inventario:** -
- **Archivos y módulos:** release/1.0.0

**Descripción.** `release/1.0.0` desde dev → PR a main (aprueba el usuario) → tag `v1.0.0` → `deploy-prod` (aprobación) → `terraform apply` de prod por el usuario → `DEMO_USERS_ENABLED=false` y `purge-demo` → AAB a producción con despliegue escalonado (20 % → 100 %). Si la 0.9 ya se envió a revisión de producción apenas llegó el acceso (PLAN.md §6, con publicación administrada), la 1.0 entra como actualización de esa ficha.

**Criterios de aceptación (verificables):**

- [ ] Checklist de publicación de PLAN.md §9 completo (cada casilla con fecha y responsable en el PR).
- [ ] `/v1/health` de prod responde con la versión 1.0.0 y el smoke e2e (E10.6) pasa contra prod con usuarios `is_test` que luego se purgan.
- [ ] En prod no hay usuarios `is_demo` (consulta registrada en el PR) y `DEMO_USERS_ENABLED=false`.
- [ ] Revisión final completa registrada en `docs/revisiones.md` sin hallazgos abiertos de severidad alta.

## 5. Features major

- E0.2 Módulo de contrato compartido (ola 2)
- E0.5 Infraestructura con Terraform (ola 2)
- E0.7 Esqueleto Android (ola 3)
- E0.4 Esquema de base de datos v1 (ola 4)
- E0.6 Pipelines de CI/CD (ola 4)
- E1.1 Tokens de diseño (ola 4)
- E7.0 Billing mínimo y primera subida (ola 4)
- E0.9 Pasarela de IA (ola 5)
- E4.1 Servicio de cuotas (ola 5)
- E10.2 Pipeline de release Android (ola 5)
- E10.3 Prueba cerrada temprana (0.1) (ola 5)
- E2.2 Onboarding y login con Google (ola 6)
- E3.1 API del grafo (ola 6)
- E3.3 Componente G0 en Canvas (ola 6)
- E2.5 Modos, +18 y consentimiento (ola 7)
- E2.6 Perfil básico y edad (ola 7)
- E3.5 Suite de invariantes de privacidad (ola 7)
- E4.3 Motor de simulación (ola 7)
- E5.1 API de chat (ola 7)
- E4.6 Resultados y decisión mutua (ola 8)
- E7.2 Billing en el cliente (ola 8)
- E8.1 Reportar y bloquear (ola 8)
- E8.3 Borrado y exportación de cuenta (ola 9)
- E6.3 Amigos de amigos (ola 10)
- E7.1 Billing en el servidor (ola 10)
- E9.2 Extracción de memorias (ola 10)
- E7.4 Auditoría del contenido premium (ola 12)
- E10.9 Release 1.0 (ola 13)

## 6. Cobertura de las 17 funciones pedidas

| Función | Features |
|---|---|
| 1. Login con Google y onboarding de 3 pasos | E2.1, E2.2 (incluye términos y política) |
| 2. Creación del gemelo, modos, +18, consentimiento | E2.3, E2.4, E2.5, E2.6 |
| 3. Match al azar | E4.2, E4.4 (datos anónimos de E2.6) |
| 4. Modo escenario | E4.8 |
| 5. Modo simulación, resultados y decisión mutua | E4.3, E4.5, E4.6 |
| 6. Chat en vivo | E5.1, E5.2 |
| 7. Grafo 'Tu parche' | E3.1-E3.4 |
| 8. Amigos de amigos | E6.3 |
| 9. Presentar a dos amigos | E6.2 |
| 10. Comunidades automáticas | E6.4 |
| 11. Evolución de la red | E6.5 |
| 12. QR y código de invitación | E6.1 |
| 13. Notificaciones push | E5.3, E5.4 |
| 14. Perfil y evolución del gemelo | E9.1, E9.2, E9.3 |
| 15. Reportar y bloquear | E8.1, E8.5, E8.7 |
| 16. Ajustes de privacidad | E8.2, E8.3, E8.8 |
| 17. Pareja nunca visible a terceros | E3.1, E3.5, E6.3, E6.4, E4.3 y E9.2 (memorias por modo) |
| ParcheU+ (billing, paywall, gating) | E4.1, E7.0-E7.4, E6.6 |

## 7. Cobertura de pantallas del inventario

| Feature | Pantallas |
|---|---|
| E0.7 Esqueleto Android | N0 (stub) |
| E1.1 Tokens de diseño | S0, I1-I4 (base) |
| E1.2 Iconos y marca | S0, A1 |
| E3.2 Layout radial del grafo | G0 |
| E1.3 Componentes base | N0 y componentes de A-I |
| E1.5 Mascota del gemelo | A2, A3, B1, B5, C1, C3, F1-F8 |
| E1.4 Componentes compuestos | Componentes de B-H |
| E2.2 Onboarding y login con Google | A1, A2, A3, A4 |
| E2.6 Perfil básico y edad | B0 (nueva) |
| E2.4 UI de preguntas del gemelo | B1 |
| E2.5 Modos, +18 y consentimiento | B2, B3, B4, B5 |
| E3.3 Componente G0 en Canvas | G0 |
| E3.4 Pantalla Tu parche | D1, D2, D3, D4, D5, I2 |
| E4.4 UI de match al azar | F1, F2 |
| E4.5 UI de simulación | F4, F5, F6 |
| E5.2 UI de chat y lista de chats | F9, lista de chats |
| E4.6 Resultados y decisión mutua | F7, F8, I3 |
| E4.7 Pantalla de inicio | C1, I1 |
| E8.1 Reportar y bloquear | H1, H2 |
| E8.3 Borrado y exportación de cuenta | H3 (borrado, descarga de datos) |
| E5.4 Centro de notificaciones | C2 |
| E6.1 Invitaciones por QR y código | C5 |
| E6.2 Presentar a dos amigos | E1, E2, E3 |
| E6.3 Amigos de amigos | D6, D7 |
| E8.2 Ajustes de privacidad | H3 (con cerrar sesión) |
| E8.7 Reportar y bloquear desde todas partes | H1 desde D2, F2, C5, E3, D6 y F8; personas bloqueadas (nueva) |
| E9.1 Perfil y gemelo | C3 |
| E4.8 Modo escenario | F3 |
| E6.4 Comunidades | D8 |
| E6.5 Evolución de la red | D9 |
| E6.6 Quién quiere conectar contigo | C2 (ítem), C1 |
| E7.3 Pantallas de ParcheU+ | G1, G2, G3, G4, I4 |
| E9.3 Entrena a tu gemelo | C4 |
| E10.4 Tema claro y accesibilidad | I1, I2, I3, I4 |

Pantallas nuevas que no están en los mockups y se construyen con el sistema de diseño: B0 'Tu perfil' y bloqueo por edad (E2.6), personas bloqueadas (E8.7), descargar mis datos (E8.3), lista de chats (E5.2), confirmaciones de bloqueo y reporte (E8.1), confirmación de borrado (E8.3), 'esperando a la otra persona' (E4.6), hojas de límite de match e informe (E4.4, E7.3), variantes de G3/G4 (E7.3), estados vacío, cargando y error, 'Ver todas' (E4.5, E9.3), recursos de ayuda (E8.1).
