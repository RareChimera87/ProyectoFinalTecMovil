# ParcheU · Equipo, reparto semanal y aprendizaje

> Los agentes de IA implementan las features; las personas **son dueñas**: definen el detalle cuando falta, revisan el PR, prueban en un dispositivo real y aprueban. El dueño de cada feature está en [backlog.md](backlog.md). Las reglas de revisión están en [PLAN.md §5](PLAN.md#5-git-flow-y-reglas-de-revisión).

## Roles

| Integrante | Rol | Es dueño de | Aprueba |
|---|---|---|---|
| **I1 · Santiago (el usuario)** | Backend central, IA, datos e infraestructura (lo más difícil) | E0 (salvo E0.7 y E0.8), API de identidad, gemelo, grafo, cuotas, fila, simulación, chat, billing, privacidad (E8.2 API, E8.3, E8.8), moderación con IA, observabilidad y release 1.0 | PR de `release/*` a `main` (único que aprueba), `terraform apply` de prod, **todo PR que toca `backend/`** aunque el dueño sea otro |
| **I2** | Interfaz y experiencia | E0.7, E1 completa, grafo G0, pantallas de onboarding (incluida B0), gemelo, parche, conocer, chat, perfil, ajustes de privacidad (UI), ParcheU+ (G1-G4) y tema claro | **Todo PR que toca `android/app/.../feature/` o `android/designsystem/`** si el dueño es otro; comparación con mockups |
| **I3** | Social, seguridad de usuarios y publicación en Play | E0.8, E7.0, E10.1-E10.3, E10.6, E10.8, Play Console, testers, ficha y Data safety, páginas legales, push y notificaciones, QR, presentar (UI), amigos de amigos (UI), comunidades (UI), evolución, connect-requests, billing en cliente, reportar y bloquear (UI **y** API: E8.1, E8.5, E8.7), API de inicio (E4.7), usuarios demo (E4.9) | Todo lo que se sube a Play; **todo PR que toca `firebase/`, `docs/play-console.md` o el pipeline de release** si el dueño es otro; checklist de publicación |

**Regla de revisión** (PLAN.md §5): todo PR pasa la revisión automática del agente antes del merge; cada feature major exige además la aprobación del **revisor par** de la tabla siguiente; y cada PR que toca una capa ajena la aprueba el dueño de esa capa (I1 backend, I2 pantallas, I3 Firebase y Play). Cuando el dueño de una feature es I2 o I3 y toca `backend/`, I1 es el revisor obligatorio de esa parte.

### Revisores par

| Feature major | Dueño | Revisor par |
|---|---|---|
| E0.2 Contrato | I1 | I2 (consumidora de los DTOs) |
| E0.5 Terraform | I1 | I3 |
| E0.7 Esqueleto Android | I2 | I1 |
| E0.4 Esquema y semillas | I1 | I3 (lo cruza con Data safety) |
| E0.6 CI/CD | I1 | I3 |
| E1.1 Tokens de diseño | I2 | I3 |
| E7.0 Billing mínimo | I3 | I1 |
| E0.9 Pasarela de IA | I1 | I2 |
| E4.1 Cuotas | I1 | I3 |
| E10.2 Pipeline de release | I3 | I1 |
| E10.3 Prueba cerrada 0.1 | I3 | I1 |
| E2.2 Onboarding y login | I2 | I1 |
| E3.1 API del grafo | I1 | I2 |
| E3.3 Componente G0 | I2 | I3 |
| E2.5 Modos y consentimiento | I2 + I1 | I3 |
| E2.6 Perfil básico y edad | I2 + I1 | I3 |
| E3.5 Suite de privacidad | I1 | I3 |
| E4.3 Motor de simulación | I1 | I2 |
| E5.1 API de chat | I1 | I3 |
| E4.6 Resultados y decisión | I2 + I1 | I3 |
| E7.2 Billing en el cliente | I3 | I1 |
| E8.1 Reportar y bloquear | I3 | I1 |
| E8.3 Borrado y exportación | I1 | I3 |
| E6.3 Amigos de amigos | I1 + I3 | I2 |
| E7.1 Billing en el servidor | I1 | I3 |
| E9.2 Extracción de memorias | I1 | I2 |
| E7.4 Auditoría premium | I1 | I3 |
| E10.9 Release 1.0 | I1 | I3 (checklist de Play) e I2 (capturas) |

## Carga por sprint

| Integrante | Sprint 1 (22 oct-2 nov) | Sprint 2 (3-9 nov) | Sprint 3 (10-23 nov) | Sprint 4 (24-30 nov) |
|---|---|---|---|---|
| I1 | 9 dueño único (E0.1-E0.6 sin E0.7, E0.9, E2.1, E4.1) + inicio de E2.3 y E3.1 | E2.3, E3.1, E3.5, E4.2, E4.3, E5.1, E8.3 + API de E2.5, E2.6 y E4.6 | E7.1, E7.4, E8.6, E8.8, E9.2, E10.5 + API de E4.8, E6.2, E6.3, E6.4, E8.2, E9.3 | E10.9, `fix/*` |
| I2 | E0.7, E1.1-E1.3, E1.5, E3.2 + inicio de E1.4, E2.2, E2.4, E3.3 | E1.4, E2.2, E2.4, E3.3, E3.4, E4.4, E4.5, E5.2 + UI de E2.5, E2.6, E4.6 y E4.7 | E7.3, E9.1, E10.4 + UI de E4.8, E8.2 y E9.3 | E10.7 (diseño), `fix/*` de UI |
| I3 | E10.1, E0.8, E7.0, E8.4, E10.2, E10.3 + inicio de E5.3 | E5.3, E7.2, E8.1, E4.9 + API de E4.7 | E5.4, E6.1, E6.5, E6.6, E8.5, E8.7, E10.6, E10.8 + UI de E6.2, E6.3 y E6.4 | E10.7 (publicación), despliegue escalonado |

## Semana a semana

### Semana 1a · 22 – 26 oct (sprint 1, olas 1-3)

| Integrante | Features y tareas | Hitos |
|---|---|---|
| I1 | **22-23 oct, como usuario:** crear `parcheu-dev` y `parcheu-prod` (`scripts/crear-proyectos.sh`), vincular facturación y agregar Firebase si Terraform no puede. E0.1 monorepo y git flow, E0.2 contrato (todos los DTOs), E0.5 Terraform (bootstrap con su ADC y apply de dev), E0.3 esqueleto backend (toolchains y versiones fijadas); redacta con I2 las 12 preguntas y los escenarios con rúbrica | 25 oct: infraestructura de dev aplicada; 26 oct: backend compila con versiones fijadas |
| I2 | E0.7 esqueleto Android (**spike de serialización el 25 oct**, versiones fijadas, enlaces legales en A4); exporta los PNG de los mockups a `docs/mockups/png/`; redacta con I1 el texto de las 12 preguntas | 26 oct: Roborazzi corriendo en CI |
| I3 | **E10.1 desde el 22 oct**: tipo de cuenta, verificación de identidad, app creada, Play App Signing, perfil de pagos, llave de subida, reclutar 15-20 testers (Google Group). E0.8 Firebase, reglas y workflow `firebase-deploy`. Borrador de textos legales para E8.4 | 24 oct: tipo de cuenta confirmado; 26 oct: ≥ 15 correos de testers |

### Semana 1b · 27 oct – 2 nov (sprint 1, olas 4-6)

| Integrante | Features y tareas | Hitos |
|---|---|---|
| I1 | E0.4 esquema y semillas, E0.6 CI/CD (con `revision-agente`, `major-gate` y `review-counter`), E0.9 pasarela de IA (**spike de IDs y créditos de Claude el 30 oct**), E2.1 sesión y términos, E4.1 cuotas; desde el 1 nov E2.3 y E3.1. **27 oct: `terraform apply` de prod** y carga de secretos de prod. Aprueba `release/0.1.0` | 28 oct: `deploy-dev` funcionando; 29 oct: tag `v0.1.0` |
| I2 | E1.1 tokens (con los ajustes AA), E1.2 iconos y marca, E3.2 layout radial, E1.3 componentes base, E1.5 mascota; desde el 1 nov E1.4, E2.2, E2.4, E3.3 | 31 oct: componentes base en Roborazzi |
| I3 | **E7.0: 28 oct AAB 0.0.1 a mano en la pista interna y suscripción `parcheu_plus` creada**, E8.4 páginas legales, E10.2 pipeline de release, **E10.3: 29 oct 0.1 enviada a revisión en prueba cerrada**; envía el enlace de inscripción el día de la aprobación (30-31 oct); desde el 1 nov E5.3 push | **Día de la aprobación: ≥ 12 testers inscritos; se registra la fecha de los 14 días** |

**Revisión de cierre del sprint 1** (2-3 nov): épicas E0 y E1 (mayores).

### Semana 2 · 3 – 9 nov (sprint 2, olas 6-9)

| Integrante | Features y tareas | Hitos |
|---|---|---|
| I1 | Cierra E2.3 y E3.1; E3.5 suite de privacidad (con la propiedad de memorias por modo), **E4.3 motor de simulación**, E5.1 chat, API de E2.5 y E2.6, E4.2 fila, API de E4.6; E8.3 si cabe (recortable de la 0.2) | 6 nov: primera simulación real en dev; 8 nov: aprueba `release/0.2.0` |
| I2 | Cierra E1.4, E2.2, E2.4, E3.3; UI de E2.5 y E2.6 (B0), E4.4 match, E4.5 simulación, E5.2 chat y lista, E3.4 Tu parche, UI de E4.6 y E4.7 | 8 nov: ciclo principal navegable en dev |
| I3 | Cierra E5.3; E7.2 billing en el cliente, **E8.1 reportar y bloquear (UI y API)**, API de E4.7 inicio, E4.9 usuarios demo (recortable); verifica vigencia de Línea 192 opción 4, Línea 106 y 123 para Santander; seguimiento diario de testers; prueba en dispositivo de cada PR de UI; construye y sube la 0.2 | **9 nov: 0.2 en prueba cerrada; primer login desde una instalación de Play** |

**Revisiones:** E2, E3 y E4 (mayores) al cerrar; features major de la semana en las ventanas del 6 y 8 nov. **6 nov:** decisión de recortes de la 0.2 (PLAN.md §6).

### Semana 3a · 10 – 16 nov (sprint 3, olas 9-11)

| Integrante | Features y tareas | Hitos |
|---|---|---|
| I1 | E7.1 billing servidor, E9.2 extracción de memorias, E10.5 observabilidad, API de E6.2, E6.3 y E8.2; lo que quedó de la ola 9 (E8.3); desde el 13 nov API de E4.8 y E6.4 (job) | 13 nov: aprueba `release/0.3.0` |
| I2 | E7.3 pantallas de ParcheU+ (con la decisión de ADR-0029), UI de E8.2 (con cerrar sesión), E9.1 perfil (con edición de B0); desde el 13 nov UI de E4.8 y E9.3 | 12 nov: paywall navegable con precios de `ProductDetails` |
| I3 | E5.4 notificaciones, E6.1 QR y código, UI de E6.2 y E6.3, E8.5 administración de reportes, E10.6 smoke e2e; desde el 13 nov E10.8 ficha y Data safety | **13 nov: 0.3 en prueba cerrada; 14 nov: compra con license tester; ≈14 nov: solicitud de acceso a producción** |

### Semana 3b · 17 – 23 nov (sprint 3, olas 11-12)

| Integrante | Features y tareas | Hitos |
|---|---|---|
| I1 | E8.6 moderación con IA (recortable), E8.8 retención, API de E9.3 (job `twin-accuracy`), E7.4 auditoría premium | 18 nov: decisión de contingencia; 20 nov: aprueba `release/0.9.0` |
| I2 | Cierra E4.8 y E9.3 (UI); E10.4 tema claro y accesibilidad con la comparación lado a lado | 21 nov: todas las capturas comparadas con los mockups (tabla de diferencias aceptadas) |
| I3 | UI de E6.4, E6.5 evolución, E6.6 quién quiere conectar contigo, **E8.7 reportar desde todas partes y bloqueados**; cierra E10.8; respuesta a observaciones de Play; **envía la 0.9 a revisión de producción apenas llega el acceso** | **20 nov: 0.9 en prueba cerrada; 23 nov: congelamiento de funciones** |

**Revisiones:** E7 y E8 (mayores) al cerrar; revisiones de 3 features pequeñas de E5, E6 y E9 abiertas por `review-counter`.

### Semana 4 · 24 – 30 nov (sprint 4, ola 13)

| Integrante | Features y tareas | Hitos |
|---|---|---|
| I1 | E10.9 release 1.0 (rama `release/1.0.0`, revisión final completa, apply de prod, purga de demo), correcciones `fix/*` | **24-25 nov: 1.0 enviada a producción** (actualización sobre la 0.9 si ya estaba en revisión) |
| I2 | Correcciones de UI, capturas finales para la ficha, E10.7 (sección de diseño en sustentación) | - |
| I3 | Despliegue escalonado 20 % → 100 %, monitoreo de la revisión de Play, E10.7 (sección de publicación) | **30 nov: en producción (o, si Play no termina, instalada desde la prueba cerrada); sustentación** |

## Qué debe aprender cada uno

### I1 · Santiago
- **Ktor 3:** plugins (Authentication, StatusPages, ContentNegotiation), `testApplication`.
- **Exposed + Flyway + Cloud SQL Connector:** transacciones, `FOR UPDATE SKIP LOCKED`, migraciones fuera de orden.
- **Cloud Run, Cloud Run Jobs, Cloud Tasks (OIDC), Scheduler, Pub/Sub push.**
- **Terraform** con provider google 7 y **WIF** para GitHub Actions.
- **Vertex AI:** SDK Google Gen AI en modo Vertex, salida estructurada por esquema, precios y cuotas; Claude en Model Garden y su facturación.
- **Prompting de agentes:** personas, delimitación de datos, defensa contra inyección, jueces con rúbrica.
- **Play Developer API:** `subscriptionsv2`, acknowledge, RTDN.
- **Kotest property testing** para la suite de privacidad (incluida la propiedad de memorias por modo).
- **Ley 1581 y retención:** qué exige la autorización, qué se conserva y por cuánto (arquitectura.md §10.2-10.3), para responder en la sustentación.
- Lectura: ADR-0002, 0003, 0004, 0008-0014.

### I2 · Interfaz y experiencia
- **Compose avanzado:** `Canvas` y `DrawScope`, `pointerInput` y hit-testing, semántica para TalkBack, animaciones que respetan la escala de animación.
- **Material 3:** `ColorScheme` personalizado, tipografía con fuentes propias, temas claro y oscuro.
- **Navigation Compose 2.9** con rutas tipadas; **Koin** en Compose.
- **MVVM/UDF:** `StateFlow`, efectos por `Channel`, Turbine.
- **Robolectric + Roborazzi:** grabar y verificar capturas, `@Config(sdk=[35])`, `fontScale`.
- **Accesibilidad WCAG AA:** contraste (los ajustes de ort y mintt en claro, ADR-0030), tamaños táctiles, escala de fuente.
- **Formularios en Compose** (B0: fecha, selector de universidad, chips de intereses) y **Storage Access Framework** (`ACTION_CREATE_DOCUMENT` para 'Descargar mis datos').
- Lectura: inventario de mockups completo, ADR-0005, 0006, 0007; arquitectura.md §2 y §14.

### I3 · Social y publicación
- **Play Console:** tipos de cuenta y regla de 12 testers × 14 días, pistas de prueba, Play App Signing, declaraciones de contenido, Data safety, clasificación IARC, público objetivo, política de contenido generado por usuarios, solicitud de acceso a producción, despliegue escalonado.
- **Play Billing Library 8:** `ProductDetails`, planes base, `launchBillingFlow`, compras pendientes, restaurar, license testers.
- **Firebase:** proyectos, apps Android, SHA-1/SHA-256, reglas de Firestore y su emulador, Hosting, FCM y canales de notificación, App Links con `assetlinks.json`.
- **Google Code Scanner y ZXing.**
- **Ktor básico para revisar y ser dueño de backend sencillo** (E4.7 API, E4.9, E8.1, E8.5, E10.6): rutas, `testApplication`, Exposed en lectura y escritura simples; I1 aprueba esos PR.
- **Primera subida a Play desde la consola, pistas interna y cerrada, publicación administrada y revisión de producción.**
- **Ley 1581 de 2012** (datos personales en Colombia) para la política de privacidad.
- Lectura: PLAN.md §9 (checklist), ADR-0013, 0016, 0017, 0018; arquitectura.md §8 y §9.

## Rituales
- **Diario (15 min, asíncrono en el chat del equipo):** qué se mezcló, qué se revisa hoy, bloqueos. I3 reporta el número de testers inscritos.
- **Revisión de features major:** el revisor par aprueba antes del merge (check `major-gate`) y la revisión post-merge se hace en la ventana de la ola; los hallazgos van a ramas `fix/*` antes de lanzar features dependientes y quedan en `docs/revisiones.md`.
- **Revisión de 3 features pequeñas:** cuando `review-counter` abre el issue, la hace el dueño de la ola en la siguiente ventana de revisión.
- **Cierre de sprint (2, 9 y 23 nov):** demo en dispositivo, revisión de épicas mayores, ajuste del backlog.
- **Contingencias:** el 6 nov se decide qué se recorta de la 0.2 y el 18 nov si se activa el plan de PLAN.md §6.
