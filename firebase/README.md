# Firebase de ParcheU (E0.8)

Reglas, índices y Hosting de Firebase. Decisiones: [ADR-0004](../docs/adr/0004-firestore-canal-solo-lectura.md)
(Firestore solo lectura para clientes) y [ADR-0028](../docs/adr/0028-firebase-hosting-y-admin.md) (Hosting).

| Archivo | Qué es |
|---|---|
| `firebase.json` | Reglas, índices, Hosting (`public/`) y emulador de Firestore |
| `.firebaserc` | Alias `dev` (`parcheu-dev`) y `prod` (`parcheu-prod`) |
| `firestore.rules` | Todo denegado; lectura solo de miembros, participantes o dueño; ninguna escritura de clientes |
| `firestore.indexes.json` | Índices compuestos (vacío: la 1.0 aún no tiene consultas compuestas; las agregan E5.2 y E4.x si las necesitan) |
| `public/` | Hosting vacío (E8.4 agrega `/privacidad`, `/terminos`, `/borrar-cuenta`; E6.1 `assetlinks.json` e `/i/*`) |
| `test/` | Pruebas de las reglas con `@firebase/rules-unit-testing` en el emulador |

## Documentos de Firestore (arquitectura.md §8.2)

`chats/{id}`, `chats/{id}/messages/{id}` (`senderUid`, `system`, `text`, `createdAt`, `hidden`),
`simulations/{id}` y `matchTickets/{uid}`. Solo el backend (Admin SDK, que ignora las reglas) escribe.

## Pruebas locales

El emulador de Firestore necesita Java 21 o superior (el JDK por defecto de la máquina es 17):

```bash
cd firebase
npm ci
JAVA_HOME=/opt/android-studio/jbr PATH=/opt/android-studio/jbr/bin:$PATH npm test
```

## Despliegue

- Automático: `.github/workflows/firebase-deploy.yml` (push a `dev` que toca `firebase/**` → dev; tag `v*` →
  prod con la aprobación del environment `production`).
- Manual a dev: `cd firebase && npx firebase deploy --only firestore:rules,firestore:indexes,hosting --project parcheu-dev`.

## Apps registradas

| Entorno | Paquete | App ID |
|---|---|---|
| dev | `me.santiagoreina.parcheu.dev` | `1:388770932007:android:d6f5452d14d98bf1d43502` |
| prod | `me.santiagoreina.parcheu` | pendiente (el proyecto `parcheu-prod` aún no existe; ver `docs/pendientes-humanos.md`) |

Huellas de debug registradas en dev: SHA-1 `66:39:6A:60:45:47:1C:72:AA:E4:68:37:0B:A8:A2:42:E5:2F:ED:8C` y
SHA-256 `A3:5E:13:90:B5:65:FC:11:00:74:A1:37:B3:8E:E7:C4:0F:62:56:57:FC:F7:03:39:36:85:84:46:3A:85:7B:DB`
(keystore de debug de la máquina de I3; cada desarrollador agrega la suya con
`firebase apps:android:sha:create <APP_ID> <SHA> --project parcheu-dev`).

## google-services.json

Nunca se versiona. En local: `scripts/fetch-google-services.sh dev` (E0.7). En CI: secreto `GOOGLE_SERVICES_JSON_DEV` /
`GOOGLE_SERVICES_JSON_PROD` en base64.
